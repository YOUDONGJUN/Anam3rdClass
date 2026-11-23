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
import com.anam3.member.MemberRepository;

/**
 * Authorization: Bearer {token} 헤더를 읽어 로그인 사용자를 SecurityContext 에 넣는다.
 * 차단된 회원은 토큰이 있어도 로그인되지 않은 것으로 처리한다.
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String PREFIX = "Bearer ";

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
            jwtService.verify(header.substring(PREFIX.length()))
                    .flatMap(members::findById)
                    .filter(Member::isActive)
                    .ifPresent(this::authenticate);
        }
        chain.doFilter(request, response);
    }

    private void authenticate(Member member) {
        LoginMember principal = new LoginMember(member.getId(), member.getRole());
        var authentication = new UsernamePasswordAuthenticationToken(principal, null,
                List.of(new SimpleGrantedAuthority("ROLE_" + member.getRole().name())));
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }
}
