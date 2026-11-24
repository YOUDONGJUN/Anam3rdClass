package com.anam3.auth;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Deque;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

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
import com.anam3.auth.AuthDtos.EmailVerifyRequest;
import com.anam3.auth.AuthDtos.KakaoLoginRequest;
import com.anam3.auth.AuthDtos.LegacyClaimRequest;
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
import com.anam3.member.Member;
import com.anam3.member.MemberRepository;
import com.anam3.member.MemberResponse;
import com.anam3.member.MemberRole;
import com.anam3.member.MemberStatus;
import com.anam3.school.Department;
import com.anam3.school.School;
import com.anam3.school.SchoolService;

/**
 * 로그인·가입.
 * <pre>
 * 카카오 로그인 ─┬─ 이미 가입한 카카오 계정 → 로그인 끝
 *               └─ 처음 → 학교 이메일로 인증코드 → 코드 확인 ─┬─ 이미 있는 학교 이메일(예전 회원, 카카오 계정 변경) → 그 계정에 연결하고 로그인
 *                                                           └─ 처음 → 이름·기수·학과 입력 → 가입하고 로그인
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
    /** 예전(아이디·비밀번호 시절) 비밀번호는 BCrypt 로 저장돼 있다 */
    private static final PasswordEncoder LEGACY_PASSWORDS = new BCryptPasswordEncoder();

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
    /** 예전 아이디·비밀번호를 틀린 시각 (카카오 계정마다). 서버가 한 대라 메모리로 충분하다 */
    private final Map<Long, Deque<Instant>> legacyFailures = new ConcurrentHashMap<>();

    public AuthService(MemberRepository members, KakaoClient kakao, JwtService jwtService,
            EmailVerificationRepository verifications, VerificationCodes codes, VerificationMailer mailer,
            SchoolService schoolService, OwnerPolicy ownerPolicy, AppProperties props, Environment env, Clock clock,
            PlatformTransactionManager transactionManager) {
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

    // ---------------- 2. 학교 이메일 인증코드 ----------------

    /** 코드를 저장(커밋)한 뒤 메일을 보낸다. 메일이 실패하면 저장한 코드를 지운다 */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public EmailCodeSent sendEmailCode(EmailCodeRequest req) {
        SignupClaims claims = signupClaims(req.signupToken());
        String email = Emails.normalize(req.email());
        if (Emails.hasAlias(email)) {
            throw ApiException.badRequest("EMAIL_ALIAS", "학교 이메일을 + 없이 그대로 입력해 주세요");
        }
        String code = codes.newCode();
        SavedCode saved = tx.execute(status -> saveCode(claims, email, code));
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
        Instant now = clock.instant();

        verifications.deleteCreatedBefore(now.minus(Duration.ofDays(1)));
        verifications.findFirstByEmailAndKakaoIdOrderByCreatedAtDescIdDesc(email, claims.kakaoId())
                .filter(last -> last.getCreatedAt().isAfter(now.minus(RESEND_AFTER)))
                .ifPresent(last -> {
                    throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "TOO_SOON",
                            "인증코드는 1분에 한 번만 받을 수 있어요. 메일함을 먼저 확인해 주세요");
                });
        Instant hourAgo = now.minus(Duration.ofHours(1));
        if (verifications.countByEmailAndKakaoIdAndCreatedAtAfter(email, claims.kakaoId(), hourAgo) >= HOURLY_LIMIT_PER_REQUESTER
                || verifications.countByEmailAndCreatedAtAfter(email, hourAgo) >= HOURLY_LIMIT_PER_EMAIL
                || verifications.countByKakaoIdAndCreatedAtAfter(claims.kakaoId(), hourAgo) >= HOURLY_LIMIT_PER_KAKAO) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "TOO_MANY",
                    "인증코드를 너무 많이 요청했어요. 1시간 뒤에 다시 시도해 주세요");
        }

        EmailVerification saved = verifications.save(
                new EmailVerification(email, claims.kakaoId(), codes.hash(email, code), now.plus(CODE_TTL), now));
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
                .findLatestIds(email, claims.kakaoId(), PageRequest.of(0, 1)).stream().findFirst()
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

        // 이미 있는 학교 이메일: 예전 회원(관리자가 이메일을 넣어 둠)이거나 카카오 계정이 바뀐 경우 → 그 계정에 연결
        Optional<Member> existing = members.findByEmail(email);
        if (existing.isPresent()) {
            Member member = existing.get();
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
        return AuthStep.profileRequired(jwtService.issueSignup(claims.kakaoId(), email), email, school.getId(),
                school.getName());
    }

    // ---------------- 4. 이름·기수·학과 입력 + 약관 동의 → 가입 ----------------

    public AuthStep signup(SignupRequest req) {
        SignupClaims claims = signupClaims(req.signupToken());
        String email = claims.verifiedEmail();
        if (email == null) {
            throw ApiException.badRequest("EMAIL_NOT_VERIFIED", "학교 이메일 인증을 먼저 해 주세요");
        }
        if (members.findByKakaoId(claims.kakaoId()).isPresent()) {
            throw ApiException.conflict("ALREADY_SIGNED_UP", "이미 가입한 카카오 계정이에요. 다시 로그인해 주세요");
        }
        if (members.existsByEmail(email)) {
            throw ApiException.conflict("DUPLICATE_EMAIL", "이미 가입된 학교 이메일이에요");
        }
        School school = schoolService.requireForEmail(email);
        schoolService.checkCohort(school, req.cohort());
        Department department = schoolService.requireDepartment(school, req.departmentId(), null);

        Instant now = clock.instant();
        Member member = members.save(new Member(claims.kakaoId(), email, school, req.name().strip(), req.cohort(),
                department, MemberRole.USER, now));
        member.agreeTerms(now);
        ownerPolicy.apply(member);
        return done(member);
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
        if (members.findByKakaoId(claims.kakaoId()).isPresent()) {
            throw ApiException.conflict("ALREADY_SIGNED_UP", "이미 가입한 카카오 계정이에요. 다시 로그인해 주세요");
        }
        if (members.existsByEmail(email)) {
            throw ApiException.conflict("DUPLICATE_EMAIL", "이미 가입된 학교 이메일이에요");
        }
        Instant now = clock.instant();
        checkLegacyLimit(claims.kakaoId(), now);
        School school = schoolService.requireForEmail(email);
        Member member = members.findByLoginId(req.loginId().strip().toLowerCase(Locale.ROOT))
                .filter(Member::isUnclaimedLegacy)
                .filter(m -> m.isSameSchool(school.getId())) // 예전 계정과 같은 학교 이메일로만 잇는다
                .filter(m -> LEGACY_PASSWORDS.matches(req.password(), m.getLegacyPasswordHash()))
                .orElseThrow(() -> {
                    legacyFailures.computeIfAbsent(claims.kakaoId(), k -> new ConcurrentLinkedDeque<>()).add(now);
                    return ApiException.badRequest("LEGACY_LOGIN_FAILED", "예전 아이디나 비밀번호가 맞지 않아요");
                });
        if (!member.isActive()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "BANNED", "이용이 제한된 계정이에요. 관리자에게 문의해 주세요");
        }
        member.assignEmail(email, school);
        member.linkKakao(claims.kakaoId());
        member.clearLegacyLogin();
        ownerPolicy.apply(member);
        legacyFailures.remove(claims.kakaoId());
        return done(member);
    }

    private void checkLegacyLimit(Long kakaoId, Instant now) {
        Deque<Instant> failures = legacyFailures.get(kakaoId);
        if (failures == null) {
            return;
        }
        failures.removeIf(at -> at.isBefore(now.minus(Duration.ofHours(1))));
        if (failures.size() >= HOURLY_LEGACY_FAILURES) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "TOO_MANY",
                    "너무 많이 틀렸어요. 1시간 뒤에 다시 시도해 주세요");
        }
    }

    AuthStep done(Member member) {
        if (!member.isActive()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "BANNED", "이용이 제한된 계정이에요. 관리자에게 문의해 주세요");
        }
        return AuthStep.done(jwtService.issue(member), MemberResponse.from(member));
    }

    private SignupClaims signupClaims(String token) {
        return jwtService.verifySignup(token)
                .orElseThrow(() -> ApiException.badRequest("SIGNUP_EXPIRED", "가입 시간이 지났어요. 카카오 로그인부터 다시 해 주세요"));
    }
}
