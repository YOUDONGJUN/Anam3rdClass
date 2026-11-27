package com.anam3.auth;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import org.springframework.core.env.Environment;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.anam3.auth.AuthDtos.AuthConfig;
import com.anam3.auth.AuthDtos.AuthStep;
import com.anam3.auth.AuthDtos.EmailCodeRequest;
import com.anam3.auth.AuthDtos.EmailCodeSent;
import com.anam3.auth.AuthDtos.EmailLoginRequest;
import com.anam3.auth.AuthDtos.EmailVerifyRequest;
import com.anam3.auth.AuthDtos.KakaoAppLoginRequest;
import com.anam3.auth.AuthDtos.KakaoLoginRequest;
import com.anam3.auth.AuthDtos.LegacyClaimRequest;
import com.anam3.auth.AuthDtos.PasswordChangeRequest;
import com.anam3.auth.AuthDtos.PasswordSetRequest;
import com.anam3.auth.AuthDtos.SignupOptions;
import com.anam3.auth.AuthDtos.SignupRequest;
import com.anam3.auth.JwtService.SignupClaims;
import com.anam3.auth.email.EmailVerification;
import com.anam3.auth.email.EmailVerificationRepository;
import com.anam3.auth.email.VerificationCodes;
import com.anam3.auth.email.VerificationMailer;
import com.anam3.auth.kakao.KakaoClient;
import com.anam3.common.ApiException;
import com.anam3.common.AppProperties;
import com.anam3.common.Emails;
import com.anam3.common.RateLimiter;
import com.anam3.common.RateLimits;
import com.anam3.member.Member;
import com.anam3.sanction.SanctionService;
import com.anam3.member.MemberRepository;
import com.anam3.member.MemberResponse;
import com.anam3.member.MemberRole;
import com.anam3.member.MemberStatus;
import com.anam3.school.Department;
import com.anam3.school.School;
import com.anam3.school.SchoolService;

/**
 * 로그인·가입. 학교 이메일 하나에 계정 하나 — 카카오로 오든 이메일로 오든 같은 학교 이메일이면 같은 계정이다.
 * <pre>
 * 카카오 로그인 ─┬─ 이미 가입한 카카오 계정 → 로그인 끝
 *               └─ 처음 → 학교 이메일로 인증코드 → 코드 확인 ─┬─ 이미 있는 학교 이메일(예전 회원, 카카오 계정 변경, 이메일 가입자) → 그 계정에 카카오 연결하고 로그인
 *                                                           └─ 처음 → 이름·기수·학과 입력 → 가입하고 로그인
 *
 * 이메일로 시작(가입 · 비밀번호 찾기 · 비밀번호 만들기) → 학교 이메일로 인증코드 → 코드 확인 ─┬─ 이미 있는 학교 이메일 → 새 비밀번호 → 로그인
 *                                                                                        └─ 처음 → 이름·기수·학과 + 비밀번호 → 가입하고 로그인
 * 이메일 로그인: 학교 이메일 + 비밀번호
 * </pre>
 */
@Service
@Transactional
public class AuthService {

