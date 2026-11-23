package com.anam3.auth;

import java.time.Clock;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.anam3.auth.AuthDtos.AuthResponse;
import com.anam3.auth.AuthDtos.ChangePasswordRequest;
import com.anam3.auth.AuthDtos.DepartmentOption;
import com.anam3.auth.AuthDtos.LoginRequest;
import com.anam3.auth.AuthDtos.ProfileRequest;
import com.anam3.auth.AuthDtos.SignupOptions;
import com.anam3.auth.AuthDtos.SignupRequest;
import com.anam3.common.ApiException;
import com.anam3.member.Department;
import com.anam3.member.Member;
import com.anam3.member.MemberRepository;
import com.anam3.member.MemberResponse;
import com.anam3.member.MemberRole;
import com.anam3.setting.SettingService;

@Service
@Transactional
public class AuthService {

    private final MemberRepository members;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final SettingService settingService;
    private final Clock clock;

    public AuthService(MemberRepository members, PasswordEncoder passwordEncoder, JwtService jwtService,
            SettingService settingService, Clock clock) {
        this.members = members;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.settingService = settingService;
        this.clock = clock;
    }

    public AuthResponse signup(SignupRequest req) {
        if (!settingService.inviteCode().equalsIgnoreCase(req.inviteCode().strip())) {
            throw ApiException.badRequest("INVALID_INVITE_CODE", "입장 코드가 맞지 않아요. 단톡방 공지를 확인해 주세요");
        }
        checkCohort(req.cohort());
        String loginId = normalize(req.loginId());
        if (members.existsByLoginId(loginId)) {
            throw ApiException.conflict("DUPLICATE_LOGIN_ID", "이미 사용 중인 아이디예요");
        }
        Member member = members.save(new Member(loginId, passwordEncoder.encode(req.password()),
                req.name().strip(), req.cohort(), req.department(), MemberRole.USER, clock.instant()));
        return new AuthResponse(jwtService.issue(member), MemberResponse.from(member));
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest req) {
        Member member = members.findByLoginId(normalize(req.loginId()))
                .filter(m -> passwordEncoder.matches(req.password(), m.getPasswordHash()))
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS",
                        "아이디 또는 비밀번호가 맞지 않아요"));
        if (!member.isActive()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "BANNED", "이용이 제한된 계정이에요. 관리자에게 문의해 주세요");
        }
        return new AuthResponse(jwtService.issue(member), MemberResponse.from(member));
    }

    @Transactional(readOnly = true)
    public MemberResponse me(LoginMember me) {
        return MemberResponse.from(load(me));
    }

    public MemberResponse updateProfile(LoginMember me, ProfileRequest req) {
        checkCohort(req.cohort());
        Member member = load(me);
        member.updateProfile(req.name().strip(), req.cohort(), req.department());
        return MemberResponse.from(member);
    }

    public void changePassword(LoginMember me, ChangePasswordRequest req) {
        Member member = load(me);
        if (!passwordEncoder.matches(req.currentPassword(), member.getPasswordHash())) {
            throw ApiException.badRequest("WRONG_PASSWORD", "현재 비밀번호가 맞지 않아요");
        }
        member.changePassword(passwordEncoder.encode(req.newPassword()));
    }

    @Transactional(readOnly = true)
    public SignupOptions signupOptions() {
        List<DepartmentOption> departments = Arrays.stream(Department.values())
                .map(d -> new DepartmentOption(d, d.label()))
                .toList();
        return new SignupOptions(settingService.latestCohort(), departments);
    }

    /** 1기 ~ 최신 기수(운영 페이지에서 설정)만 허용 */
    private void checkCohort(int cohort) {
        if (cohort < 1 || cohort > settingService.latestCohort()) {
            throw ApiException.badRequest("INVALID_COHORT", "기수를 확인해 주세요");
        }
    }

    private Member load(LoginMember me) {
        return members.findById(me.id()).orElseThrow(() -> ApiException.notFound("회원 정보를 찾을 수 없어요"));
    }

    private static String normalize(String loginId) {
        return loginId.strip().toLowerCase(Locale.ROOT);
    }
}
