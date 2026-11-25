package com.anam3.meetup;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.anam3.auth.LoginMember;
import com.anam3.common.ApiException;
import com.anam3.common.ContentFilter;
import com.anam3.feedback.FeedbackService;
import com.anam3.meetup.MeetupDtos.MeetupCreateRequest;
import com.anam3.meetup.MeetupDtos.MeetupDetail;
import com.anam3.meetup.MeetupDtos.MeetupSummary;
import com.anam3.meetup.MeetupDtos.MeetupUpdateRequest;
import com.anam3.meetup.MeetupMapper.WaitInfo;
import com.anam3.meetup.MeetupDtos.OptionsChange;
import com.anam3.meetup.MeetupDtos.PeopleChange;
import com.anam3.meetup.MeetupDtos.ScheduleChange;
import com.anam3.member.Member;
import com.anam3.member.MemberRepository;
import com.anam3.push.PushNotifier;
import com.anam3.safety.MemberBlockRepository;
import com.anam3.safety.ReportRepository;

@Service
@Transactional
public class MeetupService {

    private static final Duration MAX_AHEAD = Duration.ofDays(31);
    private static final int LIST_LIMIT = 100;
    /** 최대 인원 상한 (MeetupCreateRequest 와 같다) */
    public static final int MAX_SEATS = 30;
    /** 장소 추천: 최근 이만큼 동안 열린 3교시에서, 이만큼까지 */
    private static final Duration PLACE_WINDOW = Duration.ofDays(180);
    private static final int PLACE_LIMIT = 8;

    private final MeetupRepository meetups;
    private final ParticipationRepository participations;
    private final MemberRepository members;
    private final MemberBlockRepository blocks;
    private final ReportRepository reports;
    private final MeetupMapper mapper;
    private final PushNotifier notifier;
    private final FeedbackService feedback;
    private final MeetupWaitRepository waits;
    private final Clock clock;

    public MeetupService(MeetupRepository meetups, ParticipationRepository participations, MemberRepository members,
            MemberBlockRepository blocks, ReportRepository reports, MeetupMapper mapper, PushNotifier notifier,
            FeedbackService feedback, MeetupWaitRepository waits, Clock clock) {
        this.meetups = meetups;
        this.participations = participations;
        this.members = members;
        this.blocks = blocks;
        this.reports = reports;
        this.mapper = mapper;
        this.notifier = notifier;
        this.feedback = feedback;
        this.waits = waits;
        this.clock = clock;
    }

    public MeetupDetail create(LoginMember viewer, MeetupCreateRequest req) {
        Instant now = clock.instant();
        checkTimes(req.meetAt(), req.deadlineAt(), now);
        if (req.minPeople() != null && req.maxPeople() != null && req.maxPeople() < req.minPeople()) {
            throw ApiException.badRequest("INVALID_PEOPLE", "최대 인원은 최소 인원보다 같거나 많아야 해요");
        }
        checkFee(req.fee(), req.feePrepaid());
        List<String> purposes = purposes(req.purposes());
        ContentFilter.check(req.title(), req.description(), req.place(), String.join(" ", purposes));

        Member host = loadMember(viewer);
        MeetupAudience audience = req.audience() == null ? MeetupAudience.SCHOOL : req.audience();
        Meetup meetup = meetups.save(new Meetup(host, audience, req.title().strip(), clean(req.description()),
                req.place().strip(), req.categories(), purposes, req.meetAt(), req.deadlineAt(), req.minPeople(),
                req.maxPeople(), blankToNull(req.openChatUrl()), now));
        meetup.applyOptions(Boolean.TRUE.equals(req.lateJoin()), req.fee(), Boolean.TRUE.equals(req.feePrepaid()));
        participations.save(new Participation(meetup, host, now));
        notifier.opened(meetup); // 연합이면 다섯 학교, 아니면 우리 학교(우리 기수만이면 그 기수)
        return detail(viewer, meetup.getId());
    }