    static final Duration CODE_TTL = Duration.ofMinutes(10);
    static final Duration RESEND_AFTER = Duration.ofSeconds(60);
    /**
     * 한 시간에 받을 수 있는 코드 수. 같은 사람(카카오 계정)이 같은 이메일로 5번까지.
     * 남이 내 이메일로 코드를 잔뜩 요청해도 내 인증이 막히지 않도록 이메일 하나에 대한 전체 한도는 넉넉히 둔다.
     */
    private static final int HOURLY_LIMIT_PER_REQUESTER = 5;
    private static final int HOURLY_LIMIT_PER_EMAIL = 15;
    private static final int HOURLY_LIMIT_PER_KAKAO = 10;
    /** 한 이메일로 하루에 틀릴 수 있는 횟수 (누가 요청했든). 6자리 코드를 하나씩 맞혀 보는 것을 막는다 */
    static final int DAILY_FAILED_LIMIT_PER_EMAIL = 20;
    /** 예전 아이디·비밀번호 확인: 카카오 계정마다 1시간에 틀릴 수 있는 횟수 */
    static final int HOURLY_LEGACY_FAILURES = 10;
    /** 예전 아이디 하나를 하루에 틀릴 수 있는 횟수 (여러 카카오 계정으로 나눠 맞혀 보는 것을 막는다) */
    static final int DAILY_LEGACY_FAILURES_PER_ID = 20;
    /**
     * 이메일 로그인: 같은 이메일을 같은 IP 에서 1시간에 틀릴 수 있는 횟수.
     * IP 마다 따로 세서, 남이 내 이메일로 일부러 틀려도 내 로그인은 막히지 않는다
     */
    static final int HOURLY_LOGIN_FAILURES = 10;
    /** 이메일 로그인: 한 이메일을 여러 IP 에서 합쳐 1시간에 틀릴 수 있는 횟수 (여기저기서 나눠 맞혀 보는 것을 막는다) */
    static final int HOURLY_LOGIN_FAILURES_PER_EMAIL = 50;
    /** 이메일 로그인 시도: 같은 IP 에서 10분에 (비밀번호 확인이 무거워서 한 곳에서 몰아치면 다른 원우가 못 들어온다) */
    static final int LOGIN_ATTEMPTS_PER_IP = 60;
    /** "이메일로 시작"한 인증코드 요청: 같은 IP 에서 1시간에 (한 곳에서 모두의 하루 메일 한도를 다 쓰지 못하게) */
    static final int HOURLY_EMAIL_START_CODES_PER_IP = 20;
    /**
     * "이메일로 시작"(카카오 없이 누구나 할 수 있다)한 인증코드 요청 한도. 메일 발송 한도(Gmail 하루 500통)를 다 쓰거나
     * 남의 메일함을 채우지 못하게 따로 센다. 카카오로 시작한 요청은 이 수에 들지 않아서 막히지 않는다
     */
    static final int HOURLY_LIMIT_EMAIL_START = 100;
    static final int DAILY_LIMIT_EMAIL_START = 300;
    /** 같은 이메일로 "이메일로 시작"한 코드 요청 (1시간) */
    private static final int HOURLY_LIMIT_EMAIL_START_PER_EMAIL = 5;
    /** 로그인한 채로 비밀번호 바꾸기: 지금 비밀번호를 회원마다 1시간에 틀릴 수 있는 횟수 */
    static final int HOURLY_PASSWORD_CHANGE_FAILURES = 10;
    /** BCrypt 는 72바이트까지만 쓴다 (한글이 섞인 긴 비밀번호) */
    private static final int PASSWORD_MAX_BYTES = 72;
    /** 비밀번호 확인(BCrypt)은 무거워서 동시에 몇 개만 (몰려오면 서버 CPU 를 다 쓰지 않게) */
    private final Semaphore passwordChecks = new Semaphore(4);
    /** 비밀번호는 BCrypt (예전 아이디·비밀번호 시절 비밀번호도 BCrypt 로 저장돼 있다) */
    private static final PasswordEncoder PASSWORDS = new BCryptPasswordEncoder();
    /** 없는 이메일로 로그인해도 비밀번호 확인만큼 시간이 걸리게 (응답 시간으로 가입 여부를 알 수 없게) */
    private static final String NO_ACCOUNT_HASH = PASSWORDS.encode("no-account-" + System.nanoTime());

    private final MemberRepository members;
    private final KakaoClient kakao;
    private final JwtService jwtService;
    private final EmailVerificationRepository verifications;
    private final VerificationCodes codes;
    private final VerificationMailer mailer;
    private final SchoolService schoolService;
    private final OwnerPolicy ownerPolicy;
    private final AppProperties props;
    private final Environment env;
    private final Clock clock;
    /** 카카오 서버·메일 서버를 기다리는 동안에는 DB 연결을 잡지 않으려고 트랜잭션을 직접 나눈다 */
    private final TransactionTemplate tx;
    /** 예전 아이디·비밀번호를 틀린 기록 (카카오 계정마다 · 예전 아이디마다) */
    private final RateLimiter legacyFailures;
    private final RateLimiter legacyFailuresPerId;
    /** 이메일 로그인: 틀린 기록 (이메일+IP 마다 · 이메일마다), 시도 수 (IP 마다) */
    private final RateLimiter loginFailures;
    private final RateLimiter loginFailuresPerEmail;
    private final RateLimiter loginAttemptsPerIp;
    /** "이메일로 시작"한 인증코드 요청 수 (IP 마다) */
    private final RateLimiter emailStartCodesPerIp;
    /** 로그인한 채로 비밀번호 바꾸기: 지금 비밀번호를 틀린 기록 (회원마다) */
    private final RateLimiter passwordChangeFailures;
    private final SecureRandom random = new SecureRandom();

