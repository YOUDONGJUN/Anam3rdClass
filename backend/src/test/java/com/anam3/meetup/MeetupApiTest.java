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

    private String host;     // 김민수
    private String jiyoung;  // 이지영
    private String hyunwoo;  // 박현우
    private String outsider; // 최서연

    @BeforeEach
    void users() throws Exception {
        host = signup("minsu", "김민수", 2025);
        jiyoung = signup("jiyoung", "이지영", 2024);
        hyunwoo = signup("hyunwoo", "박현우", 2025);
        outsider = signup("seoyeon", "최서연", 2023);
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
        post("/api/meetups/" + id + "/join", jiyoung, null).andExpect(status().isOk());

        get("/api/meetups/" + id, jiyoung)
                .andExpect(jsonPath("$.status").value("RECRUITING"))
                .andExpect(jsonPath("$.revealed").value(false))
                .andExpect(jsonPath("$.participants[*].name", contains("김*수", "이지영"))) // 나는 실명
                .andExpect(jsonPath("$.participants[0].admissionLabel").value("25학번"))
                .andExpect(jsonPath("$.participants[0].masked").value(true))
                .andExpect(jsonPath("$.openChatUrl").isEmpty())
                .andExpect(jsonPath("$.hasOpenChat").value(true));

        get("/api/meetups/" + id, host)
                .andExpect(jsonPath("$.participants[*].name", contains("김민수", "이*영")));
    }

    @Test
    void 성사되면_참여자에게만_실명과_오픈채팅이_공개된다() throws Exception {
        long id = createMeetup(host, 3, 6, IN_2H, IN_1H, CHAT);
        post("/api/meetups/" + id + "/join", jiyoung, null);
        post("/api/meetups/" + id + "/join", hyunwoo, null)
                .andExpect(jsonPath("$.status").value("CONFIRMED"));

        get("/api/meetups/" + id, jiyoung)
                .andExpect(jsonPath("$.revealed").value(true))
                .andExpect(jsonPath("$.participants[*].name", contains("김민수", "이지영", "박현우")))
                .andExpect(jsonPath("$.openChatUrl").value(CHAT));

        // 참여 안 한 사람은 여전히 가려진다
        get("/api/meetups/" + id, outsider)
                .andExpect(jsonPath("$.revealed").value(false))
                .andExpect(jsonPath("$.participants[*].name", contains("김*수", "이*영", "박*우")))
                .andExpect(jsonPath("$.host.name").value("김*수"))
                .andExpect(jsonPath("$.openChatUrl").isEmpty())
                .andExpect(jsonPath("$.canJoin").value(true));

        // 목록에서도 서버가 가려서 내려준다
        get("/api/meetups", outsider).andExpect(jsonPath("$[0].host.name").value("김*수"));
        get("/api/meetups", jiyoung).andExpect(jsonPath("$[0].host.name").value("김민수"));
    }

    @Test
    void 운영자는_항상_실명을_본다() throws Exception {
        long id = createMeetup(host, 3, 6, IN_2H, IN_1H, CHAT);

        get("/api/meetups/" + id, adminToken())
                .andExpect(jsonPath("$.revealed").value(true))
                .andExpect(jsonPath("$.host.name").value("김민수"))
                .andExpect(jsonPath("$.openChatUrl").value(CHAT));
    }

    @Test
    void 정원이_차면_더_참여할_수_없다() throws Exception {
        long id = createMeetup(host, 2, 2, IN_2H, IN_1H, null);
        post("/api/meetups/" + id + "/join", jiyoung, null).andExpect(status().isOk());

        post("/api/meetups/" + id + "/join", hyunwoo, null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("FULL"));
    }

    @Test
    void 같은_번개에_두번_참여할_수_없다() throws Exception {
        long id = createMeetup(host, 3, 6, IN_2H, IN_1H, null);
        post("/api/meetups/" + id + "/join", jiyoung, null).andExpect(status().isOk());

        post("/api/meetups/" + id + "/join", jiyoung, null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ALREADY_JOINED"));
    }

    @Test
    void 마감까지_미달이면_무산되고_목록에서_조용히_사라진다() throws Exception {
        long id = createMeetup(host, 3, 6, IN_2H, IN_1H, null);
        post("/api/meetups/" + id + "/join", jiyoung, null);

        clock.advance(Duration.ofMinutes(90)); // 마감 지남, 모임 전

        get("/api/meetups", outsider).andExpect(jsonPath("$", hasSize(0)));
        get("/api/meetups/" + id, host).andExpect(jsonPath("$.status").value("FAILED"));
        // 본인의 "내 번개"에서는 무산 여부를 알 수 있다
        get("/api/me/meetups", host).andExpect(jsonPath("$[0].status").value("FAILED"));
        // 무산된 번개는 성사되지 않았으므로 실명 공개도 없다
        get("/api/meetups/" + id, jiyoung).andExpect(jsonPath("$.participants[0].name").value("김*수"));

        post("/api/meetups/" + id + "/join", outsider, null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CLOSED"));
    }

    @Test
    void 참여자는_마감_전까지만_나갈_수_있고_개설자는_못_나간다() throws Exception {
        long id = createMeetup(host, 2, 6, IN_2H, IN_1H, null);
        post("/api/meetups/" + id + "/join", jiyoung, null);

        delete("/api/meetups/" + id + "/join", host)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("HOST_CANNOT_LEAVE"));

        delete("/api/meetups/" + id + "/join", jiyoung)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.participantCount").value(1))
                .andExpect(jsonPath("$.status").value("RECRUITING"));

        post("/api/meetups/" + id + "/join", jiyoung, null);
        clock.advance(Duration.ofMinutes(61));
        delete("/api/meetups/" + id + "/join", jiyoung)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CLOSED"));
    }

    @Test
    void 수정과_취소는_개설자와_운영자만() throws Exception {
        long id = createMeetup(host, 3, 6, IN_2H, IN_1H, null);
        String update = """
                {"title":"장소 바뀜","description":"","place":"참살이길","category":"SOJU","openChatUrl":"%s"}
                """.formatted(CHAT);

        put("/api/meetups/" + id, jiyoung, update).andExpect(status().isForbidden());
        post("/api/meetups/" + id + "/cancel", jiyoung, null).andExpect(status().isForbidden());

        put("/api/meetups/" + id, host, update)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.place").value("참살이길"))
                .andExpect(jsonPath("$.category").value("SOJU"));

        post("/api/meetups/" + id + "/cancel", adminToken(), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELED"));

        get("/api/meetups", jiyoung).andExpect(jsonPath("$", hasSize(0)));
        post("/api/meetups/" + id + "/join", jiyoung, null).andExpect(jsonPath("$.code").value("CANCELED"));
    }

    @Test
    void 성사되어_모였던_번개는_지난_번개에_나온다() throws Exception {
        long confirmed = createMeetup(host, 2, 6, IN_2H, IN_1H, null);
        post("/api/meetups/" + confirmed + "/join", jiyoung, null);
        createMeetup(hyunwoo, 3, 6, IN_2H, IN_1H, null); // 무산될 번개

        clock.advance(Duration.ofHours(3));

        get("/api/meetups?scope=past", outsider)
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].status").value("DONE"));
        get("/api/meetups", outsider).andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void 목록은_가까운_시간순() throws Exception {
        long later = createMeetup(host, 2, 6, Duration.ofHours(5), IN_1H, null);
        long sooner = createMeetup(jiyoung, 2, 6, IN_2H, IN_1H, null);

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
                .andExpect(jsonPath("$.message").value("번개를 찾을 수 없어요"));
    }
}