    /**
     * scope = upcoming(다가오는 3교시, 무산 제외) | past(성사되어 실제로 모였던 3교시).
     * board = school(우리 학교, 기본) | all(연합: 다섯 학교의 연합 3교시).
     * 우리 학교 게시판에서 다른 기수 전용 3교시는 보이지 않는다 (그 학교 교수·대학원장은 모두 본다).
     * 나와 차단 관계인 사람이 연 3교시는 빠진다.
     */
    @Transactional(readOnly = true)
    public List<MeetupSummary> list(LoginMember viewer, String scope, String board) {
        Instant now = clock.instant();
        boolean federated = "all".equals(board);
        boolean allCohorts = viewer.isStaff();
        Set<Long> hidden = hiddenHostIds(viewer);
        List<Meetup> found = "past".equals(scope)
                ? meetups.findPastConfirmed(now, federated, viewer.schoolId(), viewer.cohort(), allCohorts,
                        PageRequest.of(0, 30))
                : meetups.findUpcoming(now, federated, viewer.schoolId(), viewer.cohort(), allCohorts,
                        PageRequest.of(0, LIST_LIMIT)).stream()
                        .filter(m -> m.statusAt(now) != MeetupStatus.FAILED) // 무산된 3교시는 조용히 숨긴다
                        .toList();
        return toSummaries(viewer, found.stream().filter(m -> !hidden.contains(m.getHost().getId())).toList(), now);
    }

    /** 내가 열었거나 참여한 3교시 (무산/취소 포함 — 본인에게만 보인다) */
    @Transactional(readOnly = true)
    public List<MeetupSummary> mine(LoginMember viewer) {
        Instant now = clock.instant();
        List<Meetup> found = participations.findMeetupsOfMember(viewer.id(), PageRequest.of(0, LIST_LIMIT));
        return found.stream().map(m -> mapper.toSummary(m, viewer, true, now)).toList();
    }

    /** 관리 화면: 대학원장은 모든 학교, 교수는 자기 학교 3교시 */
    @Transactional(readOnly = true)
    public List<MeetupSummary> all(LoginMember viewer) {
        Instant now = clock.instant();
        List<Meetup> found = viewer.isOwner()
                ? meetups.findAllWithHost(PageRequest.of(0, 200))
                : meetups.findAllWithHost(viewer.schoolId(), PageRequest.of(0, 200));
        return toSummaries(viewer, found, now);
    }

    @Transactional(readOnly = true)
    public MeetupDetail detail(LoginMember viewer, Long id) {
        return detail(viewer, id, true);
    }

    /** checkVisible=false: 방금까지 참여자였던 사람(나가기 응답) — 그사이 기수·학교가 바뀌었어도 나갈 수 있게 */
    private MeetupDetail detail(LoginMember viewer, Long id, boolean checkVisible) {
        Meetup meetup = meetups.findWithHost(id).orElseThrow(MeetupService::notFound);
        List<Participation> people = participations.findWithMember(id);
        boolean joined = people.stream().anyMatch(p -> p.getMember().getId().equals(viewer.id()));
        // 다른 학교·다른 기수 전용: 단톡방 링크로 들어와도 내용은 보여주지 않고 안내만 한다
        if (checkVisible && !joined && !canSee(meetup, viewer)) {
            throw closedTo(meetup);
        }
        boolean blocked = !meetup.isHostedBy(viewer.id()) && blocks.existsBetween(viewer.id(), meetup.getHost().getId());
        Instant now = clock.instant();
        boolean manages = meetup.isHostedBy(viewer.id()) || viewer.isOwner();
        WaitInfo wait = new WaitInfo(!joined && waits.existsByMeetupIdAndMemberId(id, viewer.id()),
                manages ? waits.countByMeetupId(id) : 0);
        return mapper.toDetail(meetup, people, viewer, blocked, feedback.prompt(meetup, viewer, joined, now), wait,
                now);
    }