    public AuthService(MemberRepository members, KakaoClient kakao, JwtService jwtService,
            EmailVerificationRepository verifications, VerificationCodes codes, VerificationMailer mailer,
            SchoolService schoolService, OwnerPolicy ownerPolicy, AppProperties props, Environment env, Clock clock,
            PlatformTransactionManager transactionManager, RateLimits limits) {
        this.members = members;
        this.kakao = kakao;
        this.jwtService = jwtService;
        this.verifications = verifications;
        this.codes = codes;
        this.mailer = mailer;
        this.schoolService = schoolService;
        this.ownerPolicy = ownerPolicy;
        this.props = props;
        this.env = env;
        this.clock = clock;
        this.tx = new TransactionTemplate(transactionManager);
        this.legacyFailures = limits.create(HOURLY_LEGACY_FAILURES, Duration.ofHours(1));
        this.legacyFailuresPerId = limits.create(DAILY_LEGACY_FAILURES_PER_ID, Duration.ofDays(1));
        this.loginFailures = limits.create(HOURLY_LOGIN_FAILURES, Duration.ofHours(1));
        this.loginFailuresPerEmail = limits.create(HOURLY_LOGIN_FAILURES_PER_EMAIL, Duration.ofHours(1));
        this.loginAttemptsPerIp = limits.create(LOGIN_ATTEMPTS_PER_IP, Duration.ofMinutes(10));
        this.emailStartCodesPerIp = limits.create(HOURLY_EMAIL_START_CODES_PER_IP, Duration.ofHours(1));
        this.passwordChangeFailures = limits.create(HOURLY_PASSWORD_CHANGE_FAILURES, Duration.ofHours(1));
    }

    @Transactional(readOnly = true)
    public AuthConfig config() {
        return new AuthConfig(kakao.clientId(), env.matchesProfiles("local"), props.supportEmail());
    }

    // ---------------- 1. 카카오 로그인 ----------------

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public AuthStep kakaoLogin(KakaoLoginRequest req) {
        long kakaoId = kakao.fetchUserId(req.code(), req.redirectUri()); // DB 연결 없이 카카오 서버를 기다린다
        return tx.execute(status -> members.findByKakaoId(kakaoId)
                .map(this::done)
                .orElseGet(() -> AuthStep.emailRequired(jwtService.issueSignup(kakaoId, null))));
    }

    /** 앱(안드로이드): 카카오톡으로 로그인해서 받은 토큰. 그다음은 웹 카카오 로그인과 같다 */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public AuthStep kakaoAppLogin(KakaoAppLoginRequest req) {
        long kakaoId = kakao.fetchUserIdByAccessToken(req.accessToken()); // DB 연결 없이 카카오 서버를 기다린다
        return tx.execute(status -> members.findByKakaoId(kakaoId)
                .map(this::done)
                .orElseGet(() -> AuthStep.emailRequired(jwtService.issueSignup(kakaoId, null))));
    }

    // ---------------- 1-1. 이메일로 시작 · 이메일 로그인 ----------------

