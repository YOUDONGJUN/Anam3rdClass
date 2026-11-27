package com.anam3.safety;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.anam3.admin.AdminAction;
import com.anam3.admin.AdminActionLog;
import com.anam3.auth.LoginMember;
import com.anam3.chat.ChatMessage;
import com.anam3.common.ApiException;
import com.anam3.feedback.MeetupFeedback.Rating;
import com.anam3.feedback.MeetupFeedbackRepository;
import com.anam3.meetup.Meetup;
import com.anam3.meetup.MeetupRepository;
import com.anam3.meetup.MeetupService;
import com.anam3.meetup.Participation;
import com.anam3.meetup.ParticipationRepository;
import com.anam3.member.Member;
import com.anam3.member.MemberRepository;
import com.anam3.member.MemberRole;
import com.anam3.member.MemberStatus;
import com.anam3.notice.Notice;
import com.anam3.notice.NoticeRepository;
import com.anam3.notice.NoticeService;
import com.anam3.push.PushNotifier;
import com.anam3.safety.SafetyDtos.ReportRequest;
import com.anam3.safety.SafetyDtos.ReportView;
import com.anam3.sanction.MemberSanctionRepository;
import com.anam3.school.SchoolDtos.SchoolTag;

/**
 * 신고. 누구나 3교시·공지·3교시에서 만난 사람을 신고할 수 있다.
 * 교수는 자기 학교 신고를, 대학원장은 모든 신고를 관리 화면에서 보고 처리한다.
 * 새 신고는 처리할 수 있는 교수·대학원장에게, 처리 결과는 신고한 사람에게 알린다 (신고당한 사람에게는 알리지 않는다).
 */
@Service
@Transactional
public class ReportService {

    static final int DAILY_LIMIT = 20;

    private final ReportRepository reports;
    private final MeetupRepository meetups;
    private final ParticipationRepository participations;
    private final NoticeRepository notices;
    private final MemberRepository members;
    private final MeetupFeedbackRepository feedbacks;
    private final PushNotifier notifier;
    private final AdminActionLog actionLog;
    private final Clock clock;
    private final LeftMembers leftMembers;
    private final ChatMessageTargets chatMessages;
    private final MemberSanctionRepository sanctions;

    public ReportService(ReportRepository reports, MeetupRepository meetups, ParticipationRepository participations,
            NoticeRepository notices, MemberRepository members, MeetupFeedbackRepository feedbacks,
            PushNotifier notifier, AdminActionLog actionLog, Clock clock, LeftMembers leftMembers,
            ChatMessageTargets chatMessages, MemberSanctionRepository sanctions) {
        this.reports = reports;
        this.meetups = meetups;
        this.participations = participations;
        this.notices = notices;
        this.members = members;
        this.feedbacks = feedbacks;
        this.notifier = notifier;
        this.actionLog = actionLog;
        this.clock = clock;
        this.leftMembers = leftMembers;
        this.chatMessages = chatMessages;
        this.sanctions = sanctions;
    }