    /** 수정은 개설자와 대학원장만 */
    public MeetupDetail update(LoginMember viewer, Long id, MeetupUpdateRequest req) {
        // 인원을 바꿀 수 있으니 참여·나가기와 겹치지 않게 잠그고 읽는다
        Meetup meetup = meetups.findByIdForUpdate(id).orElseThrow(MeetupService::notFound);
        if (!viewer.isOwner() && !meetup.isHostedBy(viewer.id())) {
            throw ApiException.forbidden("3교시를 연 사람만 고칠 수 있어요");
        }
        checkOpen(meetup);
        List<String> purposes = purposes(req.purposes());
        ContentFilter.check(req.title(), req.description(), req.place(), String.join(" ", purposes));
        Instant now = clock.instant();
        Instant meetAtBefore = meetup.getMeetAt();
        String placeBefore = meetup.getPlace();
        boolean reachedBefore = meetup.isReached();
        boolean fullBefore = meetup.isFull();
        if (req.schedule() != null) {
            changeSchedule(meetup, req.schedule(), now);
        }
        if (req.audience() != null && req.audience() != meetup.audience()) {
            changeAudience(meetup, req.audience(), now);
        }
        if (req.options() != null) {
            OptionsChange o = req.options();
            checkFee(o.fee(), o.feePrepaid());
            meetup.applyOptions(Boolean.TRUE.equals(o.lateJoin()), o.fee(), Boolean.TRUE.equals(o.feePrepaid()));
        }
        if (req.people() != null) {
            changePeople(meetup, req.people(), now);
        }
        meetup.update(req.title().strip(), clean(req.description()), req.place().strip(), req.categories(),
                purposes, blankToNull(req.openChatUrl()), now);
        notifier.changed(meetup, viewer.id(), !meetAtBefore.equals(meetup.getMeetAt()),
                !placeBefore.equals(meetup.getPlace()));
        if (!reachedBefore && meetup.isReached()) {
            notifier.reached(meetup, viewer.id()); // 최소 인원을 낮춰서 바로 성사
        }
        notifyIfSpotOpened(meetup, fullBefore, now);
        return detail(viewer, id);
    }

    /**
     * 정원 늘리기: 빈자리를 기다리는 사람을 태우려고 연 사람(·대학원장)이 누른다. 늘리기만 한다.
     * 자리가 나면 기다리던 사람에게 알린다
     */
    public MeetupDetail addSeats(LoginMember viewer, Long id, int maxPeople) {
        Meetup meetup = meetups.findByIdForUpdate(id).orElseThrow(MeetupService::notFound);
        if (!viewer.isOwner() && !meetup.isHostedBy(viewer.id())) {
            throw ApiException.forbidden("3교시를 연 사람만 정원을 늘릴 수 있어요");
        }
        checkOpen(meetup);
        if (meetup.getMaxPeople() == null) {
            throw ApiException.badRequest("UNLIMITED", "인원 제한이 없는 3교시예요");
        }
        if (maxPeople <= meetup.getMaxPeople()) {
            throw ApiException.badRequest("SEATS_NOT_MORE",
                    "지금 정원(" + meetup.getMaxPeople() + "명)보다 많게 정해 주세요");
        }
        Instant now = clock.instant();
        boolean fullBefore = meetup.isFull();
        meetup.changePeople(meetup.getMinPeople(), maxPeople, now);
        notifyIfSpotOpened(meetup, fullBefore, now);
        return detail(viewer, id);
    }

    /**
     * 빈자리 대기 신청: 정원이 찬 3교시에 "자리 나면 알림". 참여할 수 있는 사람만 (학교·기수·차단·마감 규칙은 참여와 같다).
     * 이미 신청했으면 그대로 둔다
     */
    public MeetupDetail waitForSeat(LoginMember viewer, Long id) {
        Instant now = clock.instant();
        Meetup meetup = meetups.findByIdForUpdate(id).orElseThrow(MeetupService::notFound);
        if (participations.existsByMeetupIdAndMemberId(id, viewer.id())) {
            throw ApiException.conflict("ALREADY_JOINED", "이미 참여 중인 3교시예요");
        }
        checkJoinRules(viewer, meetup, now);
        if (!meetup.isFull()) {
            throw ApiException.badRequest("NOT_FULL", "자리가 있어요. 바로 참여해 주세요");
        }
        if (!waits.existsByMeetupIdAndMemberId(id, viewer.id())) {
            waits.save(new MeetupWait(meetup, members.getReferenceById(viewer.id()), now));
        }
        return detail(viewer, id);
    }

    /** 빈자리 대기 취소 (신청하지 않았어도 그대로 성공) */
    public MeetupDetail cancelWait(LoginMember viewer, Long id) {
        meetups.findById(id).orElseThrow(MeetupService::notFound);
        waits.deleteOne(id, viewer.id());
        return detail(viewer, id);
    }

