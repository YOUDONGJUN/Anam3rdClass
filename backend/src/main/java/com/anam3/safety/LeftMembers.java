package com.anam3.safety;

import java.util.Optional;

import org.springframework.stereotype.Component;

import com.anam3.auth.LoginMember;
import com.anam3.meetup.Meetup;
import com.anam3.meetup.MeetupLeaveRepository;
import com.anam3.meetup.MeetupRepository;
import com.anam3.meetup.ParticipationRepository;
import com.anam3.member.Member;
import com.anam3.member.MemberRepository;

/**
 * 성사된 뒤 나간 원우를 신고·차단할 때: 나간 기록 번호로 그 3교시와 사람을 찾는다.
 * 그 3교시의 연 사람이거나 지금 참여 중인 원우만 (나간 원우가 보이는 사람과 같다)
 */
@Component
public class LeftMembers {

    public record Found(Meetup meetup, Member member) {
    }

    private final MeetupLeaveRepository leaves;
    private final MeetupRepository meetups;
    private final ParticipationRepository participations;
    private final MemberRepository members;

    public LeftMembers(MeetupLeaveRepository leaves, MeetupRepository meetups, ParticipationRepository participations,
            MemberRepository members) {
        this.leaves = leaves;
        this.meetups = meetups;
        this.participations = participations;
        this.members = members;
    }

    public Optional<Found> find(LoginMember viewer, Long leaveId) {
        return leaves.findById(leaveId).flatMap(leave -> meetups.findWithHost(leave.getMeetupId())
                .filter(m -> m.isHostedBy(viewer.id()) || participations.existsByMeetupIdAndMemberId(m.getId(), viewer.id()))
                .flatMap(m -> members.findWithSchool(leave.getMemberId()).map(member -> new Found(m, member))));
    }
}
