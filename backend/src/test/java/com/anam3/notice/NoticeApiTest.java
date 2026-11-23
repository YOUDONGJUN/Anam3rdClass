package com.anam3.notice;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.anam3.support.ApiTestSupport;

class NoticeApiTest extends ApiTestSupport {

    private String minsu;
    private String jiyoung;

    @BeforeEach
    void users() throws Exception {
        minsu = signup("minsu", "김민수", 2025);
        jiyoung = signup("jiyoung", "이지영", 2024);
    }

    private long createNotice(String token, String title) throws Exception {
        String body = """
                {"title":"%s","content":"금요일 7시 종강 파티 합니다"}
                """.formatted(title);
        return ((Number) read(post("/api/notices", token, body).andExpect(status().isCreated()), "$.id"))
                .longValue();
    }

    @Test
    void 누구나_공지를_쓸_수_있고_글쓴이는_가려진다() throws Exception {
        long id = createNotice(minsu, "종강 파티");

        get("/api/notices/" + id, jiyoung)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.author.name").value("김*수"))
                .andExpect(jsonPath("$.author.admissionLabel").value("25학번"))
                .andExpect(jsonPath("$.author.official").value(false))
                .andExpect(jsonPath("$.canEdit").value(false));

        get("/api/notices/" + id, minsu)
                .andExpect(jsonPath("$.author.name").value("김민수"))
                .andExpect(jsonPath("$.canEdit").value(true));
    }

    @Test
    void 운영자_글은_운영자로_표시된다() throws Exception {
        long id = createNotice(adminToken(), "공지");

        get("/api/notices/" + id, minsu)
                .andExpect(jsonPath("$.author.name").value("운영자"))
                .andExpect(jsonPath("$.author.official").value(true));
    }

    @Test
    void 수정과_삭제는_글쓴이와_운영자만() throws Exception {
        long id = createNotice(minsu, "종강 파티");
        String edit = """
                {"title":"종강 파티 (장소 변경)","content":"참살이길로 바뀌었어요"}
                """;

        put("/api/notices/" + id, jiyoung, edit).andExpect(status().isForbidden());
        delete("/api/notices/" + id, jiyoung).andExpect(status().isForbidden());

        put("/api/notices/" + id, minsu, edit)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("종강 파티 (장소 변경)"));

        delete("/api/notices/" + id, adminToken()).andExpect(status().isNoContent());
        get("/api/notices/" + id, minsu).andExpect(status().isNotFound());
    }

    @Test
    void 상단_고정은_운영자만_하고_고정글이_맨_위() throws Exception {
        long first = createNotice(minsu, "먼저 쓴 글");
        createNotice(jiyoung, "나중에 쓴 글");

        put("/api/admin/notices/" + first + "/pin", minsu, "{\"pinned\":true}").andExpect(status().isForbidden());
        put("/api/admin/notices/" + first + "/pin", adminToken(), "{\"pinned\":true}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pinned").value(true));

        get("/api/notices", jiyoung)
                .andExpect(jsonPath("$[0].title").value("먼저 쓴 글"))
                .andExpect(jsonPath("$[0].pinned").value(true))
                .andExpect(jsonPath("$[1].title").value("나중에 쓴 글"));
    }

    @Test
    void 빈_공지는_쓸_수_없다() throws Exception {
        post("/api/notices", minsu, "{\"title\":\"\",\"content\":\"\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }
}
