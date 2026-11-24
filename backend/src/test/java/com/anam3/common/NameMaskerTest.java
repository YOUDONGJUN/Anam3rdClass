package com.anam3.common;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class NameMaskerTest {

    @ParameterizedTest
    @CsvSource({
            "김고대, 김*대",
            "이수, 이*",
            "남궁고대, 남**대",
            "김, *",
            "' 박호랑 ', 박*랑",
            "John Smith, J*** ****h",
    })
    void 가운데를_가린다(String name, String expected) {
        assertThat(NameMasker.mask(name)).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource(value = {"''", "'   '"})
    void 빈_이름은_익명(String name) {
        assertThat(NameMasker.mask(name)).isEqualTo("익명");
    }
}
