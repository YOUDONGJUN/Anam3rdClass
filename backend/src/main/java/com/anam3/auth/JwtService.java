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
 *   <li>가입 토큰 (iss=anam3-signup, 30분): 카카오 로그인(또는 이메일로 시작) → 학교 이메일 인증 → 프로필 입력·비밀번호 사이를 잇는다.
 *       요청한 사람 번호(카카오면 카카오 회원번호, 이메일로 시작했으면 임의의 음수)와, 인증을 마쳤다면 그 학교 이메일을 담는다</li>
 * </ul>
 */
@Component
public class JwtService {

    private static final String ISSUER = "anam3";
    private static final String SIGNUP_ISSUER = "anam3-signup";
    private static final Duration SIGNUP_TTL = Duration.ofMinutes(30);
    private static final String EMAIL_CLAIM = "email";
    /** 이메일로 시작한 가입 토큰에만 붙는다 (없으면 카카오) */
    private static final String VIA_CLAIM = "via";
    /** 로그인 토큰 버전 (Member.tokenVersion). 비밀번호를 바꾸면 예전 토큰이 끊긴다 */
    private static final String VERSION_CLAIM = "tv";
    private static final String VIA_EMAIL = "email";

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
                .claim(VERSION_CLAIM, member.getTokenVersion())
                .build();
        return encode(claims);
    }

    /** 유효한 로그인 토큰이면 회원 id 와 토큰 버전 (버전이 없는 예전 토큰은 0), 발급 시각 */
    public Optional<LoginToken> verify(String token) {
        try {
            Jwt jwt = decoder.decode(token);
            Object version = jwt.getClaims().get(VERSION_CLAIM);
            return Optional.of(new LoginToken(Long.parseLong(jwt.getSubject()),
                    version instanceof Number n ? n.intValue() : 0,
                    jwt.getIssuedAt() == null ? Instant.EPOCH : jwt.getIssuedAt()));
        } catch (JwtException | NumberFormatException e) {
            return Optional.empty();
        }
    }

    /** 로그인 토큰 내용: 누구의 몇 번째 토큰인지 */
    /**
     * 쓰는 동안 로그인을 늘려 줄지: 받은 지 하루가 지난 토큰이면 새 토큰을 준다 (JwtAuthenticationFilter).
     * 매일 쓰는 원우가 30일째에 갑자기 로그아웃되지 않게. 30일 동안 안 쓰면 그대로 만료된다
     */
    public boolean shouldRenew(LoginToken token) {
        return token.issuedAt().isBefore(clock.instant().minus(RENEW_AFTER));
    }

    private static final Duration RENEW_AFTER = Duration.ofDays(1);

    public record LoginToken(long memberId, int version, Instant issuedAt) {
    }

    /** 카카오 로그인으로 시작한 가입 토큰. verifiedEmail 이 null 이면 아직 학교 이메일 인증 전 */
    public String issueSignup(long kakaoId, String verifiedEmail) {
        return issueSignup(new SignupClaims(kakaoId, true, verifiedEmail));
    }

    /** 가입 토큰 (단계가 넘어갈 때 인증한 이메일을 더해 다시 발급한다) */
    public String issueSignup(SignupClaims signup) {
        Instant now = clock.instant();
        JwtClaimsSet.Builder claims = JwtClaimsSet.builder()
                .issuer(SIGNUP_ISSUER)
                .subject(String.valueOf(signup.requesterId()))
                .issuedAt(now)
                .expiresAt(now.plus(SIGNUP_TTL));
        if (!signup.kakao()) {
            claims.claim(VIA_CLAIM, VIA_EMAIL);
        }
        if (signup.verifiedEmail() != null) {
            claims.claim(EMAIL_CLAIM, signup.verifiedEmail());
        }
        return encode(claims.build());
    }

    public Optional<SignupClaims> verifySignup(String token) {
        try {
            Jwt jwt = signupDecoder.decode(token);
            boolean kakao = !VIA_EMAIL.equals(jwt.getClaimAsString(VIA_CLAIM));
            return Optional.of(new SignupClaims(Long.parseLong(jwt.getSubject()), kakao, jwt.getClaimAsString(EMAIL_CLAIM)));
        } catch (JwtException | NumberFormatException e) {
            return Optional.empty();
        }
    }

    private String encode(JwtClaimsSet claims) {
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    /**
     * 가입 토큰 내용.
     * requesterId: 인증코드 요청 횟수를 세는 사람 번호 — 카카오면 카카오 회원번호, 이메일로 시작했으면 임의의 음수.
     * verifiedEmail 은 학교 이메일 인증을 마쳤을 때만 있다
     */
    public record SignupClaims(long requesterId, boolean kakao, String verifiedEmail) {

        /** 카카오로 시작했으면 카카오 회원번호, 이메일로 시작했으면 null */
        public Long kakaoId() {
            return kakao ? requesterId : null;
        }

        public SignupClaims withVerifiedEmail(String email) {
            return new SignupClaims(requesterId, kakao, email);
        }
    }
}
