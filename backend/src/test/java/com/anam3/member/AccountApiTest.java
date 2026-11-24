package com.anam3.member;

import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;

import org.junit.jupiter.api.Test;

import com.anam3.support.ApiTestSupport;

/** 앱 안에서 탈퇴 (앱스토어 필수) */
class AccountApiTest extends ApiTestSupport {

    @Test
    void 탈퇴하면_개인정보가_지워지고_모집_중_3교시에서_빠진다() throws Exception {
        String godae = signup("godae", "김고대", 61);
        String horang = signup("horang", "박호랑", 61);
        long godaeId = memberId("godae");
        long mine = createMeetup(godae, 3, 6, Duration.ofHours(2), Duration.ofHours(1), null);
        long horangs = createMeetup(horang, 3, 6, Duration.ofHours(2), Duration.ofHours(1), null);
        post("/api/meetups/" + mine + "/join", horang, null).andExpect(status().isOk());
        post("/api/meetups/" + horangs + "/join", godae, null)
                .andExpect(jsonPath("$.participantCount").value(2));
        String owner = ownerToken();
        post("/api/blocks", horang, "{\"ref\":" + participantRef(horang, horangs, 1) + "}")
                .andExpect(status().isCreated());

        delete("/api/me", godae).andExpect(status().isNoContent());

        // 토큰은 바로 무효, 내가 연 3교시는 취소, 참여 중이던 3교시에서는 빠진다
        get("/api/me", godae).andExpect(status().isUnauthorized());
        get("/api/meetups/" + mine, horang).andExpect(jsonPath("$.status").value("CANCELED"));
        get("/api/meetups/" + horangs, horang)
                .andExpect(jsonPath("$.participantCount").value(1))
                .andExpect(jsonPath("$.participants[*].name", contains("박호랑")));
        get("/api/blocks", horang).andExpect(jsonPath("$.length()").value(0));

        // 관리 화면에는 "탈퇴한 원우"로만 남는다 (이메일·카카오 연결 없음)
        get("/api/admin/members", owner)
                .andExpect(jsonPath("$[?(@.id == " + godaeId + ")].name").value("탈퇴한 원우"))
                .andExpect(jsonPath("$[?(@.id == " + godaeId + ")].status").value("WITHDRAWN"))
                .andExpect(jsonPath("$[?(@.id == " + godaeId + ")].kakaoLinked").value(false));
        Integer withEmail = jdbc.queryForObject("select count(*) from members where email = 'godae@korea.ac.kr'",
                Integer.class);
        org.assertj.core.api.Assertions.assertThat(withEmail).isZero();
        post("/api/admin/members/" + godaeId + "/unban", owner, null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("WITHDRAWN"));

        // 같은 카카오 계정·학교 이메일로 다시 가입하면 새 계정
        kakaoLogin("godae").andExpect(jsonPath("$.step").value("EMAIL_REQUIRED"));
        clock.advance(Duration.ofMinutes(2));
        String again = signup("godae", "김고대", 61);
        get("/api/me", again).andExpect(jsonPath("$.id").value(org.hamcrest.Matchers.not((int) godaeId)));
    }

    @Test
    void 성사된_3교시에는_탈퇴한_원우로_남는다() throws Exception {
        String godae = signup("godae", "김고대", 61);
        String horang = signup("horang", "박호랑", 61);
        long id = createMeetup(horang, 2, 6, Duration.ofHours(2), Duration.ofHours(1), null);
        post("/api/meetups/" + id + "/join", godae, null).andExpect(jsonPath("$.status").value("CONFIRMED"));
        clock.advance(Duration.ofMinutes(90)); // 모집 마감 뒤

        delete("/api/me", godae).andExpect(status().isNoContent());
        get("/api/meetups/" + id, horang)
                .andExpect(jsonPath("$.participantCount").value(2))
                .andExpect(jsonPath("$.participants[*].name", contains("박호랑", "탈퇴한 원우")))
                .andExpect(jsonPath("$.participants[1].masked").value(false));
    }

    @Test
    void 대학원장은_탈퇴할_수_없다() throws Exception {
        delete("/api/me", ownerToken())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("OWNER_CANNOT_WITHDRAW"));
    }
}
