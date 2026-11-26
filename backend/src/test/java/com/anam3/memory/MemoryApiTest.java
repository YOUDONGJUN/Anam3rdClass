package com.anam3.memory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import com.anam3.push.PushMessage;
import com.anam3.push.PushType;
import com.anam3.support.ApiTestSupport;

/** 추억 (T0 = 3/2(월) 18:00 한국 시간, 3교시는 23:00 에 모인다) */
class MemoryApiTest extends ApiTestSupport {

    private String host;
    private String anam;
    private String horang;
    private String sinchon;
    private long meetupId;

    /** 김고대가 열고 이안암이 참여해 성사된 3교시 (23:00). 박호랑은 같은 학교, 이신촌은 연세대 */
    private void gathered() throws Exception {
        host = signup("godae", "김고대", 61);
        anam = signup("anam", "이안암", 61);
        horang = signup("horang", "박호랑", 61);
        sinchon = signup("sinchon@yonsei.ac.kr", "이신촌", 3, departmentId("YONSEI", "인컴"));
        registerDevice(host, "host-phone");
        registerDevice(anam, "anam-phone");
        meetupId = createMeetup(host, 2, null, Duration.ofHours(5), Duration.ofHours(3), null);
        post("/api/meetups/" + meetupId + "/join", anam, null).andExpect(status().isOk());
        clock.set(T0.plus(Duration.ofHours(5).plusMinutes(30))); // 23:30 — 모였다
        pushes.clear();
    }

    private String base() {
        return "/api/meetups/" + meetupId + "/memories";
    }

