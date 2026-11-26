package com.anam3.member;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;

import org.junit.jupiter.api.Test;

import com.anam3.support.ApiTestSupport;

/**
 * 내 정보 고치기: 이름은 대학원장만, 학과·기수는 누구나 (원우는 30일에 한 번, 교수·대학원장은 제한 없이).
 * 랭킹에 안 나오기 설정.
 */
class ProfileApiTest extends ApiTestSupport {

    private static String profile(String name, int cohort, long departmentId) {
        return name == null
                ? "{\"cohort\":%d,\"departmentId\":%d}".formatted(cohort, departmentId)
                : "{\"name\":\"%s\",\"cohort\":%d,\"departmentId\":%d}".formatted(name, cohort, departmentId);
    }

    @Test
    void 원우는_가입한_날엔_학과_기수를_자유롭게_고치고_그_뒤로_학과는_30일에_한_번() throws Exception {
        String token = signup("godae", "김고대", 61);
        long bigdata = departmentId("KU", "빅융");

        // 가입한 날: 몇 번이든 고치고, 30일 제한을 시작하지 않는다 (학과만 고쳐도 기수가 잠기지 않게)
        put("/api/me/profile", token, profile(null, 61, bigdata))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.departmentShortLabel").value("빅융"))
                .andExpect(jsonPath("$.profileEditableAt").doesNotExist());
        put("/api/me/profile", token, profile(null, 62, bigdata))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cohort").value(62))
                .andExpect(jsonPath("$.name").value("김고대"))
                .andExpect(jsonPath("$.profileEditableAt").doesNotExist());

        // 다음 날부터 학과는 30일에 한 번
        clock.advance(Duration.ofDays(1));
        put("/api/me/profile", token, profile(null, 62, departmentId("KU", "인융")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.profileEditableAt").value("2026-04-02T09:00:00Z"));
        put("/api/me/profile", token, profile(null, 62, bigdata))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PROFILE_CHANGE_LIMIT"))
                .andExpect(jsonPath("$.message").value("학과는 30일에 한 번 바꿀 수 있어요. 4월 2일부터 다시 바꿀 수 있어요"));

        // 바뀐 게 없으면 그대로 성공 (횟수를 쓰지 않는다)
        put("/api/me/profile", token, profile("김고대", 62, departmentId("KU", "인융"))).andExpect(status().isOk());

        clock.advance(Duration.ofDays(30));
        token = login("godae"); // 로그인은 30일 뒤 다시
        put("/api/me/profile", token, profile(null, 62, bigdata))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.departmentShortLabel").value("빅융"))
                .andExpect(jsonPath("$.profileEditableAt").value("2026-05-02T09:00:00Z"));
    }

    @Test
    void 원우는_가입한_날에만_기수를_직접_고치고_그_뒤로는_요청한다() throws Exception {
        String token = signup("godae", "김고대", 61);
        long inyung = departmentId("KU", "인융");
        get("/api/me", token).andExpect(jsonPath("$.cohortEditableUntil").value("2026-03-03T09:00:00Z"));

        // 하루가 지나면 기수는 못 바꾼다 (다른 기수 전용 3교시에 들어가지 못하게). 학과는 그대로 바꾼다
        clock.advance(Duration.ofDays(1));
        put("/api/me/profile", token, profile(null, 62, inyung))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("기수는 가입한 날에만 직접 고칠 수 있어요. 교수·대학원장에게 요청해 주세요"));
        put("/api/me/profile", token, profile(null, 61, departmentId("KU", "빅융")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cohort").value(61))
                .andExpect(jsonPath("$.departmentShortLabel").value("빅융"));

        // 교수·대학원장은 언제나
        String manager = managerToken("horang", "박호랑", 61);
        get("/api/me", manager).andExpect(jsonPath("$.cohortEditableUntil").doesNotExist());
    }

    @Test
    void 원우와_교수는_이름을_바꿀_수_없다() throws Exception {
        String user = signup("godae", "김고대", 61);
        String manager = managerToken("horang", "박호랑", 61);
        long inyung = departmentId("KU", "인융");

        put("/api/me/profile", user, profile("김새이름", 61, inyung))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("이름은 바꿀 수 없어요. 교수·대학원장에게 요청해 주세요"));
        put("/api/me/profile", manager, profile("박새이름", 61, inyung))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("이름은 대학원장에게 바꿔 달라고 요청해 주세요"));
        get("/api/me", manager).andExpect(jsonPath("$.name").value("박호랑"));
    }

    @Test
    void 교수와_대학원장은_학과_기수를_제한_없이_대학원장은_이름도_바꾼다() throws Exception {
        String manager = managerToken("horang", "박호랑", 61);
        String owner = ownerToken();
        long bigdata = departmentId("KU", "빅융");
        long inyung = departmentId("KU", "인융");

        put("/api/me/profile", manager, profile(null, 62, bigdata))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.profileEditableAt").doesNotExist());
        put("/api/me/profile", manager, profile(null, 61, inyung)).andExpect(status().isOk())
                .andExpect(jsonPath("$.cohort").value(61));

        put("/api/me/profile", owner, profile("유대학원장", 62, bigdata))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("유대학원장"))
                .andExpect(jsonPath("$.cohort").value(62));
        put("/api/me/profile", owner, profile("박관리", 61, inyung))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("박관리"));
    }

    @Test
    void 우리_학교_학과와_있는_기수만() throws Exception {
        String token = signup("godae", "김고대", 61);

        put("/api/me/profile", token, profile(null, 61, departmentId("YONSEI", "인컴")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_DEPARTMENT"));
        put("/api/me/profile", token, profile(null, 63, departmentId("KU", "인융")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_COHORT"));
        put("/api/me/profile", token, "{\"cohort\":61}").andExpect(status().isBadRequest());
        // 실패한 요청은 횟수를 쓰지 않는다
        put("/api/me/profile", token, profile(null, 62, departmentId("KU", "인융"))).andExpect(status().isOk());
    }

    @Test
    void 관리_화면에서_고친_것은_30일_제한에_들어가지_않는다() throws Exception {
        String token = signup("godae", "김고대", 61);
        String owner = ownerToken();
        put("/api/admin/members/" + memberId("godae") + "/profile", owner,
                profile("김고대", 62, departmentId("KU", "빅융"))).andExpect(status().isOk());

        get("/api/me", token).andExpect(jsonPath("$.profileEditableAt").doesNotExist());
        put("/api/me/profile", token, profile(null, 61, departmentId("KU", "인융"))).andExpect(status().isOk());
    }

    @Test
    void 랭킹에_안_나오기를_켜고_끈다() throws Exception {
        String token = signup("godae", "김고대", 61);
        get("/api/me", token).andExpect(jsonPath("$.rankingHidden").value(false));

        put("/api/me/ranking", token, "{\"hidden\":true}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rankingHidden").value(true));
        get("/api/me", token).andExpect(jsonPath("$.rankingHidden").value(true));
        put("/api/me/ranking", token, "{}").andExpect(status().isBadRequest());
        put("/api/me/ranking", token, "{\"hidden\":false}").andExpect(jsonPath("$.rankingHidden").value(false));
    }
}
