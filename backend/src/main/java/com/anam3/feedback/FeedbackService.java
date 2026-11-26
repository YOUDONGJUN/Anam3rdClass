package com.anam3.feedback;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.anam3.auth.LoginMember;
import com.anam3.common.ApiException;
import com.anam3.feedback.FeedbackDtos.FeedbackPending;
import com.anam3.feedback.FeedbackDtos.FeedbackPrompt;
import com.anam3.feedback.FeedbackDtos.FeedbackRequest;
import com.anam3.feedback.FeedbackDtos.FeedbackStatsView;
import com.anam3.feedback.FeedbackDtos.StatsGroup;
import com.anam3.meetup.Meetup;
import com.anam3.meetup.MeetupAudience;
import com.anam3.meetup.MeetupRepository;
import com.anam3.meetup.ParticipationRepository;
import com.anam3.member.MemberRepository;
import com.anam3.safety.ReportRepository;
import com.anam3.school.School;
import com.anam3.school.SchoolDtos.SchoolTag;
import com.anam3.school.SchoolRepository;

/**
 * 3교시 후기. 성사된 3교시가 끝난 다음 날(한국 시간 0시)부터 7일 동안 참여자(연 사람 포함)에게 한 번 묻는다.
 * 알림은 보내지 않고, 앱을 열 때 화면이 묻는다. 결과는 대학원장 통계에 학교별 숫자로만 쓴다.
 */
@Service
@Transactional
public class FeedbackService {

    static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    /** 후기를 받는 기간 (모임 다음 날부터) */
    static final int ASK_DAYS = 7;
    /** 묶음마다 답이 이보다 적으면 숫자를 감춘다 */
    static final int MIN_ANSWERS = 5;

    private final MeetupFeedbackRepository feedbacks;
    private final MeetupRepository meetups;
    private final ParticipationRepository participations;
    private final MemberRepository members;
    private final SchoolRepository schools;
    private final ReportRepository reports;
    private final Clock clock;

    public FeedbackService(MeetupFeedbackRepository feedbacks, MeetupRepository meetups,
            ParticipationRepository participations, MemberRepository members, SchoolRepository schools,
            ReportRepository reports, Clock clock) {
        this.feedbacks = feedbacks;
        this.meetups = meetups;
        this.participations = participations;
        this.members = members;
        this.schools = schools;
        this.reports = reports;
        this.clock = clock;
    }

    /** 모임 다음 날 0시 (한국 시간) */
    static Instant opensAt(Meetup meetup) {
        return meetup.getMeetAt().atZone(SEOUL).toLocalDate().plusDays(1).atStartOfDay(SEOUL).toInstant();
    }

    static Instant closesAt(Meetup meetup) {
        return opensAt(meetup).plus(Duration.ofDays(ASK_DAYS));
    }

    /** 지금 이 3교시의 후기를 받는지: 성사돼서 여럿이 모였고(혼자 끝난 3교시 제외), 다음 날부터 7일 안 */
    static boolean asking(Meetup meetup, Instant now) {
        return meetup.isGatheredAt(now)
                && !now.isBefore(opensAt(meetup)) && now.isBefore(closesAt(meetup));
    }

    /** 앱을 열면 물어볼 3교시: 내가 함께했고, 아직 답하지도 닫지도 않은 것 (가장 최근 것부터) */
    @Transactional(readOnly = true)
    public List<FeedbackPending> pending(LoginMember viewer) {
        Instant now = clock.instant();
        List<Meetup> recent = participations.findPastMeetupsOf(viewer.id(), now.minus(Duration.ofDays(ASK_DAYS + 2)),
                now);
        List<Meetup> asking = recent.stream().filter(m -> asking(m, now)).toList();
        if (asking.isEmpty()) {
            return List.of();
        }
        Set<Long> done = feedbacks.findMeetupIdsOf(viewer.id(), asking.stream().map(Meetup::getId).toList());
        return asking.stream()
                .filter(m -> !done.contains(m.getId()))
                .map(m -> new FeedbackPending(m.getId(), m.getTitle(), m.getPlace(), m.getCategories(), m.getMeetAt(),
                        m.getParticipantCount(), closesAt(m)))
                .toList();
    }

