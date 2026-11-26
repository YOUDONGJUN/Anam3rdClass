package com.anam3.ranking;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.Test;

import com.anam3.support.ApiTestSupport;

/**
 * 랭킹: 성사된 뒤 모임 시간이 지난 3교시만 센다. 이름은 가려서, 상위 10명 + 내 기록.
 * 카테고리 순서: HOST(방장왕) · ATTEND(출석왕) · RATE(성사율) · FEDERATION(연합왕)
 */
class RankingApiTest extends ApiTestSupport {

    private static final String HOST = "$.categories[0]";
    private static final String ATTEND = "$.categories[1]";
    private static final String RATE = "$.categories[2]";
    private static final String FEDERATION = "$.categories[3]";

    /** 최소 2명 3교시를 열고 joiners 가 탄다 (audience: SCHOOL · ALL) */
    private long ride(String host, Duration meetIn, String audience, String... joiners) throws Exception {
        long id = createMeetup(host, 2, null, meetIn, meetIn.minusHours(1), null, audience);
        for (String joiner : joiners) {
            post("/api/meetups/" + id + "/join", joiner, null).andExpect(status().isOk());
        }
        return id;
    }

    @Test
    void 성사된_뒤_모임_시간이_지난_3교시만_세고_이름은_가린다() throws Exception {
        String godae = signup("godae", "김고대", 61);
        String anam = signup("anam", "이안암", 61);
        String horang = signup("horang", "박호랑", 61);
        ride(godae, Duration.ofDays(1), "SCHOOL", anam);
        ride(godae, Duration.ofDays(2), "SCHOOL", anam, horang);
        ride(horang, Duration.ofDays(3), "SCHOOL"); // 아무도 안 타서 무산 — 세지 않는다
        long canceled = ride(anam, Duration.ofDays(3), "SCHOOL", horang);
        post("/api/meetups/" + canceled + "/cancel", anam, null).andExpect(status().isOk());

        // 모임 시간 전에는 아무것도 없다
        get("/api/rankings", horang)
                .andExpect(status().isOk())
                .andExpect(jsonPath(HOST + ".top", empty()));

        clock.set(T0.plus(Duration.ofDays(4)));
        get("/api/rankings?period=month&scope=all", horang)
                .andExpect(jsonPath("$.period").value("month"))
                .andExpect(jsonPath("$.scope").value("all"))
                .andExpect(jsonPath("$.since").value("2026-02-28T15:00:00Z"))
                .andExpect(jsonPath(HOST + ".category").value("HOST"))
                .andExpect(jsonPath(HOST + ".top", hasSize(1)))
                .andExpect(jsonPath(HOST + ".top[0].rank").value(1))
                .andExpect(jsonPath(HOST + ".top[0].name").value("김*대"))
                .andExpect(jsonPath(HOST + ".top[0].masked").value(true))
                .andExpect(jsonPath(HOST + ".top[0].school.shortName").value("고려대"))
                .andExpect(jsonPath(HOST + ".top[0].departmentLabel").value("인융"))
                .andExpect(jsonPath(HOST + ".top[0].cohortLabel").doesNotExist())
                .andExpect(jsonPath(HOST + ".top[0].value").value(2))
                .andExpect(jsonPath(HOST + ".mine").doesNotExist())
                // 같은 기록이면 같은 등수
                .andExpect(jsonPath(ATTEND + ".top[*].rank", contains(1, 1, 3)))
                .andExpect(jsonPath(ATTEND + ".top[*].value", contains(2, 2, 1)))
                .andExpect(jsonPath(ATTEND + ".mine.rank").value(3))
                .andExpect(jsonPath(ATTEND + ".mine.me").value(true))
                .andExpect(jsonPath(ATTEND + ".mine.name").value("박호랑"))
                .andExpect(jsonPath(ATTEND + ".mine.masked").value(false))
                .andExpect(jsonPath(RATE + ".top", empty()))
                .andExpect(jsonPath(FEDERATION + ".top", empty()));

        // 학교 대항: 고려대가 1등 (다른 학교는 탄 사람이 없다)
        get("/api/rankings", godae)
                .andExpect(jsonPath("$.schools", hasSize(5)))
                .andExpect(jsonPath("$.schools[0].school.code").value("KU"))
                .andExpect(jsonPath("$.schools[0].rank").value(1))
                .andExpect(jsonPath("$.schools[0].active").value(3))
                .andExpect(jsonPath("$.schools[0].meetups").value(2))
                .andExpect(jsonPath("$.schools[0].mine").value(true))
                .andExpect(jsonPath("$.schools[1].rank").value(2))
                .andExpect(jsonPath("$.schools[1].active").value(0));
    }

