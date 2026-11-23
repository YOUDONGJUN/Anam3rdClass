package com.anam3.admin;

import java.security.SecureRandom;
import java.util.List;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.anam3.admin.AdminDtos.AdminMemberView;
import com.anam3.admin.AdminDtos.StatsView;
import com.anam3.admin.AdminDtos.TemporaryPasswordView;
import com.anam3.common.ApiException;
import com.anam3.meetup.MeetupRepository;
import com.anam3.member.Member;
import com.anam3.member.MemberRepository;
import com.anam3.notice.NoticeRepository;

@Service
@Transactional
public class AdminService {

    private static final String PASSWORD_CHARS = "abcdefghjkmnpqrstuvwxyz23456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final MemberRepository members;
    private final MeetupRepository meetups;
    private final NoticeRepository notices;
    private final PasswordEncoder passwordEncoder;

    public AdminService(MemberRepository members, MeetupRepository meetups, NoticeRepository notices,
            PasswordEncoder passwordEncoder) {
        this.members = members;
        this.meetups = meetups;
        this.notices = notices;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional(readOnly = true)
    public List<AdminMemberView> members() {
        return members.findAllByOrderByCreatedAtDesc().stream().map(AdminMemberView::from).toList();
    }

    public AdminMemberView ban(Long memberId) {
        Member member = load(memberId);
        if (member.isAdmin()) {
            throw ApiException.badRequest("CANNOT_BAN_ADMIN", "관리자 계정은 차단할 수 없어요");
        }
        member.ban();
        return AdminMemberView.from(member);
    }

    public AdminMemberView unban(Long memberId) {
        Member member = load(memberId);
        member.unban();
        return AdminMemberView.from(member);
    }

    /** 비밀번호를 잊은 회원에게 임시 비밀번호를 발급한다. 운영자가 직접 전달 */
    public TemporaryPasswordView resetPassword(Long memberId) {
        Member member = load(memberId);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 10; i++) {
            sb.append(PASSWORD_CHARS.charAt(RANDOM.nextInt(PASSWORD_CHARS.length())));
        }
        String temporary = sb.toString();
        member.changePassword(passwordEncoder.encode(temporary));
        return new TemporaryPasswordView(temporary);
    }

    @Transactional(readOnly = true)
    public StatsView stats() {
        return new StatsView(members.count(), meetups.count(), meetups.countConfirmed(), notices.count());
    }

    private Member load(Long memberId) {
        return members.findById(memberId).orElseThrow(() -> ApiException.notFound("회원을 찾을 수 없어요"));
    }
}