    /** 후기 남기기 · 닫기. 한 번 답하면 바꿀 수 없다 (닫기만 했으면 기간 안에 답할 수 있다) */
    public void submit(LoginMember viewer, Long meetupId, FeedbackRequest req) {
        Instant now = clock.instant();
        Meetup meetup = meetups.findById(meetupId).orElseThrow(() -> ApiException.notFound("3교시를 찾을 수 없어요"));
        if (!participations.existsByMeetupIdAndMemberId(meetupId, viewer.id())) {
            throw ApiException.forbidden("함께한 3교시에만 후기를 남길 수 있어요");
        }
        if (!asking(meetup, now)) {
            throw ApiException.badRequest("NOT_ASKING", "지금은 이 3교시에 후기를 남길 수 없어요");
        }
        Optional<MeetupFeedback> existing = feedbacks.findByMeetupIdAndMemberId(meetupId, viewer.id());
        if (existing.isPresent() && existing.get().isAnswered()) {
            throw ApiException.conflict("ALREADY_ANSWERED", "이미 후기를 남겼어요");
        }
        if (req.skipping()) {
            if (existing.isEmpty()) {
                save(new MeetupFeedback(meetup, members.getReferenceById(viewer.id()), now));
            }
            return;
        }
        if (req.met() == null) {
            throw ApiException.badRequest("MET_REQUIRED", "실제로 모였는지 골라 주세요");
        }
        if (req.met() && req.rating() == null) {
            throw ApiException.badRequest("RATING_REQUIRED", "어땠는지 골라 주세요");
        }
        MeetupFeedback feedback = existing
                .orElseGet(() -> new MeetupFeedback(meetup, members.getReferenceById(viewer.id()), now));
        feedback.answer(req.met(), req.rating(), now);
        save(feedback);
    }

    /** 같은 사람이 두 번 눌러 동시에 들어와도 한 줄만 남는다 */
    private void save(MeetupFeedback feedback) {
        try {
            feedbacks.saveAndFlush(feedback);
        } catch (DataIntegrityViolationException e) {
            throw ApiException.conflict("ALREADY_ANSWERED", "이미 후기를 남겼어요");
        }
    }

    /** 3교시 상세에 붙일 후기 칸: 함께한 사람에게, 후기를 받는 동안만 */
    @Transactional(readOnly = true)
    public FeedbackPrompt prompt(Meetup meetup, LoginMember viewer, boolean joined, Instant now) {
        if (!joined || !asking(meetup, now)) {
            return null;
        }
        boolean answered = feedbacks.findByMeetupIdAndMemberId(meetup.getId(), viewer.id())
                .map(MeetupFeedback::isAnswered)
                .orElse(false);
        return new FeedbackPrompt(closesAt(meetup), answered);
    }

    /** 3교시를 지울 때 */
    public void deleteFor(Long meetupId) {
        feedbacks.deleteByMeetupId(meetupId);
    }

