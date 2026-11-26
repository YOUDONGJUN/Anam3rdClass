package com.anam3.safety;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.anam3.auth.LoginMember;
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
import com.anam3.safety.SafetyDtos.ReportRequest;
import com.anam3.safety.SafetyDtos.ReportView;
import com.anam3.school.SchoolDtos.SchoolTag;

/**
 * 신고. 누구나 3교시·공지·3교시에서 만난 사람을 신고할 수 있다.
 * 교수는 자기 학교 신고를, 대학원장은 모든 신고를 관리 화면에서 보고 처리한다.
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
    private final Clock clock;

    public ReportService(ReportRepository reports, MeetupRepository meetups, ParticipationRepository participations,
            NoticeRepository notices, MemberRepository members, MeetupFeedbackRepository feedbacks, Clock clock) {
        this.reports = reports;
        this.meetups = meetups;
        this.participations = participations;
        this.notices = notices;
        this.members = members;
        this.feedbacks = feedbacks;
        this.clock = clock;
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
    }

    /** 관리 화면: 대학원장은 모든 신고, 교수는 자기 학교 신고. 처리 안 된 것부터 */
    @Transactional(readOnly = true)
    public List<ReportView> list(LoginMember viewer) {
        List<Report> found = viewer.isOwner()
                ? reports.findForOwner(PageRequest.of(0, 200))
                : reports.findForSchool(viewer.schoolId(), MemberRole.USER, PageRequest.of(0, 200));
        return found.stream().map(r -> view(r, viewer)).toList();
    }

    /** 처리 완료(resolved=true) / 다시 열기(false) */
    public ReportView resolve(LoginMember viewer, Long reportId, boolean resolved) {
        Report report = reports.findById(reportId).orElseThrow(() -> ApiException.notFound("신고를 찾을 수 없어요"));
        if (!canManage(viewer, report)) {
            throw ApiException.forbidden("우리 학교 신고만 처리할 수 있어요");
        }
        if (resolved) {
            report.resolve(members.getReferenceById(viewer.id()), clock.instant());
        } else {
            report.reopen();
        }
        return view(report, viewer);
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
    private static ReportView view(Report r, LoginMember viewer) {
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
                viewer.isOwner() && r.isFromFeedback());
    }

    /** 신고 당시 내용: 비어 있지 않은 줄만 */
    private static String joinLines(String... lines) {
        return java.util.Arrays.stream(lines).filter(l -> l != null && !l.isBlank())
                .collect(java.util.stream.Collectors.joining("\n"));
    }
}
