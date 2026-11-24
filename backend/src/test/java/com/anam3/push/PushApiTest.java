package com.anam3.push;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.anam3.support.ApiTestSupport;

/** 앱 푸시: 기기 등록 · 알림 종류 끄기 · 언제 누구에게 알리나 */
class PushApiTest extends ApiTestSupport {

    @Autowired
    PushNotifier notifier;

    private int deviceCount(String deviceToken) {
        Integer n = jdbc.queryForObject("select count(*) from push_devices where token = ?", Integer.class,
                deviceToken);
        return n == null ? 0 : n;
    }

    private List<PushType> types(String deviceToken) {
        return pushes.to(deviceToken).stream().map(PushMessage::type).toList();
    }

    /** 3교시 수정 요청 (제목 · 장소 · 시간) */
    private static String edit(String title, String place, java.time.Instant meetAt, java.time.Instant deadlineAt) {
        return String.format("{\"title\":\"%s\",\"description\":\"\",\"place\":\"%s\",\"categories\":[\"BEER\"],"
                + "\"purposes\":[],\"openChatUrl\":\"\",\"schedule\":{\"meetAt\":\"%s\",\"deadlineAt\":\"%s\"}}",
                title, place, meetAt, deadlineAt);
    }

    private PushMessage last(String deviceToken) {
        List<PushMessage> got = pushes.to(deviceToken);
        assertThat(got).as(deviceToken + " 가 받은 알림").isNotEmpty();
        return got.getLast();
    }

    @Test
    void 기기를_등록하고_로그아웃하면_지운다() throws Exception {
        String godae = signup("godae", "김고대", 61);
        String anam = signup("anam", "이안암", 60);
        registerDevice(godae, "phone-1");
        registerDevice(godae, "phone-1"); // 앱을 열 때마다 다시 알려도 하나
        assertThat(deviceCount("phone-1")).isEqualTo(1);

        // 같은 폰에서 다른 사람이 로그인하면 그 사람 것으로
        registerDevice(anam, "phone-1");
        assertThat(jdbc.queryForObject("select member_id from push_devices where token = 'phone-1'", Long.class))
                .isEqualTo(memberId("anam"));

        // 로그아웃: 로그인 없이 토큰만으로 지운다
        post("/api/push/unregister", null, "{\"token\":\"phone-1\"}").andExpect(status().isNoContent());
        assertThat(deviceCount("phone-1")).isZero();

        // 한 사람은 기기 5대까지 (오래 안 연 기기부터 빠진다)
        for (int i = 1; i <= 6; i++) {
            clock.advance(Duration.ofMinutes(1));
            registerDevice(godae, "pc-" + i);
        }
        assertThat(deviceCount("pc-1")).isZero();
        assertThat(deviceCount("pc-6")).isEqualTo(1);

        post("/api/me/push/devices", godae, "{\"token\":\"x\",\"platform\":\"windows\"}")
                .andExpect(status().isBadRequest());
        post("/api/me/push/devices", null, "{\"token\":\"x\",\"platform\":\"android\"}")
                .andExpect(status().isUnauthorized());
    }

    @Test
    void 알림_종류를_끌_수_있다() throws Exception {
        String godae = signup("godae", "김고대", 61);
        get("/api/me", godae).andExpect(jsonPath("$.pushOff", empty()));
        put("/api/me/push/settings", godae, "{\"off\":[\"NOTICE\",\"JOIN\",\"NOTICE\"]}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pushOff", contains("JOIN", "NOTICE")));
        get("/api/me", godae).andExpect(jsonPath("$.pushOff", contains("JOIN", "NOTICE")));
        put("/api/me/push/settings", godae, "{\"off\":[\"WHATEVER\"]}").andExpect(status().isBadRequest());
        put("/api/me/push/settings", godae, "{\"off\":[]}").andExpect(jsonPath("$.pushOff", empty()));

