package com.anam3.school;

import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import com.anam3.support.ApiTestSupport;

/** 학교별 화면 이름: 안암 · 신촌 · 서강 · 혜화 · 왕십리 + "3교시" */
class SchoolPlaceApiTest extends ApiTestSupport {

    private static String body(String place) {
        String field = place == null ? "" : ",\"place\":\"" + place + "\"";
        return """
                {"name":"연세대학교","shortName":"연세대","graduateSchool":"인공지능융합대학원","latestCohort":99%s}
                """.formatted(field);
    }

    /** 학교는 테스트 사이에 지우지 않으니 되돌린다 */
    @AfterEach
    void restore() {
        jdbc.update("update schools set place = '신촌' where code = 'YONSEI'");
    }

    @Test
    void 학교마다_동네_이름이_있고_내_정보와_배지에_실린다() throws Exception {
        String godae = signup("godae", "김고대", 61);
        String sinchon = signup("sinchon@yonsei.ac.kr", "이신촌", 3, departmentId("YONSEI", "인컴"));
        get("/api/me", godae).andExpect(jsonPath("$.school.place").value("안암"));
        get("/api/me", sinchon).andExpect(jsonPath("$.school.place").value("신촌"));
        get("/api/admin/schools", ownerToken()).andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.code == 'SKKU')].place", contains("혜화")))
                .andExpect(jsonPath("$[?(@.code == 'SOGANG')].place", contains("서강")))
                .andExpect(jsonPath("$[?(@.code == 'HANYANG')].place", contains("왕십리")));
    }

    @Test
    void 대학원장이_동네_이름을_바꾸고_빈칸이면_지우고_안_보내면_그대로() throws Exception {
        String owner = ownerToken();
        String url = "/api/admin/schools/" + schoolId("YONSEI");

        put(url, owner, body(" 연희 ")).andExpect(status().isOk());
        get("/api/admin/schools", owner).andExpect(jsonPath("$[?(@.code == 'YONSEI')].place", contains("연희")));

        // 예전 화면처럼 place 를 보내지 않으면 그대로 둔다
        put(url, owner, body(null)).andExpect(status().isOk());
        get("/api/admin/schools", owner).andExpect(jsonPath("$[?(@.code == 'YONSEI')].place", contains("연희")));

        put(url, owner, body("아주아주긴동네이름")).andExpect(status().isBadRequest());

        put(url, owner, body("")).andExpect(status().isOk());
        get("/api/admin/schools", owner)
                .andExpect(jsonPath("$[?(@.code == 'YONSEI')].place", contains((Object) null)));
    }
}
