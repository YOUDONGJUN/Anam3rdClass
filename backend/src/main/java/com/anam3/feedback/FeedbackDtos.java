package com.anam3.feedback;

import java.time.Instant;
import java.util.List;

import com.anam3.feedback.MeetupFeedback.Rating;
import com.anam3.meetup.MeetupCategory;
import com.anam3.school.SchoolDtos.SchoolTag;

public final class FeedbackDtos {

    private FeedbackDtos() {
    }

    /** 앱을 열면 물어볼 3교시 (가장 최근 것부터). until 까지만 묻는다 */
    public record FeedbackPending(Long meetupId, String title, String place, List<MeetupCategory> categories,
            Instant meetAt, int participantCount, Instant until) {
    }

    /**
     * 후기 남기기. skip=true 면 답하지 않고 닫기 (다시 묻지 않는다).
     * met=false 면 rating 은 보지 않는다. met=true 면 rating 이 있어야 한다.
     */
    public record FeedbackRequest(Boolean met, Rating rating, Boolean skip) {

        public boolean skipping() {
            return Boolean.TRUE.equals(skip);
        }
    }

    /** 3교시 상세: 후기를 받는 동안만 내려간다 (참여자에게만). answered 면 이미 남겼다 */
    public record FeedbackPrompt(Instant until, boolean answered) {
    }

    /**
     * 대학원장 통계. days 가 null 이면 전체 기간.
     * 묶음마다 답이 minAnswers 개보다 적으면 enough=false 이고 모임·만족도 숫자를 비워서 보낸다 (누가 답했는지 짐작할 수 없게).
     * total 의 모임·만족도 숫자도 숫자를 보여주는 묶음만 더한다 (감춘 묶음을 전체에서 빼서 알아낼 수 없게).
     */
    public record FeedbackStatsView(Integer days, int minAnswers, StatsGroup total, List<StatsGroup> groups,
            /** 후기에서 '아쉬웠어요'를 고른 뒤 한 신고 수 */
            long feedbackReports,
            /** 답이 적어 감춘 묶음이 있어 total 의 모임·만족도에서 뺐다 */
            boolean partial) {
    }

    /**
     * 학교 하나 (federation=true 면 연합 3교시).
     * meetups: 모임 시간이 지난 3교시 (취소 포함) · reached: 그중 성사 · participants: 성사된 3교시 참여자 수 ·
     * answers: 답한 후기 수 · metKnown: 후기가 하나라도 있는 성사 3교시 · metMeetups: 그중 '모였어요'가 더 많거나 같은 것 ·
     * good/ok/bad: 만족도 (모였다고 답한 사람만)
     */
    public record StatsGroup(SchoolTag school, boolean federation, int meetups, int reached, int participants,
            int answers, boolean enough, Integer metKnown, Integer metMeetups, Integer good, Integer ok, Integer bad) {
    }
}
