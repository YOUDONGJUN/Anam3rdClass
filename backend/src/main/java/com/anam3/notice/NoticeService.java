package com.anam3.notice;

import java.time.Clock;
import java.util.List;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.anam3.auth.LoginMember;
import com.anam3.common.ApiException;
import com.anam3.common.ContentFilter;
import com.anam3.common.NameMasker;
import com.anam3.member.Member;
import com.anam3.member.MemberRepository;
import com.anam3.notice.NoticeDtos.AuthorView;
import com.anam3.notice.NoticeDtos.NoticeDetail;
import com.anam3.notice.NoticeDtos.NoticeRequest;
import com.anam3.notice.NoticeDtos.NoticeSummary;
import com.anam3.school.SchoolDtos.SchoolTag;

/**
 * 공지. 우리 학교 공지와 연합 공지(대학원장)가 보인다.
 * 고치기·지우기는 글쓴이, 그 학교 교수, 대학원장. 상단 고정은 그 학교 교수·대학원장 (연합 공지는 대학원장만).
 */
@Service
@Transactional
public class NoticeService {

    private static final int PREVIEW_LENGTH = 80;

    private final NoticeRepository notices;
    private final MemberRepository members;
    private final Clock clock;

    public NoticeService(NoticeRepository notices, MemberRepository members, Clock clock) {
        this.notices = notices;
        this.members = members;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<NoticeSummary> list(LoginMember viewer) {
        return notices.findForList(viewer.schoolId(), PageRequest.of(0, 100)).stream()
                .map(n -> new NoticeSummary(n.getId(), n.getTitle(), preview(n.getContent()),
                        author(n.getAuthor(), viewer), n.isPinned(), n.isFederated(), n.getCreatedAt(),
                        n.getUpdatedAt()))
                .toList();
    }

    @Transactional(readOnly = true)
    public NoticeDetail detail(LoginMember viewer, Long id) {
        Notice notice = load(id);
        if (!canSee(notice, viewer)) {
            throw ApiException.notFound("공지를 찾을 수 없어요");
        }
        return toDetail(notice, viewer);
    }

    public NoticeDetail create(LoginMember viewer, NoticeRequest req) {
        boolean federated = Boolean.TRUE.equals(req.federated());
        if (federated && !viewer.isOwner()) {
            throw ApiException.forbidden("연합 공지는 대학원장만 쓸 수 있어요");
        }
        ContentFilter.check(req.title(), req.content());
        Member author = members.findWithSchool(viewer.id())
                .orElseThrow(() -> ApiException.notFound("회원 정보를 찾을 수 없어요"));
        Notice notice = notices.save(new Notice(author, federated ? null : author.getSchool(), req.title().strip(),
                req.content().strip(), clock.instant()));
        return toDetail(notice, viewer);
    }

    public NoticeDetail update(LoginMember viewer, Long id, NoticeRequest req) {
        Notice notice = load(id);
        checkEditable(viewer, notice);
        ContentFilter.check(req.title(), req.content());
        notice.edit(req.title().strip(), req.content().strip(), clock.instant());
        return toDetail(notice, viewer);
    }

    public void delete(LoginMember viewer, Long id) {
        Notice notice = load(id);
        checkEditable(viewer, notice);
        notices.delete(notice);
    }

    /** 교수·대학원장 전용 (/api/admin/** 에서만 호출). 교수는 자기 학교 공지만 */
    public NoticeDetail pin(LoginMember viewer, Long id, boolean pinned) {
        Notice notice = load(id);
        if (!canManage(viewer, notice)) {
            throw ApiException.forbidden("우리 학교 공지만 고정할 수 있어요");
        }
        notice.pin(pinned);
        return toDetail(notice, viewer);
    }

    /** 연합 공지이거나 우리 학교 공지. 대학원장은 모두 (신고 처리) */
    public static boolean canSee(Notice notice, LoginMember viewer) {
        return viewer.isOwner() || notice.isFederated() || notice.getSchool().getId().equals(viewer.schoolId());
    }

    private Notice load(Long id) {
        return notices.findWithAuthor(id).orElseThrow(() -> ApiException.notFound("공지를 찾을 수 없어요"));
    }

    /** 그 학교 교수, 대학원장 (연합 공지는 대학원장만) */
    private static boolean canManage(LoginMember viewer, Notice notice) {
        return notice.isFederated() ? viewer.isOwner() : viewer.manages(notice.getSchool().getId());
    }

    /** 글쓴이, 그 학교 교수, 대학원장이 고치거나 지울 수 있다 */
    private static void checkEditable(LoginMember viewer, Notice notice) {
        if (!notice.isWrittenBy(viewer.id()) && !canManage(viewer, notice)) {
            throw ApiException.forbidden("글쓴이만 고치거나 지울 수 있어요");
        }
    }

    private NoticeDetail toDetail(Notice n, LoginMember viewer) {
        return new NoticeDetail(n.getId(), n.getTitle(), n.getContent(), author(n.getAuthor(), viewer), n.isPinned(),
                n.isFederated(), n.isWrittenBy(viewer.id()) || canManage(viewer, n), canManage(viewer, n),
                n.getCreatedAt(), n.getUpdatedAt());
    }

    /**
     * 대학원장 글은 실명 + "대학원장" 표시. 그 외(교수 포함)는 3교시와 같은 규칙으로 가운데 마스킹
     * (대학원장과 본인에겐 실명). 탈퇴한 원우는 "탈퇴한 원우".
     */
    private static AuthorView author(Member author, LoginMember viewer) {
        boolean me = author.getId().equals(viewer.id());
        SchoolTag school = SchoolTag.from(author.getSchool());
        if (author.isOwner()) {
            return new AuthorView(author.getName(), author.cohortLabel(), author.departmentShortLabel(), school, true,
                    me, false);
        }
        boolean showReal = me || viewer.isOwner() || author.isWithdrawn();
        String name = showReal ? author.getName() : NameMasker.mask(author.getName());
        return new AuthorView(name, author.cohortLabel(), author.departmentShortLabel(), school, false, me,
                !showReal);
    }

    private static String preview(String content) {
        String oneLine = content.replaceAll("\\s+", " ").strip();
        return oneLine.length() <= PREVIEW_LENGTH ? oneLine : oneLine.substring(0, PREVIEW_LENGTH) + "…";
    }
}
