package com.anam3.school;

import java.time.Clock;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.anam3.admin.AdminAction;
import com.anam3.admin.AdminActionLog;
import com.anam3.auth.LoginMember;
import com.anam3.common.ApiException;
import com.anam3.school.SchoolDtos.DepartmentRequest;
import com.anam3.school.SchoolDtos.DepartmentView;
import com.anam3.school.SchoolDtos.DomainView;
import com.anam3.school.SchoolDtos.NewSchoolRequest;
import com.anam3.school.SchoolDtos.SchoolRequest;
import com.anam3.school.SchoolDtos.SchoolView;

/**
 * 학교 · 이메일 도메인 · 학과 · 학교별 최신 기수.
 * 읽기는 누구나(가입 화면), 바꾸기는 대학원장만 (SecurityConfig, AdminController).
 */
@Service
@Transactional
public class SchoolService {

    private final SchoolRepository schools;
    private final SchoolEmailDomainRepository domains;
    private final DepartmentRepository departments;
    private final AdminActionLog actionLog;
    private final Clock clock;

    public SchoolService(SchoolRepository schools, SchoolEmailDomainRepository domains,
            DepartmentRepository departments, AdminActionLog actionLog, Clock clock) {
        this.schools = schools;
        this.domains = domains;
        this.departments = departments;
        this.actionLog = actionLog;
        this.clock = clock;
    }

    // ---------------- 읽기 ----------------

    @Transactional(readOnly = true)
    public List<School> all() {
        return schools.findAllByOrderBySortOrderAscIdAsc();
    }

    /**
     * 모든 학교와 도메인·학과. 가입 화면은 숨긴 학과를 빼고(includeHidden=false), 관리 화면은 모두 본다.
     */
    @Transactional(readOnly = true)
    public List<SchoolView> views(boolean includeHidden) {
        Map<Long, List<DomainView>> domainsBySchool = domains.findAllByOrderByIdAsc().stream()
                .collect(Collectors.groupingBy(d -> d.getSchool().getId(),
                        Collectors.mapping(DomainView::from, Collectors.toList())));
        Map<Long, List<DepartmentView>> departmentsBySchool = departments.findAllByOrderBySortOrderAscIdAsc().stream()
                .filter(d -> includeHidden || d.isActive())
                .collect(Collectors.groupingBy(d -> d.getSchool().getId(),
                        Collectors.mapping(DepartmentView::from, Collectors.toList())));
        return all().stream()
                .map(s -> new SchoolView(s.getId(), s.getCode(), s.getName(), s.getShortName(), s.getGraduateSchool(),
                        s.getLatestCohort(), s.getPlace(), s.getCampus(), domainsBySchool.getOrDefault(s.getId(), List.of()),
                        departmentsBySchool.getOrDefault(s.getId(), List.of())))
                .toList();
    }

    /**
     * 이메일 도메인이 등록된 학교면 그 학교. 하위 도메인도 같은 학교로 본다
     * (hong@korea.ac.kr → 고려대학교, hong@g.skku.edu → 성균관대학교). 여러 개 맞으면 가장 긴 도메인.
     */
    @Transactional(readOnly = true)
    public Optional<School> findForEmail(String email) {
        int at = email == null ? -1 : email.lastIndexOf('@');
        if (at < 0) {
            return Optional.empty();
        }
        String emailDomain = email.substring(at + 1).toLowerCase(Locale.ROOT);
        return domains.findAllByOrderByIdAsc().stream()
                .filter(d -> d.matches(emailDomain))
                .max(Comparator.comparingInt(d -> d.getDomain().length()))
                .map(SchoolEmailDomain::getSchool);
    }

    /** 등록된 학교 이메일이 아니면 친절한 에러 */
    @Transactional(readOnly = true)
    public School requireForEmail(String email) {
        return findForEmail(email).orElseThrow(() -> ApiException.badRequest("SCHOOL_EMAIL_REQUIRED",
                "IT연합회 학교 이메일만 쓸 수 있어요 (" + String.join(", ", domainList()) + ")"));
    }

    /** 가입할 때 고를 학과(숨기지 않은)가 있는지 */
    @Transactional(readOnly = true)
    public boolean hasActiveDepartment(School school) {
        return departments.findAllByOrderBySortOrderAscIdAsc().stream()
                .anyMatch(d -> d.isActive() && d.getSchool().getId().equals(school.getId()));
    }

    @Transactional(readOnly = true)
    public School require(Long schoolId) {
        return schools.findById(schoolId).orElseThrow(() -> ApiException.notFound("학교를 찾을 수 없어요"));
    }

    /**
     * 이 학교의 학과인지 확인해서 돌려준다. 숨긴 학과는 고를 수 없다 (지금 그 학과인 원우를 그대로 두는 건 된다).
     */
    @Transactional(readOnly = true)
    public Department requireDepartment(School school, Long departmentId, Department current) {
        Department department = departments.findById(departmentId)
                .filter(d -> d.belongsTo(school))
                .orElseThrow(() -> ApiException.badRequest("INVALID_DEPARTMENT", "학과를 다시 골라 주세요"));
        boolean unchanged = current != null && current.getId().equals(department.getId());
        if (!department.isActive() && !unchanged) {
            throw ApiException.badRequest("INVALID_DEPARTMENT", "지금은 고를 수 없는 학과예요");
        }
        return department;
    }

    /** 1기 ~ 이 학교의 최신 기수만 허용 */
    public void checkCohort(School school, int cohort) {
        if (cohort < 1 || cohort > school.getLatestCohort()) {
            throw ApiException.badRequest("INVALID_COHORT", "기수를 확인해 주세요");
        }
    }

    // ---------------- 대학원장 전용 (바꾼 것은 관리자 조치 기록에 남는다) ----------------