    /**
     * 대학원장 통계: 모임 시간이 최근 days 일 안(null 이면 전체)인 3교시를 학교별 · 연합으로 묶는다.
     * 개별 응답이나 3교시 하나하나의 결과는 내보내지 않는다.
     */
    @Transactional(readOnly = true)
    public FeedbackStatsView stats(LoginMember viewer, Integer days) {
        if (!viewer.isOwner()) {
            throw ApiException.forbidden("대학원장만 볼 수 있어요");
        }
        if (days != null && (days < 1 || days > 366)) {
            throw ApiException.badRequest("BAD_PERIOD", "기간은 1일에서 366일 사이로 골라 주세요");
        }
        Instant now = clock.instant();
        Instant since = days == null ? Instant.EPOCH : now.minus(Duration.ofDays(days));

        Map<Long, Acc> bySchool = new HashMap<>();
        Acc federation = new Acc();
        Map<Long, Acc> byMeetup = new HashMap<>();
        for (Meetup m : meetups.findPastWithSchool(since, now)) {
            Acc acc = m.audience() == MeetupAudience.ALL ? federation
                    : bySchool.computeIfAbsent(m.getSchool().getId(), id -> new Acc());
            acc.meetups++;
            if (m.isGatheredAt(now)) { // 랭킹과 같은 기준: 혼자 끝난 3교시는 성사로 세지 않는다
                acc.reached++;
                acc.participants += m.getParticipantCount();
                byMeetup.put(m.getId(), acc);
            }
        }

        if (!byMeetup.isEmpty()) {
            Map<Long, int[]> metVotes = new HashMap<>(); // 3교시마다 [모였어요, 안 모였어요]
            for (MeetupFeedback f : feedbacks.findAnsweredIn(byMeetup.keySet())) {
                Long meetupId = f.getMeetup().getId();
                Acc acc = byMeetup.get(meetupId);
                acc.answers++;
                int[] votes = metVotes.computeIfAbsent(meetupId, id -> new int[2]);
                votes[Boolean.TRUE.equals(f.getMet()) ? 0 : 1]++;
                if (f.getRating() != null) {
                    switch (f.getRating()) {
                        case GOOD -> acc.good++;
                        case OK -> acc.ok++;
                        case BAD -> acc.bad++;
                    }
                }
            }
            metVotes.forEach((meetupId, votes) -> {
                Acc acc = byMeetup.get(meetupId);
                acc.metKnown++;
                if (votes[0] >= votes[1]) {
                    acc.metMeetups++;
                }
            });
        }

        List<StatsGroup> groups = new ArrayList<>();
        List<Acc> accs = new ArrayList<>();
        for (School school : schools.findAllByOrderBySortOrderAscIdAsc()) {
            Acc acc = bySchool.getOrDefault(school.getId(), new Acc());
            accs.add(acc);
            groups.add(acc.view(SchoolTag.from(school), false));
        }
        accs.add(federation);
        groups.add(federation.view(null, true));

        // 전체의 모임·만족도 숫자는 숫자를 보여주는 칸만 더한다 — 감춘 칸까지 더하면 전체에서 빼서 그 칸을 알아낼 수 있다
        Acc all = new Acc();
        Acc shown = new Acc();
        boolean partial = false;
        for (Acc acc : accs) {
            all.add(acc);
            if (acc.answers >= MIN_ANSWERS) {
                shown.add(acc);
            } else if (acc.answers > 0) {
                partial = true;
            }
        }
        boolean enough = shown.answers >= MIN_ANSWERS;
        StatsGroup total = new StatsGroup(null, false, all.meetups, all.reached, all.participants, all.answers, enough,
                enough ? shown.metKnown : null, enough ? shown.metMeetups : null,
                enough ? shown.good : null, enough ? shown.ok : null, enough ? shown.bad : null);

        long feedbackReports = reports.countByFromFeedbackTrueAndCreatedAtGreaterThanEqual(since);
        return new FeedbackStatsView(days, MIN_ANSWERS, total, groups, feedbackReports, partial);
    }

    /** 묶음 하나의 숫자 */
    private static final class Acc {
        int meetups;
        int reached;
        int participants;
        int answers;
        int metKnown;
        int metMeetups;
        int good;
        int ok;
        int bad;

        void add(Acc o) {
            meetups += o.meetups;
            reached += o.reached;
            participants += o.participants;
            answers += o.answers;
            metKnown += o.metKnown;
            metMeetups += o.metMeetups;
            good += o.good;
            ok += o.ok;
            bad += o.bad;
        }

        /** 칸 하나: 답이 적으면 모임·만족도 숫자를 감춘다 */

        StatsGroup view(SchoolTag school, boolean federation) {
            boolean enough = answers >= MIN_ANSWERS;
            return new StatsGroup(school, federation, meetups, reached, participants, answers, enough,
                    enough ? metKnown : null, enough ? metMeetups : null,
                    enough ? good : null, enough ? ok : null, enough ? bad : null);
        }
    }
}
