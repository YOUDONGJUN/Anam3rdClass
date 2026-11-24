package com.anam3.auth;

import java.io.IOException;
import java.util.List;

import jakarta.servlet.http.HttpServletResponse;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.http.HttpMethod;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import com.anam3.common.AppProperties;
import com.anam3.member.MemberRepository;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, JwtService jwtService,
            MemberRepository members) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable) // 쿠키가 아닌 Authorization 헤더 방식이라 CSRF 대상 아님
                .cors(cors -> {
                })
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .headers(h -> h.frameOptions(f -> f.sameOrigin())) // 로컬 H2 콘솔용
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/auth/**", "/api/health").permitAll()
                        // 대학원장 전용: 학교·도메인·학과 바꾸기, 교수 임명·해임, 예전 회원 이메일 지정
                        .requestMatchers(HttpMethod.GET, "/api/admin/schools").hasAnyRole("MANAGER", "OWNER")
                        .requestMatchers("/api/admin/schools/**", "/api/admin/departments/**").hasRole("OWNER")
                        .requestMatchers(HttpMethod.POST, "/api/admin/schools").hasRole("OWNER")
                        .requestMatchers("/api/admin/members/*/role", "/api/admin/members/*/email").hasRole("OWNER")
                        // 교수·대학원장: 나머지 관리 화면
                        .requestMatchers("/api/admin/**").hasAnyRole("MANAGER", "OWNER")
                        .requestMatchers("/api/**").authenticated()
                        .anyRequest().permitAll()) // 화면(정적 파일)
                .exceptionHandling(e -> e
                        .authenticationEntryPoint((req, res, ex) ->
                                writeError(res, 401, "UNAUTHORIZED", "로그인이 필요해요"))
                        .accessDeniedHandler((req, res, ex) ->
                                writeError(res, 403, "FORBIDDEN", "권한이 없어요")))
                .addFilterBefore(new JwtAuthenticationFilter(jwtService, members),
                        UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    /** 스프링 기본 인메모리 사용자 생성을 막는다 (우리는 카카오 로그인 + JWT 만 쓴다) */
    @Bean
    public UserDetailsService userDetailsService() {
        return username -> {
            throw new UsernameNotFoundException(username);
        };
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource(AppProperties props) {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(props.corsAllowedOrigins());
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        return source;
    }

    static void writeError(HttpServletResponse res, int status, String code, String message)
            throws IOException {
        res.setStatus(status);
        res.setContentType("application/json;charset=UTF-8");
        res.getWriter().write("{\"code\":\"" + code + "\",\"message\":\"" + message + "\"}");
    }
}