    /** 우리 학교 3교시에서 많이 쓴 장소 (3교시 열기 화면의 추천) */
    @Transactional(readOnly = true)
    public List<String> popularPlaces(LoginMember viewer) {
        Instant since = clock.instant().minus(PLACE_WINDOW);
        return meetups.findPopularPlaces(viewer.schoolId(), viewer.cohort(), since, PageRequest.of(0, PLACE_LIMIT));
    }

    /** 꽉 찼던 3교시에 자리가 났으면 기다리던 사람 모두에게 알린다 (먼저 참여한 사람이 탄다) */
    private void notifyIfSpotOpened(Meetup meetup, boolean fullBefore, Instant now) {
        if (fullBefore && meetup.isJoinableAt(now)) {
            notifier.spotOpened(meetup, waits.findMemberIds(meetup.getId()));
        }
    }

    /** 열 때와 같은 시간 규칙: 모임은 지금 이후 한 달 이내, 마감은 지금 이후이고 모임 시간보다 늦지 않게 */
    private static void checkTimes(Instant meetAt, Instant deadlineAt, Instant now) {
        if (!meetAt.isAfter(now)) {
            throw ApiException.badRequest("INVALID_TIME", "모임 시간은 지금 이후로 정해 주세요");
        }
        if (meetAt.isAfter(now.plus(MAX_AHEAD))) {
            throw ApiException.badRequest("INVALID_TIME", "3교시는 한 달 이내 일정만 열 수 있어요");
        }
        if (!deadlineAt.isAfter(now)) {
            throw ApiException.badRequest("INVALID_DEADLINE", "모집 마감은 지금 이후로 정해 주세요");
        }
        if (deadlineAt.isAfter(meetAt)) {
            throw ApiException.badRequest("INVALID_DEADLINE", "모집 마감은 모임 시간보다 늦을 수 없어요");
        }
    }

    private static void checkFee(Integer fee, Boolean feePrepaid) {
        if (fee == null && Boolean.TRUE.equals(feePrepaid)) {
            throw ApiException.badRequest("INVALID_FEE", "선입금은 참여비가 있을 때만 고를 수 있어요");
        }
    }

    /** 시간 바꾸기. 그대로면 검사하지 않는다 (마감이 지난 성사 3교시도 제목·장소는 고칠 수 있게) */
    private static void changeSchedule(Meetup meetup, ScheduleChange schedule, Instant now) {
        if (schedule.meetAt().equals(meetup.getMeetAt()) && schedule.deadlineAt().equals(meetup.getDeadlineAt())) {
            return;
        }
        checkTimes(schedule.meetAt(), schedule.deadlineAt(), now);
        meetup.reschedule(schedule.meetAt(), schedule.deadlineAt(), now);
    }

    /** 대상 바꾸기: 넓히기는 언제나, 좁히기는 이미 참여한 원우가 모두 새 대상에 들 때만 */
    private void changeAudience(Meetup meetup, MeetupAudience audience, Instant now) {
        for (Participation p : participations.findWithMember(meetup.getId())) {
            Member m = p.getMember();
            if (!meetup.wouldBeOpenTo(audience, m.getSchool().getId(), m.getCohort())) {
                String who = meetup.isOfSchool(m.getSchool().getId()) ? "다른 기수" : "다른 학교";
                String to = audience == MeetupAudience.COHORT ? "'우리 기수만'으로" : "'우리 학교'로";
                throw ApiException.badRequest("AUDIENCE_NARROWER", who + " 원우가 이미 참여해서 " + to + " 바꿀 수 없어요");
            }
        }
        meetup.changeAudience(audience, now);
    }

    /**
     * 인원 바꾸기: 이미 참여한 사람보다 최대 인원을 적게 줄일 수 없고(내보내지 않는다),
     * 성사된 3교시는 최소 인원을 지금 인원보다 올릴 수 없다(공개된 실명이 다시 가려지지 않게).
     * 최소 인원을 낮추면 바로 성사될 수 있다.
     */
    private void changePeople(Meetup meetup, PeopleChange people, Instant now) {
        Integer min = people.minPeople();
        Integer max = people.maxPeople();
        if (min != null && max != null && max < min) {
            throw ApiException.badRequest("INVALID_PEOPLE", "최대 인원은 최소 인원보다 같거나 많아야 해요");
        }
        int joined = meetup.getParticipantCount();
        if (max != null && max < joined) {
            throw ApiException.badRequest("PEOPLE_BELOW_JOINED",
                    "이미 " + joined + "명이 참여했어요. 최대 인원을 " + joined + "명보다 적게 줄일 수 없어요");
        }
        if (meetup.isReached() && min != null && min > joined) {
            throw ApiException.badRequest("ALREADY_REACHED",
                    "이미 성사된 3교시예요. 최소 인원을 지금 인원(" + joined + "명)보다 늘릴 수 없어요");
        }
        meetup.changePeople(min, max, now);
    }

