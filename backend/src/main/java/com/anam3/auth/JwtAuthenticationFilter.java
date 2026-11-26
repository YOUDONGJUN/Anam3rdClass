package com.anam3.auth;

import java.io.IOException;
import java.util.List;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import com.anam3.member.Member;
import com.anam3.member.MemberStatus;
import com.anam3.member.MemberRepository;

/**
 * Authorization: Bearer {token} 헤더를 읽어 로그인 사용자를 SecurityContext 에 넣는다.
 * 차단·탈퇴한 회원은 토큰이 있어도 로그인되지 않은 것으로 처리한다.
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String PREFIX = "Bearer ";
    /** 늘린 로그인 토큰을 실어 보내는 응답 헤더 */
    public static final String RENEWED_TOKEN_HEADER = "X-Renewed-Token";

    private final JwtService jwtService;
    private final MemberRepository members;

    public JwtAuthenticationFilter(JwtService jwtService, MemberRepository members) {
        this.jwtService = jwtService;
        this.members = members;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith(PREFIX)) {
            var token = jwtService.verify(header.substring(PREFIX.length()));
            // 비밀번호를 바꾸기 전에 받은 토큰(버전이 다름)은 받지 않는다
            var found = token.flatMap(t -> members.findWithSchool(t.memberId()))
                    .filter(m -> m.getTokenVersion() == token.get().version());
            // 쓰는 도중 이용이 제한되면 "로그인이 필요해요"(401) 대신 까닭을 알린다
            if (found.isPresent() && found.get().getStatus() == MemberStatus.BANNED) {
                SecurityConfig.writeError(response, 403, "BANNED", "이용이 제한된 계정이에요. 교수·대학원장에게 문의해 주세요");
                return;
            }
            var member = found.filter(Member::isActive);
            if (member.isPresent()) {
                if (!member.get().hasAgreedTerms() && !allowedBeforeTerms(request)) {
                    SecurityConfig.writeError(response, 403, "TERMS_REQUIRED", "이용약관에 먼저 동의해 주세요");
                    return;
                }
                authenticate(member.get());
                // 받은 지 하루가 지난 토큰이면 새 토큰을 실어 보낸다 — 화면이 바꿔 끼운다 (쓰는 동안 로그인 유지)
                if (jwtService.shouldRenew(token.get())) {
                    response.setHeader(RENEWED_TOKEN_HEADER, jwtService.issue(member.get()));
                }
            }
        }
        chain.doFilter(request, response);
    }

    /** 약관에 동의하기 전(예전 회원)에는 보기와 약관 동의·탈퇴만 된다 */
    private static boolean allowedBeforeTerms(HttpServletRequest request) {
        String method = request.getMethod();
        String path = request.getRequestURI();
        return "GET".equals(method) || "HEAD".equals(method) || "OPTIONS".equals(method)
                || path.startsWith("/api/auth/")
                || ("POST".equals(method) && "/api/me/terms".equals(path))
                || ("DELETE".equals(method) && "/api/me".equals(path));
    }

    private void authenticate(Member member) {
        LoginMember principal = new LoginMember(member.getId(), member.getRole(), member.getCohort(),
                member.getSchool().getId());
        var authentication = new UsernamePasswordAuthenticationToken(principal, null,
                List.of(new SimpleGrantedAuthority("ROLE_" + member.getRole().name())));
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }
}
