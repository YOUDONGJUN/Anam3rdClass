package com.anam3.common;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

/**
 * 운영(prod)에서 DATABASE_URL 환경변수 하나로 DB 에 붙는다.
 * 무료 DB(Neon)는 연결이 없으면 잠들어서 무료 사용량을 아끼므로, 유휴 커넥션은 금방 닫는다.
 */
@Configuration
@Profile("prod")
@ConditionalOnProperty("DATABASE_URL")
public class DatabaseUrlDataSourceConfig {

    @Bean
    public DataSource dataSource(@Value("${DATABASE_URL}") String databaseUrl) {
        DatabaseUrl parsed = DatabaseUrl.parse(databaseUrl);
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(parsed.jdbcUrl());
        if (parsed.username() != null) {
            config.setUsername(parsed.username());
        }
        if (parsed.password() != null) {
            config.setPassword(parsed.password());
        }
        config.setPoolName("anam3");
        config.setMaximumPoolSize(5);
        config.setMinimumIdle(0);
        config.setIdleTimeout(60_000);
        config.setMaxLifetime(600_000);
        config.setConnectionTimeout(30_000); // 잠든 DB 가 깨어나는 시간
        return new HikariDataSource(config);
    }
}
