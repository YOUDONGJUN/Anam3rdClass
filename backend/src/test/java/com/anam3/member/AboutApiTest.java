package com.anam3.member;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;

import com.anam3.support.ApiTestSupport;

/** "써클"을 누르면 보이는 만든 곳 정보 */
class AboutApiTest extends ApiTestSupport {

    @Test
    void 로그인한_원우에게_대학원장_이름_학교_기수_이메일을_보여준다() throws Exception {
        ownerToken();
        String godae = signup("godae", "김고대", 61);
        get("/api/about", godae).andExpect(status().isOk())
                .andExpect(jsonPath("$.company").value("써클"))
                .andExpect(jsonPath("$.operator.school.shortName").value("고려대"))
                .andExpect(jsonPath("$.operator.email").value(OWNER_EMAIL))
                .andExpect(jsonPath("$.operator.cohortLabel").exists())
                .andExpect(jsonPath("$.supportEmail").value("help@example.com"));
        // 로그인하지 않으면 볼 수 없다 (이메일이 긁히지 않게)
        get("/api/about", null).andExpect(status().isUnauthorized());
    }
}
