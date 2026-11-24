package com.anam3.meetup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.anam3.support.ApiTestSupport;

class MeetupApiTest extends ApiTestSupport {

    private static final Duration IN_1H = Duration.ofHours(1);
    private static final Duration IN_2H = Duration.ofHours(2);
    private static final String CHAT = "https://open.kakao.com/o/abc123";

    private String host;     // 김고대
    private String anam;  // 이안암
    private String horang;  // 박호랑
    private String outsider; // 최석탑

    @BeforeEach
    void users() throws Exception {
        host = signup("godae", "김고대", 61);
        anam = signup("anam", "이안암", 60);
        horang = signup("horang", "박호랑", 61);
        outsider = signup("seoktap", "최석탑", 59);
    }

    @Test
    void 번개를_열면_개설자가_첫_참여자() throws Exception {
        long id = createMeetup(host, 3, 6, IN_2H, IN_1H, CHAT);

        get("/api/meetups/" + id, host)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RECRUITING"))
                .andExpect(jsonPath("$.participantCount").value(1))
                .andExpect(jsonPath("$.hosting").value(true))
                .andExpect(jsonPath("$.joined").value(true))
                .andExpect(jsonPath("$.canEdit").value(true))
                .andExpect(jsonPath("$.canLeave").value(false))
                .andExpect(jsonPath("$.openChatUrl").value(CHAT)); // 개설자는 자기 링크를 본다
    }

    @Test
    void 성사_전에는_참여자끼리도_이름이_가려진다() throws Exception {
        long id = createMeetup(host, 3, 6, IN_2H, IN_1H, CHAT);
        post("/api/meetups/" + id + "/join", anam, null).andExpect(status().isOk());

        get("/api/meetups/" + id, anam)
                .andExpect(jsonPath("$.status").value("RECRUITING"))
                .andExpect(jsonPath("$.revealed").value(false))
                .andExpect(jsonPath("$.participants[*].name", contains("김*대", "이안암"))) // 나는 실명
                .andExpect(jsonPath("$.participants[0].cohortLabel").value("61기"))
                .andExpect(jsonPath("$.participants[0].departmentLabel").value("인융"))
                .andExpect(jsonPath("$.participants[0].masked").value(true))
                .andExpect(jsonPath("$.openChatUrl").isEmpty())
                .andExpect(jsonPath("$.hasOpenChat").value(true));

        get("/api/meetups/" + id, host)
                .andExpect(jsonPath("$.participants[*].name", contains("김고대", "이*암")));
    }

    @Test
    void 성사되면_참여자에게만_실명과_오픈채팅이_공개된다() throws Exception {
        long id = createMeetup(host, 3, 6, IN_2H, IN_1H, CHAT);
        post("/api/meetups/" + id + "/join", anam, null);
        post("/api/meetups/" + id + "/join", horang, null)
                .andExpect(jsonPath("$.status").value("CONFIRMED"));

        get("/api/meetups/" + id, anam)
                .andExpect(jsonPath("$.revealed").value(true))
                .andExpect(jsonPath("$.participants[*].name", contains("김고대", "이안암", "박호랑")))
                .andExpect(jsonPath("$.openChatUrl").value(CHAT));

        // 참여 안 한 사람은 여전히 가려진다
        get("/api/meetups/" + id, outsider)
                .andExpect(jsonPath("$.revealed").value(false))
                .andExpect(jsonPath("$.participants[*].name", contains("김*대", "이*암", "박*랑")))
                .andExpect(jsonPath("$.host.name").value("김*대"))
                .andExpect(jsonPath("$.openChatUrl").isEmpty())
                .andExpect(jsonPath("$.canJoin").value(true));

        // 목록에서도 서버가 가려서 내려준다
        get("/api/meetups", outsider).andExpect(jsonPath("$[0].host.name").value("김*대"));
        get("/api/meetups", anam).andExpect(jsonPath("$[0].host.name").value("김고대"));
    }

    @Test
    void 대학원장은_항상_실명을_본다() throws Exception {
        long id = createMeetup(host, 3, 6, IN_2H, IN_1H, CHAT);

        get("/api/meetups/" + id, ownerToken())
                .andExpect(jsonPath("$.revealed").value(true))
                .andExpect(jsonPath("$.host.name").value("김고대"))
                .andExpect(jsonPath("$.openChatUrl").value(CHAT))
                .andExpect(jsonPath("$.canEdit").value(true))
                .andExpect(jsonPath("$.canCancel").value(true));
    }

