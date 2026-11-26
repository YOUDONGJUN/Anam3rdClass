package com.anam3.safety;

import java.time.Clock;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.anam3.auth.LoginMember;
import com.anam3.common.ApiException;
import com.anam3.common.NameMasker;
import com.anam3.meetup.MeetupService;
import com.anam3.meetup.Participation;
import com.anam3.meetup.ParticipationRepository;
import com.anam3.member.Member;
import com.anam3.member.MemberRepository;
import com.anam3.safety.SafetyDtos.BlockView;
import com.anam3.school.SchoolDtos.SchoolTag;

/**
 * 차단. 3교시 상세 화면에서 만난 사람을 참여 번호로 차단한다 (회원 번호는 화면에 내보내지 않는다).
 * 차단 목록에도 이름은 가려서 보여준다.
 */
@Service
@Transactional
public class BlockService {

    private final MemberBlockRepository blocks;
    private final ParticipationRepository participations;
    private final MemberRepository members;
    private final Clock clock;
    private final LeftMembers leftMembers;

    public BlockService(MemberBlockRepository blocks, ParticipationRepository participations,
            MemberRepository members, Clock clock, LeftMembers leftMembers) {
        this.blocks = blocks;
        this.participations = participations;
        this.members = members;
        this.clock = clock;
        this.leftMembers = leftMembers;
    }

    public BlockView block(LoginMember viewer, Long participationRef) {
        return block(viewer, participationRef, false);
    }

    /** left: ref 가 성사된 뒤 나간 원우의 기록 번호 */
    public BlockView block(LoginMember viewer, Long ref, boolean left) {
        Member target = left
                ? leftMembers.find(viewer, ref).map(LeftMembers.Found::member)
                        .orElseThrow(() -> ApiException.notFound("차단할 사람을 찾을 수 없어요"))
                : participations.findById(ref)
                        .filter(p -> canSeeMeetupOf(p, viewer))
                        .map(Participation::getMember)
                        .orElseThrow(() -> ApiException.notFound("차단할 사람을 찾을 수 없어요"));
        if (target.getId().equals(viewer.id())) {
            throw ApiException.badRequest("SELF_BLOCK", "나 자신은 차단할 수 없어요");
        }
        if (target.isWithdrawn()) {
            throw ApiException.badRequest("WITHDRAWN", "이미 탈퇴한 원우예요");
        }
        if (blocks.existsByBlockerIdAndBlockedId(viewer.id(), target.getId())) {
            throw ApiException.conflict("ALREADY_BLOCKED", "이미 차단한 사람이에요");
        }
        MemberBlock block = blocks.save(new MemberBlock(members.getReferenceById(viewer.id()), target, clock.instant()));
        return view(block);
    }

    @Transactional(readOnly = true)
    public List<BlockView> list(LoginMember viewer) {
        return blocks.findAllOfBlocker(viewer.id()).stream().map(BlockService::view).toList();
    }

    public void unblock(LoginMember viewer, Long blockId) {
        MemberBlock block = blocks.findByIdAndBlockerId(blockId, viewer.id())
                .orElseThrow(() -> ApiException.notFound("차단 기록을 찾을 수 없어요"));
        blocks.delete(block);
    }

    /** 내가 볼 수 있는 3교시의 참여자만 (참여 번호를 아무거나 넣어 보는 걸 막는다) */
    private boolean canSeeMeetupOf(Participation p, LoginMember viewer) {
        return MeetupService.canSee(p.getMeetup(), viewer)
                || participations.existsByMeetupIdAndMemberId(p.getMeetup().getId(), viewer.id());
    }

    private static BlockView view(MemberBlock b) {
        Member m = b.getBlocked();
        String name = m.isWithdrawn() || m.isOwner() ? m.getName() : NameMasker.mask(m.getName());
        return new BlockView(b.getId(), name, m.cohortLabel(), m.departmentShortLabel(), SchoolTag.from(m.getSchool()),
                b.getCreatedAt());
    }
}