    public void report(LoginMember viewer, ReportRequest req) {
        Instant now = clock.instant();
        Member reporter = members.getReferenceById(viewer.id());
        String detail = req.detail() == null || req.detail().isBlank() ? null : req.detail().strip();

        Report report = switch (req.targetType()) {
            case MEETUP -> {
                Meetup meetup = meetups.findWithHost(req.targetId())
                        .filter(m -> canSee(m, viewer))
                        .orElseThrow(() -> ApiException.notFound("3교시를 찾을 수 없어요"));
                Report r = new Report(reporter, Report.TargetType.MEETUP, meetup, null, meetup.getHost(),
                        meetup.getSchool(), req.reason(), detail, now);
                r.snapshot(meetup.getTitle(), joinLines(meetup.getDescription(), "장소: " + meetup.getPlace()));
                yield r;
            }
            case NOTICE -> {
                Notice notice = notices.findWithAuthor(req.targetId())
                        .filter(n -> NoticeService.canSee(n, viewer))
                        .orElseThrow(() -> ApiException.notFound("공지를 찾을 수 없어요"));
                Report r = new Report(reporter, Report.TargetType.NOTICE, null, notice, notice.getAuthor(),
                        notice.getSchool(), req.reason(), detail, now);
                r.snapshot(notice.getTitle(), notice.getContent());
                yield r;
            }
            case MEMBER -> {
                if (Boolean.TRUE.equals(req.chat())) {
                    // 톡방 메시지: 보낸 원우가 대상 (그 뒤에 나갔어도). 메시지 내용을 남긴다 — 교수·대학원장은 톡방을 볼 수 없다
                    ChatMessage message = chatMessages.find(viewer, req.targetId())
                            .orElseThrow(() -> ApiException.notFound("신고할 메시지를 찾을 수 없어요"));
                    Member sender = message.getMember();
                    Report r = new Report(reporter, Report.TargetType.MEMBER, message.getMeetup(), null, sender,
                            sender.getSchool(), req.reason(), detail, now);
                    r.snapshot(ChatMessageTargets.title(message), ChatMessageTargets.body(message));
                    yield r;
                }
                if (Boolean.TRUE.equals(req.left())) {
                    // 성사된 뒤 나간 원우 (실명을 보고 나갔다)
                    LeftMembers.Found found = leftMembers.find(viewer, req.targetId())
                            .orElseThrow(() -> ApiException.notFound("신고할 사람을 찾을 수 없어요"));
                    yield new Report(reporter, Report.TargetType.MEMBER, found.meetup(), null, found.member(),
                            found.member().getSchool(), req.reason(), detail, now);
                }
                Participation p = participations.findById(req.targetId())
                        .filter(x -> canSee(x.getMeetup(), viewer))
                        .orElseThrow(() -> ApiException.notFound("신고할 사람을 찾을 수 없어요"));
                Member target = p.getMember();
                Report r = new Report(reporter, Report.TargetType.MEMBER, p.getMeetup(), null, target,
                        target.getSchool(), req.reason(), detail, now);
                // 후기에서 '아쉬웠어요'를 정말 골랐을 때만 표시한다 (통계가 부풀지 않게)
                if (Boolean.TRUE.equals(req.fromFeedback())
                        && feedbacks.existsByMeetupIdAndMemberIdAndRating(p.getMeetup().getId(), viewer.id(), Rating.BAD)) {
                    r.markFromFeedback();
                }
                yield r;
            }
        };
        if (report.getMember() != null && report.getMember().getId().equals(viewer.id())) {
            throw ApiException.badRequest("SELF_REPORT", "내 글이나 나 자신은 신고할 수 없어요");
        }

        List<Report> recent = reports.findByReporterIdAndCreatedAtAfter(viewer.id(), now.minus(Duration.ofDays(1)));
        if (recent.stream().anyMatch(r -> sameTarget(r, report))) {
            throw ApiException.conflict("ALREADY_REPORTED", "이미 신고했어요. 교수·대학원장이 확인할 거예요");
        }
        if (recent.size() >= DAILY_LIMIT) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "TOO_MANY", "오늘은 신고를 더 할 수 없어요. 내일 다시 해 주세요");
        }
        reports.save(report);
        notifyStaff(report, viewer.id());
    }

    /**
     * 새 신고를 처리할 수 있는 사람에게 알린다 — 관리 화면 신고 목록과 같은 범위
     * (대학원장은 모두, 교수는 자기 학교의 일반 원우가 대상인 신고만. 학교가 없는 연합 공지는 대학원장만).
     * 신고한 사람과 신고당한 사람은 뺀다
     */
    private void notifyStaff(Report report, Long reporterId) {
        Member target = report.getMember();
        boolean targetIsStaff = target != null && target.isStaff();
        Long schoolId = report.getSchool() == null || targetIsStaff ? null : report.getSchool().getId();
        List<Long> to = members.findStaffFor(schoolId, MemberRole.OWNER, MemberRole.MANAGER, MemberStatus.ACTIVE)
                .stream()
                .map(Member::getId)
                .filter(id -> !id.equals(reporterId) && (target == null || !id.equals(target.getId())))
                .toList();
        // 서로 다른 원우 여럿이 같은 사람을 신고하면 "주의"로 알린다 (자동 제재는 하지 않는다 — 사람이 확인한다)
        int reporters = target == null ? 0
                : reporterCounts(List.of(target.getId()), clock.instant()).getOrDefault(target.getId(), 0);
        if (reporters == FLAG_REPORTERS) {
            notifier.reportFlagged(to, reporters);
        } else {
            notifier.reportFiled(to, report.getReason().label());
        }
    }

    /** "주의" 표시: 최근 30일 동안 서로 다른 원우 이만큼이 같은 사람을 신고 */
    public static final int FLAG_REPORTERS = 3;
    private static final Duration FLAG_WINDOW = Duration.ofDays(30);

    private Map<Long, Integer> reporterCounts(Collection<Long> memberIds, Instant now) {
        Map<Long, Integer> result = new HashMap<>();
        if (memberIds.isEmpty()) {
            return result;
        }
        for (Object[] row : reports.countReporters(memberIds, now.minus(FLAG_WINDOW))) {
            result.put((Long) row[0], ((Number) row[1]).intValue());
        }
        return result;
    }

    private Map<Long, Integer> strikeCounts(Collection<Long> memberIds) {
        Map<Long, Integer> result = new HashMap<>();
        if (memberIds.isEmpty()) {
            return result;
        }
        for (Object[] row : sanctions.countActive(memberIds)) {
            result.put((Long) row[0], ((Number) row[1]).intValue());
        }
        return result;
    }

    /** 신고 화면 한 줄에 붙이는 신고당한 사람의 지금 상태 */
    private record TargetState(Map<Long, Integer> reporters, Map<Long, Integer> strikes, Instant now) {
    }

    private TargetState stateOf(List<Report> found) {
        List<Long> ids = found.stream().map(Report::getMember).filter(Objects::nonNull).map(Member::getId)
                .distinct().toList();
        Instant now = clock.instant();
        return new TargetState(reporterCounts(ids, now), strikeCounts(ids), now);
    }

    /** 관리 화면: 대학원장은 모든 신고, 교수는 자기 학교 신고. 처리 안 된 것부터 */
    @Transactional(readOnly = true)
    public List<ReportView> list(LoginMember viewer) {
        List<Report> found = viewer.isOwner()
                ? reports.findForOwner(PageRequest.of(0, 200))
                : reports.findForSchool(viewer.schoolId(), MemberRole.USER, PageRequest.of(0, 200));
        TargetState state = stateOf(found);
        return found.stream().map(r -> view(r, viewer, state)).toList();
    }

    /** 처리 완료(resolved=true) / 다시 열기(false) */
    public ReportView resolve(LoginMember viewer, Long reportId, boolean resolved) {
        Report report = reports.findById(reportId).orElseThrow(() -> ApiException.notFound("신고를 찾을 수 없어요"));
        if (!canManage(viewer, report)) {
            throw ApiException.forbidden("우리 학교 신고만 처리할 수 있어요");
        }
        boolean wasOpen = report.getStatus() == Report.Status.OPEN;
        if (resolved) {
            report.resolve(members.getReferenceById(viewer.id()), clock.instant());
        } else {
            report.reopen();
        }
        if (wasOpen == resolved) { // 상태가 바뀌었을 때만 기록·알림
            actionLog.record(viewer, resolved ? AdminAction.Type.RESOLVE_REPORT : AdminAction.Type.REOPEN_REPORT,
                    report.getMember(), idOf(report.getMeetup()),
                    report.getTargetType().label() + " 신고 · " + report.getReason().label());
            Long reporterId = report.getReporter().getId();
            if (resolved && !reporterId.equals(viewer.id())) {
                notifier.reportResolved(reporterId, report.getId());
            }
        }
        return view(report, viewer, stateOf(List.of(report)));
    }

    /** 관리 화면 현황: 처리 안 된 신고 수 */
    @Transactional(readOnly = true)
    public long openCount(LoginMember viewer) {
        return viewer.isOwner()
                ? reports.countByStatus(Report.Status.OPEN)
                : reports.countForSchool(viewer.schoolId(), Report.Status.OPEN, MemberRole.USER);
    }

    private boolean canSee(Meetup meetup, LoginMember viewer) {
        return MeetupService.canSee(meetup, viewer)
                || participations.existsByMeetupIdAndMemberId(meetup.getId(), viewer.id());
    }

    /** 대학원장은 모두, 교수는 자기 학교의 일반 원우가 대상인 신고만 (교수·대학원장이 대상이면 대학원장만) */
    /** 이 교수·대학원장이 처리할 수 있는 신고 (경고를 줄 때) */
    @Transactional(readOnly = true)
    public Report manageable(LoginMember viewer, Long reportId) {
        return reports.findById(reportId)
                .filter(r -> canManage(viewer, r))
                .orElseThrow(() -> ApiException.notFound("신고를 찾을 수 없어요"));
    }

    private static boolean canManage(LoginMember viewer, Report report) {
        if (viewer.isOwner()) {
            return true;
        }
        boolean targetIsStaff = report.getMember() != null && report.getMember().isStaff();
        return !targetIsStaff && report.getSchool() != null && viewer.manages(report.getSchool().getId());
    }

    private static boolean sameTarget(Report a, Report b) {
        return a.getTargetType() == b.getTargetType()
                && Objects.equals(idOf(a.getMeetup()), idOf(b.getMeetup()))
                && Objects.equals(a.getNotice() == null ? null : a.getNotice().getId(),
                        b.getNotice() == null ? null : b.getNotice().getId())
                && Objects.equals(a.getMember() == null ? null : a.getMember().getId(),
                        b.getMember() == null ? null : b.getMember().getId());
    }

    private static Long idOf(Meetup m) {
        return m == null ? null : m.getId();
    }

    /** 후기에서 온 신고라는 표시는 대학원장에게만 (교수에게는 신고한 사람을 짐작할 단서를 주지 않는다) */
    private static ReportView view(Report r, LoginMember viewer, TargetState state) {
        Meetup meetup = r.getMeetup();
        Notice notice = r.getNotice();
        Member member = r.getMember();
        String affiliation = member == null ? null
                : member.getSchool().getShortName() + " · " + member.cohortLabel()
                        + (member.departmentShortLabel() == null ? "" : " · " + member.departmentShortLabel());
        return new ReportView(r.getId(), r.getTargetType(), r.getReason(), r.getDetail(), r.getTargetSnapshot(),
                r.getStatus(), r.getCreatedAt(), r.getResolvedAt(),
                meetup == null ? null : meetup.getId(), meetup == null ? null : meetup.getTitle(),
                notice == null ? null : notice.getId(), notice == null ? null : notice.getTitle(),
                member == null ? null : member.getId(), member == null ? null : member.getName(), affiliation,
                member == null ? null : member.getRole(),
                member != null && member.getStatus() == MemberStatus.BANNED, SchoolTag.from(r.getSchool()),
                viewer.isOwner() && r.isFromFeedback(),
                member == null ? 0 : state.reporters().getOrDefault(member.getId(), 0),
                member == null ? 0 : state.strikes().getOrDefault(member.getId(), 0),
                member != null && member.isSuspendedAt(state.now()) ? member.getSuspendedUntil() : null);
    }

    /** 신고 당시 내용: 비어 있지 않은 줄만 */
    private static String joinLines(String... lines) {
        return java.util.Arrays.stream(lines).filter(l -> l != null && !l.isBlank())
                .collect(java.util.stream.Collectors.joining("\n"));
    }
}