    @Test
    void 교수도_3교시에서는_일반_원우처럼_이름이_가려지고_취소만_할_수_있다() throws Exception {
        long id = createMeetup(host, 3, 6, IN_2H, IN_1H, CHAT);
        String manager = managerToken("professor", "정교수", 58);

        get("/api/meetups/" + id, manager)
                .andExpect(jsonPath("$.revealed").value(false))
                .andExpect(jsonPath("$.host.name").value("김*대"))
                .andExpect(jsonPath("$.openChatUrl").isEmpty())
                .andExpect(jsonPath("$.canEdit").value(false))
                .andExpect(jsonPath("$.canCancel").value(true));

        put("/api/meetups/" + id, manager, """
                {"title":"바꿈","description":"","place":"안암","categories":["BEER"],"purposes":[],"openChatUrl":""}
                """).andExpect(status().isForbidden());
        post("/api/meetups/" + id + "/cancel", manager, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELED"));
    }

    @Test
    void 정원이_차면_더_참여할_수_없다() throws Exception {
        long id = createMeetup(host, 2, 2, IN_2H, IN_1H, null);
        post("/api/meetups/" + id + "/join", anam, null).andExpect(status().isOk());

        post("/api/meetups/" + id + "/join", horang, null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("FULL"));
    }

    @Test
    void 인원_제한_없는_번개() throws Exception {
        long id = createMeetup(host, 2, null, IN_2H, IN_1H, null);
        post("/api/meetups/" + id + "/join", anam, null).andExpect(status().isOk());
        post("/api/meetups/" + id + "/join", horang, null).andExpect(status().isOk());

        get("/api/meetups/" + id, outsider)
                .andExpect(jsonPath("$.maxPeople").isEmpty())
                .andExpect(jsonPath("$.participantCount").value(3))
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.canJoin").value(true));
        post("/api/meetups/" + id + "/join", outsider, null).andExpect(status().isOk());
    }

    @Test
    void 같은_번개에_두번_참여할_수_없다() throws Exception {
        long id = createMeetup(host, 3, 6, IN_2H, IN_1H, null);
        post("/api/meetups/" + id + "/join", anam, null).andExpect(status().isOk());

        post("/api/meetups/" + id + "/join", anam, null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ALREADY_JOINED"));
    }

    @Test
    void 마감까지_미달이면_무산되고_목록에서_조용히_사라진다() throws Exception {
        long id = createMeetup(host, 3, 6, IN_2H, IN_1H, null);
        post("/api/meetups/" + id + "/join", anam, null);

        clock.advance(Duration.ofMinutes(90)); // 마감 지남, 모임 전

        get("/api/meetups", outsider).andExpect(jsonPath("$", hasSize(0)));
        get("/api/meetups/" + id, host).andExpect(jsonPath("$.status").value("FAILED"));
        // 본인의 "내 번개"에서는 무산 여부를 알 수 있다
        get("/api/me/meetups", host).andExpect(jsonPath("$[0].status").value("FAILED"));
        // 무산된 번개는 성사되지 않았으므로 실명 공개도 없다
        get("/api/meetups/" + id, anam).andExpect(jsonPath("$.participants[0].name").value("김*대"));

        post("/api/meetups/" + id + "/join", outsider, null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CLOSED"));
    }

    @Test
    void 참여자는_마감_전까지만_나갈_수_있고_개설자는_못_나간다() throws Exception {
        long id = createMeetup(host, 2, 6, IN_2H, IN_1H, null);
        post("/api/meetups/" + id + "/join", anam, null);

        delete("/api/meetups/" + id + "/join", host)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("HOST_CANNOT_LEAVE"));

        delete("/api/meetups/" + id + "/join", anam)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.participantCount").value(1))
                .andExpect(jsonPath("$.status").value("RECRUITING"));

        post("/api/meetups/" + id + "/join", anam, null);
        clock.advance(Duration.ofMinutes(61));
        delete("/api/meetups/" + id + "/join", anam)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CLOSED"));
    }

    @Test
    void 수정과_취소는_개설자와_관리자만() throws Exception {
        long id = createMeetup(host, 3, 6, IN_2H, IN_1H, null);
        String update = """
                {"title":"장소 바뀜","description":"","place":"참살이길","categories":["SOJU","HIGHBALL"],
                 "purposes":["학업"],"openChatUrl":"%s"}
                """.formatted(CHAT);

        put("/api/meetups/" + id, anam, update).andExpect(status().isForbidden());
        post("/api/meetups/" + id + "/cancel", anam, null).andExpect(status().isForbidden());

        put("/api/meetups/" + id, host, update)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.place").value("참살이길"))
                .andExpect(jsonPath("$.categories", contains("SOJU", "HIGHBALL")))
                .andExpect(jsonPath("$.purposes", contains("학업")));

        post("/api/meetups/" + id + "/cancel", ownerToken(), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELED"));

        get("/api/meetups", anam).andExpect(jsonPath("$", hasSize(0)));
        post("/api/meetups/" + id + "/join", anam, null).andExpect(jsonPath("$.code").value("CANCELED"));
    }

    @Test
    void 성사되어_모였던_번개는_지난_번개에_나온다() throws Exception {
        long confirmed = createMeetup(host, 2, 6, IN_2H, IN_1H, null);
        post("/api/meetups/" + confirmed + "/join", anam, null);
        createMeetup(horang, 3, 6, IN_2H, IN_1H, null); // 무산될 번개

        clock.advance(Duration.ofHours(3));

        get("/api/meetups?scope=past", outsider)
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].status").value("DONE"));
        get("/api/meetups", outsider).andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void 목록은_가까운_시간순() throws Exception {
        long later = createMeetup(host, 2, 6, Duration.ofHours(5), IN_1H, null);
        long sooner = createMeetup(anam, 2, 6, IN_2H, IN_1H, null);

        List<Integer> ids = read(get("/api/meetups", outsider), "$[*].id");
        assertThat(ids).containsExactly((int) sooner, (int) later);
    }

    @Test
    void 시간과_인원_검증() throws Exception {
        // 마감이 모임보다 늦음
        post("/api/meetups", host, meetupJson("치맥", 2, 4, T0.plus(IN_1H), T0.plus(IN_2H), null))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_DEADLINE"));
        // 지난 시간
        post("/api/meetups", host, meetupJson("치맥", 2, 4, T0.minus(IN_1H), T0.minus(IN_2H), null))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_TIME"));
        // 최대 < 최소
        post("/api/meetups", host, meetupJson("치맥", 4, 3, T0.plus(IN_2H), T0.plus(IN_1H), null))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PEOPLE"));
        // 한 달 넘게
        post("/api/meetups", host, meetupJson("치맥", 2, 4, T0.plus(Duration.ofDays(40)), T0.plus(IN_1H), null))
                .andExpect(status().isBadRequest());
        // 오픈채팅 링크 형식
        post("/api/meetups", host, meetupJson("치맥", 2, 4, T0.plus(IN_2H), T0.plus(IN_1H), "javascript:alert(1)"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        // 제목 없음
        post("/api/meetups", host, meetupJson("", 2, 4, T0.plus(IN_2H), T0.plus(IN_1H), null))
                .andExpect(status().isBadRequest());
    }

    @Test
    void 없는_번개는_404() throws Exception {
        get("/api/meetups/999999", host)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("3교시를 찾을 수 없어요"));
    }

    @Test
    void 마실_것은_여러_개_고르고_모임_목적을_붙인다() throws Exception {
        String body = """
                {"title":"소맥 하실 분","description":"","place":"참살이길 ○○호프","categories":["BEER","SOJU","BEER"],
                 "purposes":[" 친목 ","학업","친목",""],"meetAt":"%s","deadlineAt":"%s","minPeople":2,"maxPeople":6}
                """.formatted(T0.plus(IN_2H), T0.plus(IN_1H));
        // 빈 목적은 거절
        post("/api/meetups", host, body).andExpect(status().isBadRequest());

        body = body.replace(",\"\"]", "]");
        long id = ((Number) read(post("/api/meetups", host, body).andExpect(status().isCreated()), "$.id")).longValue();

        // 종류는 정해진 순서로, 목적은 공백·중복을 정리해서 고른 순서대로
        get("/api/meetups/" + id, anam)
                .andExpect(jsonPath("$.place").value("참살이길 ○○호프"))
                .andExpect(jsonPath("$.categories", contains("SOJU", "BEER")))
                .andExpect(jsonPath("$.purposes", contains("친목", "학업")));
        get("/api/meetups", anam)
                .andExpect(jsonPath("$[0].categories", contains("SOJU", "BEER")))
                .andExpect(jsonPath("$[0].purposes", contains("친목", "학업")));
    }

    @Test
    void 종류와_목적_검증() throws Exception {
        String base = """
                {"title":"치맥","description":"","place":"안암","categories":%s,"purposes":%s,
                 "meetAt":"%s","deadlineAt":"%s","minPeople":2,"maxPeople":6}
                """;
        String meet = T0.plus(IN_2H).toString();
        String deadline = T0.plus(IN_1H).toString();
        // 종류를 하나도 안 고름
        post("/api/meetups", host, base.formatted("[]", "[]", meet, deadline))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("무엇을 마실지 하나 이상 골라 주세요"));
        // 목적 4개
        post("/api/meetups", host, base.formatted("[\"BEER\"]", "[\"a\",\"b\",\"c\",\"d\"]", meet, deadline))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("모임 목적은 3개까지 고를 수 있어요"));
        // 목적에 쉼표 (DB 에 쉼표로 이어 저장하므로)
        post("/api/meetups", host, base.formatted("[\"BEER\"]", "[\"친목,잡담\"]", meet, deadline))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("모임 목적에는 쉼표(,)를 쓸 수 없어요"));
        // 목적이 너무 김
        post("/api/meetups", host, base.formatted("[\"BEER\"]", "[\"열세글자가넘는아주긴모임목적\"]", meet, deadline))
                .andExpect(status().isBadRequest());
        // 목적은 안 넣어도 된다
        post("/api/meetups", host, base.formatted("[\"BEER\"]", "null", meet, deadline))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.purposes", hasSize(0)));
    }

    @Test
    void 최소_인원이_없으면_열자마자_성사되고_참여하면_바로_실명() throws Exception {
        long id = createMeetup(host, null, null, IN_2H, IN_1H, CHAT);

        get("/api/meetups/" + id, outsider)
                .andExpect(jsonPath("$.minPeople").isEmpty())
                .andExpect(jsonPath("$.maxPeople").isEmpty())
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.revealed").value(false))
                .andExpect(jsonPath("$.host.name").value("김*대"))
                .andExpect(jsonPath("$.canJoin").value(true));

        post("/api/meetups/" + id + "/join", anam, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.revealed").value(true))
                .andExpect(jsonPath("$.host.name").value("김고대"))
                .andExpect(jsonPath("$.openChatUrl").value(CHAT));

        // 마감이 지나도 무산되지 않는다
        clock.advance(Duration.ofMinutes(90));
        get("/api/meetups", outsider).andExpect(jsonPath("$[0].status").value("CONFIRMED"));
    }

    @Test
    void 최소_인원이_없어도_혼자였으면_지난_3교시에_안_나온다() throws Exception {
        createMeetup(host, null, 6, IN_2H, IN_1H, null);
        long together = createMeetup(anam, null, 6, IN_2H, IN_1H, null);
        post("/api/meetups/" + together + "/join", horang, null);

        clock.advance(Duration.ofHours(3));

        get("/api/meetups?scope=past", outsider)
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value((int) together));
    }

    @Test
    void 대학원장_이름은_누구에게나_실명과_대학원장_표시() throws Exception {
        long id = createMeetup(ownerToken(), 3, 6, IN_2H, IN_1H, null);
        post("/api/meetups/" + id + "/join", anam, null);

        get("/api/meetups/" + id, outsider)
                .andExpect(jsonPath("$.revealed").value(false))
                .andExpect(jsonPath("$.host.name").value("박관리"))
                .andExpect(jsonPath("$.host.owner").value(true))
                .andExpect(jsonPath("$.host.masked").value(false))
                .andExpect(jsonPath("$.participants[1].name").value("이*암"))
                .andExpect(jsonPath("$.participants[1].owner").value(false));
    }

    @Test
    void 우리_기수만_3교시는_같은_기수만_보고_참여한다() throws Exception {
        // host 김고대 61기, horang 61기, anam 60기, outsider 59기
        long id = createMeetup(host, 2, 6, IN_2H, IN_1H, null, "COHORT");

        get("/api/meetups/" + id, host).andExpect(jsonPath("$.onlyCohort").value(61));
        get("/api/meetups", horang)
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].onlyCohort").value(61))
                .andExpect(jsonPath("$[0].joinable").value(true));
        post("/api/meetups/" + id + "/join", horang, null).andExpect(status().isOk());

        // 다른 기수: 목록에 안 보이고, 링크로 들어와도 안내만, 참여도 막힌다
        get("/api/meetups", anam).andExpect(jsonPath("$", hasSize(0)));
        get("/api/meetups/" + id, anam)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("COHORT_ONLY"))
                .andExpect(jsonPath("$.message").value("고려대 61기 전용 3교시예요"));
        post("/api/meetups/" + id + "/join", anam, null)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("COHORT_ONLY"));

        // 성사되어 지난 3교시에도 다른 기수에겐 안 보인다
        clock.advance(Duration.ofHours(3));
        get("/api/meetups?scope=past", horang).andExpect(jsonPath("$", hasSize(1)));
        get("/api/meetups?scope=past", outsider).andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void 교수와_대학원장은_다른_기수_전용_3교시도_보지만_참여는_못한다() throws Exception {
        long id = createMeetup(host, 2, 6, IN_2H, IN_1H, null, "COHORT");
        String manager = managerToken("professor", "정교수", 58);

        get("/api/meetups", manager)
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].joinable").value(false));
        get("/api/meetups/" + id, manager)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.canJoin").value(false));
        post("/api/meetups/" + id + "/join", manager, null).andExpect(status().isForbidden());
        get("/api/meetups/" + id, ownerToken()).andExpect(status().isOk());
    }

    @Test
    void 기수_제한이_없으면_누구나() throws Exception {
        long id = createMeetup(host, 2, 6, IN_2H, IN_1H, null, "SCHOOL");

        get("/api/meetups/" + id, outsider)
                .andExpect(jsonPath("$.onlyCohort").isEmpty())
                .andExpect(jsonPath("$.canJoin").value(true));
    }
}
