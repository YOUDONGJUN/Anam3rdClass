package com.anam3.member;

import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.anam3.common.AppProperties;
import com.anam3.school.SchoolDtos.SchoolTag;

/**
 * 만든 곳 정보: 화면의 "써클"을 누르면 보인다 (로그인한 원우에게만).
 * 대학원장 계정의 이름 · 학교 · 대학원 · 기수 · 학과 · 이메일을 그대로 쓴다 — 코드에 개인정보를 적지 않으려고.
 */
@RestController
public class AboutController {

    /**
     * operator 가 null 이면 아직 대학원장이 없다 (처음 배포한 직후).
     * supportEmail: 문의 이메일 (SUPPORT_EMAIL, 없으면 null)
     */
    public record About(String company, Operator operator, String supportEmail) {
    }

    public record Operator(String name, SchoolTag school, String graduateSchool, String cohortLabel,
            String departmentLabel, String email) {
    }

    static final String COMPANY = "써클";

    private final MemberRepository members;
    private final AppProperties props;

    public AboutController(MemberRepository members, AppProperties props) {
        this.members = members;
        this.props = props;
    }

    @GetMapping("/api/about")
    @Transactional(readOnly = true)
    public About about() {
        Operator operator = members.findAllByRole(MemberRole.OWNER).stream()
                .filter(Member::isActive)
                .findFirst()
                .map(m -> new Operator(m.getName(), SchoolTag.from(m.getSchool()), m.getSchool().getGraduateSchool(),
                        m.cohortLabel(), m.departmentLabel(), m.getEmail()))
                .orElse(null);
        return new About(COMPANY, operator, props.supportEmail());
    }
}
