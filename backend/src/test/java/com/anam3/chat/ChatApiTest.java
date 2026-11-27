package com.anam3.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.net.URI;
import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;

import com.anam3.push.PushMessage;
import com.anam3.push.PushType;
import com.anam3.support.ApiTestSupport;

/** 3교시 톡방 (T0 = 3/2(월) 18:00 한국 시간) */
class ChatApiTest extends ApiTestSupport {

    private String host;
    private String anam;
    private String horang;
    private String outsider;
    private long meetupId;

    /** 김고대가 연 3교시(최소 3명, 23:00 모임)에 이안암이 참여 — 아직 성사 전. 박호랑·최밖은 참여 전 */
    private void twoJoined() throws Exception {
        host = signup("godae", "김고대", 61);
        anam = signup("anam", "이안암", 61);
        horang = signup("horang", "박호랑", 61);
        outsider = signup("outside", "최밖에", 61);
        registerDevice(host, "host-phone");
        registerDevice(anam, "anam-phone");
        registerDevice(horang, "horang-phone");
        meetupId = createMeetup(host, 3, null, Duration.ofHours(5), Duration.ofHours(3), null);
        post("/api/meetups/" + meetupId + "/join", anam, null).andExpect(status().isOk());
        pushes.clear();
        // "이안암님이 들어왔어요" 안내 줄은 따로 시험한다 (들어오고_나가면_톡방에_안내_줄이_남는다)
        jdbc.update("delete from chat_messages where kind <> 'TEXT'");
    }

    private String url() {
        return "/api/meetups/" + meetupId + "/chat";
    }

    private ResultActions say(String token, String text) throws Exception {
        return post(url(), token, "{\"body\":\"" + text + "\"}");
    }

    private long sayId(String token, String text) throws Exception {
        return ((Number) read(say(token, text).andExpect(status().isOk()), "$.id")).longValue();
    }

    private List<PushMessage> chatPushes(String device) {
        return pushes.to(device).stream().filter(p -> p.type() == PushType.CHAT).toList();
    }

    @Test
    void 함께하는_원우만_톡방에_들어오고_성사_전에는_이름이_가려진다() throws Exception {
        twoJoined();
        say(host, "다들 몇 시쯤 와요?").andExpect(status().isOk())
                .andExpect(jsonPath("$.mine").value(true))
                .andExpect(jsonPath("$.sender.name").value("김고대"));

        get(url(), anam).andExpect(status().isOk())
                .andExpect(jsonPath("$.messages", hasSize(1)))
                .andExpect(jsonPath("$.messages[0].body").value("다들 몇 시쯤 와요?"))
                .andExpect(jsonPath("$.messages[0].mine").value(false))
                .andExpect(jsonPath("$.messages[0].sender.name").value("김*대"))
                .andExpect(jsonPath("$.messages[0].sender.masked").value(true))
                .andExpect(jsonPath("$.canWrite").value(true))
                .andExpect(jsonPath("$.federated").value(false));

        get(url(), outsider).andExpect(status().isForbidden());
        say(outsider, "끼워 주세요").andExpect(status().isForbidden());
        get(url(), ownerToken()).andExpect(status().isForbidden()); // 대학원장도 함께하지 않으면 못 본다

        // 박호랑이 참여해 성사되면 서로 실명
        post("/api/meetups/" + meetupId + "/join", horang, null).andExpect(status().isOk());
        get(url(), horang).andExpect(jsonPath("$.messages[0].sender.name").value("김고대"));
    }

    @Test
    void 연합_3교시_톡방은_학교를_함께_알려_준다() throws Exception {
        host = signup("godae", "김고대", 61);
        String sinchon = signup("sinchon@yonsei.ac.kr", "이신촌", 3, departmentId("YONSEI", "인컴"));
        meetupId = createMeetup(host, null, null, Duration.ofHours(5), Duration.ofHours(3), null, "ALL");
        post("/api/meetups/" + meetupId + "/join", sinchon, null).andExpect(status().isOk());
        say(sinchon, "연세대에서 왔어요").andExpect(status().isOk());
        // [0] 은 "이신촌님이 들어왔어요"
        get(url(), host).andExpect(jsonPath("$.federated").value(true))
                .andExpect(jsonPath("$.messages[1].sender.school.code").value("YONSEI"))
                .andExpect(jsonPath("$.messages[1].sender.name").value("이신촌"));
    }

