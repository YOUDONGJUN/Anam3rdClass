package com.anam3.notice;

import java.util.List;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.anam3.auth.LoginMember;
import com.anam3.notice.NoticeDtos.NoticeDetail;
import com.anam3.notice.NoticeDtos.NoticeRequest;
import com.anam3.notice.NoticeDtos.NoticeSummary;

@RestController
@RequestMapping("/api/notices")
public class NoticeController {

    private final NoticeService noticeService;

    public NoticeController(NoticeService noticeService) {
        this.noticeService = noticeService;
    }

    @GetMapping
    public List<NoticeSummary> list(@AuthenticationPrincipal LoginMember me) {
        return noticeService.list(me);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public NoticeDetail create(@AuthenticationPrincipal LoginMember me, @Valid @RequestBody NoticeRequest req) {
        return noticeService.create(me, req);
    }

    @GetMapping("/{id}")
    public NoticeDetail detail(@AuthenticationPrincipal LoginMember me, @PathVariable Long id) {
        return noticeService.detail(me, id);
    }

    @PutMapping("/{id}")
    public NoticeDetail update(@AuthenticationPrincipal LoginMember me, @PathVariable Long id,
            @Valid @RequestBody NoticeRequest req) {
        return noticeService.update(me, id, req);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal LoginMember me, @PathVariable Long id) {
        noticeService.delete(me, id);
    }
}
