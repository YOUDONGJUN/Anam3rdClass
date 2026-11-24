package com.anam3.notice;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.anam3.support.ApiTestSupport;

/** 공지: 교수는 우리 학교 공지만, 대학원장은 연합 공지나 고른 학교 공지를 쓴다. 원우는 읽기만 */
class NoticeApiTest extends ApiTestSupport {

    private String godae;   // 고려대 원우
    private String anam;    // 고려대 원우
    private String manager; // 고려대 교수 (박호랑)

    @BeforeEach
    void users() throws Exception {
        godae = signup("godae", "김고대", 61);
        anam = signup("anam", "이안암", 60);
        manager = managerToken("horang", "박호랑", 61);
    }

    private long createNotice(String token, String title) throws Exception {
        return createNotice(token, title, "");
    }

    /** extra: ,"federated":true 또는 ,"schoolId":3 처럼 붙일 칸 */
    private long createNotice(String token, String title, String extra) throws Exception {
        String body = """
                {"title":"%s","content":"금요일 7시 종강 파티 합니다"%s}
                """.formatted(title, extra);
        return ((Number) read(post("/api/notices", token, body).andExpect(status().isCreated()), "$.id"))
                .longValue();
    }

    @Test
    void 공지는_교수_대학원장만_쓰고_교수_이름은_가려진다() throws Exception {
        post("/api/notices", godae, "{\"title\":\"종강 파티\",\"content\":\"금요일 7시\"}")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("공지는 교수·대학원장만 쓸 수 있어요"));

        long id = createNotice(manager, "종강 파티");
        get("/api/notices/" + id, anam)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.author.name").value("박*랑"))
                .andExpect(jsonPath("$.author.cohortLabel").value("61기"))
                .andExpect(jsonPath("$.author.official").value(false))
                .andExpect(jsonPath("$.school.code").value("KU"))
                .andExpect(jsonPath("$.federated").value(false))
                .andExpect(jsonPath("$.canEdit").value(false));

        get("/api/notices/" + id, manager)
                .andExpect(jsonPath("$.author.name").value("박호랑"))
                .andExpect(jsonPath("$.canEdit").value(true));
    }

    @Test
    void 대학원장_글은_실명과_대학원장_표시() throws Exception {
        long id = createNotice(ownerToken(), "공지");

        get("/api/notices/" + id, godae)
                .andExpect(jsonPath("$.author.name").value("박관리"))
                .andExpect(jsonPath("$.author.cohortLabel").value("61기"))
                .andExpect(jsonPath("$.author.official").value(true))
                .andExpect(jsonPath("$.author.masked").value(false));
    }

    @Test
    void 교수는_우리_학교_공지만_쓰고_대학원장은_전체나_한_학교를_고른다() throws Exception {
        long yonsei = schoolId("YONSEI");
        post("/api/notices", manager, """
                {"title":"연세대에","content":"교수가 다른 학교에","schoolId":%d}
                """.formatted(yonsei))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("교수는 우리 학교 공지만 쓸 수 있어요"));
        post("/api/notices", manager, "{\"title\":\"연합\",\"content\":\"교수가 연합으로\",\"federated\":true}")
                .andExpect(status().isForbidden());
        // 자기 학교를 고르는 건 된다
        createNotice(manager, "고려대 공지", ",\"schoolId\":" + schoolId("KU"));

        String owner = ownerToken();
        long forYonsei = createNotice(owner, "연세대만", ",\"schoolId\":" + yonsei);
        long united = createNotice(owner, "다섯 학교 모두", ",\"federated\":true");
        get("/api/notices/" + forYonsei, owner)
                .andExpect(jsonPath("$.school.code").value("YONSEI"))
                .andExpect(jsonPath("$.federated").value(false));
        get("/api/notices/" + united, owner).andExpect(jsonPath("$.school").isEmpty());

        String sinchon = signup("sinchon@yonsei.ac.kr", "이신촌", 30);
        get("/api/notices/" + forYonsei, sinchon).andExpect(status().isOk());
        get("/api/notices/" + forYonsei, godae).andExpect(status().isNotFound());
        get("/api/notices", sinchon).andExpect(jsonPath("$[*].title", hasItem("연세대만")))
                .andExpect(jsonPath("$[*].title", not(hasItem("고려대 공지"))));
        get("/api/notices", godae).andExpect(jsonPath("$[*].title", not(hasItem("연세대만"))))
                .andExpect(jsonPath("$[*].title", hasItem("다섯 학교 모두")));
        // 대학원장은 모든 학교 공지를 본다
        get("/api/notices", owner)
                .andExpect(jsonPath("$[*].title", hasItem("연세대만")))
                .andExpect(jsonPath("$[*].title", hasItem("고려대 공지")));

        post("/api/notices", owner, "{\"title\":\"없는 학교\",\"content\":\"내용\",\"schoolId\":99999}")
                .andExpect(status().isNotFound());
    }

    @Test
    void 수정과_삭제는_글쓴이_대학원장_그_학교_교수_대학원장_글은_대학원장만() throws Exception {
        long id = createNotice(manager, "종강 파티");
        String edit = """
                {"title":"종강 파티 (장소 변경)","content":"참살이길로 바뀌었어요"}
                """;

        put("/api/notices/" + id, anam, edit).andExpect(status().isForbidden());
        delete("/api/notices/" + id, anam).andExpect(status().isForbidden());

        put("/api/notices/" + id, manager, edit)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("종강 파티 (장소 변경)"));

        // 같은 학교 다른 교수도 우리 학교 공지는 관리한다
        String other = managerToken("prof", "최교수", 60);
        put("/api/notices/" + id, other, edit).andExpect(status().isOk());

        // 대학원장이 쓴 공지는 교수가 못 고친다
        String owner = ownerToken();
        long byOwner = createNotice(owner, "대학원장 공지");
        get("/api/notices/" + byOwner, manager).andExpect(jsonPath("$.canEdit").value(false));
        put("/api/notices/" + byOwner, manager, edit).andExpect(status().isForbidden());
        delete("/api/notices/" + byOwner, manager).andExpect(status().isForbidden());

        delete("/api/notices/" + id, owner).andExpect(status().isNoContent());
        get("/api/notices/" + id, godae).andExpect(status().isNotFound());
    }

    @Test
    void 상단_고정은_교수_대학원장만_하고_고정글이_맨_위() throws Exception {
        long first = createNotice(manager, "먼저 쓴 글");
        createNotice(manager, "나중에 쓴 글");

        put("/api/admin/notices/" + first + "/pin", godae, "{\"pinned\":true}").andExpect(status().isForbidden());
        put("/api/admin/notices/" + first + "/pin", manager, "{\"pinned\":true}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pinned").value(true));

        get("/api/notices", anam)
                .andExpect(jsonPath("$[0].title").value("먼저 쓴 글"))
                .andExpect(jsonPath("$[0].pinned").value(true))
                .andExpect(jsonPath("$[1].title").value("나중에 쓴 글"));
    }

    @Test
    void 빈_공지는_쓸_수_없다() throws Exception {
        post("/api/notices", manager, "{\"title\":\"\",\"content\":\"\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }
}