    @Test
    void 나가면_더는_못_본다() throws Exception {
        twoJoined();
        say(anam, "곧 갈게요").andExpect(status().isOk());
        delete("/api/meetups/" + meetupId + "/join", anam).andExpect(status().isOk());
        get(url(), anam).andExpect(status().isForbidden());
        // 남은 사람에게 나간 원우의 메시지는 그대로 (참여 번호는 없다)
        get(url(), host).andExpect(jsonPath("$.messages[0].body").value("곧 갈게요"))
                .andExpect(jsonPath("$.messages[0].sender.ref").value(nullValue()));
    }

    @Test
    void 새_메시지는_다른_원우에게_폰_알림으로_가고_알림함에는_쌓이지_않는다() throws Exception {
        twoJoined();
        say(host, "치킨 먹을까요").andExpect(status().isOk());

        assertThat(chatPushes("host-phone")).isEmpty();
        List<PushMessage> got = chatPushes("anam-phone");
        assertThat(got).hasSize(1);
        assertThat(got.getFirst().title()).isEqualTo("치맥 번개");
        assertThat(got.getFirst().body()).isEqualTo("김*대: 치킨 먹을까요");
        assertThat(got.getFirst().path()).isEqualTo("/meetups/" + meetupId + "/chat");
        assertThat(chatPushes("horang-phone")).isEmpty(); // 아직 함께하지 않는다
        assertThat(jdbc.queryForObject("select count(*) from notifications where type = 'CHAT'", Long.class))
                .isZero();

        // 같은 톡방은 30초에 한 번만 울린다
        say(host, "아니면 피자").andExpect(status().isOk());
        assertThat(chatPushes("anam-phone")).hasSize(1);
        clock.advance(Duration.ofSeconds(31));
        say(host, "정해 주세요").andExpect(status().isOk());
        assertThat(chatPushes("anam-phone")).hasSize(2);
    }

    @Test
    void 알림을_끄거나_톡방을_보고_있으면_폰은_울리지_않는다() throws Exception {
        twoJoined();
        put("/api/meetups/" + meetupId + "/chat/mute", anam, "{\"muted\":true}").andExpect(status().isNoContent());
        say(host, "조용히 해도 돼요").andExpect(status().isOk());
        assertThat(chatPushes("anam-phone")).isEmpty();
        get(url(), anam).andExpect(jsonPath("$.muted").value(true));

        put("/api/meetups/" + meetupId + "/chat/mute", anam, "{\"muted\":false}").andExpect(status().isNoContent());
        // 실시간 연결을 열고 톡방을 보는 중 (읽음 표시를 보냄)
        mvc.perform(MockMvcRequestBuilders.get("/api/live").header("Authorization", "Bearer " + anam))
                .andExpect(MockMvcResultMatchers.request().asyncStarted());
        put("/api/meetups/" + meetupId + "/chat/read", anam, "{\"lastId\":0}").andExpect(status().isNoContent());
        clock.advance(Duration.ofSeconds(31));
        say(host, "보고 있죠?").andExpect(status().isOk());
        assertThat(chatPushes("anam-phone")).isEmpty();

        // 톡방을 닫으면 다시 폰으로
        post("/api/meetups/" + meetupId + "/chat/leave", anam, null).andExpect(status().isNoContent());
        clock.advance(Duration.ofSeconds(31));
        say(host, "어디 갔어요").andExpect(status().isOk());
        assertThat(chatPushes("anam-phone")).hasSize(1);
    }

    @Test
    void 밤에는_울리지_않지만_모임_즈음은_예외() throws Exception {
        host = signup("godae", "김고대", 61);
        anam = signup("anam", "이안암", 61);
        registerDevice(anam, "anam-phone");
        // 사흘 뒤 23:00 모임
        meetupId = createMeetup(host, null, null, Duration.ofHours(77), Duration.ofHours(70), null);
        post("/api/meetups/" + meetupId + "/join", anam, null).andExpect(status().isOk());
        pushes.clear();

        clock.set(T0.plus(Duration.ofHours(7))); // 3/3 01:00 — 모임은 한참 뒤
        say(host, "늦은 밤에 미안").andExpect(status().isOk());
        assertThat(chatPushes("anam-phone")).isEmpty();

        clock.set(T0.plus(Duration.ofHours(78))); // 3/6 00:00 — 모임 한 시간 뒤
        say(host, "2차 어디로?").andExpect(status().isOk());
        assertThat(chatPushes("anam-phone")).hasSize(1);
    }