    public School addSchool(LoginMember actor, NewSchoolRequest req) {
        String domain = normalizeDomain(req.emailDomain());
        checkDomainFree(domain);
        if (schools.existsByName(req.name().strip())) {
            throw ApiException.conflict("DUPLICATE_SCHOOL", "이미 등록된 학교예요");
        }
        int nextOrder = all().stream().mapToInt(School::getSortOrder).max().orElse(0) + 1;
        School school = schools.save(new School(req.name().strip(), req.shortName().strip(),
                blankToNull(req.graduateSchool()), req.latestCohort(), nextOrder, clock.instant()));
        school.changePlace(blankToNull(req.place()));
        school.changeCampus(req.campus());
        domains.save(new SchoolEmailDomain(school, domain));
        actionLog.record(actor, AdminAction.Type.ADD_SCHOOL, null, null, school.getName() + " · " + domain);
        return school;
    }

    public School updateSchool(LoginMember actor, Long schoolId, SchoolRequest req) {
        School school = require(schoolId);
        school.update(req.name().strip(), req.shortName().strip(), blankToNull(req.graduateSchool()),
                req.latestCohort());
        // 동네 이름: 보내지 않으면(예전 화면) 그대로, 빈칸이면 지운다 (공식 이름으로 보인다)
        if (req.place() != null) {
            school.changePlace(blankToNull(req.place()));
        }
        if (req.campus() != null) {
            school.changeCampus(req.campus());
        }
        actionLog.record(actor, AdminAction.Type.EDIT_SCHOOL, null, null,
                school.getName() + " · 최신 " + req.latestCohort() + "기");
        return school;
    }

    public SchoolEmailDomain addDomain(LoginMember actor, Long schoolId, String rawDomain) {
        School school = require(schoolId);
        String domain = normalizeDomain(rawDomain);
        checkDomainFree(domain);
        SchoolEmailDomain saved = domains.save(new SchoolEmailDomain(school, domain));
        actionLog.record(actor, AdminAction.Type.ADD_DOMAIN, null, null, school.getShortName() + " · " + domain);
        return saved;
    }

    /** 도메인 빼기. 학교마다 하나는 남아야 한다 (이미 가입한 원우는 그대로) */
    public void removeDomain(LoginMember actor, Long schoolId, Long domainId) {
        SchoolEmailDomain domain = domains.findById(domainId)
                .filter(d -> d.getSchool().getId().equals(schoolId))
                .orElseThrow(() -> ApiException.notFound("이메일 도메인을 찾을 수 없어요"));
        if (domains.countBySchoolId(schoolId) <= 1) {
            throw ApiException.badRequest("LAST_DOMAIN", "학교마다 이메일 도메인이 하나는 있어야 해요");
        }
        domains.delete(domain);
        actionLog.record(actor, AdminAction.Type.REMOVE_DOMAIN, null, null,
                domain.getSchool().getShortName() + " · " + domain.getDomain());
    }

    public Department addDepartment(LoginMember actor, Long schoolId, DepartmentRequest req) {
        School school = require(schoolId);
        Department saved = departments.save(new Department(school, req.name().strip(), req.shortName().strip(),
                blankToNull(req.graduateSchool()), departments.maxSortOrder(schoolId) + 1, clock.instant()));
        actionLog.record(actor, AdminAction.Type.ADD_DEPARTMENT, null, null,
                school.getShortName() + " · " + saved.getName());
        return saved;
    }

    public Department updateDepartment(LoginMember actor, Long departmentId, DepartmentRequest req) {
        Department department = departments.findById(departmentId)
                .orElseThrow(() -> ApiException.notFound("학과를 찾을 수 없어요"));
        boolean wasActive = department.isActive();
        department.update(req.name().strip(), req.shortName().strip(), blankToNull(req.graduateSchool()),
                req.active() == null ? department.isActive() : req.active());
        String shown = wasActive == department.isActive() ? "" : department.isActive() ? " · 다시 보이기" : " · 숨기기";
        actionLog.record(actor, AdminAction.Type.EDIT_DEPARTMENT, null, null,
                department.getSchool().getShortName() + " · " + department.getName() + shown);
        return department;
    }

    private void checkDomainFree(String domain) {
        if (domains.existsByDomain(domain)) {
            throw ApiException.conflict("DUPLICATE_DOMAIN", "이미 등록된 이메일 도메인이에요");
        }
    }

    /** 누구나 만들 수 있는 메일(지메일·네이버 등)이나 ac.kr 같은 공용 끝자리 — 학교 도메인으로 넣으면 아무나 그 학교로 가입한다 */
    private static final Set<String> PUBLIC_DOMAINS = Set.of(
            "gmail.com", "googlemail.com", "naver.com", "daum.net", "hanmail.net", "kakao.com", "nate.com",
            "outlook.com", "hotmail.com", "live.com", "icloud.com", "me.com", "yahoo.com", "proton.me",
            "ac.kr", "co.kr", "or.kr", "go.kr", "re.kr", "kr", "com", "net", "org", "edu");

    private static String normalizeDomain(String raw) {
        String domain = raw.strip().toLowerCase(Locale.ROOT);
        domain = domain.startsWith("@") ? domain.substring(1) : domain;
        if (PUBLIC_DOMAINS.contains(domain) || !domain.contains(".")) {
            throw ApiException.badRequest("PUBLIC_DOMAIN", "학교 이메일 도메인만 넣을 수 있어요 (예: korea.ac.kr)");
        }
        return domain;
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.strip();
    }

    private List<String> domainList() {
        return domains.findAllByOrderByIdAsc().stream()
                .sorted(Comparator.comparingInt((SchoolEmailDomain d) -> d.getSchool().getSortOrder()))
                .map(d -> "@" + d.getDomain())
                .toList();
    }
}
