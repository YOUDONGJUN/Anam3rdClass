package com.anam3.common;

import jakarta.servlet.http.HttpServletRequest;

/**
 * 요청한 사람의 IP (요청 횟수 제한에 쓴다).
 * 운영 서버는 Cloudflare 를 거쳐 오고, Cloudflare 가 CF-Connecting-IP 에 실제 접속 IP 를 넣는다 (사용자가 보낸 값은 덮어쓴다).
 * X-Forwarded-For 의 맨 앞 값은 사용자가 마음대로 넣을 수 있어서 쓰지 않는다. 헤더가 없으면(로컬·테스트) 접속 주소
 */
public final class ClientIp {

    static final String HEADER = "CF-Connecting-IP";
    private static final int MAX_LENGTH = 45; // IPv6 최대 길이

    private ClientIp() {
    }

    public static String of(HttpServletRequest request) {
        String ip = request.getHeader(HEADER);
        if (ip != null && !ip.isBlank()) {
            ip = ip.strip();
            return ip.length() > MAX_LENGTH ? ip.substring(0, MAX_LENGTH) : ip;
        }
        return request.getRemoteAddr();
    }
}
