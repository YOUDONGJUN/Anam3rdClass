package com.anam3.common;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import com.anam3.support.ApiTestSupport;

/**
 * 배포 뒤에도 예전 화면이 남던 문제. 빌드마다 index.html 의 파일 시각이 1980-02-01 로 같아서
 * 예전 index.html 을 가진 브라우저가 그 시각으로 물으면(If-Modified-Since) 늘 304(그대로 써라)를 받았다.
 */
class SpaCacheTest extends ApiTestSupport {

    /** 파일 시각보다 늦은 시각: 시각 비교를 하면 무조건 304 가 나오는 조건 (운영에서 1980 시각으로 묻던 것과 같다) */
    private static final String LATER_THAN_FILE = "Fri, 01 Jan 2100 00:00:00 GMT";

    @Test
    void 화면은_저장하지_않고_시각으로_물어도_늘_새로_준다() throws Exception {
        for (String path : List.of("/index.html", "/me", "/meetups/12", "/admin")) {
            mvc.perform(MockMvcRequestBuilders.get(path).header("If-Modified-Since", LATER_THAN_FILE))
                    .andExpect(status().isOk())
                    .andExpect(header().string("Cache-Control", "no-store"))
                    .andExpect(header().doesNotExist("Last-Modified"))
                    .andExpect(content().string(containsString("<div id=\"root\">")));
        }
        // 첫 화면(/)은 index.html 로 넘긴다 (위와 같은 규칙으로 서빙된다)
        mvc.perform(MockMvcRequestBuilders.get("/")).andExpect(forwardedUrl("index.html"));
    }

    @Test
    void 없는_파일과_API는_화면으로_넘기지_않는다() throws Exception {
        mvc.perform(MockMvcRequestBuilders.get("/nothing.js")).andExpect(status().isNotFound());
        mvc.perform(MockMvcRequestBuilders.get("/api/health")).andExpect(status().isOk())
                .andExpect(content().string(containsString("ok")));
    }
}