    /** 취소·삭제할 수 있는지: 개설자, 대학원장, 그 학교 교수 (대학원장이 연 3교시는 대학원장만) */
    public static boolean canModerate(Meetup meetup, LoginMember viewer) {
        if (meetup.isHostedBy(viewer.id()) || viewer.isOwner()) {
            return true;
        }
        return !meetup.getHost().isOwner() && viewer.manages(meetup.getSchool().getId());
    }

    /** 취소는 개설자, 그 학교 교수, 대학원장 */
    public MeetupDetail cancel(LoginMember viewer, Long id) {
        Meetup meetup = meetups.findByIdForUpdate(id).orElseThrow(MeetupService::notFound);
        if (!canModerate(meetup, viewer)) {
            throw ApiException.forbidden("3교시를 연 사람만 취소할 수 있어요");
        }
        checkOpen(meetup);
        meetup.cancel(clock.instant());
        notifier.canceled(meetup, participations.findMemberIds(id), viewer.id(), false);
        return detail(viewer, id);
    }

    /**
     * 지우기는 개설자, 그 학교 교수, 대학원장 (취소와 달리 목록·기록에서 아예 사라진다).
     * 참여 기록도 함께 지우고, 신고는 남긴다 (신고 당시 제목·내용이 신고에 보관돼 있다).
     */
    public void delete(LoginMember viewer, Long id) {
        Meetup meetup = meetups.findByIdForUpdate(id).orElseThrow(MeetupService::notFound);
        if (!canModerate(meetup, viewer)) {
            throw ApiException.forbidden("3교시를 연 사람만 지울 수 있어요");
        }
        // 아직 열려 있던 3교시면 참여자에게 취소를 알린다 (지우면 참여 기록이 사라지니 먼저 읽어 둔다)
        List<Long> people = meetup.isOpenAt(clock.instant()) ? participations.findMemberIds(id) : List.of();
        reports.detachMeetup(id);
        feedback.deleteFor(id);
        waits.deleteByMeetupId(id);
        participations.deleteByMeetupId(id);
        meetups.delete(meetup);
        if (!people.isEmpty()) {
            notifier.canceled(meetup, people, viewer.id(), true);
        }
    }

    /**
     * 지금 인원으로 확정: 최소 인원이 안 찼어도 연 사람이 "이 인원으로 간다"고 정하면 바로 성사.
     * 마감이 지나 무산된 3교시도 모임 시간 전이면 되살린다. 수정과 같은 사람(연 사람 · 대학원장)만.
     */
    public MeetupDetail confirm(LoginMember viewer, Long id) {
        Instant now = clock.instant();
        Meetup meetup = meetups.findByIdForUpdate(id).orElseThrow(MeetupService::notFound);
        if (!viewer.isOwner() && !meetup.isHostedBy(viewer.id())) {
            throw ApiException.forbidden("3교시를 연 사람만 확정할 수 있어요");
        }
        if (meetup.isCanceled() || !now.isBefore(meetup.getMeetAt())) {
            throw ApiException.badRequest("NOT_OPEN", "이미 끝났거나 취소된 3교시예요");
        }
        if (meetup.isReached()) {
            throw ApiException.conflict("ALREADY_REACHED", "이미 성사된 3교시예요");
        }
        if (!meetup.isConfirmableAt(now)) {
            throw ApiException.badRequest("TOO_FEW", "나 말고 한 명은 더 있어야 확정할 수 있어요");
        }
        boolean revived = meetup.statusAt(now) == MeetupStatus.FAILED;
        meetup.confirmWithCurrent(now);
        notifier.confirmedByHost(meetup, viewer.id(), revived);
        return detail(viewer, id);
    }

