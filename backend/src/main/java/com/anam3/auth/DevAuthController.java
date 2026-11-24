package com.anam3.auth;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import org.springframework.context.annotation.Profile;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.anam3.auth.AuthDtos.AuthStep;
import com.anam3.common.ApiException;
import com.anam3.member.MemberRepository;
import com.anam3.member.MemberRole;
import com.anam3.school.SchoolDtos.SchoolTag;

/**
 * 로컬 개발 전용 로그인 (local 프로필에서만 생긴다 — 운영에는 이 주소가 없다).
 * 카카오 키·SMTP 없이 샘플 계정으로 바로 들어가 볼 수 있다.
 */
@Profile("local")
@RestController
@RequestMapping("/api/auth/dev")
public class DevAuthController {

    private final MemberRepository members;
    private final AuthService authService;

    public DevAuthController(MemberRepository members, AuthService authService) {
        this.members = members;
        this.authService = authService;
    }

    public record DevMember(Long id, String name, String cohortLabel, String departmentLabel, SchoolTag school,
            MemberRole role) {
    }

    public record DevLoginRequest(@NotNull Long memberId) {
    }

    @GetMapping("/members")
    @Transactional(readOnly = true)
    public List<DevMember> members() {
        return members.findAllForAdmin().stream()
                .filter(m -> !m.isWithdrawn())
                .map(m -> new DevMember(m.getId(), m.getName(), m.cohortLabel(), m.departmentShortLabel(),
                        SchoolTag.from(m.getSchool()), m.getRole()))
                .toList();
    }

    @PostMapping("/login")
    @Transactional(readOnly = true)
    public AuthStep login(@Valid @RequestBody DevLoginRequest req) {
        return authService.done(members.findWithSchool(req.memberId())
                .orElseThrow(() -> ApiException.notFound("회원을 찾을 수 없어요")));
    }
}
