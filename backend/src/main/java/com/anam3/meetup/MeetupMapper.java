package com.anam3.meetup;

import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Component;

import com.anam3.auth.LoginMember;
import com.anam3.common.NameMasker;
import com.anam3.feedback.FeedbackDtos.FeedbackPrompt;
import com.anam3.meetup.MeetupDtos.MeetupDetail;
import com.anam3.meetup.MeetupDtos.MeetupSummary;
import com.anam3.meetup.MeetupDtos.PersonView;
import com.anam3.member.Member;
import com.anam3.ranking.RankingService;
import com.anam3.school.SchoolDtos.SchoolTag;
import com.anam3.settlement.SettlementDtos.SettlementInfo;

/**
 * 3교시를 화면용 응답으로 바꾸면서 <b>누가 누구 이름을 볼 수 있는지</b>와 <b>무엇을 할 수 있는지</b>를 결정한다.
 *
 * <h3>공개 규칙 (민망함 방지)</h3>
 * <ul>
 *   <li>대학원장이 볼 때: 모두 실명</li>
 *   <li>성사(최소 인원 달성)된 3교시의 참여자: 서로 실명 + 오픈채팅 링크.
 *       최소 인원이 없는(무조건 진행) 3교시는 열자마자 성사라서 참여하면 바로 실명</li>
 *   <li>그 외 모두 (성사 전, 미참여자, 무산된 3교시): "김*대 · 61기 · 인융" 처럼 가운데 마스킹.
 *       <b>교수도 여기에 해당한다</b> — 같은 학생이라 누가 어느 번개에 갔는지 보이면 안 된다</li>
 *   <li>내 이름은 나에게 항상 그대로</li>
 *   <li>대학원장 본인의 이름은 누구에게나 그대로 ("대학원장" 표시와 함께)</li>
 * </ul>
 *
 * <h3>학교</h3>
 * 연합이 아닌 3교시는 같은 학교 원우만 보고 참여한다. 연합 3교시는 다섯 학교 누구나 — 사람마다 학교 배지가 붙는다.
 *
 * <h3>관리</h3>
 * 수정은 개설자·대학원장, 취소는 개설자·그 학교 교수·대학원장.
 * (교수는 오픈채팅 링크를 볼 수 없어서 수정하면 링크가 지워지므로 수정은 막는다)
 * <p>
 * 실명은 서버에서 가려서 내려보낸다. 화면(JS)에서 가리면 개발자도구로 다 보이기 때문.
 */
@Component
public class MeetupMapper {

    private final RankingService ranking;

    public MeetupMapper(RankingService ranking) {
        this.ranking = ranking;
    }

    /** 빈자리 대기: 나는 대기 중인지, 몇 명이 기다리는지 */
    public record WaitInfo(boolean waiting, long count) {

        public static final WaitInfo NONE = new WaitInfo(false, 0);
    }

    public static boolean canSeeRealNames(Meetup meetup, LoginMember viewer, boolean joined) {
        if (viewer.isOwner()) {
            return true;
        }
        return joined && meetup.isReached() && !meetup.isCanceled();
    }

    /** 지금 이 사람이 참여 버튼을 누를 수 있는지 (학교·기수 제한 포함) */
    static boolean joinableBy(Meetup m, LoginMember viewer, Instant now) {
        return m.isJoinableAt(now) && m.isOpenTo(viewer.schoolId(), viewer.cohort());
    }

    public MeetupSummary toSummary(Meetup m, LoginMember viewer, boolean joined, Instant now) {
        boolean revealed = canSeeRealNames(m, viewer, joined);
        return new MeetupSummary(
                m.getId(), m.getTitle(), m.getPlace(), m.getCategories(), m.getPurposes(),
                m.getMeetAt(), m.getDeadlineAt(), m.getMinPeople(), m.getMaxPeople(), m.getParticipantCount(),
                m.statusAt(now), joinableBy(m, viewer, now), joined, m.isHostedBy(viewer.id()),
                person(m.getHost(), viewer, revealed, true, null), m.getOnlyCohort(), m.audience(),
                SchoolTag.from(m.getSchool()), m.isLateJoin(), m.getFee(), m.isFeePrepaid(), m.getLocation(),
                m.getRegion());
    }

