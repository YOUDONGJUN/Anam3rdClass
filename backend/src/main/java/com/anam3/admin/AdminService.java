package com.anam3.admin;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.anam3.admin.AdminDtos.AdminActionView;
import com.anam3.admin.AdminDtos.AdminMemberView;
import com.anam3.admin.AdminDtos.StatsView;
import com.anam3.auth.AuthDtos.ProfileRequest;
import com.anam3.auth.LoginMember;
import com.anam3.common.ApiException;
import com.anam3.common.Emails;
import com.anam3.meetup.MeetupRepository;
import com.anam3.member.AccountService;
import com.anam3.member.Member;
import com.anam3.member.MemberRepository;
import com.anam3.member.MemberRole;
import com.anam3.member.MemberStatus;
import com.anam3.notice.NoticeRepository;
import com.anam3.safety.ReportService;
import com.anam3.school.Department;
import com.anam3.school.School;
import com.anam3.school.SchoolService;

/**
 * 원우 관리.
 * <ul>
 *   <li>교수: 자기 학교 원우(일반 회원)만 보고 차단·해제·프로필 수정</li>
 *   <li>대학원장: 모든 학교 누구나 + 교수 임명·해임, 예전 회원 이메일 지정</li>
 *   <li>대학원장은 아무도 차단하거나 직책을 바꿀 수 없다. 탈퇴한 원우는 관리할 것이 없다</li>
 * </ul>
 */
@Service
@Transactional
public class AdminService {

    private final MemberRepository members;
    private final MeetupRepository meetups;
    private final NoticeRepository notices;
    private final SchoolService schoolService;
    private final ReportService reportService;
    private final AccountService accountService;
    private final AdminActionLog actionLog;
    private final Clock clock;

    public AdminService(MemberRepository members, MeetupRepository meetups, NoticeRepository notices,
            SchoolService schoolService, ReportService reportService, AccountService accountService,
            AdminActionLog actionLog, Clock clock) {
        this.members = members;
        this.meetups = meetups;
        this.notices = notices;
        this.schoolService = schoolService;
        this.reportService = reportService;
        this.accountService = accountService;
        this.actionLog = actionLog;
        this.clock = clock;
    }

    /** 관리 화면 '기록' (대학원장 전용) */
    @Transactional(readOnly = true)
    public List<AdminActionView> actions() {
        return actionLog.recent();
    }

    /** 원우 그리드 전체 (교수는 자기 학교만). 수백 명 규모라 거르기·정렬·통계는 화면에서 한다 */
    @Transactional(readOnly = true)
    public List<AdminMemberView> members(LoginMember viewer) {
        List<Member> found = viewer.isOwner() ? members.findAllForAdmin() : members.findAllForAdmin(viewer.schoolId());
        return found.stream().map(m -> AdminMemberView.from(m, viewer.isOwner())).toList();
    }

    public AdminMemberView ban(LoginMember viewer, Long memberId) {
        Member member = load(memberId);
        if (member.isOwner()) {
            throw ApiException.badRequest("CANNOT_BAN_OWNER", "대학원장은 차단할 수 없어요");
        }
        requireManageable(viewer, member);
        if (member.getStatus() == MemberStatus.BANNED) {
            return view(viewer, member);
        }
        boolean wasManager = member.getRole() == MemberRole.MANAGER;
        member.ban();
        // 이용이 막히면 로그인할 수 없으니, 그 사람이 연 3교시는 취소하고 모집 중인 3교시에서는 뺀다
        accountService.releaseOpenMeetups(member, clock.instant());
        actionLog.record(viewer, AdminAction.Type.BAN, member, null, wasManager ? "교수 직책도 풀림" : null);
        return view(viewer, member);
    }

    public AdminMemberView unban(LoginMember viewer, Long memberId) {
        Member member = load(memberId);
        requireManageable(viewer, member);
        if (member.getStatus() == MemberStatus.BANNED) {
            member.unban();
            actionLog.record(viewer, AdminAction.Type.UNBAN, member, null, null);
        }
        return view(viewer, member);
    }

    /** 이름·기수·학과 고치기 (원우는 본인이 못 바꾸고 요청한다). 학과는 그 원우 학교의 학과만 */
    public AdminMemberView updateProfile(LoginMember viewer, Long memberId, ProfileRequest req) {
        Member member = load(memberId);
        requireManageable(viewer, member);
        School school = member.getSchool();
        schoolService.checkCohort(school, req.cohort());
        Department department = schoolService.requireDepartment(school, req.departmentId(), member.getDepartment());
        String changes = profileChanges(member, req.name().strip(), req.cohort(), department);
        member.updateProfile(req.name().strip(), req.cohort(), department);
        if (changes != null) {
            actionLog.record(viewer, AdminAction.Type.EDIT_PROFILE, member, null, changes);
        }
        return view(viewer, member);
    }

    /** 기록에 남길 바뀐 것 (이름은 바뀌었다는 것만 — 이름 자체는 적지 않는다). 바뀐 게 없으면 null */
    static String profileChanges(Member member, String name, int cohort, Department department) {
        List<String> changes = new ArrayList<>();
        if (!member.getName().equals(name)) {
            changes.add("이름");
        }
        if (member.getCohort() != cohort) {
            changes.add("기수 " + member.cohortLabel() + " → " + cohort + "기");
        }
        if (!member.sameDepartment(department)) {
            changes.add("학과 " + shortLabel(member.getDepartment()) + " → " + shortLabel(department));
        }
        return changes.isEmpty() ? null : String.join(" · ", changes);
    }

