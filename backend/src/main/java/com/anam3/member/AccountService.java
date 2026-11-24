package com.anam3.member;

import java.time.Clock;
import java.time.Instant;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.anam3.auth.LoginMember;
import com.anam3.common.ApiException;
import com.anam3.meetup.Meetup;
import com.anam3.meetup.MeetupRepository;
import com.anam3.meetup.ParticipationRepository;
import com.anam3.push.PushNotifier;
import com.anam3.push.PushService;
import com.anam3.safety.MemberBlockRepository;

/** 내 계정: 약관 동의, 탈퇴 */
@Service
@Transactional
public class AccountService {

    private final MemberRepository members;
    private final MeetupRepository meetups;
    private final ParticipationRepository participations;
    private final MemberBlockRepository blocks;
    private final PushService push;
    private final PushNotifier notifier;
    private final Clock clock;

    public AccountService(MemberRepository members, MeetupRepository meetups, ParticipationRepository participations,
            MemberBlockRepository blocks, PushService push, PushNotifier notifier, Clock clock) {
        this.members = members;
        this.meetups = meetups;
        this.participations = participations;
        this.blocks = blocks;
        this.push = push;
        this.notifier = notifier;
        this.clock = clock;
    }

    /** 예전 회원이 로그인한 뒤 약관에 동의한다 (새로 가입하는 사람은 가입할 때 동의한다) */
    public MemberResponse agreeTerms(LoginMember viewer) {
        Member member = load(viewer);
        if (!member.hasAgreedTerms()) {
            member.agreeTerms(clock.instant());
        }
        return MemberResponse.from(member);
    }

    /**
     * 탈퇴. 바로 처리하고 되돌릴 수 없다.
     * <ul>
     *   <li>내가 연, 아직 모이기 전인 3교시는 취소한다</li>
     *   <li>아직 모집 중인 3교시에서는 빠진다 (인원도 줄어든다). 이미 성사·지난 3교시에는 "탈퇴한 원우"로 남는다</li>
     *   <li>차단 기록을 지우고, 이름·이메일·카카오 연결·학과를 지운다. 같은 카카오·학교 이메일로 다시 가입할 수 있다</li>
     * </ul>
     * 대학원장(OWNER_EMAIL)은 탈퇴할 수 없다 — 먼저 서버 설정에서 대학원장을 바꿔야 한다.
     */
    public void withdraw(LoginMember viewer) {
        withdraw(load(viewer));
    }

    /** 본인 탈퇴와 대학원장이 시키는 탈퇴가 같이 쓴다 */
    public void withdraw(Member member) {
        if (member.isOwner()) {
            throw ApiException.badRequest("OWNER_CANNOT_WITHDRAW", "대학원장은 탈퇴할 수 없어요. 대학원장을 먼저 넘겨 주세요");
        }
        if (member.isWithdrawn()) {
            throw ApiException.badRequest("ALREADY_WITHDRAWN", "이미 탈퇴한 원우예요");
        }
        Instant now = clock.instant();
        releaseOpenMeetups(member, now);
        blocks.deleteAllOf(member.getId());
        push.forget(member.getId()); // 이 사람 폰으로 더는 알림을 보내지 않는다
        member.withdraw(now);
    }

    /**
     * 탈퇴·이용 제한 때: 내가 연, 아직 모이기 전인 3교시는 취소하고, 아직 모집 중인 남의 3교시에서는 빠진다.
     * 참여 인원은 3교시를 잠근 뒤 새로 읽은 값에서 줄인다 (그 사이 다른 사람이 참여해도 숫자가 어긋나지 않게).
     */
    public void releaseOpenMeetups(Member member, Instant now) {
        for (Meetup hosted : meetups.findOpenHostedBy(member.getId(), now)) {
            // 참여·수정과 겹치지 않게 잠그고 새로 읽은 뒤 취소한다
            Meetup locked = meetups.findByIdForUpdate(hosted.getId()).orElseThrow();
            if (locked.isCanceled()) {
                continue;
            }
            locked.cancel(now);
            notifier.canceled(locked, participations.findMemberIds(locked.getId()), member.getId(), false);
        }
        for (Long meetupId : participations.findOpenJoinedMeetupIds(member.getId(), now)) {
            Meetup locked = meetups.findByIdForUpdate(meetupId).orElseThrow();
            if (!locked.isRecruitingAt(now)) {
                continue; // 이미 성사·마감된 3교시에는 그대로 남는다
            }
            participations.findByMeetupIdAndMemberId(meetupId, member.getId()).ifPresent(p -> {
                participations.delete(p);
                locked.removeParticipant();
            });
        }
    }

    private Member load(LoginMember viewer) {
        return members.findWithSchool(viewer.id())
                .orElseThrow(() -> ApiException.notFound("회원 정보를 찾을 수 없어요"));
    }
}