    @Test
    void 대학원장은_학교를_골라_그_학교_원우끼리의_랭킹을_본다() throws Exception {
        String godae = signup("godae", "김고대", 61);
        String anam = signup("anam", "이안암", 61);
        String sinchon = signup("sinchon@yonsei.ac.kr", "이신촌", 3, departmentId("YONSEI", "인컴"));
        String yonsei2 = signup("baekyang@yonsei.ac.kr", "박백양", 3, departmentId("YONSEI", "인컴"));
        String owner = ownerToken();
        ride(godae, Duration.ofDays(1), "SCHOOL", anam, owner); // 대학원장도 고려대 3교시에 탔다
        ride(sinchon, Duration.ofDays(1), "SCHOOL", yonsei2);
        clock.set(T0.plus(Duration.ofDays(2)));
        long yonsei = schoolId("YONSEI");

        get("/api/rankings?scope=school&school=" + yonsei, owner)
                .andExpect(jsonPath("$.schoolId").value(yonsei))
                .andExpect(jsonPath(ATTEND + ".top", hasSize(2)))
                .andExpect(jsonPath(ATTEND + ".top[*].school.code", contains("YONSEI", "YONSEI")))
                .andExpect(jsonPath(ATTEND + ".mine").doesNotExist());

        // 원우가 다른 학교를 고르면 무시하고 우리 학교
        get("/api/rankings?scope=school&school=" + yonsei, godae)
                .andExpect(jsonPath("$.schoolId").value(schoolId("KU")))
                .andExpect(jsonPath(ATTEND + ".top[*].school.code", everyItem(org.hamcrest.Matchers.is("KU"))));
    }

    @Test
    void 이번_달과_전체() throws Exception {
        String godae = signup("godae", "김고대", 61);
        String anam = signup("anam", "이안암", 61);
        ride(godae, Duration.ofDays(1), "SCHOOL", anam);

        clock.set(Instant.parse("2026-04-02T03:00:00Z")); // 4월
        godae = login("godae"); // 한 달 지나 로그인 토큰이 끝났다
        get("/api/rankings?period=month", godae)
                .andExpect(jsonPath("$.since").value("2026-03-31T15:00:00Z"))
                .andExpect(jsonPath(ATTEND + ".top", empty()));
        get("/api/rankings?period=all", godae)
                .andExpect(jsonPath("$.period").value("all"))
                .andExpect(jsonPath("$.since").doesNotExist())
                .andExpect(jsonPath(ATTEND + ".top", hasSize(2)))
                .andExpect(jsonPath(HOST + ".mine.name").value("김고대"));
    }

    @Test
    void 성사율은_3번_이상_연_사람만_취소도_성사_못_한_것으로_센다() throws Exception {
        String godae = signup("godae", "김고대", 61);
        String anam = signup("anam", "이안암", 61);
        ride(godae, Duration.ofDays(1), "SCHOOL", anam);
        ride(godae, Duration.ofDays(2), "SCHOOL", anam);
        long canceled = ride(godae, Duration.ofDays(3), "SCHOOL", anam);
        post("/api/meetups/" + canceled + "/cancel", godae, null).andExpect(status().isOk());
        ride(anam, Duration.ofDays(1), "SCHOOL", godae);
        ride(anam, Duration.ofDays(2), "SCHOOL", godae);

        clock.set(T0.plus(Duration.ofDays(4)));
        get("/api/rankings", godae)
                .andExpect(jsonPath(RATE + ".top", hasSize(1)))
                .andExpect(jsonPath(RATE + ".top[0].me").value(true))
                .andExpect(jsonPath(RATE + ".top[0].value").value(2))
                .andExpect(jsonPath(RATE + ".top[0].of").value(3));
        get("/api/rankings", anam).andExpect(jsonPath(RATE + ".mine").doesNotExist()); // 2번만 열었다
    }