    /**
     * 카카오 없이 시작: 가입 · 비밀번호 찾기 · 비밀번호 만들기 모두 여기서 시작해 학교 이메일 인증으로 간다.
     * 요청한 사람 번호는 임의의 음수 (카카오 회원번호는 양수라 겹치지 않는다)
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public AuthStep emailStart() {
        long requesterId = -1 - random.nextLong(Long.MAX_VALUE - 1);
        return AuthStep.emailRequired(jwtService.issueSignup(new SignupClaims(requesterId, false, null)));
    }

    /**
     * 학교 이메일 + 비밀번호. 없는 이메일·비밀번호 없는 계정·틀린 비밀번호는 모두 같은 말로 답한다.
     * 비밀번호 확인(BCrypt)은 DB 커넥션을 잡지 않고 하고, 동시에 몇 개만 돌린다.
     * 한 IP 에서 몰아치는 시도와 같은 이메일을 계속 틀리는 것을 막는다 (틀린 횟수는 IP 마다 따로 센다)
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public AuthStep emailLogin(EmailLoginRequest req, String ip) {
        String email = Emails.normalize(req.email());
        Instant now = clock.instant();
        if (!loginAttemptsPerIp.tryAcquire(ip, now)) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "TOO_MANY",
                    "로그인 시도가 너무 많아요. 10분 뒤에 다시 해 주세요");
        }
        String failureKey = email + "|" + ip;
        if (loginFailures.isLimited(failureKey, now) || loginFailuresPerEmail.isLimited(email, now)) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "TOO_MANY",
                    "비밀번호를 너무 많이 틀렸어요. 1시간 뒤에 다시 하거나 비밀번호 찾기를 해 주세요");
        }
        Optional<String> found = tx.execute(status -> members.findByEmail(email)
                .filter(Member::hasEmailPassword)
                .map(Member::getEmailPasswordHash));
        boolean matched = checkPassword(req.password(), found == null ? null : found.orElse(null));
        if (!matched || found == null || found.isEmpty()) {
            loginFailures.record(failureKey, now);
            loginFailuresPerEmail.record(email, now);
            throw ApiException.badRequest("LOGIN_FAILED", "이메일이나 비밀번호가 맞지 않아요");
        }
        loginFailures.reset(failureKey);
        return tx.execute(status -> done(members.findByEmail(email).orElseThrow()));
    }

    /** 비밀번호 확인. 계정이 없어도(hash=null) 같은 시간이 걸리게 가짜 값과 비교한다 */
    private boolean checkPassword(String raw, String hash) {
        if (raw.getBytes(StandardCharsets.UTF_8).length > PASSWORD_MAX_BYTES) {
            return false;
        }
        try {
            if (!passwordChecks.tryAcquire(3, TimeUnit.SECONDS)) {
                throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "BUSY", "지금 로그인하는 사람이 많아요. 잠시 후 다시 시도해 주세요");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "BUSY", "잠시 후 다시 시도해 주세요");
        }
        try {
            return PASSWORDS.matches(raw, hash == null ? NO_ACCOUNT_HASH : hash) && hash != null;
        } finally {
            passwordChecks.release();
        }
    }

    /** 새 비밀번호 (BCrypt 는 72바이트까지라 그보다 길면 받지 않는다) */
    private static String encodePassword(String raw) {
        if (raw.getBytes(StandardCharsets.UTF_8).length > PASSWORD_MAX_BYTES) {
            throw ApiException.badRequest("PASSWORD_TOO_LONG", "비밀번호가 너무 길어요. 영문·숫자 위주로 줄여 주세요");
        }
        return PASSWORDS.encode(raw);
    }

    // ---------------- 2. 학교 이메일 인증코드 ----------------

    /** 코드를 저장(커밋)한 뒤 메일을 보낸다. 메일이 실패하면 저장한 코드를 지운다 */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public EmailCodeSent sendEmailCode(EmailCodeRequest req, String ip) {
        SignupClaims claims = signupClaims(req.signupToken());
        String email = Emails.normalize(req.email());
        if (Emails.hasAlias(email)) {
            throw ApiException.badRequest("EMAIL_ALIAS", "학교 이메일을 + 없이 그대로 입력해 주세요");
        }
        // 누구나 할 수 있는 "이메일로 시작"은 한 곳(IP)에서 몰아 보내 모두의 하루 메일 한도를 다 쓰지 못하게 한다
        if (!claims.kakao() && emailStartCodesPerIp.isLimited(ip, clock.instant())) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "TOO_MANY",
                    "인증코드를 너무 많이 요청했어요. 1시간 뒤에 다시 시도하거나 카카오로 시작해 주세요");
        }
        String code = codes.newCode();
        SavedCode saved = tx.execute(status -> saveCode(claims, email, code));
        if (!claims.kakao()) {
            emailStartCodesPerIp.record(ip, clock.instant());
        }
        try {
            mailer.sendCode(email, code);
        } catch (RuntimeException e) {
            tx.executeWithoutResult(status -> verifications.deleteById(saved.id()));
            throw e;
        }
        return new EmailCodeSent(email, saved.schoolName(), CODE_TTL.toSeconds(), RESEND_AFTER.toSeconds());
    }

    private record SavedCode(Long id, String schoolName) {
    }

    private SavedCode saveCode(SignupClaims claims, String email, String code) {
        School school = schoolService.requireForEmail(email);
        // 학과가 하나도 없는 학교(막 추가한 학교)는 새로 가입하면 학과를 못 골라 막힌다 — 코드를 보내기 전에 알린다
        if (!schoolService.hasActiveDepartment(school) && !members.existsByEmail(email)) {
            throw ApiException.badRequest("SCHOOL_NOT_READY",
                    school.getShortName() + "은 아직 가입 준비 중이에요. 학과가 등록되면 가입할 수 있어요");
        }
        Instant now = clock.instant();

        verifications.deleteCreatedBefore(now.minus(Duration.ofDays(1)));
        verifications.findFirstByEmailAndRequesterIdOrderByCreatedAtDescIdDesc(email, claims.requesterId())
                .filter(last -> last.getCreatedAt().isAfter(now.minus(RESEND_AFTER)))
                .ifPresent(last -> {
                    throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "TOO_SOON",
                            "인증코드는 1분에 한 번만 받을 수 있어요. 메일함을 먼저 확인해 주세요");
                });
        Instant hourAgo = now.minus(Duration.ofHours(1));
        // 이메일 하나에 대한 한도는 카카오로 시작한 요청과 이메일로 시작한 요청을 따로 센다
        // (누구나 만들 수 있는 "이메일로 시작" 요청으로 남의 카카오 가입을 막지 못하게)
        long sameEmail = claims.kakao()
                ? verifications.countByEmailAndRequesterIdGreaterThanAndCreatedAtAfter(email, 0L, hourAgo)
                : verifications.countByEmailAndRequesterIdLessThanAndCreatedAtAfter(email, 0L, hourAgo);
        int sameEmailLimit = claims.kakao() ? HOURLY_LIMIT_PER_EMAIL : HOURLY_LIMIT_EMAIL_START_PER_EMAIL;
        if (verifications.countByEmailAndRequesterIdAndCreatedAtAfter(email, claims.requesterId(), hourAgo) >= HOURLY_LIMIT_PER_REQUESTER
                || sameEmail >= sameEmailLimit
                || verifications.countByRequesterIdAndCreatedAtAfter(claims.requesterId(), hourAgo) >= HOURLY_LIMIT_PER_KAKAO) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "TOO_MANY",
                    "인증코드를 너무 많이 요청했어요. 1시간 뒤에 다시 시도해 주세요");
        }
        if (!claims.kakao()
                && (verifications.countByRequesterIdLessThanAndCreatedAtAfter(0L, hourAgo) >= HOURLY_LIMIT_EMAIL_START
                        || verifications.countByRequesterIdLessThanAndCreatedAtAfter(0L, now.minus(Duration.ofDays(1)))
                                >= DAILY_LIMIT_EMAIL_START)) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "TOO_MANY",
                    "지금은 인증코드를 보낼 수 없어요. 잠시 뒤에 다시 시도하거나 카카오로 시작해 주세요");
        }

        EmailVerification saved = verifications.save(
                new EmailVerification(email, claims.requesterId(), codes.hash(email, code), now.plus(CODE_TTL), now));
        return new SavedCode(saved.getId(), school.getName());
    }

    // ---------------- 3. 코드 확인 ----------------

    /** 틀린 횟수는 에러를 돌려줘도 남아야 해서 ApiException 에는 되돌리지 않는다 */
    @Transactional(noRollbackFor = ApiException.class)
    public AuthStep verifyEmail(EmailVerifyRequest req) {
        SignupClaims claims = signupClaims(req.signupToken());
        String email = Emails.normalize(req.email());
        Instant now = clock.instant();

        EmailVerification verification = verifications
                .findLatestIds(email, claims.requesterId(), PageRequest.of(0, 1)).stream().findFirst()
                .flatMap(verifications::findByIdForUpdate)
                .filter(v -> v.isUsableAt(now))
                .orElseThrow(() -> ApiException.badRequest("CODE_EXPIRED", "인증코드가 만료됐어요. 새 코드를 받아 주세요"));
        if (verifications.sumAttemptsByEmailSince(email, now.minus(Duration.ofDays(1))) >= DAILY_FAILED_LIMIT_PER_EMAIL) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "TOO_MANY_ATTEMPTS",
                    "인증코드를 너무 많이 틀렸어요. 내일 다시 시도해 주세요");
        }
        if (!codes.matches(email, req.code(), verification.getCodeHash())) {
            verification.failAttempt();
            int left = verification.remainingAttempts();
            throw ApiException.badRequest("WRONG_CODE", left > 0
                    ? "인증코드가 맞지 않아요. " + left + "번 더 시도할 수 있어요"
                    : "인증코드가 맞지 않아요. 새 코드를 받아 주세요");
        }
        School school = schoolService.requireForEmail(email);

        // 이미 있는 학교 이메일 → 새 계정을 만들지 않는다 (학교 이메일 하나에 계정 하나)
        Optional<Member> existing = members.findByEmail(email);
        if (existing.isPresent() && !claims.kakao()) {
            // 이메일로 시작: 비밀번호 찾기 · 카카오 가입자의 비밀번호 만들기 → 새 비밀번호를 정하면 그 계정으로 로그인
            Member member = existing.get();
            if (!member.isActive()) {
                throw new ApiException(HttpStatus.FORBIDDEN, "BANNED", "이용이 제한된 계정이에요. 관리자에게 문의해 주세요");
            }
            verification.markVerified(now);
            return AuthStep.passwordRequired(jwtService.issueSignup(claims.withVerifiedEmail(email)), email,
                    school.getId(), school.getName());
        }
        if (existing.isPresent()) {
            // 카카오로 시작: 예전 회원(관리자가 이메일을 넣어 둠), 카카오 계정이 바뀐 경우, 이메일로 가입한 원우 → 그 계정에 카카오 연결
            Member member = existing.get();
            if (!member.isActive()) {
                throw new ApiException(HttpStatus.FORBIDDEN, "BANNED", "이용이 제한된 계정이에요. 관리자에게 문의해 주세요");
            }
            members.findByKakaoId(claims.kakaoId())
                    .filter(other -> !other.getId().equals(member.getId()))
                    .ifPresent(other -> {
                        throw ApiException.conflict("KAKAO_IN_USE", "이 카카오 계정은 이미 다른 원우로 가입돼 있어요");
                    });
            verification.markVerified(now);
            member.linkKakao(claims.kakaoId());
            ownerPolicy.apply(member);
            return done(member);
        }

        verification.markVerified(now);
        return AuthStep.profileRequired(jwtService.issueSignup(claims.withVerifiedEmail(email)), email, school.getId(),
                school.getName());
    }

    // ---------------- 4. 이름·기수·학과 입력 + 약관 동의 (+ 이메일 가입이면 비밀번호) → 가입 ----------------

    public AuthStep signup(SignupRequest req) {
        SignupClaims claims = signupClaims(req.signupToken());
        String email = claims.verifiedEmail();
        if (email == null) {
            throw ApiException.badRequest("EMAIL_NOT_VERIFIED", "학교 이메일 인증을 먼저 해 주세요");
        }
        Long kakaoId = claims.kakaoId();
        if (kakaoId != null && members.findByKakaoId(kakaoId).isPresent()) {
            throw ApiException.conflict("ALREADY_SIGNED_UP", "이미 가입한 카카오 계정이에요. 다시 로그인해 주세요");
        }
        if (kakaoId == null && req.password() == null) {
            throw ApiException.badRequest("PASSWORD_REQUIRED", AuthDtos.PASSWORD_MESSAGE);
        }
        if (members.existsByEmail(email)) {
            throw ApiException.conflict("DUPLICATE_EMAIL", "이미 가입된 학교 이메일이에요");
        }
        School school = schoolService.requireForEmail(email);
        schoolService.checkCohort(school, req.cohort());
        Department department = schoolService.requireDepartment(school, req.departmentId(), null);

        Instant now = clock.instant();
        Member member = members.save(new Member(kakaoId, email, school, req.name().strip(), req.cohort(),
                department, MemberRole.USER, now));
        if (kakaoId == null) {
            member.setEmailPassword(encodePassword(req.password()));
        }
        member.agreeTerms(now);
        ownerPolicy.apply(member);
        return done(member);
    }

    // ---------------- 4-2. 이미 가입한 학교 이메일: 새 비밀번호 (비밀번호 찾기 · 비밀번호 만들기) ----------------

    /** 학교 이메일 인증을 마친 사람만 (가입 토큰에 인증한 이메일이 있다). 정하면 그 계정으로 로그인된다 */
    public AuthStep setPassword(PasswordSetRequest req) {
        SignupClaims claims = signupClaims(req.signupToken());
        String email = claims.verifiedEmail();
        if (email == null) {
            throw ApiException.badRequest("EMAIL_NOT_VERIFIED", "학교 이메일 인증을 먼저 해 주세요");
        }
        Member member = members.findByEmail(email)
                .orElseThrow(() -> ApiException.badRequest("NO_ACCOUNT", "이 메일로 가입한 계정이 없어요. 가입부터 해 주세요"));
        if (!member.isActive()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "BANNED", "이용이 제한된 계정이에요. 관리자에게 문의해 주세요");
        }
        member.setEmailPassword(encodePassword(req.password()));
        ownerPolicy.apply(member);
        loginFailuresPerEmail.reset(email); // 비밀번호를 새로 정했으니 잠금을 푼다
        return done(member);
    }

    // ---------------- 4-3. 로그인한 채로 비밀번호 바꾸기 (지금 비밀번호 확인) ----------------

    /**
     * 지금 비밀번호를 맞히면 새 비밀번호로 바꾼다 (메일 인증 없이). 다른 기기의 로그인은 풀리고(토큰 버전 +1),
     * 이 기기는 새 로그인 토큰을 받아 그대로 쓴다. 지금 비밀번호는 회원마다 1시간에 10번까지 틀릴 수 있다.
     * 비밀번호 확인(BCrypt)은 DB 연결을 잡지 않고 한다
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public AuthStep changePassword(LoginMember viewer, PasswordChangeRequest req) {
        Instant now = clock.instant();
        String key = String.valueOf(viewer.id());
        if (passwordChangeFailures.isLimited(key, now)) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "TOO_MANY",
                    "비밀번호를 너무 많이 틀렸어요. 1시간 뒤에 다시 하거나 학교 메일로 비밀번호를 찾아 주세요");
        }
        String hash = tx.execute(status -> members.findById(viewer.id())
                .map(Member::getEmailPasswordHash)
                .orElse(null));
        if (hash == null) {
            throw ApiException.badRequest("NO_PASSWORD", "아직 비밀번호가 없어요. 학교 메일 인증으로 먼저 만들어 주세요");
        }
        if (req.currentPassword().equals(req.newPassword())) {
            throw ApiException.badRequest("SAME_PASSWORD", "지금과 다른 비밀번호로 정해 주세요");
        }
        if (!checkPassword(req.currentPassword(), hash)) {
            passwordChangeFailures.record(key, now);
            throw ApiException.badRequest("WRONG_PASSWORD", "지금 비밀번호가 맞지 않아요");
        }
        String newHash = encodePassword(req.newPassword());
        passwordChangeFailures.reset(key);
        return tx.execute(status -> {
            Member member = members.findWithSchool(viewer.id())
                    .orElseThrow(() -> ApiException.notFound("회원 정보를 찾을 수 없어요"));
            if (!hash.equals(member.getEmailPasswordHash())) { // 그사이 다른 곳에서 바꿨다
                throw ApiException.conflict("PASSWORD_CHANGED", "비밀번호가 방금 바뀌었어요. 다시 해 주세요");
            }
            member.setEmailPassword(newHash); // 다른 기기의 로그인을 푼다
            if (member.getEmail() != null) {
                loginFailuresPerEmail.reset(member.getEmail());
            }
            return done(member); // 이 기기는 새 토큰으로 이어서 쓴다
        });
    }

    // ---------------- 그 밖 ----------------

    @Transactional(readOnly = true)
    public MemberResponse me(LoginMember me) {
        return MemberResponse.from(
                members.findWithSchool(me.id()).orElseThrow(() -> ApiException.notFound("회원 정보를 찾을 수 없어요")));
    }

    @Transactional(readOnly = true)
    public SignupOptions signupOptions() {
        return new SignupOptions(schoolService.views(false),
                members.findUnclaimedLegacySchoolIds(MemberStatus.WITHDRAWN));
    }

    // ---------------- 4-1. 예전(아이디·비밀번호) 회원: 예전 계정 잇기 ----------------

    /**
     * 학교 이메일 인증을 마친 사람이 예전 아이디·비밀번호를 맞히면, 그 예전 계정에 카카오와 학교 이메일을 연결하고 로그인한다.
     * 예전 3교시·공지 기록이 그대로 이어진다. 약관은 다음 화면(동의 화면)에서 받는다.
     */
    public AuthStep claimLegacy(LegacyClaimRequest req) {
        SignupClaims claims = signupClaims(req.signupToken());
        String email = claims.verifiedEmail();
        if (email == null) {
            throw ApiException.badRequest("EMAIL_NOT_VERIFIED", "학교 이메일 인증을 먼저 해 주세요");
        }
        if (!claims.kakao()) {
            throw ApiException.badRequest("LEGACY_NEEDS_KAKAO", "예전 계정 잇기는 카카오 로그인으로 해 주세요");
        }
        if (members.findByKakaoId(claims.kakaoId()).isPresent()) {
            throw ApiException.conflict("ALREADY_SIGNED_UP", "이미 가입한 카카오 계정이에요. 다시 로그인해 주세요");
        }
        if (members.existsByEmail(email)) {
            throw ApiException.conflict("DUPLICATE_EMAIL", "이미 가입된 학교 이메일이에요");
        }
        Instant now = clock.instant();
        String kakaoKey = String.valueOf(claims.kakaoId());
        String loginId = req.loginId().strip().toLowerCase(Locale.ROOT);
        if (legacyFailures.isLimited(kakaoKey, now)) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "TOO_MANY",
                    "너무 많이 틀렸어요. 1시간 뒤에 다시 시도해 주세요");
        }
        if (legacyFailuresPerId.isLimited(loginId, now)) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "TOO_MANY",
                    "이 아이디는 오늘 너무 많이 틀렸어요. 내일 다시 시도하거나 관리자에게 문의해 주세요");
        }
        School school = schoolService.requireForEmail(email);
        Member member = members.findByLoginId(loginId)
                .filter(Member::isUnclaimedLegacy)
                .filter(m -> m.isSameSchool(school.getId())) // 예전 계정과 같은 학교 이메일로만 잇는다
                .filter(m -> PASSWORDS.matches(req.password(), m.getLegacyPasswordHash()))
                .orElseThrow(() -> {
                    legacyFailures.record(kakaoKey, now);
                    legacyFailuresPerId.record(loginId, now);
                    return ApiException.badRequest("LEGACY_LOGIN_FAILED", "예전 아이디나 비밀번호가 맞지 않아요");
                });
        if (!member.isActive()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "BANNED", "이용이 제한된 계정이에요. 관리자에게 문의해 주세요");
        }
        member.assignEmail(email, school);
        member.linkKakao(claims.kakaoId());
        member.clearLegacyLogin();
        // 예전 시스템의 관리 직책은 잇지 않는다 (예전 비밀번호만 맞히면 관리자가 되지 않게). 필요하면 대학원장이 다시 맡긴다
        if (member.getRole() != MemberRole.USER) {
            member.changeRole(MemberRole.USER);
        }
        ownerPolicy.apply(member);
        legacyFailures.reset(kakaoKey);
        return done(member);
    }

    AuthStep done(Member member) {
        if (!member.isActive()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "BANNED", "이용이 제한된 계정이에요. 관리자에게 문의해 주세요");
        }
        if (member.isSuspendedAt(clock.instant())) {
            throw SanctionService.suspended(member.getSuspendedUntil());
        }
        return AuthStep.done(jwtService.issue(member), MemberResponse.from(member));
    }

    private SignupClaims signupClaims(String token) {
        return jwtService.verifySignup(token)
                .orElseThrow(() -> ApiException.badRequest("SIGNUP_EXPIRED", "가입 시간이 지났어요. 처음부터 다시 해 주세요"));
    }
}
