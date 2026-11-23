package com.anam3.notice;

import java.time.Clock;
import java.util.List;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.anam3.auth.LoginMember;
import com.anam3.common.ApiException;
import com.anam3.common.NameMasker;
import com.anam3.member.Member;
import com.anam3.member.MemberRepository;
import com.anam3.notice.NoticeDtos.AuthorView;
import com.anam3.notice.NoticeDtos.NoticeDetail;
import com.anam3.notice.NoticeDtos.NoticeRequest;
import com.anam3.notice.NoticeDtos.NoticeSummary;

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
        return notices.findForList(PageRequest.of(0, 100)).stream()
                .map(n -> new NoticeSummary(n.getId(), n.getTitle(), preview(n.getContent()),
                        author(n.getAuthor(), viewer), n.isPinned(), n.getCreatedAt(), n.getUpdatedAt()))
                .toList();
    }

    @Transactional(readOnly = true)
    public NoticeDetail detail(LoginMember viewer, Long id) {
        return toDetail(load(id), viewer);
    }

    public NoticeDetail create(LoginMember viewer, NoticeRequest req) {
        Member author = members.findById(viewer.id())
                .orElseThrow(() -> ApiException.notFound("회원 정보를 찾을 수 없어요"));
        Notice notice = notices.save(new Notice(author, req.title().strip(), req.content().strip(), clock.instant()));
        return toDetail(notice, viewer);
    }

    public NoticeDetail update(LoginMember viewer, Long id, NoticeRequest req) {
        Notice notice = load(id);
        checkEditable(viewer, notice);
        notice.edit(req.title().strip(), req.content().strip(), clock.instant());
        return toDetail(notice, viewer);
    }

    public void delete(LoginMember viewer, Long id) {
        Notice notice = load(id);
        checkEditable(viewer, notice);
        notices.delete(notice);
    }

    /** 운영자 전용 (/api/admin/** 에서만 호출) */
    public NoticeDetail pin(LoginMember viewer, Long id, boolean pinned) {
        Notice notice = load(id);
        notice.pin(pinned);
        return toDetail(notice, viewer);
    }

    private Notice load(Long id) {
        return notices.findWithAuthor(id).orElseThrow(() -> ApiException.notFound("공지를 찾을 수 없어요"));
    }

    private void checkEditable(LoginMember viewer, Notice notice) {
        if (!viewer.isAdmin() && !notice.isWrittenBy(viewer.id())) {
            throw ApiException.forbidden("글쓴이만 고치거나 지울 수 있어요");
        }
    }

    private NoticeDetail toDetail(Notice n, LoginMember viewer) {
        return new NoticeDetail(n.getId(), n.getTitle(), n.getContent(), author(n.getAuthor(), viewer), n.isPinned(),
                viewer.isAdmin() || n.isWrittenBy(viewer.id()), n.getCreatedAt(), n.getUpdatedAt());
    }

    /** 관리자 글은 실명 + "관리자" 표시, 그 외엔 3교시와 같은 규칙으로 가운데 마스킹 (관리자와 본인에겐 실명) */
    private static AuthorView author(Member author, LoginMember viewer) {
        boolean me = author.getId().equals(viewer.id());
        if (author.isAdmin()) {
            return new AuthorView(author.getName(), author.cohortLabel(), author.departmentShortLabel(), true, me,
                    false);
        }
        boolean showReal = me || viewer.isAdmin();
        String name = showReal ? author.getName() : NameMasker.mask(author.getName());
        return new AuthorView(name, author.cohortLabel(), author.departmentShortLabel(), false, me, !showReal);
    }

    private static String preview(String content) {
        String oneLine = content.replaceAll("\\s+", " ").strip();
        return oneLine.length() <= PREVIEW_LENGTH ? oneLine : oneLine.substring(0, PREVIEW_LENGTH) + "…";
    }
}
