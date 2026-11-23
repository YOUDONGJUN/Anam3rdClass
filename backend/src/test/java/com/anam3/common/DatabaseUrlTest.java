package com.anam3.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class DatabaseUrlTest {

    @Test
    void Neon_연결문자열을_JDBC로_바꾼다() {
        DatabaseUrl url = DatabaseUrl.parse(
                "postgresql://neondb_owner:p%40ss@ep-cool-1234-pooler.ap-southeast-1.aws.neon.tech/neondb"
                        + "?sslmode=require&channel_binding=require");

        assertThat(url.jdbcUrl()).isEqualTo(
                "jdbc:postgresql://ep-cool-1234-pooler.ap-southeast-1.aws.neon.tech/neondb?sslmode=require");
        assertThat(url.username()).isEqualTo("neondb_owner");
        assertThat(url.password()).isEqualTo("p@ss");
    }

    @Test
    void 포트를_유지하고_sslmode가_없으면_require를_붙인다() {
        DatabaseUrl url = DatabaseUrl.parse("postgres://u:p@localhost:5432/anam3");

        assertThat(url.jdbcUrl()).isEqualTo("jdbc:postgresql://localhost:5432/anam3?sslmode=require");
    }

    @Test
    void jdbc_형식은_그대로_쓴다() {
        DatabaseUrl url = DatabaseUrl.parse("jdbc:postgresql://localhost/anam3");

        assertThat(url.jdbcUrl()).isEqualTo("jdbc:postgresql://localhost/anam3");
        assertThat(url.username()).isNull();
    }

    @Test
    void 모르는_형식은_거부한다() {
        assertThatThrownBy(() -> DatabaseUrl.parse("mysql://x")).isInstanceOf(IllegalArgumentException.class);
    }
}
