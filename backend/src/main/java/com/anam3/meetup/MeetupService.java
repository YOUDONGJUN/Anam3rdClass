package com.anam3.meetup;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.anam3.auth.LoginMember;
import com.anam3.common.ApiException;
import com.anam3.meetup.MeetupDtos.MeetupCreateRequest;
import com.anam3.meetup.MeetupDtos.MeetupDetail;
import com.anam3.meetup.MeetupDtos.MeetupSummary;
import com.anam3.meetup.MeetupDtos.MeetupUpdateRequest;
import com.anam3.member.Member;
import com.anam3.member.MemberRepository;

@Service
@Transactional
public class MeetupService {

    private static final Duration MAX_AHEAD = Duration.ofDays(31);
    private static final int LIST_LIMIT = 100;

    private final MeetupRepository meetups;
    private final ParticipationRepository participations;
    private final MemberRepository members;
    private final MeetupMapper mapper;
    private final Clock clock;

    public MeetupService(MeetupRepository meetups, ParticipationRepository participations, MemberRepository members,
            MeetupMapper mapper, Clock clock) {
        this.meetups = meetups;
        this.participations = participations;
        this.members = members;
        this.mapper = mapper;
        this.clock = clock;
    }

    public MeetupDetail create(LoginMember viewer, MeetupCreateRequest req) {
        Instant now = clock.instant();
        if (!req.meetAt().isAfter(now)) {
            throw ApiException.badRequest("INVALID_TIME", "모임 시간은 지금 이후로 정해 주세요");
        }
        if (req.meetAt().isAfter(now.plus(MAX_AHEAD))) {
            throw ApiException.badRequest("INVALID_TIME", "3교시는 한 달 이내 일정만 열 수 있어요");
        }
        if (!req.deadlineAt().isAfter(now)) {
            throw ApiException.badRequest("INVALID_DEADLINE", "모집 마감은 지금 이후로 정해 주세요");
        }
        if (req.deadlineAt().isAfter(req.meetAt())) {
            throw ApiException.badRequest("INVALID_DEADLINE", "모집 마감은 모임 시간보다 늦을 수 없어요");
        }
        if (req.minPeople() != null && req.maxPeople() != null && req.maxPeople() < req.minPeople()) {
            throw ApiException.badRequest("INVALID_PEOPLE", "최대 인원은 최소 인원보다 같거나 많아야 해요");
        }

        Member host = loadMember(viewer);
        Meetup meetup = meetups.save(new Meetup(host, req.title().strip(), clean(req.description()),
                req.place().strip(), req.categories(), purposes(req.purposes()), req.meetAt(), req.deadlineAt(),
                req.minPeople(), req.maxPeople(), blankToNull(req.openChatUrl()), now));
        participations.save(new Participation(meetup, host, now));
        return detail(viewer, meetup.getId());
    }

    /** scope = upcoming(다가오는 3교시, 무산 제외) | past(성사되어 실제로 모였던 3교시) */
    @Transactional(readOnly = true)
    public List<MeetupSummary> list(LoginMember viewer, String scope) {
        Instant now = clock.instant();
        List<Meetup> found = "past".equals(scope)
                ? meetups.findPastConfirmed(now, PageRequest.of(0, 30))
                : meetups.findUpcoming(now, PageRequest.of(0, LIST_LIMIT)).stream()
                        .filter(m -> m.statusAt(now) != MeetupStatus.FAILED) // 무산된 3교시는 조용히 숨긴다
                        .toList();
        return toSummaries(viewer, found, now);
    }

    /** 내가 열었거나 참여한 3교시 (무산/취소 포함 — 본인에게만 보인다) */
    @Transactional(readOnly = true)
    public List<MeetupSummary> mine(LoginMember viewer) {
        Instant now = clock.instant();
        List<Meetup> found = participations.findMeetupsOfMember(viewer.id(), PageRequest.of(0, LIST_LIMIT));
        return found.stream().map(m -> mapper.toSummary(m, viewer, true, now)).toList();
    }

    /** 관리자용 전체 목록 */
    @Transactional(readOnly = true)
    public List<MeetupSummary> all(LoginMember viewer) {
        Instant now = clock.instant();
        return toSummaries(viewer, meetups.findAllWithHost(PageRequest.of(0, 200)), now);
    }

    @Transactional(readOnly = true)
    public MeetupDetail detail(LoginMember viewer, Long id) {
        Meetup meetup = meetups.findWithHost(id).orElseThrow(MeetupService::notFound);
        return mapper.toDetail(meetup, participations.findWithMember(id), viewer, clock.instant());
    }