    private static String shortLabel(Department department) {
        return department == null ? "없음" : department.getShortName();
    }

    /**
     * 대학원장 전용: 원우를 탈퇴시킨다 (본인 탈퇴와 같다 — 이름·이메일·카카오 연결을 지우고 "탈퇴한 원우"로 남는다.
     * 연 3교시는 취소, 모집 중인 3교시에서는 빠진다). 같은 학교 이메일로 다시 가입할 수 있다.
     */
    public AdminMemberView withdraw(LoginMember viewer, Long memberId) {
        Member member = load(memberId);
        accountService.withdraw(member);
        actionLog.record(viewer, AdminAction.Type.WITHDRAW, member, null, null);
        return view(viewer, member);
    }

    /** 대학원장 전용: 교수 임명(MANAGER)·해임(USER). 교수는 자기 학교 원우만 관리한다 */
    public AdminMemberView changeRole(LoginMember viewer, Long memberId, MemberRole role) {
        Member member = load(memberId);
        if (role == MemberRole.OWNER) {
            throw ApiException.badRequest("INVALID_ROLE", "대학원장은 넘길 수 없어요. 교수 임명·해임만 할 수 있어요");
        }
        if (member.isOwner()) {
            throw ApiException.badRequest("CANNOT_CHANGE_OWNER", "대학원장의 직책은 바꿀 수 없어요");
        }
        requireNotWithdrawn(member);
        if (role == MemberRole.MANAGER && !member.isActive()) {
            throw ApiException.badRequest("BANNED_MEMBER", "차단된 원우는 교수로 임명할 수 없어요");
        }
        if (member.getRole() != role) {
            member.changeRole(role);
            actionLog.record(viewer, role == MemberRole.MANAGER ? AdminAction.Type.APPOINT : AdminAction.Type.DISMISS,
                    member, null, null);
        }
        return view(viewer, member);
    }

    /**
     * 대학원장 전용: 아직 카카오로 로그인하지 않은 예전 회원에게 학교 이메일을 넣어 둔다.
     * 본인이 카카오로 로그인해 그 이메일을 인증하면 이 계정으로 이어진다.
     */
    public AdminMemberView assignEmail(LoginMember viewer, Long memberId, String rawEmail) {
        Member member = load(memberId);
        requireNotWithdrawn(member);
        if (member.getKakaoId() != null || member.hasEmailPassword()) {
            throw ApiException.badRequest("ALREADY_LINKED", "이미 로그인하는 원우예요. 이메일은 본인 인증으로만 바뀌어요");
        }
        String email = Emails.normalize(rawEmail);
        School school = schoolService.requireForEmail(email);
        members.findByEmail(email)
                .filter(other -> !other.getId().equals(member.getId()))
                .ifPresent(other -> {
                    throw ApiException.conflict("DUPLICATE_EMAIL", "이미 다른 원우가 쓰는 학교 이메일이에요");
                });
        member.assignEmail(email, school);
        // 이메일 주소는 남기지 않는다 (어느 학교 도메인인지만)
        actionLog.record(viewer, AdminAction.Type.ASSIGN_EMAIL, member, null, email.substring(email.indexOf('@')));
        return view(viewer, member);
    }

    /** 현황: 대학원장은 모든 학교, 교수는 자기 학교 */
    @Transactional(readOnly = true)
    public StatsView stats(LoginMember viewer) {
        long openReports = reportService.openCount(viewer);
        if (viewer.isOwner()) {
            return new StatsView(members.countByStatusNot(MemberStatus.WITHDRAWN), meetups.count(),
                    meetups.countConfirmed(), notices.count(), openReports);
        }
        Long schoolId = viewer.schoolId();
        return new StatsView(members.countBySchoolIdAndStatusNot(schoolId, MemberStatus.WITHDRAWN),
                meetups.countBySchoolId(schoolId), meetups.countConfirmed(schoolId), notices.countBySchoolId(schoolId),
                openReports);
    }

    /** 교수는 자기 학교 원우만 관리한다. 교수·대학원장 계정은 대학원장만 */
    private static void requireManageable(LoginMember viewer, Member target) {
        requireNotWithdrawn(target);
        if (viewer.isOwner()) {
            return;
        }
        if (!target.isSameSchool(viewer.schoolId())) {
            throw ApiException.forbidden("우리 학교 원우만 관리할 수 있어요");
        }
        if (target.isStaff()) {
            throw ApiException.forbidden("교수·대학원장 계정은 대학원장만 관리할 수 있어요");
        }
    }

    private static void requireNotWithdrawn(Member member) {
        if (member.isWithdrawn()) {
            throw ApiException.badRequest("WITHDRAWN", "탈퇴한 원우예요");
        }
    }

    private static AdminMemberView view(LoginMember viewer, Member member) {
        return AdminMemberView.from(member, viewer.isOwner());
    }

    private Member load(Long memberId) {
        return members.findWithSchool(memberId).orElseThrow(() -> ApiException.notFound("원우를 찾을 수 없어요"));
    }
}
