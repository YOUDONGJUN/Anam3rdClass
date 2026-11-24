package com.anam3.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class ContentFilterTest {

    @Test
    void 심한_욕설은_숫자나_기호를_끼워도_걸린다() {
        assertThat(ContentFilter.containsBanned("오늘 씨발 한잔")).isTrue();
        assertThat(ContentFilter.containsBanned("씨1발")).isTrue();
        assertThat(ContentFilter.containsBanned("씨.발")).isTrue();
        assertThat(ContentFilter.containsBanned("FUCK")).isTrue();
        assertThatThrownBy(() -> ContentFilter.check("괜찮은 제목", "지랄하네"))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("부적절한 표현");
    }

    @Test
    void 평범한_말은_걸리지_않는다() {
        assertThat(ContentFilter.containsBanned("시발점에서 만나요")).isFalse();
        assertThat(ContentFilter.containsBanned("맥주병 신나게 비워요")).isFalse();
        assertThat(ContentFilter.containsBanned("늦게 자지 마요, 보지 말고")).isFalse();
        assertThat(ContentFilter.containsBanned("우리 61기야 동기들 모여")).isFalse();
        ContentFilter.check("치맥 번개", null, "");
    }
}
