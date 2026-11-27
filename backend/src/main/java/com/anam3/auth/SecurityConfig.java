package com.anam3.auth;

import java.io.IOException;
import java.time.Clock;
import java.util.List;

import jakarta.servlet.DispatcherType;
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
            MemberRepository members, Clock clock) throws Exception {
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
                        // 실시간 연결(/api/live)이 끝날 때의 비동기 디스패치: 처음 요청에서 이미 로그인을 확인했다
                        .dispatcherTypeMatchers(DispatcherType.ASYNC).permitAll()
                        .requestMatchers("/api/auth/**", "/api/health").permitAll()
                        // 폴더 저장소(내 PC·테스트)의 사진: 로그인 대신 서명된 주소로 확인한다 (LocalFileController)
                        .requestMatchers("/api/files/**").permitAll()
                        // 로그아웃한 앱이 이 기기로 알림을 그만 보내 달라고 할 때 (로그인이 풀린 뒤일 수 있다)
                        .requestMatchers(HttpMethod.POST, "/api/push/unregister").permitAll()
                        // 대학원장 전용: 학교·도메인·학과 바꾸기, 교수 임명·해임, 예전 회원 이메일 지정
                        .requestMatchers(HttpMethod.GET, "/api/admin/schools").hasAnyRole("MANAGER", "OWNER")
                        .requestMatchers("/api/admin/schools/**", "/api/admin/departments/**").hasRole("OWNER")
                        .requestMatchers(HttpMethod.POST, "/api/admin/schools").hasRole("OWNER")
                        .requestMatchers("/api/admin/members/*/role", "/api/admin/members/*/email", "/api/admin/members/*/withdraw")
                        .hasRole("OWNER")
                        // 3교시 후기 통계는 대학원장만 (교수는 후기 결과를 보지 않는다)
                        .requestMatchers("/api/admin/feedback-stats").hasRole("OWNER")
                        // 관리자 조치 기록은 대학원장만 (교수들이 한 일을 대학원장이 돌아본다)
                        .requestMatchers("/api/admin/actions").hasRole("OWNER")
                        // 교수·대학원장: 나머지 관리 화면
                        .requestMatchers("/api/admin/**").hasAnyRole("MANAGER", "OWNER")
                        .requestMatchers("/api/**").authenticated()
                        .anyRequest().permitAll()) // 화면(정적 파일)
                .exceptionHandling(e -> e
                        .authenticationEntryPoint((req, res, ex) ->
                                writeError(res, 401, "UNAUTHORIZED", "로그인이 필요해요"))
                        .accessDeniedHandler((req, res, ex) ->
                                writeError(res, 403, "FORBIDDEN", "권한이 없어요")))
                .addFilterBefore(new JwtAuthenticationFilter(jwtService, members, clock),
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