    /** 사진 n장을 올리고 이름들 */
    private List<String> upload(String token, int n) throws Exception {
        String types = String.join(",", java.util.Collections.nCopies(n, "\"image/jpeg\""));
        ResultActions r = post(base() + "/uploads", token, "{\"contentTypes\":[" + types + "]}")
                .andExpect(status().isOk());
        List<String> keys = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            String key = read(r, "$[" + i + "].key");
            String url = read(r, "$[" + i + "].uploadUrl");
            mvc.perform(MockMvcRequestBuilders.put(URI.create(url)).contentType("image/jpeg").content(new byte[] {7}))
                    .andExpect(status().isOk());
            keys.add(key);
        }
        return keys;
    }

    private ResultActions create(String token, String caption, String visibility, List<String> keys)
            throws Exception {
        String list = String.join(",", keys.stream().map(k -> "\"" + k + "\"").toList());
        return post(base(), token, "{\"caption\":\"" + caption + "\",\"visibility\":\"" + visibility
                + "\",\"photoKeys\":[" + list + "]}");
    }

    @Test
    void 모이기_전에는_올릴_수_없고_함께한_원우만_올린다() throws Exception {
        gathered();
        clock.set(T0.plus(Duration.ofHours(4))); // 22:00 — 아직 모이기 전
        post(base() + "/uploads", anam, "{\"contentTypes\":[\"image/jpeg\"]}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("NOT_GATHERED"));
        get(base(), anam).andExpect(jsonPath("$.canAdd").value(false));

        clock.set(T0.plus(Duration.ofHours(6)));
        get(base(), anam).andExpect(jsonPath("$.canAdd").value(true)).andExpect(jsonPath("$.photosLeft").value(10));
        post(base() + "/uploads", horang, "{\"contentTypes\":[\"image/jpeg\"]}").andExpect(status().isForbidden());
        get(base(), horang).andExpect(jsonPath("$.canAdd").value(false));
    }

    @Test
    void 추억을_올리면_함께한_원우에게_알리고_기본은_함께한_원우만_본다() throws Exception {
        gathered();
        create(anam, "인생네컷 📸", "PARTICIPANTS", upload(anam, 2)).andExpect(status().isCreated())
                .andExpect(jsonPath("$.photos", hasSize(2)))
                .andExpect(jsonPath("$.mine").value(true))
                .andExpect(jsonPath("$.canDelete").value(true));

        List<PushMessage> got = pushes.to("host-phone");
        assertThat(got).hasSize(1);
        assertThat(got.getFirst().type()).isEqualTo(PushType.MEMORY);
        assertThat(got.getFirst().path()).isEqualTo("/meetups/" + meetupId + "#memories");
        assertThat(pushes.to("anam-phone")).isEmpty();

        get("/api/memories", host).andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].caption").value("인생네컷 📸"))
                .andExpect(jsonPath("$.items[0].author.name").value("이안암"))
                .andExpect(jsonPath("$.items[0].canDelete").value(false));
        get("/api/memories", horang).andExpect(jsonPath("$.items", hasSize(0)));
        get(base(), horang).andExpect(jsonPath("$.items", hasSize(0)));

        // 사진 주소로 볼 수 있다
        String url = read(get(base(), host), "$.items[0].photos[0].url");
        mvc.perform(MockMvcRequestBuilders.get(URI.create(url))).andExpect(status().isOk());
    }

    @Test
    void 공개한_추억은_그_3교시를_볼_수_있는_원우가_가려진_이름으로_본다() throws Exception {
        gathered();
        create(anam, "2차까지 완주", "PUBLIC", upload(anam, 1)).andExpect(status().isCreated());

        get("/api/memories", horang).andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].author.name").value("이*암"))
                .andExpect(jsonPath("$.items[0].mine").value(false));
        get(base(), horang).andExpect(jsonPath("$.items", hasSize(1)));
        // 다른 학교 원우는 우리 학교 3교시 추억을 보지 않는다
        get("/api/memories", sinchon).andExpect(jsonPath("$.items", hasSize(0)));
        get(base(), sinchon).andExpect(status().isNotFound());
    }

    @Test
    void 추억_사진은_3교시마다_10장까지() throws Exception {
        gathered();
        create(anam, "하나", "PARTICIPANTS", upload(anam, 5)).andExpect(status().isCreated());
        create(host, "둘", "PARTICIPANTS", upload(host, 4)).andExpect(status().isCreated());
        get(base(), anam).andExpect(jsonPath("$.photosLeft").value(1));
        post(base() + "/uploads", anam, "{\"contentTypes\":[\"image/jpeg\",\"image/jpeg\"]}")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("MEMORY_LIMIT"));
        create(anam, "셋", "PARTICIPANTS", upload(anam, 1)).andExpect(status().isCreated());
        get(base(), anam).andExpect(jsonPath("$.photosLeft").value(0)).andExpect(jsonPath("$.items", hasSize(3)));
    }

    @Test
    void 사진_없이는_올릴_수_없고_부적절한_말은_막는다() throws Exception {
        gathered();
        post(base(), anam, "{\"caption\":\"글만\",\"visibility\":\"PARTICIPANTS\",\"photoKeys\":[]}")
                .andExpect(status().isBadRequest());
        create(anam, "병신같이 놀았다", "PUBLIC", upload(anam, 1)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INAPPROPRIATE"));
        // 톡방 사진 이름으로는 올릴 수 없다
        create(anam, "몰래", "PARTICIPANTS", List.of("chat/" + meetupId + "/00000000-0000-0000-0000-000000000000.jpg"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("PHOTO_KEY"));
    }

    @Test
    void 올린_원우와_학교_교수_대학원장만_지운다() throws Exception {
        gathered();
        long id = ((Number) read(create(anam, "지울 사진", "PUBLIC", upload(anam, 1)), "$.id")).longValue();
        String url = read(get(base(), host), "$.items[0].photos[0].url");
        delete("/api/memories/" + id, host).andExpect(status().isForbidden());
        delete("/api/memories/" + id, anam).andExpect(status().isNoContent());
        get(base(), host).andExpect(jsonPath("$.items", hasSize(0)));
        mvc.perform(MockMvcRequestBuilders.get(URI.create(url))).andExpect(status().isNotFound());

        long other = ((Number) read(create(anam, "신고된 사진", "PUBLIC", upload(anam, 1)), "$.id")).longValue();
        delete("/api/memories/" + other, ownerToken()).andExpect(status().isNoContent());
    }

    @Test
    void 탈퇴하면_올린_추억이_지워진다() throws Exception {
        gathered();
        create(anam, "탈퇴 전", "PARTICIPANTS", upload(anam, 1)).andExpect(status().isCreated());
        delete("/api/me", anam).andExpect(status().isNoContent());
        get(base(), host).andExpect(jsonPath("$.items", hasSize(0)));
    }
}