    public MeetupDetail join(LoginMember viewer, Long id) {
        Instant now = clock.instant();
        Meetup meetup = meetups.findByIdForUpdate(id).orElseThrow(MeetupService::notFound);
        if (participations.existsByMeetupIdAndMemberId(id, viewer.id())) {
            throw ApiException.conflict("ALREADY_JOINED", "이미 참여 중인 3교시예요");
        }
        checkJoinRules(viewer, meetup, now);
        if (!meetup.isJoinableAt(now)) {
            throw ApiException.badRequest("FULL", "정원이 다 찼어요. 자리 나면 알림을 신청해 보세요");
        }
        boolean reachedBefore = meetup.isReached();
        participations.save(new Participation(meetup, members.getReferenceById(viewer.id()), now));
        meetup.addParticipant();
        waits.deleteOne(id, viewer.id()); // 기다리던 사람이 탔다
        notifier.joined(meetup, viewer.id(), !reachedBefore && meetup.isReached());
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
            throw ApiException.badRequest("CLOSED", "모집이 끝난 뒤에는 나갈 수 없어요. 단톡방에서 알려 주세요");
        }
        boolean fullBefore = meetup.isFull();
        participations.delete(participation);
        meetup.removeParticipant();
        notifier.left(meetup);
        notifyIfSpotOpened(meetup, fullBefore, now);
        return detail(viewer, id, false);
    }

    /** 참여·빈자리 대기 공통: 학교·기수, 취소, 차단, 마감 */
    private void checkJoinRules(LoginMember viewer, Meetup meetup, Instant now) {
        if (!meetup.isOpenTo(viewer.schoolId(), viewer.cohort())) {
            throw closedTo(meetup);
        }
        if (meetup.isCanceled()) {
            throw ApiException.badRequest("CANCELED", "취소된 3교시예요");
        }
        if (blocks.existsBetween(viewer.id(), meetup.getHost().getId())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "BLOCKED", "참여할 수 없는 3교시예요");
        }
        if (!meetup.isTakingJoinsAt(now)) {
            throw ApiException.badRequest("CLOSED", "모집이 마감된 3교시예요");
        }
    }

    /**
     * 볼 수 있는 3교시인지: 연합이면 누구나. 아니면 같은 학교 + (우리 기수 전용이면 같은 기수, 그 학교 교수는 모두).
     * 대학원장은 모든 3교시를 본다. 신고(ReportService)도 이 규칙을 쓴다.
     */
    public static boolean canSee(Meetup meetup, LoginMember viewer) {
        if (viewer.isOwner() || meetup.isFederated()) {
            return true;
        }
        if (!meetup.isOfSchool(viewer.schoolId())) {
            return false;
        }
        return viewer.isStaff() || meetup.isOpenToCohort(viewer.cohort());
    }

    /** 나와 차단 관계(내가 차단했거나 나를 차단한)인 사람들 */
    private Set<Long> hiddenHostIds(LoginMember viewer) {
        Set<Long> hidden = new HashSet<>(blocks.findBlockedIds(viewer.id()));
        hidden.addAll(blocks.findBlockerIds(viewer.id()));
        return hidden;
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

    private void checkOpen(Meetup meetup) {
        if (!meetup.isOpenAt(clock.instant())) {
            throw ApiException.badRequest("NOT_OPEN", "이미 끝났거나 취소된 3교시예요");
        }
    }

    /** 다른 학교 전용이면 SCHOOL_ONLY, 같은 학교의 다른 기수 전용이면 COHORT_ONLY */
    private static ApiException closedTo(Meetup meetup) {
        if (meetup.getOnlyCohort() != null) {
            return new ApiException(HttpStatus.FORBIDDEN, "COHORT_ONLY",
                    meetup.getSchool().getShortName() + " " + meetup.getOnlyCohort() + "기 전용 3교시예요");
        }
        return new ApiException(HttpStatus.FORBIDDEN, "SCHOOL_ONLY",
                meetup.getSchool().getShortName() + " 원우만 참여하는 3교시예요");
    }

    private Member loadMember(LoginMember viewer) {
        return members.findWithSchool(viewer.id())
                .orElseThrow(() -> ApiException.notFound("회원 정보를 찾을 수 없어요"));
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
