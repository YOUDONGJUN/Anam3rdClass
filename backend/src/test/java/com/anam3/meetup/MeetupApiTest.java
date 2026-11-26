package com.anam3.meetup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;
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
    void 연_사람은_최소_인원이_안_차도_지금_인원으로_확정한다() throws Exception {
        long id = createMeetup(host, 4, 6, IN_2H, IN_1H, CHAT);
        post("/api/meetups/" + id + "/join", anam, null);
        post("/api/meetups/" + id + "/join", horang, null)
                .andExpect(jsonPath("$.status").value("RECRUITING"));

        get("/api/meetups/" + id, host).andExpect(jsonPath("$.canConfirm").value(true));
        get("/api/meetups/" + id, anam).andExpect(jsonPath("$.canConfirm").value(false));
        post("/api/meetups/" + id + "/confirm", anam, null).andExpect(status().isForbidden());

        post("/api/meetups/" + id + "/confirm", host, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.minPeople").value(3))
                .andExpect(jsonPath("$.canConfirm").value(false));

        // 성사와 똑같이: 참여자끼리 실명과 단톡방이 열린다
        get("/api/meetups/" + id, anam)
                .andExpect(jsonPath("$.revealed").value(true))
                .andExpect(jsonPath("$.participants[*].name", contains("김고대", "이안암", "박호랑")))
                .andExpect(jsonPath("$.openChatUrl").value(CHAT));

        // 확정한 뒤에도 정원까지는 더 참여할 수 있다
        post("/api/meetups/" + id + "/join", outsider, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.participantCount").value(4));

        post("/api/meetups/" + id + "/confirm", host, null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ALREADY_REACHED"));
    }

    @Test
    void 혼자면_확정할_수_없다() throws Exception {
        long id = createMeetup(host, 3, 6, IN_2H, IN_1H, null);

        get("/api/meetups/" + id, host).andExpect(jsonPath("$.canConfirm").value(false));
        post("/api/meetups/" + id + "/confirm", host, null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("TOO_FEW"));
    }

    @Test
    void 무산된_3교시도_모임_시간_전이면_지금_인원으로_되살린다() throws Exception {
        long id = createMeetup(host, 4, 6, IN_2H, IN_1H, null);
        post("/api/meetups/" + id + "/join", anam, null);
        long late = createMeetup(host, 4, 6, IN_2H, IN_1H, null);
        post("/api/meetups/" + late + "/join", anam, null);

        clock.advance(Duration.ofMinutes(90)); // 마감 지나 무산, 모임 전
        get("/api/meetups/" + id, host)
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.canConfirm").value(true));

        post("/api/meetups/" + id + "/confirm", host, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.minPeople").value(2));
        get("/api/meetups/" + id, anam).andExpect(jsonPath("$.revealed").value(true));
        // 다시 목록에 나온다 (마감이 지났으니 새로 참여는 못 한다 — 중간 합류가 꺼져 있어서)
        get("/api/meetups", outsider).andExpect(jsonPath("$[*].id", contains((int) id)));
        post("/api/meetups/" + id + "/join", outsider, null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CLOSED"));

        clock.advance(Duration.ofMinutes(31)); // 모임 시간 지남
        get("/api/meetups/" + late, host).andExpect(jsonPath("$.canConfirm").value(false));
        post("/api/meetups/" + late + "/confirm", host, null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("NOT_OPEN"));
    }

    @Test
    void 성사된_뒤에_누가_나가도_확정이_유지되고_실명도_그대로() throws Exception {
        long id = createMeetup(host, 3, 6, IN_2H, IN_1H, null);
        post("/api/meetups/" + id + "/join", anam, null);
        post("/api/meetups/" + id + "/join", horang, null)
                .andExpect(jsonPath("$.status").value("CONFIRMED"));

        delete("/api/meetups/" + id + "/join", horang)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.participantCount").value(2));

        get("/api/meetups/" + id, anam)
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.minPeople").value(2))
                .andExpect(jsonPath("$.revealed").value(true))
                .andExpect(jsonPath("$.participants[*].name", contains("김고대", "이안암")));
    }

    @Test
    void 성사된_3교시에서_30일에_세_번_나가면_한동안_이미_성사된_3교시에는_참여할_수_없다() throws Exception {
        for (int i = 0; i < 3; i++) {
            long id = createMeetup(host, null, 6, IN_2H, IN_1H, null); // 열자마자 성사 — 참여하면 실명이 보인다
            post("/api/meetups/" + id + "/join", horang, null).andExpect(jsonPath("$.revealed").value(true));
            delete("/api/meetups/" + id + "/join", horang).andExpect(status().isOk());
        }
        long reached = createMeetup(host, null, 6, IN_2H, IN_1H, null);
        post("/api/meetups/" + reached + "/join", horang, null)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("LEAVE_LIMIT"));
        // 아직 성사 전인 3교시에는 참여할 수 있다 (실명이 바로 보이지 않는다)
        long recruiting = createMeetup(host, 3, 6, IN_2H, IN_1H, null);
        post("/api/meetups/" + recruiting + "/join", horang, null).andExpect(status().isOk());

        clock.advance(Duration.ofDays(30));
        String laterJson = meetupJson("치맥 번개", null, 6, clock.instant().plus(IN_2H), clock.instant().plus(IN_1H), null);
        Number later = read(post("/api/meetups", host, laterJson).andExpect(status().isCreated()), "$.id");
        post("/api/meetups/" + later + "/join", horang, null).andExpect(status().isOk());
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

        // 둘이 모여 성사됐던 3교시라, 한 명이 나가도 확정은 유지된다 (최소 인원 없음으로)
        delete("/api/meetups/" + id + "/join", anam)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.participantCount").value(1))
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.minPeople").isEmpty());

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
                .andExpect(jsonPath("$.purposes", contains("학업")))
                // 인원·시간·대상·참여 방식을 안 보내면(예전 화면) 모두 그대로
                .andExpect(jsonPath("$.minPeople").value(3))
                .andExpect(jsonPath("$.maxPeople").value(6))
                .andExpect(jsonPath("$.meetAt").value(T0.plus(IN_2H).toString()))
                .andExpect(jsonPath("$.audience").value("SCHOOL"))
                .andExpect(jsonPath("$.lateJoin").value(false));

        post("/api/meetups/" + id + "/cancel", ownerToken(), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELED"));

        get("/api/meetups", anam).andExpect(jsonPath("$", hasSize(0)));
        post("/api/meetups/" + id + "/join", anam, null).andExpect(jsonPath("$.code").value("CANCELED"));
    }

    @Test
    void 수정에서_인원을_바꿀_수_있다() throws Exception {
        long id = createMeetup(host, 3, 6, IN_2H, IN_1H, CHAT);
        post("/api/meetups/" + id + "/join", anam, null); // 2명 · 모집 중

        put("/api/meetups/" + id, anam, people(2, 6)).andExpect(status().isForbidden());
        put("/api/meetups/" + id, host, people(4, 3)).andExpect(jsonPath("$.code").value("INVALID_PEOPLE"));
        put("/api/meetups/" + id, host, people(1, 6)).andExpect(status().isBadRequest());

        // 최소 인원을 낮추면 바로 성사 → 참여자끼리 실명 공개
        put("/api/meetups/" + id, host, people(2, 6))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.minPeople").value(2))
                .andExpect(jsonPath("$.status").value("CONFIRMED"));
        get("/api/meetups/" + id, anam).andExpect(jsonPath("$.revealed").value(true));

        // 성사된 뒤에는 최소 인원을 지금 인원(2명)보다 올릴 수 없다
        put("/api/meetups/" + id, host, people(3, 6)).andExpect(jsonPath("$.code").value("ALREADY_REACHED"));

        // 최대를 지금 인원으로 줄이면 마감, 제한 없음으로 바꾸면 다시 받는다
        put("/api/meetups/" + id, host, people(2, 2)).andExpect(jsonPath("$.maxPeople").value(2));
        post("/api/meetups/" + id + "/join", horang, null).andExpect(jsonPath("$.code").value("FULL"));
        put("/api/meetups/" + id, host, people(2, null)).andExpect(jsonPath("$.maxPeople").isEmpty());
        post("/api/meetups/" + id + "/join", horang, null).andExpect(status().isOk());

        // 이미 참여한 3명보다 적게는 못 줄인다
        put("/api/meetups/" + id, host, people(2, 2)).andExpect(jsonPath("$.code").value("PEOPLE_BELOW_JOINED"));

        // 대학원장도 고칠 수 있고, 최소 인원 없음으로 바꿀 수 있다
        put("/api/meetups/" + id, ownerToken(), people(null, 3))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.minPeople").isEmpty())
                .andExpect(jsonPath("$.maxPeople").value(3))
                .andExpect(jsonPath("$.participantCount").value(3));
    }

    @Test
    void 수정에서_시간_대상_참여방식도_바꿀_수_있다() throws Exception {
        String sinchon = signup("sinchon@yonsei.ac.kr", "이신촌", 3, departmentId("YONSEI", "인컴"));
        long id = createMeetup(host, 2, 6, IN_2H, IN_1H, CHAT); // 고려대 61기 김고대 · 우리 학교
        post("/api/meetups/" + id + "/join", anam, null).andExpect(jsonPath("$.status").value("CONFIRMED")); // 60기

        // 시간: 열 때와 같은 규칙
        Instant meet = T0.plus(Duration.ofHours(3));
        Instant deadline = meet.minus(Duration.ofMinutes(10));
        put("/api/meetups/" + id, host, edit(meet, deadline, "SCHOOL", null))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.meetAt").value(meet.toString()))
                .andExpect(jsonPath("$.deadlineAt").value(deadline.toString()));
        put("/api/meetups/" + id, host, edit(T0.minusSeconds(60), T0.minusSeconds(120), "SCHOOL", null))
                .andExpect(jsonPath("$.code").value("INVALID_TIME"));
        put("/api/meetups/" + id, host, edit(meet, meet.plusSeconds(60), "SCHOOL", null))
                .andExpect(jsonPath("$.code").value("INVALID_DEADLINE"));

        // 대상: 다른 기수(이안암 60기)가 있어 '우리 기수만'으로는 못 좁힌다
        put("/api/meetups/" + id, host, edit(meet, deadline, "COHORT", null))
                .andExpect(jsonPath("$.code").value("AUDIENCE_NARROWER"))
                .andExpect(jsonPath("$.message").value("다른 기수 원우가 이미 참여해서 '우리 기수만'으로 바꿀 수 없어요"));
        // 연합으로 넓히면 연세대 원우도 참여하고, 그 뒤에는 '우리 학교'로 못 좁힌다
        put("/api/meetups/" + id, host, edit(meet, deadline, "ALL", null)).andExpect(jsonPath("$.audience").value("ALL"));
        post("/api/meetups/" + id + "/join", sinchon, null).andExpect(status().isOk());
        put("/api/meetups/" + id, host, edit(meet, deadline, "SCHOOL", null))
                .andExpect(jsonPath("$.message").value("다른 학교 원우가 이미 참여해서 '우리 학교'로 바꿀 수 없어요"));

        // 참여 방식
        put("/api/meetups/" + id, host, edit(meet, deadline, "ALL", "{\"lateJoin\":true,\"fee\":5000,\"feePrepaid\":true}"))
                .andExpect(jsonPath("$.lateJoin").value(true))
                .andExpect(jsonPath("$.fee").value(5000))
                .andExpect(jsonPath("$.feePrepaid").value(true));
        put("/api/meetups/" + id, host, edit(meet, deadline, "ALL", "{\"fee\":null,\"feePrepaid\":true}"))
                .andExpect(jsonPath("$.code").value("INVALID_FEE"));
        put("/api/meetups/" + id, host, edit(meet, deadline, "ALL", "{\"fee\":500}")).andExpect(status().isBadRequest());
        put("/api/meetups/" + id, host, edit(meet, deadline, "ALL", "{\"lateJoin\":false,\"fee\":null,\"feePrepaid\":false}"))
                .andExpect(jsonPath("$.lateJoin").value(false))
                .andExpect(jsonPath("$.fee").isEmpty());

        // 시간이 그대로면 마감이 지난 성사 3교시도 다른 칸은 고칠 수 있다
        clock.advance(Duration.ofMinutes(175)); // 마감(2시간 50분) 지남, 모임 전
        put("/api/meetups/" + id, host, edit(meet, deadline, "ALL", null))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"));
    }

    private static String edit(Instant meetAt, Instant deadlineAt, String audience, String options) {
        return """
                {"title":"치맥 한잔","description":"","place":"참살이길","categories":["BEER"],"purposes":[],"openChatUrl":"",
                 "schedule":{"meetAt":"%s","deadlineAt":"%s"},"audience":"%s"%s}
                """.formatted(meetAt, deadlineAt, audience, options == null ? "" : ",\"options\":" + options);
    }

    private static String people(Integer min, Integer max) {
        return """
                {"title":"치맥 한잔","description":"","place":"참살이길","categories":["BEER"],"purposes":[],
                 "openChatUrl":"","people":{"minPeople":%s,"maxPeople":%s}}
                """.formatted(min, max);
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

    // ---------- 중간 합류 · 참여비 · 지우기 ----------

    private String optionsJson(Integer min, boolean lateJoin, Integer fee, Boolean feePrepaid) {
        return """
                {"title":"치맥 번개","description":"","place":"안암역 앞","categories":["BEER"],"purposes":[],
                 "meetAt":"%s","deadlineAt":"%s","minPeople":%s,"maxPeople":6,"openChatUrl":"",
                 "audience":"SCHOOL","lateJoin":%s,"fee":%s,"feePrepaid":%s}
                """.formatted(T0.plus(IN_2H), T0.plus(IN_1H), min, lateJoin, fee, feePrepaid);
    }

    @Test
    void 참여비와_선입금_중간_합류는_열_때_정한다() throws Exception {
        long id = ((Number) read(post("/api/meetups", host, optionsJson(2, true, 15000, true))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.lateJoin").value(true))
                .andExpect(jsonPath("$.fee").value(15000))
                .andExpect(jsonPath("$.feePrepaid").value(true)), "$.id")).longValue();
        get("/api/meetups", horang)
                .andExpect(jsonPath("$[?(@.id == " + id + ")].fee").value(15000))
                .andExpect(jsonPath("$[?(@.id == " + id + ")].lateJoin").value(true));

        // 참여비 없이 선입금은 안 된다, 참여비는 1,000원 이상
        post("/api/meetups", host, optionsJson(2, false, null, true))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_FEE"));
        post("/api/meetups", host, optionsJson(2, false, 500, false)).andExpect(status().isBadRequest());

        // 비우면 참여비 없음 · 중간 합류 아님
        long plain = createMeetup(host, 2, 6, IN_2H, IN_1H, null);
        get("/api/meetups/" + plain, host)
                .andExpect(jsonPath("$.fee").isEmpty())
                .andExpect(jsonPath("$.feePrepaid").value(false))
                .andExpect(jsonPath("$.lateJoin").value(false));
    }

    @Test
    void 중간_합류_OK면_성사된_뒤에도_모임_시작_전까지_참여할_수_있다() throws Exception {
        long late = ((Number) read(post("/api/meetups", host, optionsJson(2, true, null, false)), "$.id")).longValue();
        long strict = createMeetup(host, 2, 6, IN_2H, IN_1H, null);
        post("/api/meetups/" + late + "/join", anam, null).andExpect(status().isOk());
        post("/api/meetups/" + strict + "/join", anam, null).andExpect(status().isOk());

        clock.advance(Duration.ofMinutes(90)); // 마감(1시간) 지남, 모임(2시간) 전
        get("/api/meetups/" + late, horang).andExpect(jsonPath("$.canJoin").value(true));
        post("/api/meetups/" + late + "/join", horang, null).andExpect(status().isOk());
        get("/api/meetups/" + strict, horang).andExpect(jsonPath("$.canJoin").value(false));
        post("/api/meetups/" + strict + "/join", horang, null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CLOSED"));

        clock.advance(Duration.ofMinutes(31)); // 모임 시작 뒤에는 받지 않는다
        post("/api/meetups/" + late + "/join", outsider, null).andExpect(jsonPath("$.code").value("CLOSED"));
    }

    @Test
    void 중간_합류_OK라도_마감까지_못_모여_무산되면_참여할_수_없다() throws Exception {
        long late = ((Number) read(post("/api/meetups", host, optionsJson(3, true, null, false)), "$.id")).longValue();
        clock.advance(Duration.ofMinutes(90));
        post("/api/meetups/" + late + "/join", horang, null).andExpect(jsonPath("$.code").value("CLOSED"));
    }

    @Test
    void 지우기는_개설자_그_학교_교수_대학원장만_하고_신고는_남는다() throws Exception {
        long id = createMeetup(host, 2, 6, IN_2H, IN_1H, null);
        post("/api/meetups/" + id + "/join", horang, null).andExpect(status().isOk());
        post("/api/reports", horang, """
                {"targetType":"MEETUP","targetId":%d,"reason":"SPAM","detail":""}
                """.formatted(id)).andExpect(status().isNoContent());

        get("/api/meetups/" + id, horang).andExpect(jsonPath("$.canDelete").value(false));
        get("/api/meetups/" + id, host).andExpect(jsonPath("$.canDelete").value(true));
        delete("/api/meetups/" + id, horang).andExpect(status().isForbidden());
        delete("/api/meetups/" + id, host).andExpect(status().isNoContent());
        get("/api/meetups/" + id, host).andExpect(status().isNotFound());
        get("/api/me/meetups", horang).andExpect(jsonPath("$", hasSize(0)));

        // 신고는 남고, 신고 당시 내용으로 확인한다
        get("/api/admin/reports", ownerToken())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].meetupId").isEmpty())
                .andExpect(jsonPath("$[0].targetSnapshot").value("치맥 번개\n수업 끝나고 한잔"));

        // 교수는 우리 학교 3교시만, 대학원장은 모두
        long other = createMeetup(host, 2, 6, IN_2H, IN_1H, null);
        String yonseiManager = managerToken("prof@yonsei.ac.kr", "정교수", 1);
        delete("/api/meetups/" + other, yonseiManager).andExpect(status().isForbidden());
        String kuManager = managerToken("professor", "정교수", 58);
        get("/api/meetups/" + other, kuManager).andExpect(jsonPath("$.canDelete").value(true));
        delete("/api/meetups/" + other, kuManager).andExpect(status().isNoContent());
        long third = createMeetup(host, 2, 6, IN_2H, IN_1H, null);
        delete("/api/meetups/" + third, ownerToken()).andExpect(status().isNoContent());
    }
}