    @Test
    void 안_읽은_메시지_수와_읽음() throws Exception {
        twoJoined();
        say(anam, "먼저 보냄").andExpect(status().isOk());
        say(host, "하나").andExpect(status().isOk());
        long last = sayId(host, "둘");

        get("/api/me/chats", anam).andExpect(status().isOk())
                .andExpect(jsonPath("$.totalUnread").value(2))
                .andExpect(jsonPath("$.rooms[0].meetupId").value(meetupId))
                .andExpect(jsonPath("$.rooms[0].unread").value(2))
                .andExpect(jsonPath("$.rooms[0].lastPreview").value("둘"))
                .andExpect(jsonPath("$.rooms[0].lastSender").value("김*대"));
        // 내가 보내면 그 앞은 읽은 것으로 친다
        get("/api/me/chats", host).andExpect(jsonPath("$.totalUnread").value(0))
                .andExpect(jsonPath("$.rooms[0].lastSender").value("나"));

        put("/api/meetups/" + meetupId + "/chat/read", anam, "{\"lastId\":" + last + "}")
                .andExpect(status().isNoContent());
        get("/api/me/chats", anam).andExpect(jsonPath("$.totalUnread").value(0));
        get(url(), anam).andExpect(jsonPath("$.lastReadId").value(last));

        // 알림을 끈 톡방은 전체 수에서 뺀다
        put("/api/meetups/" + meetupId + "/chat/mute", anam, "{\"muted\":true}").andExpect(status().isNoContent());
        say(host, "넷").andExpect(status().isOk());
        get("/api/me/chats", anam).andExpect(jsonPath("$.totalUnread").value(0))
                .andExpect(jsonPath("$.rooms[0].unread").value(1));

        get("/api/me/chats", outsider).andExpect(jsonPath("$.rooms", hasSize(0)));
    }

    @Test
    void 오래된_메시지는_50개씩_더_불러오고_새_메시지만_따로_받는다() throws Exception {
        twoJoined();
        long hostId = memberId("godae");
        for (int i = 1; i <= 55; i++) {
            jdbc.update("insert into chat_messages (meetup_id, member_id, body, created_at) values (?, ?, ?, ?)",
                    meetupId, hostId, "메시지 " + i, java.sql.Timestamp.from(T0.plusSeconds(i)));
        }
        ResultActions first = get(url(), anam).andExpect(jsonPath("$.messages", hasSize(50)))
                .andExpect(jsonPath("$.hasMore").value(true))
                .andExpect(jsonPath("$.messages[0].body").value("메시지 6"))
                .andExpect(jsonPath("$.messages[49].body").value("메시지 55"));
        long oldest = ((Number) read(first, "$.messages[0].id")).longValue();
        long newest = ((Number) read(first, "$.messages[49].id")).longValue();

        get(url() + "?before=" + oldest, anam).andExpect(jsonPath("$.messages", hasSize(5)))
                .andExpect(jsonPath("$.hasMore").value(false))
                .andExpect(jsonPath("$.messages[0].body").value("메시지 1"));

        say(host, "새로 온 것").andExpect(status().isOk());
        get(url() + "/updates?after=" + newest, anam).andExpect(jsonPath("$.messages", hasSize(1)))
                .andExpect(jsonPath("$.messages[0].body").value("새로 온 것"));
    }

    @Test
    void 내_메시지만_지울_수_있고_자리는_남는다() throws Exception {
        twoJoined();
        long mine = sayId(anam, "잘못 보냄");
        delete(url() + "/" + mine, host).andExpect(status().isForbidden());
        delete(url() + "/" + mine, anam).andExpect(status().isNoContent());
        get(url(), host).andExpect(jsonPath("$.messages[0].deleted").value(true))
                .andExpect(jsonPath("$.messages[0].body").value(nullValue()));
        get("/api/me/chats", host).andExpect(jsonPath("$.rooms[0].lastPreview").value("지운 메시지예요"))
                .andExpect(jsonPath("$.totalUnread").value(0));
    }