    @Test
    void 연합왕_우리_학교_랭킹에_안_나오기() throws Exception {
        String godae = signup("godae", "김고대", 61);
        String anam = signup("anam", "이안암", 61);
        String horang = signup("horang", "박호랑", 61);
        String sinchon = signup("sinchon@yonsei.ac.kr", "이신촌", 30);
        ride(godae, Duration.ofDays(1), "ALL", sinchon); // 두 학교 → 연합왕
        ride(anam, Duration.ofDays(1), "ALL", horang); // 고려대끼리 → 연합왕 아님

        clock.set(T0.plus(Duration.ofDays(2)));
        get("/api/rankings", anam)
                .andExpect(jsonPath(FEDERATION + ".top", hasSize(2)))
                .andExpect(jsonPath(FEDERATION + ".top[*].name", contains("김*대", "이*촌")))
                .andExpect(jsonPath(FEDERATION + ".mine").doesNotExist())
                .andExpect(jsonPath(ATTEND + ".top", hasSize(4)));

        // 우리 학교: 연세대 원우는 빠진다
        get("/api/rankings?scope=school", godae)
                .andExpect(jsonPath("$.scope").value("school"))
                .andExpect(jsonPath(ATTEND + ".top", hasSize(3)))
                .andExpect(jsonPath(FEDERATION + ".top[*].name", contains("김고대")));

        // 랭킹에 안 나오기: 남의 랭킹에서 빠지고, 내 기록만 순위 없이
        put("/api/me/ranking", sinchon, "{\"hidden\":true}").andExpect(status().isOk());
        get("/api/rankings", godae).andExpect(jsonPath(ATTEND + ".top", hasSize(3)));
        get("/api/rankings", sinchon)
                .andExpect(jsonPath(ATTEND + ".mine.rank").doesNotExist())
                .andExpect(jsonPath(ATTEND + ".mine.value").value(1))
                .andExpect(jsonPath(ATTEND + ".mine.name").value("이신촌"));
    }

    @Test
    void 이번_달_방장왕_출석왕_1등은_이름_옆에_배지() throws Exception {
        String godae = signup("godae", "김고대", 61);
        String anam = signup("anam", "이안암", 61);
        String horang = signup("horang", "박호랑", 61);
        ride(godae, Duration.ofDays(1), "SCHOOL", anam);
        ride(godae, Duration.ofDays(2), "SCHOOL", anam, horang);
        long upcoming = ride(horang, Duration.ofDays(5), "SCHOOL", godae, anam);

        clock.set(T0.plus(Duration.ofDays(3)));
        // 김고대: 방장 2번(1등) + 2번 탐 / 이안암: 2번 탐 / 박호랑: 1번 — 출석왕은 김고대·이안암 공동
        get("/api/meetups/" + upcoming, horang)
                .andExpect(jsonPath("$.participants[0].crowns", empty()))
                .andExpect(jsonPath("$.participants[1].crowns", contains("HOST", "ATTEND")))
                .andExpect(jsonPath("$.participants[2].crowns", contains("ATTEND")));

        // 랭킹에 안 나오기를 켜면 배지도 없다
        put("/api/me/ranking", godae, "{\"hidden\":true}").andExpect(status().isOk());
        clock.advance(Duration.ofMinutes(1));
        get("/api/meetups/" + upcoming, horang)
                .andExpect(jsonPath("$.participants[1].crowns", empty()))
                .andExpect(jsonPath("$.participants[2].crowns", contains("ATTEND")));
    }

    @Test
    void 로그인해야_본다() throws Exception {
        get("/api/rankings", null).andExpect(status().isUnauthorized());
    }
}
