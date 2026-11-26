package com.anam3.admin;

import java.util.List;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.anam3.admin.AdminDtos.AdminActionView;
import com.anam3.admin.AdminDtos.AdminMemberView;
import com.anam3.admin.AdminDtos.EmailRequest;
import com.anam3.admin.AdminDtos.RoleRequest;
import com.anam3.admin.AdminDtos.StatsView;
import com.anam3.auth.AuthDtos.ProfileRequest;
import com.anam3.auth.LoginMember;
import com.anam3.meetup.MeetupDtos.MeetupSummary;
import com.anam3.meetup.MeetupService;
import com.anam3.notice.NoticeDtos.NoticeDetail;
import com.anam3.notice.NoticeDtos.PinRequest;
import com.anam3.notice.NoticeService;
import com.anam3.safety.ReportService;
import com.anam3.safety.SafetyDtos.ReportView;
import com.anam3.safety.SafetyDtos.ResolveRequest;
import com.anam3.school.SchoolDtos.DepartmentRequest;
import com.anam3.school.SchoolDtos.DomainRequest;
import com.anam3.school.SchoolDtos.NewSchoolRequest;
import com.anam3.school.SchoolDtos.SchoolRequest;
import com.anam3.school.SchoolDtos.SchoolView;
import com.anam3.school.SchoolService;

/**
 * 관리 API. SecurityConfig 에서 /api/admin/** 은 교수·대학원장만,
 * 학교·학과 바꾸기, 직책·이메일 지정은 대학원장만 통과시킨다.
 * 교수는 자기 학교 원우·3교시·신고만 본다 (서비스에서 거른다).
 */
@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final AdminService adminService;
    private final SchoolService schoolService;
    private final MeetupService meetupService;
    private final NoticeService noticeService;
    private final ReportService reportService;

    public AdminController(AdminService adminService, SchoolService schoolService, MeetupService meetupService,
            NoticeService noticeService, ReportService reportService) {
        this.adminService = adminService;
        this.schoolService = schoolService;
        this.meetupService = meetupService;
        this.noticeService = noticeService;
        this.reportService = reportService;
    }

    @GetMapping("/stats")
    public StatsView stats(@AuthenticationPrincipal LoginMember me) {
        return adminService.stats(me);
    }

    // ---------------- 원우 ----------------

    @GetMapping("/members")
    public List<AdminMemberView> members(@AuthenticationPrincipal LoginMember me) {
        return adminService.members(me);
    }

    @PostMapping("/members/{id}/ban")
    public AdminMemberView ban(@AuthenticationPrincipal LoginMember me, @PathVariable Long id) {
        return adminService.ban(me, id);
    }

    @PostMapping("/members/{id}/unban")
    public AdminMemberView unban(@AuthenticationPrincipal LoginMember me, @PathVariable Long id) {
        return adminService.unban(me, id);
    }

    @PutMapping("/members/{id}/profile")
    public AdminMemberView updateProfile(@AuthenticationPrincipal LoginMember me, @PathVariable Long id,
            @Valid @RequestBody ProfileRequest req) {
        return adminService.updateProfile(me, id, req);
    }

    /** 대학원장 전용 */
    @PutMapping("/members/{id}/role")
    public AdminMemberView changeRole(@AuthenticationPrincipal LoginMember me, @PathVariable Long id,
            @Valid @RequestBody RoleRequest req) {
        return adminService.changeRole(me, id, req.role());
    }

    /** 대학원장 전용 */
    @PutMapping("/members/{id}/email")
    public AdminMemberView assignEmail(@AuthenticationPrincipal LoginMember me, @PathVariable Long id,
            @Valid @RequestBody EmailRequest req) {
        return adminService.assignEmail(me, id, req.email());
    }

    /** 대학원장 전용: 탈퇴시키기 */
    @PostMapping("/members/{id}/withdraw")
    public AdminMemberView withdraw(@AuthenticationPrincipal LoginMember me, @PathVariable Long id) {
        return adminService.withdraw(me, id);
    }

    // ---------------- 학교 · 학과 (바꾸기는 대학원장 전용) ----------------

    /** 모든 학교와 도메인·학과 (숨긴 학과 포함) */
    @GetMapping("/schools")
    public List<SchoolView> schools() {
        return schoolService.views(true);
    }

    @PostMapping("/schools")
    @ResponseStatus(HttpStatus.CREATED)
    public List<SchoolView> addSchool(@AuthenticationPrincipal LoginMember me,
            @Valid @RequestBody NewSchoolRequest req) {
        schoolService.addSchool(me, req);
        return schoolService.views(true);
    }

    /** 이름·짧은 이름·대학원 이름·최신 기수 */
    @PutMapping("/schools/{id}")
    public List<SchoolView> updateSchool(@AuthenticationPrincipal LoginMember me, @PathVariable Long id,
            @Valid @RequestBody SchoolRequest req) {
        schoolService.updateSchool(me, id, req);
        return schoolService.views(true);
    }

    @PostMapping("/schools/{id}/domains")
    @ResponseStatus(HttpStatus.CREATED)
    public List<SchoolView> addDomain(@AuthenticationPrincipal LoginMember me, @PathVariable Long id,
            @Valid @RequestBody DomainRequest req) {
        schoolService.addDomain(me, id, req.domain());
        return schoolService.views(true);
    }

    @DeleteMapping("/schools/{id}/domains/{domainId}")
    public List<SchoolView> removeDomain(@AuthenticationPrincipal LoginMember me, @PathVariable Long id,
            @PathVariable Long domainId) {
        schoolService.removeDomain(me, id, domainId);
        return schoolService.views(true);
    }

    @PostMapping("/schools/{id}/departments")
    @ResponseStatus(HttpStatus.CREATED)
    public List<SchoolView> addDepartment(@AuthenticationPrincipal LoginMember me, @PathVariable Long id,
            @Valid @RequestBody DepartmentRequest req) {
        schoolService.addDepartment(me, id, req);
        return schoolService.views(true);
    }

    /** 이름 바꾸기, 숨기기(active=false)·다시 보이기 */
    @PutMapping("/departments/{id}")
    public List<SchoolView> updateDepartment(@AuthenticationPrincipal LoginMember me, @PathVariable Long id,
            @Valid @RequestBody DepartmentRequest req) {
        schoolService.updateDepartment(me, id, req);
        return schoolService.views(true);
    }

    // ---------------- 관리자 조치 기록 (대학원장 전용) ----------------

    /** 최근 200건: 누가 · 언제 · 무엇을 · 누구에게 */
    @GetMapping("/actions")
    public List<AdminActionView> actions() {
        return adminService.actions();
    }

    // ---------------- 신고 ----------------

    @GetMapping("/reports")
    public List<ReportView> reports(@AuthenticationPrincipal LoginMember me) {
        return reportService.list(me);
    }

    @PutMapping("/reports/{id}")
    public ReportView resolveReport(@AuthenticationPrincipal LoginMember me, @PathVariable Long id,
            @Valid @RequestBody ResolveRequest req) {
        return reportService.resolve(me, id, req.resolved());
    }

    // ---------------- 3교시 · 공지 ----------------

    @GetMapping("/meetups")
    public List<MeetupSummary> meetups(@AuthenticationPrincipal LoginMember me) {
        return meetupService.all(me);
    }

    @PutMapping("/notices/{id}/pin")
    public NoticeDetail pin(@AuthenticationPrincipal LoginMember me, @PathVariable Long id,
            @Valid @RequestBody PinRequest req) {
        return noticeService.pin(me, id, req.pinned());
    }
}
