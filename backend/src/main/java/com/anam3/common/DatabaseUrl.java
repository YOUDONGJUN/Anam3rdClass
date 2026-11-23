package com.anam3.common;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Neon/Supabase 등이 주는 postgresql://user:pass@host/db?sslmode=require 형식을
 * JDBC URL + 계정 정보로 바꿔준다. 이미 jdbc: 로 시작하면 그대로 쓴다.
 */
public record DatabaseUrl(String jdbcUrl, String username, String password) {

    public static DatabaseUrl parse(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("DATABASE_URL 이 비어 있어요");
        }
        String url = raw.strip();
        if (url.startsWith("jdbc:")) {
            return new DatabaseUrl(url, null, null);
        }
        if (!url.startsWith("postgres://") && !url.startsWith("postgresql://")) {
            throw new IllegalArgumentException("지원하지 않는 DATABASE_URL 형식이에요: postgresql:// 또는 jdbc: 로 시작해야 해요");
        }

        URI uri = URI.create(url.replaceFirst("^postgres(ql)?://", "postgresql://"));
        String username = null;
        String password = null;
        if (uri.getRawUserInfo() != null) {
            String[] parts = uri.getRawUserInfo().split(":", 2);
            username = decode(parts[0]);
            password = parts.length > 1 ? decode(parts[1]) : null;
        }

        List<String> params = new ArrayList<>();
        if (uri.getRawQuery() != null) {
            for (String p : uri.getRawQuery().split("&")) {
                // pgjdbc 가 모르는 libpq 전용 옵션은 버린다
                if (!p.isBlank() && !p.startsWith("channel_binding=")) {
                    params.add(p);
                }
            }
        }
        if (params.stream().noneMatch(p -> p.startsWith("sslmode="))) {
            params.add("sslmode=require");
        }

        String hostPort = uri.getHost() + (uri.getPort() > 0 ? ":" + uri.getPort() : "");
        String path = uri.getRawPath() == null ? "" : uri.getRawPath();
        String jdbcUrl = "jdbc:postgresql://" + hostPort + path + "?" + String.join("&", params);
        return new DatabaseUrl(jdbcUrl, username, password);
    }

    private static String decode(String s) {
        return URLDecoder.decode(s, StandardCharsets.UTF_8);
    }
}