        // 끈 종류는 보내지 않는다: 내 3교시에 누가 참여해도 조용
        String anam = signup("anam", "이안암", 61);
        registerDevice(godae, "godae-phone");
        put("/api/me/push/settings", godae, "{\"off\":[\"JOIN\"]}").andExpect(status().isOk());
        long id = createMeetup(godae, 3, 6, Duration.ofDays(2), Duration.ofDays(1), null);
        post("/api/meetups/" + id + "/join", anam, null).andExpect(status().isOk());
        assertThat(pushes.to("godae-phone")).isEmpty();
    }

    @Test
    void 참여_나감_성사는_알리고_한_사람에게는_보내지_않는다() throws Exception {
        String host = signup("godae", "김고대", 61);
        String anam = signup("anam", "이안암", 61);
        String horang = signup("horang", "박호랑", 61);
        registerDevice(host, "host-phone");
        registerDevice(anam, "anam-phone");
        registerDevice(horang, "horang-phone");
        long id = createMeetup(host, 3, 4, Duration.ofDays(2), Duration.ofDays(1), null);

        post("/api/meetups/" + id + "/join", anam, null).andExpect(status().isOk());
        PushMessage joined = last("host-phone");
        assertThat(joined.type()).isEqualTo(PushType.JOIN);
        assertThat(joined.title()).isEqualTo("치맥 번개");
        assertThat(joined.body()).isEqualTo("한 명이 참여했어요 · 성사까지 1명");
        assertThat(joined.path()).isEqualTo("/meetups/" + id);
        assertThat(pushes.to("anam-phone")).isEmpty(); // 참여한 본인에게는 안 보낸다

        // 세 번째 사람이 들어와 성사 → 참여자 모두에게 성사 알림 (연 사람도 참여 알림 대신 성사 알림)
        post("/api/meetups/" + id + "/join", horang, null).andExpect(status().isOk());
        assertThat(last("host-phone").type()).isEqualTo(PushType.REACHED);
        assertThat(last("anam-phone").body()).isEqualTo("성사됐어요! 참여자 실명과 단톡방이 공개됐어요");
        assertThat(pushes.to("horang-phone")).isEmpty();

        // 마감 전에 나가면 연 사람에게
        delete("/api/meetups/" + id + "/join", anam).andExpect(status().isOk());
        assertThat(last("host-phone").body()).isEqualTo("한 명이 나갔어요 · 성사까지 1명");
        assertThat(types("host-phone")).containsExactly(PushType.JOIN, PushType.REACHED, PushType.JOIN);
    }

    @Test
    void 시간_장소가_바뀌거나_취소되면_참여자에게() throws Exception {
        String host = signup("godae", "김고대", 61);
        String anam = signup("anam", "이안암", 61);
        registerDevice(host, "host-phone");
        registerDevice(anam, "anam-phone");
        long id = createMeetup(host, null, null, Duration.ofDays(2), Duration.ofDays(1), null);
        post("/api/meetups/" + id + "/join", anam, null).andExpect(status().isOk());
        pushes.clear();

        var meet = T0.plus(Duration.ofDays(2));
        var deadline = T0.plus(Duration.ofDays(1));
        // 제목만 고치면 조용
        put("/api/meetups/" + id, host, edit("치맥 번개 2", "안암역 앞", meet, deadline)).andExpect(status().isOk());
        assertThat(pushes.all()).isEmpty();

        // 시간을 바꾸면 (2026-03-04 18:00 KST → 03-04 20:00 KST)
        var later = meet.plus(Duration.ofHours(2));
        put("/api/meetups/" + id, host, edit("치맥 번개 2", "안암역 앞", later, deadline)).andExpect(status().isOk());
        PushMessage moved = last("anam-phone");
        assertThat(moved.type()).isEqualTo(PushType.CHANGED);
        assertThat(moved.title()).isEqualTo("치맥 번개 2");
        assertThat(moved.body()).isEqualTo("시간이 바뀌었어요 · 3/4(수) 20:00");
        assertThat(pushes.to("host-phone")).isEmpty(); // 고친 사람은 빼고

        put("/api/meetups/" + id, host, edit("치맥 번개 2", "참살이길", later, deadline)).andExpect(status().isOk());
        assertThat(last("anam-phone").body()).isEqualTo("장소가 바뀌었어요 · 참살이길");

        post("/api/meetups/" + id + "/cancel", host, null).andExpect(status().isOk());
        assertThat(last("anam-phone").body()).isEqualTo("취소됐어요 · 3/4(수) 20:00 3교시");
    }

    @Test
    void 열린_3교시를_지우면_참여자에게_취소를_알린다() throws Exception {
        String host = signup("godae", "김고대", 61);
        String anam = signup("anam", "이안암", 61);
        registerDevice(anam, "anam-phone");
        long id = createMeetup(host, 3, null, Duration.ofDays(2), Duration.ofDays(1), null);
        post("/api/meetups/" + id + "/join", anam, null).andExpect(status().isOk());

        delete("/api/meetups/" + id, host).andExpect(status().isNoContent());
        PushMessage gone = last("anam-phone");
        assertThat(gone.type()).isEqualTo(PushType.CHANGED);
        assertThat(gone.path()).isEqualTo("/me"); // 지운 3교시는 열 수 없으니 내 정보로
    }

    @Test
    void 새_연합_3교시는_다섯_학교에_알리고_차단한_사람은_뺀다() throws Exception {
        String host = signup("godae", "김고대", 61);
        String anam = signup("anam", "이안암", 61);
        String sinchon = signup("sinchon@yonsei.ac.kr", "이신촌", 30);
        String horang = signup("horang", "박호랑", 61);
        registerDevice(host, "host-phone");
        registerDevice(anam, "anam-phone");
        registerDevice(sinchon, "sinchon-phone");
        registerDevice(horang, "horang-phone");

        // 호랑이 김고대를 차단 (김고대의 3교시에서)
        long kuOnly = createMeetup(host, null, null, Duration.ofDays(2), Duration.ofDays(1), null);
        post("/api/blocks", horang, "{\"ref\":" + participantRef(horang, kuOnly, 0) + "}").andExpect(status().isCreated());
        pushes.clear();

        long id = createMeetup(host, null, null, Duration.ofDays(3), Duration.ofDays(2), null, "ALL");
        PushMessage opened = last("sinchon-phone");
        assertThat(opened.type()).isEqualTo(PushType.FEDERATION);
        assertThat(opened.title()).isEqualTo("새 연합 3교시");
        assertThat(opened.body()).isEqualTo("고려대 · 치맥 번개 · 3/5(목) 18:00");
        assertThat(opened.path()).isEqualTo("/meetups/" + id);
        assertThat(pushes.to("anam-phone")).hasSize(1);
        assertThat(pushes.to("host-phone")).isEmpty();
        assertThat(pushes.to("horang-phone")).isEmpty();

        // 우리 학교 3교시는 알리지 않는다
        createMeetup(host, null, null, Duration.ofDays(3), Duration.ofDays(2), null);
        assertThat(pushes.to("anam-phone")).hasSize(1);
    }

    @Test
    void 새_공지는_그_학교에_연합_공지는_다섯_학교에_알린다() throws Exception {
        String owner = ownerToken();
        String manager = managerToken("horang", "박호랑", 61);
        String anam = signup("anam", "이안암", 61);
        String sinchon = signup("sinchon@yonsei.ac.kr", "이신촌", 30);
        registerDevice(anam, "anam-phone");
        registerDevice(sinchon, "sinchon-phone");

        String notice = "{\"title\":\"%s\",\"content\":\"금요일 7시 종강 파티 합니다\"%s}";
        post("/api/notices", anam, notice.formatted("원우 공지", "")).andExpect(status().isForbidden());
        assertThat(pushes.all()).isEmpty();

        long id = ((Number) read(post("/api/notices", manager, notice.formatted("종강 파티", ""))
                .andExpect(status().isCreated()), "$.id")).longValue();
        PushMessage posted = last("anam-phone");
        assertThat(posted.type()).isEqualTo(PushType.NOTICE);
        assertThat(posted.title()).isEqualTo("새 공지");
        assertThat(posted.body()).isEqualTo("종강 파티");
        assertThat(posted.path()).isEqualTo("/notices/" + id);
        assertThat(pushes.to("sinchon-phone")).isEmpty(); // 다른 학교는 받지 않는다

        put("/api/me/push/settings", anam, "{\"off\":[\"NOTICE\"]}").andExpect(status().isOk());
        post("/api/notices", owner, notice.formatted("연합 체육대회", ",\"federated\":true"))
                .andExpect(status().isCreated());
        assertThat(last("sinchon-phone").title()).isEqualTo("새 연합 공지");
        assertThat(pushes.to("anam-phone")).hasSize(1); // 공지 알림을 껐다
    }

    @Test
    void 모집_마감_30분_전에_한_번_알린다() throws Exception {
        String host = signup("godae", "김고대", 61);
        String anam = signup("anam", "이안암", 61);
        registerDevice(host, "host-phone");
        registerDevice(anam, "anam-phone");
        long id = createMeetup(host, 4, null, Duration.ofDays(1), Duration.ofHours(3), null);
        post("/api/meetups/" + id + "/join", anam, null).andExpect(status().isOk());
        long quick = createMeetup(host, 4, null, Duration.ofDays(1), Duration.ofMinutes(50), null);
        pushes.clear();

        clock.advance(Duration.ofMinutes(140)); // 마감 40분 전
        assertThat(notifier.remindDeadlines()).isZero();

        clock.advance(Duration.ofMinutes(20)); // 마감 20분 전
        assertThat(notifier.remindDeadlines()).isEqualTo(1); // 금방 마감하는 3교시(quick)는 알리지 않는다
        PushMessage remind = last("anam-phone");
        assertThat(remind.type()).isEqualTo(PushType.DEADLINE);
        assertThat(remind.body()).isEqualTo("모집 마감 20분 전이에요 · 2명 더 모이면 성사돼요");
        assertThat(last("host-phone").type()).isEqualTo(PushType.DEADLINE);
        assertThat(quick).isPositive();

        clock.advance(Duration.ofMinutes(1));
        assertThat(notifier.remindDeadlines()).isZero(); // 한 번만
    }

    @Test
    void 앱을_지운_기기와_탈퇴한_원우의_기기는_지운다() throws Exception {
        String host = signup("godae", "김고대", 61);
        String anam = signup("anam", "이안암", 61);
        registerDevice(host, "old-phone");
        registerDevice(host, "new-phone");
        pushes.markDead("old-phone");
        long id = createMeetup(host, 3, null, Duration.ofDays(2), Duration.ofDays(1), null);
        post("/api/meetups/" + id + "/join", anam, null).andExpect(status().isOk());
        assertThat(pushes.to("new-phone")).hasSize(1);
        assertThat(deviceCount("old-phone")).isZero();

        registerDevice(anam, "anam-phone");
        delete("/api/me", anam).andExpect(status().isNoContent());
        assertThat(deviceCount("anam-phone")).isZero();
    }

    @Test
    void 앱에서_카카오톡으로_로그인한다() throws Exception {
        // 웹 카카오 로그인으로 가입한 원우가 앱에서 카카오톡으로 들어오면 같은 계정
        signup("godae", "김고대", 61);
        post("/api/auth/kakao/app", null, "{\"accessToken\":\"app-token-godae\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.step").value("DONE"))
                .andExpect(jsonPath("$.member.name").value("김고대"));

        // 처음이면 학교 이메일 인증부터
        post("/api/auth/kakao/app", null, "{\"accessToken\":\"app-token-newbie\"}")
                .andExpect(jsonPath("$.step").value("EMAIL_REQUIRED"));
        post("/api/auth/kakao/app", null, "{\"accessToken\":\"other-app-token-godae\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("KAKAO_LOGIN_FAILED"));
        post("/api/auth/kakao/app", null, "{\"accessToken\":\"\"}").andExpect(status().isBadRequest());
    }

    @Test
    void 앱_링크_확인_파일() throws Exception {
        get("/.well-known/assetlinks.json", null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].relation[0]").value("delegate_permission/common.handle_all_urls"))
                .andExpect(jsonPath("$[0].target.package_name").value("kr.anam3.app"))
                .andExpect(jsonPath("$[0].target.sha256_cert_fingerprints[0]").value(
                        "14:6D:E9:83:C5:73:06:50:D8:EE:B9:95:2F:34:FC:64:16:A0:83:42:E6:1D:BE:A8:8A:04:96:B2:3F:CF:44:E5"));
    }
}
