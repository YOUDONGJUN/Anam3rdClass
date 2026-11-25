package com.anam3.feedback;

import java.util.List;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.anam3.auth.LoginMember;
import com.anam3.feedback.FeedbackDtos.FeedbackPending;
import com.anam3.feedback.FeedbackDtos.FeedbackRequest;
import com.anam3.feedback.FeedbackDtos.FeedbackStatsView;

/** 3교시 후기. 통계는 대학원장만 (SecurityConfig 와 서비스 둘 다에서 막는다) */
@RestController
public class FeedbackController {

    private final FeedbackService feedbackService;

    public FeedbackController(FeedbackService feedbackService) {
        this.feedbackService = feedbackService;
    }

    /** 앱을 열면 물어볼 3교시 */
    @GetMapping("/api/feedback/pending")
    public List<FeedbackPending> pending(@AuthenticationPrincipal LoginMember me) {
        return feedbackService.pending(me);
    }

    @PostMapping("/api/meetups/{id}/feedback")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void submit(@AuthenticationPrincipal LoginMember me, @PathVariable Long id,
            @Valid @RequestBody FeedbackRequest req) {
        feedbackService.submit(me, id, req);
    }

    /** days 가 없으면 전체 기간 */
    @GetMapping("/api/admin/feedback-stats")
    public FeedbackStatsView stats(@AuthenticationPrincipal LoginMember me,
            @RequestParam(required = false) Integer days) {
        return feedbackService.stats(me, days);
    }
}
