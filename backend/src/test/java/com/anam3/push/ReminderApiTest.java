package com.anam3.push;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.anam3.support.ApiTestSupport;

/** 모임 1시간 전 알림 · 밤에는 조용히 (T0 = 3/2(월) 18:00 한국 시간) */
class ReminderApiTest extends ApiTestSupport {

    /** 3/3(화) 01:00 한국 시간 */
    private static final Instant NIGHT = T0.plus(Duration.ofHours(7));
    /** 3/3(화) 08:00 한국 시간 */
    private static final Instant MORNING = T0.plus(Duration.ofHours(14));

    @Autowired
    PushNotifier notifier;

    private List<PushType> types(String deviceToken) {
        return pushes.to(deviceToken).stream().map(PushMessage::type).toList();
    }

    private PushMessage last(String deviceToken) {
        List<PushMessage> got = pushes.to(deviceToken);
        assertThat(got).as(deviceToken + " 가 받은 알림").isNotEmpty();
        return got.getLast();
    }

    private static String reschedule(Instant meetAt, Instant deadlineAt) {
        return String.format("{\"title\":\"치맥 번개\",\"description\":\"\",\"place\":\"안암역 앞\",\"categories\":[\"BEER\"],"
                + "\"purposes\":[],\"openChatUrl\":\"\",\"schedule\":{\"meetAt\":\"%s\",\"deadlineAt\":\"%s\"}}",
                meetAt, deadlineAt);
    }

    @Test
    void 성사된_3교시는_모이기_1시간_전에_참여자_모두에게_한_번_알린다() throws Exception {
        String host = signup("godae", "김고대", 61);
        String anam = signup("anam", "이안암", 61);
        String horang = signup("horang", "박호랑", 61);
        registerDevice(host, "host-phone");
        registerDevice(anam, "anam-phone");
        registerDevice(horang, "horang-phone");
        // 5시간 뒤(23:00) 모이는 3교시 셋: 성사 · 성사 전에 마감(무산) · 성사 뒤 취소
        long id = createMeetup(host, 2, null, Duration.ofHours(5), Duration.ofHours(3), null);
        post("/api/meetups/" + id + "/join", anam, null).andExpect(status().isOk());
        long failed = createMeetup(host, 4, null, Duration.ofHours(5), Duration.ofHours(3), null);
        post("/api/meetups/" + failed + "/join", horang, null).andExpect(status().isOk());
        long canceled = createMeetup(host, 2, null, Duration.ofHours(5), Duration.ofHours(3), null);
        post("/api/meetups/" + canceled + "/join", horang, null).andExpect(status().isOk());
        post("/api/meetups/" + canceled + "/cancel", host, null).andExpect(status().isOk());
        pushes.clear();

        clock.set(T0.plus(Duration.ofMinutes(4 * 60 - 1))); // 모임 61분 전
        assertThat(notifier.remindMeetups()).isZero();

        clock.set(T0.plus(Duration.ofHours(4))); // 모임 1시간 전
        assertThat(notifier.remindMeetups()).isEqualTo(1);
        PushMessage remind = last("anam-phone");
        assertThat(remind.type()).isEqualTo(PushType.REMINDER);
        assertThat(remind.title()).isEqualTo("치맥 번개");
        assertThat(remind.body()).isEqualTo("1시간 뒤 3교시예요 · 23:00 · 안암역 앞");
        assertThat(remind.path()).isEqualTo("/meetups/" + id);
        assertThat(last("host-phone").body()).isEqualTo(remind.body()); // 연 사람도 받는다
        assertThat(pushes.to("horang-phone")).isEmpty(); // 무산 · 취소된 3교시는 알리지 않는다

        clock.advance(Duration.ofMinutes(1));
        assertThat(notifier.remindMeetups()).isZero(); // 한 번만
    }

    @Test
    void 모임_시간을_바꾸면_새_시간_1시간_전에_다시_알린다() throws Exception {
        String host = signup("godae", "김고대", 61);
        String anam = signup("anam", "이안암", 61);
        registerDevice(anam, "anam-phone");
        long id = createMeetup(host, 2, null, Duration.ofHours(5), Duration.ofHours(3), null);
        post("/api/meetups/" + id + "/join", anam, null).andExpect(status().isOk());
        clock.set(T0.plus(Duration.ofHours(4)));
        assertThat(notifier.remindMeetups()).isEqualTo(1);

        // 30분 미뤘다 (지난 마감은 다시 정해야 한다)
        Instant later = T0.plus(Duration.ofMinutes(5 * 60 + 30));
        put("/api/meetups/" + id, host, reschedule(later, T0.plus(Duration.ofMinutes(4 * 60 + 30))))
                .andExpect(status().isOk());
        pushes.clear();
        assertThat(notifier.remindMeetups()).isZero(); // 아직 1시간 30분 남았다

        clock.set(later.minus(Duration.ofHours(1)));
        assertThat(notifier.remindMeetups()).isEqualTo(1);
        assertThat(last("anam-phone").body()).isEqualTo("1시간 뒤 3교시예요 · 23:30 · 안암역 앞");
    }

