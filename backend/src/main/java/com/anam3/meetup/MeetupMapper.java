package com.anam3.meetup;

import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Component;

import com.anam3.auth.LoginMember;
import com.anam3.common.NameMasker;
import com.anam3.meetup.MeetupDtos.MeetupDetail;
import com.anam3.meetup.MeetupDtos.MeetupSummary;
import com.anam3.meetup.MeetupDtos.PersonView;
import com.anam3.member.Member;

/**
 * 3교시를 화면용 응답으로 바꾸면서 <b>누가 누구 이름을 볼 수 있는지</b>를 결정한다.
 *
 * <h3>공개 규칙 (민망함 방지)</h3>
 * <ul>
 *   <li>관리자가 볼 때: 모두 실명</li>
 *   <li>성사(최소 인원 달성)된 3교시의 참여자: 서로 실명 + 오픈채팅 링크.
 *       최소 인원이 없는(무조건 진행) 3교시는 열자마자 성사라서 참여하면 바로 실명</li>
 *   <li>그 외 모두 (성사 전, 미참여자, 무산된 3교시): "김*수 · 61기 · 인융" 처럼 가운데 마스킹</li>
 *   <li>내 이름은 나에게 항상 그대로</li>
 *   <li>관리자 본인의 이름은 누구에게나 그대로 ("관리자" 표시와 함께)</li>
 * </ul>
 * 실명은 서버에서 가려서 내려보낸다. 화면(JS)에서 가리면 개발자도구로 다 보이기 때문.
 */
@Component
public class MeetupMapper {

    public static boolean canSeeRealNames(Meetup meetup, LoginMember viewer, boolean joined) {
        if (viewer.isAdmin()) {
            return true;
        }
        return joined && meetup.isReached() && !meetup.isCanceled();
    }

    public MeetupSummary toSummary(Meetup m, LoginMember viewer, boolean joined, Instant now) {
        boolean revealed = canSeeRealNames(m, viewer, joined);
        return new MeetupSummary(
                m.getId(), m.getTitle(), m.getPlace(), m.getCategories(), m.getPurposes(),
                m.getMeetAt(), m.getDeadlineAt(), m.getMinPeople(), m.getMaxPeople(), m.getParticipantCount(),
                m.statusAt(now), m.isJoinableAt(now), joined, m.isHostedBy(viewer.id()),
                person(m.getHost(), viewer, revealed, true));
    }

    public MeetupDetail toDetail(Meetup m, List<Participation> participations, LoginMember viewer, Instant now) {
        boolean joined = participations.stream().anyMatch(p -> p.getMember().getId().equals(viewer.id()));
        boolean hosting = m.isHostedBy(viewer.id());
        boolean revealed = canSeeRealNames(m, viewer, joined);
        boolean manageable = (hosting || viewer.isAdmin()) && m.isOpenAt(now);
        boolean hasOpenChat = m.getOpenChatUrl() != null && !m.getOpenChatUrl().isBlank();
        boolean canSeeOpenChat = viewer.isAdmin() || hosting || revealed;

        List<PersonView> people = participations.stream()
                .map(p -> person(p.getMember(), viewer, revealed, m.isHostedBy(p.getMember().getId())))
                .toList();

        return new MeetupDetail(
                m.getId(), m.getTitle(), m.getDescription(), m.getPlace(), m.getCategories(), m.getPurposes(),
                m.getMeetAt(), m.getDeadlineAt(), m.getMinPeople(), m.getMaxPeople(), m.getParticipantCount(),
                m.statusAt(now), m.isJoinableAt(now), joined, hosting,
                person(m.getHost(), viewer, revealed, true),
                people,
                revealed,
                canSeeOpenChat && hasOpenChat ? m.getOpenChatUrl() : null,
                hasOpenChat,
                manageable,
                manageable,
                !joined && m.isJoinableAt(now),
                joined && !hosting && m.isRecruitingAt(now),
                m.getCreatedAt());
    }

    private PersonView person(Member member, LoginMember viewer, boolean revealed, boolean host) {
        boolean me = member.getId().equals(viewer.id());
        boolean admin = member.isAdmin();
        boolean showReal = revealed || me || admin;
        String name = showReal ? member.getName() : NameMasker.mask(member.getName());
        return new PersonView(name, member.cohortLabel(), member.departmentShortLabel(), me, host, admin, !showReal);
    }
}
