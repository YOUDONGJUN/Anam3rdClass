package com.anam3.auth;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.stereotype.Component;

import com.anam3.common.AppProperties;
import com.anam3.member.Member;
import com.nimbusds.jose.jwk.source.ImmutableSecret;

/**
 * 토큰(JWT) 발급/검증. 두 종류가 있고 발급자(iss)가 달라서 서로 바꿔 쓸 수 없다.
 * <ul>
 *   <li>로그인 토큰 (iss=anam3, 30일): 회원 id 만 담는다. 직책·차단 여부는 매 요청마다 DB 에서 확인한다</li>
 *   <li>가입 토큰 (iss=anam3-signup, 30분): 카카오 로그인 → 학교 이메일 인증 → 프로필 입력 사이를 잇는다.
 *       카카오 회원번호와, 인증을 마쳤다면 그 학교 이메일을 담는다</li>
 * </ul>
 */
@Component
public class JwtService {

    private static final String ISSUER = "anam3";
    private static final String SIGNUP_ISSUER = "anam3-signup";
    private static final Duration SIGNUP_TTL = Duration.ofMinutes(30);
    private static final String EMAIL_CLAIM = "email";

    private final NimbusJwtEncoder encoder;
    private final NimbusJwtDecoder decoder;
    private final NimbusJwtDecoder signupDecoder;
    private final Duration ttl;
    private final Clock clock;

    public JwtService(AppProperties props, Clock clock) {
        String secret = props.jwt() == null ? null : props.jwt().secret();
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException("JWT_SECRET 환경변수를 32자 이상으로 설정해 주세요");
        }
        SecretKey key = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        this.encoder = new NimbusJwtEncoder(new ImmutableSecret<>(key));
        this.decoder = decoder(key, ISSUER, clock);
        this.signupDecoder = decoder(key, SIGNUP_ISSUER, clock);
        this.ttl = props.jwt().ttl() == null ? Duration.ofDays(30) : props.jwt().ttl();
        this.clock = clock;
    }

    private static NimbusJwtDecoder decoder(SecretKey key, String issuer, Clock clock) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
        JwtTimestampValidator timestampValidator = new JwtTimestampValidator(Duration.ofSeconds(30));
        timestampValidator.setClock(clock);
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(timestampValidator, new JwtIssuerValidator(issuer)));
        return decoder;
    }

    /** 로그인 토큰 */
    public String issue(Member member) {
        Instant now = clock.instant();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(ISSUER)
                .subject(String.valueOf(member.getId()))
                .issuedAt(now)
                .expiresAt(now.plus(ttl))
                .build();
        return encode(claims);
    }

    /** 유효한 로그인 토큰이면 회원 id 를 돌려준다 */
    public Optional<Long> verify(String token) {
        try {
            Jwt jwt = decoder.decode(token);
            return Optional.of(Long.valueOf(jwt.getSubject()));
        } catch (JwtException | NumberFormatException e) {
            return Optional.empty();
        }
    }

    /** 가입 토큰. verifiedEmail 이 null 이면 아직 학교 이메일 인증 전 */
    public String issueSignup(long kakaoId, String verifiedEmail) {
        Instant now = clock.instant();
        JwtClaimsSet.Builder claims = JwtClaimsSet.builder()
                .issuer(SIGNUP_ISSUER)
                .subject(String.valueOf(kakaoId))
                .issuedAt(now)
                .expiresAt(now.plus(SIGNUP_TTL));
        if (verifiedEmail != null) {
            claims.claim(EMAIL_CLAIM, verifiedEmail);
        }
        return encode(claims.build());
    }

    public Optional<SignupClaims> verifySignup(String token) {
        try {
            Jwt jwt = signupDecoder.decode(token);
            return Optional.of(new SignupClaims(Long.parseLong(jwt.getSubject()), jwt.getClaimAsString(EMAIL_CLAIM)));
        } catch (JwtException | NumberFormatException e) {
            return Optional.empty();
        }
    }

    private String encode(JwtClaimsSet claims) {
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    /** 가입 토큰 내용. verifiedEmail 은 학교 이메일 인증을 마쳤을 때만 있다 */
    public record SignupClaims(long kakaoId, String verifiedEmail) {
    }
}
