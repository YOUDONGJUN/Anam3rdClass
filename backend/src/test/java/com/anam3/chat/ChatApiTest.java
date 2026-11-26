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
        get(url(), host).andExpect(jsonPath("$.federated").value(true))
                .andExpect(jsonPath("$.messages[0].sender.school.code").value("YONSEI"))
                .andExpect(jsonPath("$.messages[0].sender.name").value("이신촌"));
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
        get(url(), anam).andExpect(jsonPath("$.messages[0].hidden").value(true))
                .andExpect(jsonPath("$.messages[0].body").value(nullValue()));
        get(url(), host).andExpect(jsonPath("$.messages[0].body").value("안녕하세요"));
        get("/api/me/chats", anam).andExpect(jsonPath("$.totalUnread").value(0))
                .andExpect(jsonPath("$.rooms[0].lastPreview").value("차단한 원우의 메시지예요"));
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
