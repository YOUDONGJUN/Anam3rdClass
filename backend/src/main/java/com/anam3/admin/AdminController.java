package com.anam3.admin;

import java.util.List;

import jakarta.validation.Valid;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.anam3.admin.AdminDtos.AdminMemberView;
import com.anam3.admin.AdminDtos.InviteCodeRequest;
import com.anam3.admin.AdminDtos.LatestCohortRequest;
import com.anam3.admin.AdminDtos.SettingsView;
import com.anam3.admin.AdminDtos.StatsView;
import com.anam3.admin.AdminDtos.TemporaryPasswordView;
import com.anam3.auth.LoginMember;
import com.anam3.meetup.MeetupDtos.MeetupSummary;
import com.anam3.meetup.MeetupService;
import com.anam3.notice.NoticeDtos.NoticeDetail;
import com.anam3.notice.NoticeDtos.PinRequest;
import com.anam3.notice.NoticeService;
import com.anam3.setting.SettingService;

/** 운영자 전용 API. SecurityConfig 에서 /api/admin/** 은 ADMIN 만 통과시킨다 */
@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final AdminService adminService;
    private final SettingService settingService;
    private final MeetupService meetupService;
    private final NoticeService noticeService;

    public AdminController(AdminService adminService, SettingService settingService, MeetupService meetupService,
            NoticeService noticeService) {
        this.adminService = adminService;
        this.settingService = settingService;
        this.meetupService = meetupService;
        this.noticeService = noticeService;
    }

    @GetMapping("/stats")
    public StatsView stats() {
        return adminService.stats();
    }

    @GetMapping("/members")
    public List<AdminMemberView> members() {
        return adminService.members();
    }

    @PostMapping("/members/{id}/ban")
    public AdminMemberView ban(@PathVariable Long id) {
        return adminService.ban(id);
    }

    @PostMapping("/members/{id}/unban")
    public AdminMemberView unban(@PathVariable Long id) {
        return adminService.unban(id);
    }

    @PostMapping("/members/{id}/reset-password")
    public TemporaryPasswordView resetPassword(@PathVariable Long id) {
        return adminService.resetPassword(id);
    }

    @GetMapping("/settings")
    public SettingsView settings() {
        return currentSettings();
    }

    @PutMapping("/settings/invite-code")
    public SettingsView changeInviteCode(@Valid @RequestBody InviteCodeRequest req) {
        settingService.changeInviteCode(req.inviteCode());
        return currentSettings();
    }

    @PutMapping("/settings/latest-cohort")
    public SettingsView changeLatestCohort(@Valid @RequestBody LatestCohortRequest req) {
        settingService.changeLatestCohort(req.latestCohort());
        return currentSettings();
    }

    private SettingsView currentSettings() {
        return new SettingsView(settingService.inviteCode(), settingService.latestCohort());
    }

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