    @Test
    void 빈_메시지와_취소된_3교시는_보낼_수_없다() throws Exception {
        twoJoined();
        say(anam, "  ").andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("EMPTY"));
        say(anam, "취소 전").andExpect(status().isOk());
        post("/api/meetups/" + meetupId + "/cancel", host, null).andExpect(status().isOk());
        get(url(), anam).andExpect(jsonPath("$.canWrite").value(false))
                .andExpect(jsonPath("$.messages", hasSize(1)));
        say(anam, "취소 뒤").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CHAT_CLOSED"));
    }

    @Test
    void 차단한_원우의_메시지는_가린다() throws Exception {
        twoJoined();
        post("/api/meetups/" + meetupId + "/join", horang, null).andExpect(status().isOk());
        long horangRef = participantRef(host, meetupId, 2);
        post("/api/blocks", anam, "{\"ref\":" + horangRef + "}").andExpect(status().isCreated());
        say(horang, "안녕하세요").andExpect(status().isOk());
        // [0] 은 "박호랑님이 들어왔어요"
        get(url(), anam).andExpect(jsonPath("$.messages[1].hidden").value(true))
                .andExpect(jsonPath("$.messages[1].body").value(nullValue()));
        get(url(), host).andExpect(jsonPath("$.messages[1].body").value("안녕하세요"));
        get("/api/me/chats", anam).andExpect(jsonPath("$.totalUnread").value(0))
                .andExpect(jsonPath("$.rooms[0].lastPreview").value("차단한 원우의 메시지예요"));
    }

    // ---------- 들어옴 · 나감 ----------

    @Test
    void 들어오고_나가면_톡방에_안내_줄이_남는다() throws Exception {
        host = signup("godae", "김고대", 61);
        anam = signup("anam", "이안암", 61);
        horang = signup("horang", "박호랑", 61);
        registerDevice(host, "host-phone");
        meetupId = createMeetup(host, 3, null, Duration.ofHours(5), Duration.ofHours(3), null);

        // 성사 전에는 안내 줄의 이름도 가려진다. 폰 알림은 보내지 않는다
        post("/api/meetups/" + meetupId + "/join", anam, null).andExpect(status().isOk());
        get(url(), host).andExpect(jsonPath("$.messages", hasSize(1)))
                .andExpect(jsonPath("$.messages[0].kind").value("JOIN"))
                .andExpect(jsonPath("$.messages[0].sender.name").value("이*암"))
                .andExpect(jsonPath("$.messages[0].body").value(nullValue()));
        assertThat(chatPushes("host-phone")).isEmpty();

        // 성사되면 실명
        post("/api/meetups/" + meetupId + "/join", horang, null).andExpect(status().isOk());
        get(url(), host).andExpect(jsonPath("$.messages[0].sender.name").value("이안암"))
                .andExpect(jsonPath("$.messages[1].kind").value("JOIN"))
                .andExpect(jsonPath("$.messages[1].sender.name").value("박호랑"));
        // 톡방 목록에는 보낸 사람 없이 한 줄로, 안 읽은 수에는 세지 않는다
        get("/api/me/chats", host).andExpect(jsonPath("$.rooms[0].lastPreview").value("박호랑님이 들어왔어요"))
                .andExpect(jsonPath("$.rooms[0].lastSender").value(nullValue()))
                .andExpect(jsonPath("$.totalUnread").value(0));

        // 안내 줄은 들어온 사람도 지울 수 없다
        long joinNotice = ((Number) read(get(url(), anam), "$.messages[0].id")).longValue();
        delete(url() + "/" + joinNotice, anam).andExpect(status().isForbidden());

        // 나가기 · 내보내기 모두 "나갔어요" (내보낸 것은 드러내지 않는다)
        delete("/api/meetups/" + meetupId + "/join", anam).andExpect(status().isOk());
        long horangRef = participantRef(host, meetupId, 1);
        delete("/api/meetups/" + meetupId + "/participants/" + horangRef, host).andExpect(status().isOk());
        get(url(), host).andExpect(jsonPath("$.messages", hasSize(4)))
                .andExpect(jsonPath("$.messages[2].kind").value("LEAVE"))
                .andExpect(jsonPath("$.messages[3].kind").value("LEAVE"))
                .andExpect(jsonPath("$.messages[3].sender.ref").value(nullValue()));
        assertThat(chatPushes("host-phone")).isEmpty();
    }

    // ---------- 이모티콘 ----------

    @Test
    void 이모티콘은_정해진_것만_하나씩_보낸다() throws Exception {
        twoJoined();
        long id = ((Number) read(post(url(), anam, "{\"sticker\":\"tiger-going\"}").andExpect(status().isOk())
                .andExpect(jsonPath("$.sticker").value("tiger-going"))
                .andExpect(jsonPath("$.kind").value("TEXT"))
                .andExpect(jsonPath("$.body").value(nullValue())), "$.id")).longValue();
        assertThat(chatPushes("host-phone").getLast().body()).isEqualTo("이*암: (이모티콘) 가는 중");
        get("/api/me/chats", host).andExpect(jsonPath("$.rooms[0].lastPreview").value("(이모티콘) 가는 중"))
                .andExpect(jsonPath("$.totalUnread").value(1));
        get(url(), host).andExpect(jsonPath("$.messages[0].sticker").value("tiger-going"));

        post(url(), anam, "{\"sticker\":\"pepe\"}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("STICKER"));
        post(url(), anam, "{\"sticker\":\"tiger-going\",\"body\":\"같이\"}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("STICKER_ALONE"));

        delete(url() + "/" + id, anam).andExpect(status().isNoContent());
        get(url(), host).andExpect(jsonPath("$.messages[0].sticker").value(nullValue()))
                .andExpect(jsonPath("$.messages[0].deleted").value(true));
    }

    // ---------- 읽음 숫자 ----------

    @Test
    void 다른_원우가_어디까지_읽었는지_알려_주고_새로_들어온_원우는_그때까지_읽은_것으로_친다() throws Exception {
        twoJoined();
        long first = sayId(host, "하나");
        // 이안암은 아직 안 읽었다 (들어온 때까지만 읽음) → 김고대 화면에서 "하나" 옆 1
        ResultActions page = get(url(), host).andExpect(jsonPath("$.reads", hasSize(1)));
        assertThat(((Number) read(page, "$.reads[0]")).longValue()).isLessThan(first);
        // 보낸 사람은 거기까지 읽은 것으로 친다
        get(url(), anam).andExpect(jsonPath("$.reads[0]").value(first));

        put(url() + "/read", anam, "{\"lastId\":" + first + "}").andExpect(status().isNoContent());
        get(url() + "/updates?after=" + first, host).andExpect(jsonPath("$.messages", hasSize(0)))
                .andExpect(jsonPath("$.reads[0]").value(first));

        // 나중에 들어온 박호랑은 예전 메시지를 읽은 것으로 친다 (카톡처럼)
        post("/api/meetups/" + meetupId + "/join", horang, null).andExpect(status().isOk());
        ResultActions after = get(url(), host).andExpect(jsonPath("$.reads", hasSize(2)));
        assertThat(((Number) read(after, "$.reads[1]")).longValue()).isGreaterThan(first);
    }

    // ---------- 답장 · 공감 · 공지 ----------

    private long joinNoticeId() {
        return jdbc.queryForObject("select max(id) from chat_messages where kind = 'JOIN'", Long.class);
    }

    @Test
    void 답장하면_원래_메시지를_함께_보인다() throws Exception {
        twoJoined();
        long question = sayId(host, "몇 시에 와요?");
        post(url(), anam, "{\"body\":\"9시요\",\"replyToId\":" + question + "}").andExpect(status().isOk())
                .andExpect(jsonPath("$.reply.id").value(question))
                .andExpect(jsonPath("$.reply.sender").value("김*대"))
                .andExpect(jsonPath("$.reply.mine").value(false))
                .andExpect(jsonPath("$.reply.preview").value("몇 시에 와요?"));
        // 이모티콘으로도 답장한다
        post(url(), host, "{\"sticker\":\"tiger-going\",\"replyToId\":" + question + "}").andExpect(status().isOk())
                .andExpect(jsonPath("$.reply.mine").value(true));
        get(url(), host).andExpect(jsonPath("$.messages[1].reply.sender").value("김고대"))
                .andExpect(jsonPath("$.messages[1].reply.mine").value(true))
                .andExpect(jsonPath("$.messages[0].reply").value(nullValue()));
        long tiger = ((Number) read(get(url(), host), "$.messages[2].id")).longValue();
        post(url(), anam, "{\"body\":\"ㅋㅋ\",\"replyToId\":" + tiger + "}").andExpect(status().isOk())
                .andExpect(jsonPath("$.reply.preview").value("(이모티콘) 가는 중"));

        // 원래 메시지를 지우면 "지운 메시지예요". 지운 메시지 · 안내 줄에는 답장할 수 없다
        delete(url() + "/" + question, host).andExpect(status().isNoContent());
        get(url(), anam).andExpect(jsonPath("$.messages[1].reply.preview").value("지운 메시지예요"));
        post(url(), anam, "{\"body\":\"네\",\"replyToId\":" + question + "}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("REPLY_GONE"));
        post("/api/meetups/" + meetupId + "/join", horang, null).andExpect(status().isOk());
        post(url(), anam, "{\"body\":\"반가워요\",\"replyToId\":" + joinNoticeId() + "}")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("REPLY_GONE"));
    }

    @Test
    void 공감은_메시지마다_하나씩_누르고_몇_명인지_보인다() throws Exception {
        twoJoined();
        post("/api/meetups/" + meetupId + "/join", horang, null).andExpect(status().isOk());
        long id = sayId(host, "내일 봬요");
        put(url() + "/" + id + "/reaction", anam, "{\"reaction\":\"HEART\"}").andExpect(status().isOk())
                .andExpect(jsonPath("$.reactions", hasSize(1)))
                .andExpect(jsonPath("$.reactions[0].reaction").value("HEART"))
                .andExpect(jsonPath("$.reactions[0].count").value(1))
                .andExpect(jsonPath("$.reactions[0].mine").value(true));
        put(url() + "/" + id + "/reaction", horang, "{\"reaction\":\"LIKE\"}").andExpect(status().isOk());
        put(url() + "/" + id + "/reaction", host, "{\"reaction\":\"HEART\"}").andExpect(status().isOk());
        // 공감 순서대로 (누가 눌렀는지는 보이지 않는다)
        get(url() + "/" + id, host).andExpect(jsonPath("$.reactions", hasSize(2)))
                .andExpect(jsonPath("$.reactions[0].reaction").value("HEART"))
                .andExpect(jsonPath("$.reactions[0].count").value(2))
                .andExpect(jsonPath("$.reactions[0].mine").value(true))
                .andExpect(jsonPath("$.reactions[1].reaction").value("LIKE"))
                .andExpect(jsonPath("$.reactions[1].mine").value(false));

        // 다른 것을 누르면 바뀌고, null 이면 빠진다
        put(url() + "/" + id + "/reaction", anam, "{\"reaction\":\"LOL\"}").andExpect(status().isOk());
        put(url() + "/" + id + "/reaction", horang, "{\"reaction\":null}").andExpect(status().isOk());
        get(url() + "/" + id, host).andExpect(jsonPath("$.reactions", hasSize(2)))
                .andExpect(jsonPath("$.reactions[0].reaction").value("HEART"))
                .andExpect(jsonPath("$.reactions[0].count").value(1))
                .andExpect(jsonPath("$.reactions[1].reaction").value("LOL"));
        assertThat(jdbc.queryForObject("select count(*) from chat_reactions", Long.class)).isEqualTo(2);

        // 없는 공감 · 안내 줄 · 함께하지 않는 원우는 안 된다
        put(url() + "/" + id + "/reaction", anam, "{\"reaction\":\"PEPE\"}").andExpect(status().isBadRequest());
        put(url() + "/" + joinNoticeId() + "/reaction", anam, "{\"reaction\":\"HEART\"}")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("REACTION"));
        put(url() + "/" + id + "/reaction", outsider, "{\"reaction\":\"HEART\"}").andExpect(status().isForbidden());
        get(url() + "/999999", host).andExpect(status().isNotFound());

        // 지우면 공감도 보이지 않고 더 누를 수 없다
        delete(url() + "/" + id, host).andExpect(status().isNoContent());
        get(url() + "/" + id, anam).andExpect(jsonPath("$.reactions", hasSize(0)));
        put(url() + "/" + id + "/reaction", anam, "{\"reaction\":\"HEART\"}").andExpect(status().isBadRequest());
    }

    @Test
    void 연_사람은_메시지_하나를_톡방_공지로_올린다() throws Exception {
        twoJoined();
        long place = sayId(host, "고대 정문 앞에서 봬요");
        long other = sayId(anam, "넵");
        get(url(), anam).andExpect(jsonPath("$.pinned").value(nullValue()))
                .andExpect(jsonPath("$.canPin").value(false));
        get(url(), host).andExpect(jsonPath("$.canPin").value(true));

        put(url() + "/pin", anam, "{\"messageId\":" + place + "}").andExpect(status().isForbidden());
        put(url() + "/pin", host, "{\"messageId\":" + place + "}").andExpect(status().isOk())
                .andExpect(jsonPath("$.pinned.id").value(place))
                .andExpect(jsonPath("$.pinned.preview").value("고대 정문 앞에서 봬요"));
        get(url(), anam).andExpect(jsonPath("$.pinned.id").value(place))
                .andExpect(jsonPath("$.pinned.sender").value("김*대"));

        // 새로 올리면 앞의 것은 내려간다 (원우 메시지도 올릴 수 있다)
        put(url() + "/pin", host, "{\"messageId\":" + other + "}").andExpect(jsonPath("$.pinned.id").value(other));
        assertThat(jdbc.queryForObject("select count(*) from chat_messages where pinned_at is not null", Long.class))
                .isEqualTo(1);
        get(url() + "/updates?after=" + other, anam).andExpect(jsonPath("$.pinned.id").value(other));

        // 지우면 공지도 내려간다
        delete(url() + "/" + other, anam).andExpect(status().isNoContent());
        get(url(), host).andExpect(jsonPath("$.pinned").value(nullValue()));
        put(url() + "/pin", host, "{\"messageId\":" + other + "}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PIN"));

        // 내리기
        put(url() + "/pin", host, "{\"messageId\":" + place + "}").andExpect(status().isOk());
        put(url() + "/pin", host, "{\"messageId\":null}").andExpect(status().isOk())
                .andExpect(jsonPath("$.pinned").value(nullValue()));
        get(url(), anam).andExpect(jsonPath("$.pinned").value(nullValue()));

        // 취소된 3교시는 못 올린다
        post("/api/meetups/" + meetupId + "/cancel", host, null).andExpect(status().isOk());
        get(url(), host).andExpect(jsonPath("$.canPin").value(false));
        put(url() + "/pin", host, "{\"messageId\":" + place + "}").andExpect(status().isBadRequest());
    }

    @Test
    void 답장_공감_공지가_있는_3교시도_대학원장이_지울_수_있다() throws Exception {
        twoJoined();
        long first = sayId(host, "첫 메시지");
        long reply = ((Number) read(post(url(), anam, "{\"body\":\"답장\",\"replyToId\":" + first + "}")
                .andExpect(status().isOk()), "$.id")).longValue();
        post(url(), host, "{\"body\":\"답장의 답장\",\"replyToId\":" + reply + "}").andExpect(status().isOk());
        put(url() + "/" + first + "/reaction", anam, "{\"reaction\":\"HEART\"}").andExpect(status().isOk());
        put(url() + "/pin", host, "{\"messageId\":" + reply + "}").andExpect(status().isOk());

        delete("/api/meetups/" + meetupId, ownerToken()).andExpect(status().isNoContent());
        assertThat(jdbc.queryForObject("select count(*) from chat_messages", Long.class)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from chat_reactions", Long.class)).isZero();
    }

    // ---------- 사진 ----------

    /** 사진 올릴 주소를 받아 그 주소로 올린다 */
    private String upload(String token, byte[] data) throws Exception {
        ResultActions r = post(url() + "/uploads", token, "{\"contentTypes\":[\"image/jpeg\"]}")
                .andExpect(status().isOk());
        String key = read(r, "$[0].key");
        String uploadUrl = read(r, "$[0].uploadUrl");
        mvc.perform(MockMvcRequestBuilders.put(URI.create(uploadUrl)).contentType("image/jpeg").content(data))
                .andExpect(status().isOk());
        return key;
    }

    @Test
    void 사진을_올려_보내고_30일이_지나면_볼_수_없다() throws Exception {
        twoJoined();
        String key = upload(anam, new byte[] {1, 2, 3});
        assertThat(key).startsWith("chat/" + meetupId + "/");
        post(url(), anam, "{\"photoKeys\":[\"" + key + "\"]}").andExpect(status().isOk())
                .andExpect(jsonPath("$.photos", hasSize(1)))
                .andExpect(jsonPath("$.photos[0].expired").value(false));

        ResultActions page = get(url(), host).andExpect(jsonPath("$.messages[0].photos[0].key").value(key));
        String view = read(page, "$.messages[0].photos[0].url");
        mvc.perform(MockMvcRequestBuilders.get(URI.create(view))).andExpect(status().isOk());
        assertThat(chatPushes("host-phone").getLast().body()).isEqualTo("이*암: 사진 1장을 보냈어요");
        get("/api/me/chats", host).andExpect(jsonPath("$.rooms[0].lastPreview").value("사진 1장"));

        clock.advance(Duration.ofDays(30));
        get(url(), host).andExpect(jsonPath("$.messages[0].photos[0].expired").value(true))
                .andExpect(jsonPath("$.messages[0].photos[0].url").value(nullValue()));
    }

    @Test
    void 올리지_않았거나_다른_3교시_사진은_보낼_수_없다() throws Exception {
        twoJoined();
        ResultActions r = post(url() + "/uploads", anam, "{\"contentTypes\":[\"image/jpeg\"]}");
        String notUploaded = read(r, "$[0].key");
        post(url(), anam, "{\"photoKeys\":[\"" + notUploaded + "\"]}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PHOTO_MISSING"));
        post(url(), anam, "{\"photoKeys\":[\"chat/999/00000000-0000-0000-0000-000000000000.jpg\"]}")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("PHOTO_KEY"));
        post(url() + "/uploads", anam, "{\"contentTypes\":[\"image/gif\"]}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PHOTO_TYPE"));
        post(url() + "/uploads", outsider, "{\"contentTypes\":[\"image/jpeg\"]}").andExpect(status().isForbidden());

        // 서명이 틀린 주소로는 올릴 수 없다
        String uploadUrl = read(r, "$[0].uploadUrl");
        mvc.perform(MockMvcRequestBuilders.put(URI.create(uploadUrl.replaceAll("sig=[^&]+", "sig=wrong"))).contentType("image/jpeg")
                .content(new byte[] {1})).andExpect(status().isForbidden());
    }

    @Test
    void 톡방_사진은_하루_30장까지() throws Exception {
        twoJoined();
        long anamId = memberId("anam");
        jdbc.update("insert into chat_messages (meetup_id, member_id, created_at) values (?, ?, ?)",
                meetupId, anamId, java.sql.Timestamp.from(T0));
        long messageId = jdbc.queryForObject("select max(id) from chat_messages", Long.class);
        for (int i = 0; i < 28; i++) {
            jdbc.update("insert into chat_photos (message_id, storage_key, position) values (?, ?, ?)",
                    messageId, "chat/" + meetupId + "/old-" + i + ".jpg", i);
        }
        get(url(), anam).andExpect(jsonPath("$.photosLeftToday").value(2));
        post(url() + "/uploads", anam, "{\"contentTypes\":[\"image/jpeg\",\"image/jpeg\",\"image/jpeg\"]}")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("PHOTO_DAILY_LIMIT"));
        post(url() + "/uploads", anam, "{\"contentTypes\":[\"image/jpeg\",\"image/jpeg\"]}")
                .andExpect(status().isOk());

        clock.advance(Duration.ofDays(1).plusMinutes(1));
        get(url(), anam).andExpect(jsonPath("$.photosLeftToday").value(30));
    }

    @Test
    void 사진_메시지를_지우면_저장소에서도_지운다() throws Exception {
        twoJoined();
        String key = upload(anam, new byte[] {9});
        long id = ((Number) read(post(url(), anam, "{\"photoKeys\":[\"" + key + "\"]}"), "$.id")).longValue();
        String view = read(get(url(), anam), "$.messages[0].photos[0].url");
        delete(url() + "/" + id, anam).andExpect(status().isNoContent());
        mvc.perform(MockMvcRequestBuilders.get(URI.create(view))).andExpect(status().isNotFound());
        get(url(), host).andExpect(jsonPath("$.messages[0].photos", hasSize(0)));
    }
}
