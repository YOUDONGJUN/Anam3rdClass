package com.anam3.common;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class EmailsTest {

    @Test
    void 저장과_비교는_소문자로() {
        assertThat(Emails.normalize("  Hong@Korea.AC.kr ")).isEqualTo("hong@korea.ac.kr");
        assertThat(Emails.normalizeOrNull(" ")).isNull();
    }

    @Test
    void 교수에게는_앞_두_글자만_보인다() {
        assertThat(Emails.mask("hong@korea.ac.kr")).isEqualTo("ho**@korea.ac.kr");
        assertThat(Emails.mask("ab@korea.ac.kr")).isEqualTo("a*@korea.ac.kr");
        assertThat(Emails.mask("a@korea.ac.kr")).isEqualTo("a@korea.ac.kr");
        assertThat(Emails.mask(null)).isNull();
    }
}