    @Test
    void 방금_연_3교시와_곧_모일_3교시와_끈_사람에게는_보내지_않는다() throws Exception {
        String host = signup("godae", "김고대", 61);
        String anam = signup("anam", "이안암", 61);
        registerDevice(host, "host-phone");
        registerDevice(anam, "anam-phone");

        // 모임 1시간 30분 전에 연 3교시: 방금 모였다
        long quick = createMeetup(host, null, null, Duration.ofMinutes(90), Duration.ofMinutes(60), null);
        post("/api/meetups/" + quick + "/join", anam, null).andExpect(status().isOk());
        pushes.clear();
        clock.set(T0.plus(Duration.ofMinutes(30)));
        assertThat(notifier.remindMeetups()).isZero();
        assertThat(pushes.all()).isEmpty();

        // 서버가 늦게 깨서 모임까지 10분도 안 남았다
        clock.set(T0);
        long late = createMeetup(host, 2, null, Duration.ofHours(3), Duration.ofHours(2), null);
        post("/api/meetups/" + late + "/join", anam, null).andExpect(status().isOk());
        pushes.clear();
        clock.set(T0.plus(Duration.ofMinutes(3 * 60 - 9)));
        assertThat(notifier.remindMeetups()).isZero();

        // 10분 넘게 남았으면 남은 시간으로 알린다. 1시간 전 알림을 끈 사람은 받지 않는다
        clock.set(T0);
        long sleepy = createMeetup(host, 2, null, Duration.ofHours(4), Duration.ofHours(2), null);
        post("/api/meetups/" + sleepy + "/join", anam, null).andExpect(status().isOk());
        put("/api/me/push/settings", anam, "{\"off\":[\"REMINDER\"]}").andExpect(status().isOk());
        pushes.clear();
        clock.set(T0.plus(Duration.ofMinutes(4 * 60 - 25)));
        assertThat(notifier.remindMeetups()).isEqualTo(1);
        assertThat(last("host-phone").body()).isEqualTo("25분 뒤 3교시예요 · 22:00 · 안암역 앞");
        assertThat(pushes.to("anam-phone")).isEmpty();
    }

    @Test
    void 조용한_시간은_한국_시간_밤_12시부터_아침_8시_전까지() {
        assertThat(QuietHours.contains(Instant.parse("2026-03-02T14:59:00Z"))).isFalse(); // 23:59
        assertThat(QuietHours.contains(Instant.parse("2026-03-02T15:00:00Z"))).isTrue(); // 00:00
        assertThat(QuietHours.contains(Instant.parse("2026-03-02T22:59:00Z"))).isTrue(); // 07:59
        assertThat(QuietHours.contains(Instant.parse("2026-03-02T23:00:00Z"))).isFalse(); // 08:00
    }

    @Test
    void 밤에_연_새_3교시와_새_공지는_아침_8시에_알린다() throws Exception {
        clock.set(NIGHT);
        String host = signup("godae", "김고대", 61);
        String manager = managerToken("horang", "박호랑", 61);
        String anam = signup("anam", "이안암", 61);
        String sinchon = signup("sinchon@yonsei.ac.kr", "이신촌", 30);
        registerDevice(host, "host-phone");
        registerDevice(anam, "anam-phone");
        registerDevice(sinchon, "sinchon-phone");

        long open = createMeetup(host, 3, 6, Duration.ofDays(2), Duration.ofDays(1), null);
        long federation = createMeetup(host, 3, 6, Duration.ofDays(2), Duration.ofDays(1), null, "ALL");
        long canceled = createMeetup(host, 3, 6, Duration.ofDays(2), Duration.ofDays(1), null);
        long full = createMeetup(host, null, 2, Duration.ofDays(2), Duration.ofDays(1), null);
        post("/api/notices", manager, "{\"title\":\"종강 파티\",\"content\":\"금요일 7시 종강 파티 합니다\"}")
                .andExpect(status().isCreated());
        assertThat(pushes.to("anam-phone")).isEmpty();
        assertThat(pushes.to("sinchon-phone")).isEmpty();

        // 내 3교시 알림은 밤에도 바로 간다
        post("/api/meetups/" + full + "/join", anam, null).andExpect(status().isOk());
        assertThat(types("host-phone")).containsExactly(PushType.JOIN);
        post("/api/meetups/" + canceled + "/cancel", host, null).andExpect(status().isOk());
        pushes.clear();

        clock.set(MORNING.minus(Duration.ofMinutes(1)));
        assertThat(notifier.announceHeld()).isZero();
        assertThat(pushes.all()).isEmpty();

        clock.set(MORNING);
        assertThat(notifier.announceHeld()).isEqualTo(3); // 취소된 것과 꽉 찬 것은 건너뛴다
        assertThat(types("anam-phone")).containsExactly(PushType.SCHOOL, PushType.FEDERATION, PushType.NOTICE);
        assertThat(pushes.to("anam-phone").getFirst().path()).isEqualTo("/meetups/" + open);
        assertThat(pushes.to("anam-phone").get(1).path()).isEqualTo("/meetups/" + federation);
        assertThat(types("sinchon-phone")).containsExactly(PushType.FEDERATION);
        assertThat(types("host-phone")).containsExactly(PushType.NOTICE); // 내가 연 3교시 알림은 빼고

        clock.advance(Duration.ofMinutes(1));
        assertThat(notifier.announceHeld()).isZero(); // 한 번만
    }

    @Test
    void 밤_12시_전에_연_3교시는_바로_알린다() throws Exception {
        clock.set(T0.plus(Duration.ofMinutes(5 * 60 + 59))); // 23:59
        String host = signup("godae", "김고대", 61);
        String anam = signup("anam", "이안암", 61);
        registerDevice(anam, "anam-phone");
        createMeetup(host, 3, 6, Duration.ofDays(2), Duration.ofDays(1), null);
        assertThat(types("anam-phone")).containsExactly(PushType.SCHOOL);

        clock.set(MORNING);
        pushes.clear();
        assertThat(notifier.announceHeld()).isZero(); // 미룬 것이 없다
    }
}