    public MeetupDetail update(LoginMember viewer, Long id, MeetupUpdateRequest req) {
        Meetup meetup = meetups.findWithHost(id).orElseThrow(MeetupService::notFound);
        checkManageable(viewer, meetup);
        meetup.update(req.title().strip(), clean(req.description()), req.place().strip(), req.categories(),
                purposes(req.purposes()), blankToNull(req.openChatUrl()), clock.instant());
        return detail(viewer, id);
    }

    public MeetupDetail cancel(LoginMember viewer, Long id) {
        Meetup meetup = meetups.findByIdForUpdate(id).orElseThrow(MeetupService::notFound);
        checkManageable(viewer, meetup);
        meetup.cancel(clock.instant());
        return detail(viewer, id);
    }

    public MeetupDetail join(LoginMember viewer, Long id) {
        Instant now = clock.instant();
        Meetup meetup = meetups.findByIdForUpdate(id).orElseThrow(MeetupService::notFound);
        if (participations.existsByMeetupIdAndMemberId(id, viewer.id())) {
            throw ApiException.conflict("ALREADY_JOINED", "이미 참여 중인 3교시예요");
        }
        if (meetup.isCanceled()) {
            throw ApiException.badRequest("CANCELED", "취소된 3교시예요");
        }
        if (!meetup.isRecruitingAt(now)) {
            throw ApiException.badRequest("CLOSED", "모집이 마감된 3교시예요");
        }
        if (!meetup.isJoinableAt(now)) {
            throw ApiException.badRequest("FULL", "정원이 다 찼어요");
        }
        participations.save(new Participation(meetup, members.getReferenceById(viewer.id()), now));
        meetup.addParticipant();
        return detail(viewer, id);
    }

    public MeetupDetail leave(LoginMember viewer, Long id) {
        Instant now = clock.instant();
        Meetup meetup = meetups.findByIdForUpdate(id).orElseThrow(MeetupService::notFound);
        if (meetup.isHostedBy(viewer.id())) {
            throw ApiException.badRequest("HOST_CANNOT_LEAVE", "3교시를 연 사람은 나갈 수 없어요. 대신 3교시를 취소해 주세요");
        }
        Participation participation = participations.findByMeetupIdAndMemberId(id, viewer.id())
                .orElseThrow(() -> ApiException.badRequest("NOT_JOINED", "참여하지 않은 3교시예요"));
        if (!meetup.isRecruitingAt(now)) {
            throw ApiException.badRequest("CLOSED", "모집이 끝난 뒤에는 나갈 수 없어요. 오픈채팅에서 알려 주세요");
        }
        participations.delete(participation);
        meetup.removeParticipant();
        return detail(viewer, id);
    }

    private List<MeetupSummary> toSummaries(LoginMember viewer, List<Meetup> found, Instant now) {
        if (found.isEmpty()) {
            return List.of();
        }
        Set<Long> joinedIds = participations.findJoinedMeetupIds(viewer.id(),
                found.stream().map(Meetup::getId).toList());
        return found.stream()
                .map(m -> mapper.toSummary(m, viewer, joinedIds.contains(m.getId()), now))
                .toList();
    }

    private void checkManageable(LoginMember viewer, Meetup meetup) {
        if (!viewer.isAdmin() && !meetup.isHostedBy(viewer.id())) {
            throw ApiException.forbidden("3교시를 연 사람만 할 수 있어요");
        }
        if (!meetup.isOpenAt(clock.instant())) {
            throw ApiException.badRequest("NOT_OPEN", "이미 끝났거나 취소된 3교시예요");
        }
    }

    private Member loadMember(LoginMember viewer) {
        return members.findById(viewer.id()).orElseThrow(() -> ApiException.notFound("회원 정보를 찾을 수 없어요"));
    }

    private static ApiException notFound() {
        return ApiException.notFound("3교시를 찾을 수 없어요");
    }

    /** 앞뒤 공백을 지우고, 빈 값과 중복을 빼고, 고른 순서는 그대로 둔다 */
    private static List<String> purposes(List<String> raw) {
        if (raw == null) {
            return List.of();
        }
        Set<String> cleaned = new LinkedHashSet<>();
        for (String p : raw) {
            if (p != null && !p.isBlank()) {
                cleaned.add(p.strip());
            }
        }
        return List.copyOf(cleaned);
    }

    private static String clean(String s) {
        return s == null ? "" : s.strip();
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.strip();
    }
}
