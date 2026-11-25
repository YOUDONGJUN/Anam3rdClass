package com.anam3.settlement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.anam3.push.PushMessage;
import com.anam3.push.PushNotifier;
import com.anam3.push.PushType;
import com.anam3.support.ApiTestSupport;

/** 정산 (T0 = 3/2(월) 18:00 한국 시간, 3교시는 23:00 에 모인다) */
class SettlementApiTest extends ApiTestSupport {

    /** 3교시가 모이는 때: 3/2 23:00 한국 시간 */
    private static final Instant MEET = T0.plus(Duration.ofHours(5));
    /** 모인 뒤: 3/2 23:30 */
    private static final Instant AFTER = MEET.plus(Duration.ofMinutes(30));
    /** 3/3 08:00 */
    private static final Instant MORNING = T0.plus(Duration.ofHours(14));

    @Autowired
    PushNotifier notifier;

    private String host;
    private String anam;
    private String horang;
    private long meetupId;
    /** 참여 번호: 0 연 사람(김고대) · 1 이안암 · 2 박호랑 */
    private long hostRef;
    private long anamRef;
    private long horangRef;

    /** 김고대가 열고 이안암 · 박호랑이 참여해 성사된 3교시 (23:00 모임). 모두 폰을 등록했다 */
    private void reachedMeetup() throws Exception {
        host = signup("godae", "김고대", 61);
        anam = signup("anam", "이안암", 61);
        horang = signup("horang", "박호랑", 61);
        registerDevice(host, "host-phone");
        registerDevice(anam, "anam-phone");
        registerDevice(horang, "horang-phone");
        meetupId = createMeetup(host, 2, null, Duration.ofHours(5), Duration.ofHours(3), null);
        post("/api/meetups/" + meetupId + "/join", anam, null).andExpect(status().isOk());
        post("/api/meetups/" + meetupId + "/join", horang, null).andExpect(status().isOk());
        hostRef = participantRef(host, meetupId, 0);
        anamRef = participantRef(host, meetupId, 1);
        horangRef = participantRef(host, meetupId, 2);
        pushes.clear();
    }

    private static String settle(int total, long... refs) {
        StringBuilder list = new StringBuilder();
        for (long ref : refs) {
            list.append(list.isEmpty() ? "" : ",").append(ref);
        }
        return "{\"total\":" + total + ",\"refs\":[" + list + "]}";
    }

    private static String sent(boolean sent) {
        return "{\"sent\":" + sent + "}";
    }

    private String url() {
        return "/api/meetups/" + meetupId + "/settlement";
    }

    private PushMessage last(String deviceToken) {
        List<PushMessage> got = pushes.to(deviceToken);
        assertThat(got).as(deviceToken + " 가 받은 알림").isNotEmpty();
        return got.getLast();
    }

    @Test
    void 함께한_원우_누구든_정산을_만들고_1인_금액은_100원_단위로_올림한다() throws Exception {
        reachedMeetup();
        clock.set(AFTER);
        put("/api/me/pay-links", anam, "{\"tossId\":\"toss.me/anam_kim\",\"kakaoPayUrl\":\"qr.kakaopay.com/Ab12Cd34\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tossId").value("anam_kim"))
                .andExpect(jsonPath("$.kakaoPayUrl").value("https://qr.kakaopay.com/Ab12Cd34"));
        get("/api/meetups/" + meetupId, anam).andExpect(jsonPath("$.canSettle").value(true))
                .andExpect(jsonPath("$.settlement").value(nullValue()));

        // 연 사람이 아닌 이안암이 계산했다: 100,000원 ÷ 3명 = 33,333.3 → 33,400원
        post(url(), anam, settle(100_000, hostRef, horangRef)).andExpect(status().isOk())
                .andExpect(jsonPath("$.canSettle").value(false))
                .andExpect(jsonPath("$.settlement.mine").value(true))
                .andExpect(jsonPath("$.settlement.perPerson").value(33_400))
                .andExpect(jsonPath("$.settlement.headcount").value(3))
                .andExpect(jsonPath("$.settlement.shares", hasSize(2)))
                .andExpect(jsonPath("$.settlement.editable").value(true))
                .andExpect(jsonPath("$.settlement.canRemind").value(false));

