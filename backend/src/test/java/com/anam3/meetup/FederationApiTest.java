package com.anam3.meetup;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.anam3.support.ApiTestSupport;

/** IT연합회: 학교별 3교시·공지와 연합 3교시·공지 */
class FederationApiTest extends ApiTestSupport {

    private static final Duration IN_1H = Duration.ofHours(1);
    private static final Duration IN_2H = Duration.ofHours(2);

    private String godae;   // 고려대 61기
    private String horang;  // 고려대 60기
    private String sinchon; // 연세대 3기

    @BeforeEach
    void users() throws Exception {
        godae = signup("godae", "김고대", 61);
        horang = signup("horang", "박호랑", 60);
        sinchon = signup("sinchon@yonsei.ac.kr", "이신촌", 3, departmentId("YONSEI", "인컴"));
    }

    @Test
    void 우리_학교_3교시는_다른_학교에_보이지_않고_참여도_못한다() throws Exception {
        long id = createMeetup(godae, 2, 6, IN_2H, IN_1H, null);

        get("/api/meetups", horang).andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].audience").value("SCHOOL"))
                .andExpect(jsonPath("$[0].school.shortName").value("고려대"));
        get("/api/meetups", sinchon).andExpect(jsonPath("$", hasSize(0)));
        get("/api/meetups?board=all", sinchon).andExpect(jsonPath("$", hasSize(0)));

        get("/api/meetups/" + id, sinchon)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("SCHOOL_ONLY"))
                .andExpect(jsonPath("$.message").value("고려대 원우만 참여하는 3교시예요"));
        post("/api/meetups/" + id + "/join", sinchon, null)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("SCHOOL_ONLY"));
    }

    @Test
    void 연합_3교시는_연합_게시판에서_다섯_학교_누구나_보고_참여한다() throws Exception {
        long id = createMeetup(sinchon, 3, 8, IN_2H, IN_1H, "https://open.kakao.com/o/united", "ALL");

        // 우리 학교 게시판에는 안 나오고 연합 게시판에 나온다
        get("/api/meetups", godae).andExpect(jsonPath("$", hasSize(0)));
        get("/api/meetups?board=all", godae)
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].audience").value("ALL"))
                .andExpect(jsonPath("$[0].school.shortName").value("연세대"))
                .andExpect(jsonPath("$[0].host.name").value("이*촌"))
                .andExpect(jsonPath("$[0].host.school.code").value("YONSEI"))
                .andExpect(jsonPath("$[0].joinable").value(true));
        get("/api/meetups?board=all", sinchon).andExpect(jsonPath("$", hasSize(1)));

        post("/api/meetups/" + id + "/join", godae, null).andExpect(status().isOk());
        post("/api/meetups/" + id + "/join", horang, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                // 성사되면 학교가 달라도 참여자끼리 실명, 사람마다 학교 배지
                .andExpect(jsonPath("$.participants[*].name", contains("이신촌", "김고대", "박호랑")))
                .andExpect(jsonPath("$.participants[*].school.shortName", contains("연세대", "고려대", "고려대")))
                .andExpect(jsonPath("$.openChatUrl").value("https://open.kakao.com/o/united"));

        clock.advance(Duration.ofHours(3));
        get("/api/meetups?scope=past&board=all", godae).andExpect(jsonPath("$", hasSize(1)));
        get("/api/meetups?scope=past", godae).andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void 교수는_자기_학교_3교시만_취소한다() throws Exception {
        String kuManager = managerToken("anam", "이안암", 60);
        long yonseiMeetup = createMeetup(sinchon, 2, 6, IN_2H, IN_1H, null, "ALL");
        long kuMeetup = createMeetup(godae, 2, 6, IN_2H, IN_1H, null, "ALL");

        get("/api/meetups/" + yonseiMeetup, kuManager).andExpect(jsonPath("$.canCancel").value(false));
        post("/api/meetups/" + yonseiMeetup + "/cancel", kuManager, null).andExpect(status().isForbidden());

        get("/api/meetups/" + kuMeetup, kuManager).andExpect(jsonPath("$.canCancel").value(true));
        post("/api/meetups/" + kuMeetup + "/cancel", kuManager, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELED"));

        // 대학원장은 모든 학교
        post("/api/meetups/" + yonseiMeetup + "/cancel", ownerToken(), null).andExpect(status().isOk());
    }

    @Test
    void 대학원장은_다른_학교_3교시도_보지만_참여는_같은_학교_것만() throws Exception {
        long id = createMeetup(sinchon, 2, 6, IN_2H, IN_1H, null);
        String owner = ownerToken();

        get("/api/meetups/" + id, owner).andExpect(status().isOk()).andExpect(jsonPath("$.canJoin").value(false));
        post("/api/meetups/" + id + "/join", owner, null).andExpect(status().isForbidden());
    }

    @Test
    void 공지는_우리_학교와_연합_공지만_보이고_연합_공지는_대학원장만_쓴다() throws Exception {
        String owner = ownerToken();
        long united = ((Number) read(post("/api/notices", owner, """
                {"title":"연합 공지","content":"다섯 학교 모두에게","federated":true}
                """).andExpect(status().isCreated()).andExpect(jsonPath("$.federated").value(true)), "$.id"))
                .longValue();
        long ku = ((Number) read(post("/api/notices", godae, """
                {"title":"고려대 공지","content":"고려대만"}
                """).andExpect(jsonPath("$.federated").value(false)), "$.id")).longValue();
        post("/api/notices", sinchon, """
                {"title":"연합으로","content":"써 볼래요","federated":true}
                """).andExpect(status().isForbidden());

        get("/api/notices", sinchon)
                .andExpect(jsonPath("$[*].title", contains("연합 공지")))
                .andExpect(jsonPath("$[0].author.official").value(true));
        get("/api/notices", horang).andExpect(jsonPath("$", hasSize(2)));
        get("/api/notices/" + ku, sinchon).andExpect(status().isNotFound());
        get("/api/notices/" + united, sinchon).andExpect(status().isOk());

        // 연합 공지 고정은 대학원장만, 학교 공지는 그 학교 교수도
        String yonseiManager = managerToken("prof@yonsei.ac.kr", "정교수", 1);
        put("/api/admin/notices/" + united + "/pin", yonseiManager, "{\"pinned\":true}")
                .andExpect(status().isForbidden());
        put("/api/admin/notices/" + ku + "/pin", yonseiManager, "{\"pinned\":true}")
                .andExpect(status().isForbidden());
        put("/api/admin/notices/" + united + "/pin", owner, "{\"pinned\":true}").andExpect(status().isOk());
    }
}