    /**
     * @param blocked 나와 연 사람 사이에 차단이 있으면 true — 참여 버튼을 감춘다
     * @param feedback 후기를 받는 동안 함께한 사람에게만 (아니면 null)
     * @param wait 빈자리 대기
     * @param settlement 정산 (함께한 사람에게만)
     */
    public MeetupDetail toDetail(Meetup m, List<Participation> participations, LoginMember viewer, boolean blocked,
            FeedbackPrompt feedback, WaitInfo wait, SettlementInfo settlement, List<PersonView> leftPeople, Instant now) {
        boolean joined = participations.stream().anyMatch(p -> p.getMember().getId().equals(viewer.id()));
        boolean hosting = m.isHostedBy(viewer.id());
        boolean revealed = canSeeRealNames(m, viewer, joined);
        boolean open = m.isOpenAt(now);
        boolean canEdit = (hosting || viewer.isOwner()) && open;
        boolean canCancel = MeetupService.canModerate(m, viewer) && open;
        boolean canDelete = MeetupService.canDelete(m, viewer, now);
        boolean joinable = joinableBy(m, viewer, now) && !blocked;
        boolean hasOpenChat = m.getOpenChatUrl() != null && !m.getOpenChatUrl().isBlank();
        boolean canSeeOpenChat = viewer.isOwner() || hosting || revealed;
        boolean manages = hosting || viewer.isOwner();
        // 정원이 차서 지금은 못 타지만, 자리가 나면 탈 수 있는 사람
        boolean canWait = !joined && !hosting && !blocked && m.isFull() && m.isTakingJoinsAt(now)
                && m.isOpenTo(viewer.schoolId(), viewer.cohort());

        List<PersonView> people = participations.stream()
                .map(p -> person(p.getMember(), viewer, revealed, m.isHostedBy(p.getMember().getId()), p.getId()))
                .toList();

        return new MeetupDetail(
                m.getId(), m.getTitle(), m.getDescription(), m.getPlace(), m.getCategories(), m.getPurposes(),
                m.getMeetAt(), m.getDeadlineAt(), m.getMinPeople(), m.getMaxPeople(), m.getParticipantCount(),
                m.statusAt(now), joinable, joined, hosting,
                person(m.getHost(), viewer, revealed, true, null),
                people,
                revealed,
                canSeeOpenChat && hasOpenChat ? m.getOpenChatUrl() : null,
                hasOpenChat,
                canEdit,
                canCancel,
                !joined && joinable,
                joined && !hosting && m.isRecruitingAt(now),
                m.getCreatedAt(),
                m.getOnlyCohort(),
                m.audience(),
                SchoolTag.from(m.getSchool()),
                m.isLateJoin(),
                m.getFee(),
                m.isFeePrepaid(),
                canDelete,
                manages && m.isConfirmableAt(now),
                feedback,
                wait.waiting() && canWait,
                canWait,
                manages ? (int) wait.count() : null,
                manages && open && m.getMaxPeople() != null && m.getMaxPeople() < MeetupService.MAX_SEATS,
                settlement.canSettle(),
                settlement.view(),
                leftPeople,
                m.getLocation(),
                m.getRegion());
    }

    /** 다른 화면(정산)에서 사람을 보여줄 때. 공개 규칙은 3교시와 같다 */
    public PersonView personView(Member member, LoginMember viewer, boolean revealed, boolean host, Long ref) {
        return person(member, viewer, revealed, host, ref);
    }

    private PersonView person(Member member, LoginMember viewer, boolean revealed, boolean host, Long ref) {
        boolean me = member.getId().equals(viewer.id());
        boolean owner = member.isOwner();
        // 탈퇴한 원우는 이름이 이미 지워져 "탈퇴한 원우"로만 보인다
        boolean showReal = revealed || me || owner || member.isWithdrawn();
        String name = showReal ? member.getName() : NameMasker.mask(member.getName());
        return new PersonView(name, member.cohortLabel(), member.departmentShortLabel(),
                SchoolTag.from(member.getSchool()), me, host, owner, !showReal, ref,
                ranking.crowns().getOrDefault(member.getId(), List.of()), member.isWithdrawn());
    }
}