        PushMessage request = last("horang-phone");
        assertThat(request.type()).isEqualTo(PushType.SETTLE);
        assertThat(request.body()).isEqualTo("정산 요청 · 1인 33,400원");
        assertThat(request.path()).isEqualTo("/meetups/" + meetupId);
        assertThat(last("host-phone").body()).isEqualTo(request.body());
        assertThat(pushes.to("anam-phone")).isEmpty(); // 계산한 사람에게는 보내지 않는다

        // 나눌 사람이 보는 정산: 계산한 사람 실명과 금액이 붙은 토스 링크
        get("/api/meetups/" + meetupId, horang)
                .andExpect(jsonPath("$.settlement.mine").value(false))
                .andExpect(jsonPath("$.settlement.payer.name").value("이안암"))
                .andExpect(jsonPath("$.settlement.payer.masked").value(false))
                .andExpect(jsonPath("$.settlement.tossUrl").value("https://toss.me/anam_kim/33400"))
                .andExpect(jsonPath("$.settlement.kakaoPayUrl").value("https://qr.kakaopay.com/Ab12Cd34"))
                .andExpect(jsonPath("$.settlement.editable").value(false));
        get("/api/me/settlements", horang).andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].meetupId").value(meetupId))
                .andExpect(jsonPath("$[0].amount").value(33_400))
                .andExpect(jsonPath("$[0].payer.name").value("이안암"));
        get("/api/me/settlements", anam).andExpect(jsonPath("$", empty()));

        // 3교시 하나에 정산 하나
        post(url(), host, settle(50_000, anamRef)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ALREADY_SETTLED"));
    }

    @Test
    void 성사된_3교시가_모인_뒤_7일_안에_함께한_원우만_만든다() throws Exception {
        reachedMeetup();
        String junior = signup("junior", "최후배", 61);

        // 모이기 전
        post(url(), anam, settle(60_000, hostRef)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("NOT_SETTLING"));

        clock.set(AFTER);
        // 함께하지 않은 원우는 만들지도 보지도 못한다
        post(url(), junior, settle(60_000, hostRef)).andExpect(status().isForbidden());
        get("/api/meetups/" + meetupId, junior).andExpect(jsonPath("$.canSettle").value(false))
                .andExpect(jsonPath("$.settlement").value(nullValue()));

        // 나눌 사람 고르기
        post(url(), anam, settle(60_000, anamRef)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("NO_SHARE")); // 나만 골랐다
        post(url(), anam, settle(60_000, 999_999)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_SHARE"));
        post(url(), anam, settle(500, hostRef)).andExpect(status().isBadRequest());
        post(url(), anam, settle(3_000_001, hostRef)).andExpect(status().isBadRequest());

        // 모인 지 7일이 지나면 만들 수 없다
        clock.set(MEET.plus(Duration.ofDays(7)));
        get("/api/meetups/" + meetupId, anam).andExpect(jsonPath("$.canSettle").value(false));
        post(url(), anam, settle(60_000, hostRef)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("NOT_SETTLING"));

        // 무산된 3교시는 정산하지 않는다
        clock.set(T0);
        long failed = createMeetup(host, 4, null, Duration.ofHours(5), Duration.ofHours(3), null);
        post("/api/meetups/" + failed + "/join", anam, null).andExpect(status().isOk());
        clock.set(AFTER);
        post("/api/meetups/" + failed + "/settlement", anam, settle(60_000, participantRef(anam, failed, 0)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("NOT_SETTLING"));
    }

    @Test
    void 보냈어요_받았어요를_누르고_모두_보내면_계산한_원우에게_알린다() throws Exception {
        reachedMeetup();
        clock.set(AFTER);
        post(url(), anam, settle(90_000, hostRef, horangRef)).andExpect(status().isOk());
        pushes.clear();

        put(url() + "/sent", horang, sent(true)).andExpect(status().isOk())
                .andExpect(jsonPath("$.settlement.sentCount").value(1))
                .andExpect(jsonPath("$.settlement.done").value(false));
        get("/api/me/settlements", horang).andExpect(jsonPath("$", empty()));
        // 보낸 원우가 있으면 고칠 수 없다 (취소하고 다시 만든다)
        put(url(), anam, settle(60_000, hostRef)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SETTLEMENT_LOCKED"));
        get("/api/meetups/" + meetupId, anam).andExpect(jsonPath("$.settlement.editable").value(false));

        // 내 몫이 아니면 표시할 수 없다
        put(url() + "/sent", anam, sent(true)).andExpect(status().isForbidden()); // 계산한 사람은 몫이 없다
        put(url() + "/shares/" + hostRef, horang, sent(true)).andExpect(status().isForbidden());

        // 마지막 원우가 보내면 끝, 계산한 원우에게 알린다
        put(url() + "/sent", host, sent(true)).andExpect(status().isOk())
                .andExpect(jsonPath("$.settlement.done").value(true));
        PushMessage done = last("anam-phone");
        assertThat(done.type()).isEqualTo(PushType.SETTLE);
        assertThat(done.body()).isEqualTo("정산 끝! 모두 보냈어요");

        // 누가 취소하면 다시 안 끝난 정산 — 계산한 원우가 현금으로 받았다고 표시해서 끝낸다 (본인이라 알림 없음)
        put(url() + "/sent", host, sent(false)).andExpect(status().isOk())
                .andExpect(jsonPath("$.settlement.done").value(false));
        get("/api/me/settlements", host).andExpect(jsonPath("$", hasSize(1)));
        pushes.clear();
        put(url() + "/shares/" + hostRef, anam, sent(true)).andExpect(status().isOk())
                .andExpect(jsonPath("$.settlement.done").value(true))
                .andExpect(jsonPath("$.settlement.canRemind").value(false))
                .andExpect(jsonPath("$.settlement.remindableAt").value(nullValue()));
        assertThat(pushes.all()).isEmpty();
        put(url() + "/shares/" + 999_999, anam, sent(true)).andExpect(status().isNotFound());
    }

    @Test
    void 계산한_원우만_고치고_취소한다() throws Exception {
        reachedMeetup();
        clock.set(AFTER);
        post(url(), anam, settle(90_000, hostRef, horangRef)).andExpect(status().isOk());
        put(url(), host, settle(60_000, anamRef)).andExpect(status().isForbidden());
        delete(url(), host).andExpect(status().isForbidden());
        pushes.clear();

        // 박호랑은 안 왔다: 90,000원 ÷ 2명
        put(url(), anam, settle(90_000, hostRef)).andExpect(status().isOk())
                .andExpect(jsonPath("$.settlement.perPerson").value(45_000))
                .andExpect(jsonPath("$.settlement.headcount").value(2))
                .andExpect(jsonPath("$.settlement.shares", hasSize(1)));
        assertThat(last("host-phone").body()).isEqualTo("정산을 고쳤어요 · 1인 45,000원");
        assertThat(pushes.to("horang-phone")).isEmpty();
        get("/api/meetups/" + meetupId, horang).andExpect(jsonPath("$.settlement.shares", hasSize(1)));
        get("/api/me/settlements", horang).andExpect(jsonPath("$", empty()));

        delete(url(), anam).andExpect(status().isOk())
                .andExpect(jsonPath("$.settlement").value(nullValue()))
                .andExpect(jsonPath("$.canSettle").value(true));
        put(url() + "/sent", host, sent(true)).andExpect(status().isNotFound());
    }

    @Test
    void 다시_알리기는_안_보낸_원우에게_하루에_한_번() throws Exception {
        reachedMeetup();
        clock.set(AFTER);
        post(url(), anam, settle(90_000, hostRef, horangRef)).andExpect(status().isOk());
        put(url() + "/sent", horang, sent(true)).andExpect(status().isOk());
        pushes.clear();

        post(url() + "/remind", anam, null).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("REMIND_LIMIT"));
        post(url() + "/remind", host, null).andExpect(status().isForbidden());

        clock.set(AFTER.plus(Duration.ofHours(24)));
        get("/api/meetups/" + meetupId, anam).andExpect(jsonPath("$.settlement.canRemind").value(true));
        post(url() + "/remind", anam, null).andExpect(status().isOk())
                .andExpect(jsonPath("$.settlement.canRemind").value(false));
        assertThat(last("host-phone").body()).isEqualTo("아직 안 보낸 정산이 있어요 · 1인 30,000원");
        assertThat(pushes.to("horang-phone")).isEmpty(); // 이미 보냈다
    }

    @Test
    void 밤에_만든_정산_알림은_아침_8시에_보낸다() throws Exception {
        reachedMeetup();
        clock.set(T0.plus(Duration.ofHours(7))); // 3/3 01:00
        post(url(), anam, settle(90_000, hostRef, horangRef)).andExpect(status().isOk());
        assertThat(pushes.all()).isEmpty();

        // 하나는 아침에 다시 알린다 — 그사이 모두 보낸 정산은 요청 대신 "정산 끝"만
        clock.set(T0);
        long other = createMeetup(host, 2, null, Duration.ofHours(5), Duration.ofHours(3), null);
        post("/api/meetups/" + other + "/join", horang, null).andExpect(status().isOk());
        clock.set(T0.plus(Duration.ofHours(7)));
        post("/api/meetups/" + other + "/settlement", horang, settle(40_000, participantRef(horang, other, 0)))
                .andExpect(status().isOk());
        put("/api/meetups/" + other + "/settlement/sent", host, sent(true)).andExpect(status().isOk());
        pushes.clear();

        clock.set(MORNING.minus(Duration.ofMinutes(1)));
        assertThat(notifier.announceHeld()).isZero();
        clock.set(MORNING);
        assertThat(notifier.announceHeld()).isEqualTo(2);
        assertThat(pushes.to("host-phone")).extracting(PushMessage::body).containsExactly("정산 요청 · 1인 30,000원");
        assertThat(pushes.to("horang-phone")).extracting(PushMessage::body)
                .containsExactly("정산 요청 · 1인 30,000원", "정산 끝! 모두 보냈어요");
        assertThat(notifier.announceHeld()).isZero(); // 한 번만
    }

    @Test
    void 송금_링크는_토스와_카카오페이_형식만_받는다() throws Exception {
        String godae = signup("godae", "김고대", 61);
        put("/api/me/pay-links", godae, "{\"tossId\":\"https://toss.me/김고대/\",\"kakaoPayUrl\":\"HTTPS://QR.KAKAOPAY.COM/FAbc1234/\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tossId").value("김고대"))
                .andExpect(jsonPath("$.kakaoPayUrl").value("https://qr.kakaopay.com/FAbc1234"));
        assertThat(SettlementService.tossUrl("김고대", 12_300))
                .isEqualTo("https://toss.me/%EA%B9%80%EA%B3%A0%EB%8C%80/12300");

        put("/api/me/pay-links", godae, "{\"tossId\":\"toss.me/a b\"}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_TOSS_ID"));
        put("/api/me/pay-links", godae, "{\"kakaoPayUrl\":\"https://evil.example/qr.kakaopay.com/abcd\"}")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("BAD_KAKAOPAY_URL"));
        put("/api/me/pay-links", godae, "{\"tossId\":\"toss.me/x/../../y\"}").andExpect(status().isBadRequest());

        // 비우면 지운다
        put("/api/me/pay-links", godae, "{\"tossId\":\"\",\"kakaoPayUrl\":\" \"}").andExpect(status().isOk())
                .andExpect(jsonPath("$.tossId").value(nullValue()))
                .andExpect(jsonPath("$.kakaoPayUrl").value(nullValue()));
    }

    @Test
    void 탈퇴하면_송금_링크를_지우고_3교시를_지우면_정산도_지운다() throws Exception {
        reachedMeetup();
        clock.set(AFTER);
        put("/api/me/pay-links", anam, "{\"tossId\":\"anam_kim\"}").andExpect(status().isOk());
        post(url(), anam, settle(90_000, hostRef, horangRef)).andExpect(status().isOk());

        long anamId = memberId("anam"); // 탈퇴하면 이메일이 지워진다
        delete("/api/me", anam).andExpect(status().isNoContent());
        get("/api/meetups/" + meetupId, horang)
                .andExpect(jsonPath("$.settlement.payer.name").value("탈퇴한 원우"))
                .andExpect(jsonPath("$.settlement.tossUrl").value(nullValue()));
        assertThat(jdbc.queryForObject("select toss_id from members where id = ?", String.class, anamId))
                .isNull();

        delete("/api/meetups/" + meetupId, host).andExpect(status().isNoContent());
        assertThat(jdbc.queryForObject("select count(*) from settlements", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from settlement_shares", Integer.class)).isZero();
    }
}
