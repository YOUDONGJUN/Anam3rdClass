package com.anam3.member;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.anam3.auth.LoginMember;
import com.anam3.common.ApiException;
import com.anam3.meetup.Meetup;
import com.anam3.meetup.MeetupRepository;
import com.anam3.meetup.MeetupWaitRepository;
import com.anam3.meetup.ParticipationRepository;
import com.anam3.member.MeDtos.MyProfileRequest;
import com.anam3.push.PushNotifier;
import com.anam3.push.PushService;
import com.anam3.safety.MemberBlockRepository;
import com.anam3.school.Department;
import com.anam3.school.SchoolService;

/** 내 계정: 약관 동의, 내 정보 고치기, 랭킹 설정, 탈퇴 */
@Service
@Transactional
public class AccountService {

    private final MemberRepository members;
    private final MeetupRepository meetups;
    private final ParticipationRepository participations;
    private final MemberBlockRepository blocks;
    private final PushService push;
    private final PushNotifier notifier;
    private final MeetupWaitRepository waits;
    private final SchoolService schoolService;
    private final Clock clock;

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("M월 d일", Locale.KOREAN)
            .withZone(ZoneId.of("Asia/Seoul"));

    public AccountService(MemberRepository members, MeetupRepository meetups, ParticipationRepository participations,
            MemberBlockRepository blocks, PushService push, PushNotifier notifier, MeetupWaitRepository waits,
            SchoolService schoolService, Clock clock) {
        this.members = members;
        this.meetups = meetups;
        this.participations = participations;
        this.blocks = blocks;
        this.push = push;
        this.notifier = notifier;
        this.waits = waits;
        this.schoolService = schoolService;
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
     * 내 정보 고치기.
     * <ul>
     *   <li>이름: 대학원장만 바꾼다. 교수는 대학원장에게, 원우는 교수·대학원장에게 요청한다 (학교 이메일로 본인 인증을 하고 들어와서)</li>
     *   <li>학과·기수: 교수·대학원장은 언제나, 원우는 30일에 한 번 (바뀐 게 없으면 횟수를 쓰지 않는다)</li>
     *   <li>학과는 내 학교의 학과만, 기수는 1기 ~ 내 학교 최신 기수</li>
     * </ul>
     */
    public MemberResponse updateProfile(LoginMember viewer, MyProfileRequest req) {
        Member member = load(viewer);
        String name = member.getName();
        if (req.name() != null && !req.name().strip().equals(member.getName())) {
            if (!member.isOwner()) {
                throw ApiException.forbidden(member.isStaff() ? "이름은 대학원장에게 바꿔 달라고 요청해 주세요"
                        : "이름은 바꿀 수 없어요. 교수·대학원장에게 요청해 주세요");
            }
            name = req.name().strip();
        }
        Department department = member.getDepartment();
        boolean affiliationChanged = member.getCohort() != req.cohort()
                || department == null || !department.getId().equals(req.departmentId());
        if (affiliationChanged) {
            Instant now = clock.instant();
            Instant editableAt = MemberResponse.profileEditableAt(member);
            if (editableAt != null && now.isBefore(editableAt)) {
                throw ApiException.badRequest("PROFILE_CHANGE_LIMIT",
                        "학과·기수는 30일에 한 번 바꿀 수 있어요. " + DAY.format(editableAt) + "부터 다시 바꿀 수 있어요");
            }
            schoolService.checkCohort(member.getSchool(), req.cohort());
            department = schoolService.requireDepartment(member.getSchool(), req.departmentId(), member.getDepartment());
            member.changeOwnProfile(name, req.cohort(), department, now);
        } else if (!name.equals(member.getName())) {
            member.changeOwnProfile(name, member.getCohort(), department, clock.instant());
        }
        return MemberResponse.from(member);
    }

    /** 랭킹에 안 나오기 켜고 끄기 */
    public MemberResponse changeRankingHidden(LoginMember viewer, boolean hidden) {
        Member member = load(viewer);
        member.changeRankingHidden(hidden);
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
     * 빠져서 정원이 찬 3교시에 자리가 나면 기다리던 사람에게 알린다. 이 사람의 빈자리 대기는 지운다.
     */
    public void releaseOpenMeetups(Member member, Instant now) {
        waits.deleteAllOfMember(member.getId());
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
                boolean wasFull = locked.isFull();
                participations.delete(p);
                locked.removeParticipant();
                if (wasFull && locked.isJoinableAt(now)) {
                    notifier.spotOpened(locked, waits.findMemberIds(meetupId));
                }
            });
        }
    }

    private Member load(LoginMember viewer) {
        return members.findWithSchool(viewer.id())
                .orElseThrow(() -> ApiException.notFound("회원 정보를 찾을 수 없어요"));
    }
}
