package com.anam3.feedback;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.anam3.feedback.MeetupFeedback.Rating;

public interface MeetupFeedbackRepository extends JpaRepository<MeetupFeedback, Long> {

    Optional<MeetupFeedback> findByMeetupIdAndMemberId(Long meetupId, Long memberId);

    /** 후기에서 '아쉬웠어요'를 골랐는지 (그 뒤 신고에 "후기에서" 표시를 붙일지) */
    boolean existsByMeetupIdAndMemberIdAndRating(Long meetupId, Long memberId, Rating rating);

    /** 이 사람이 이미 답했거나 닫은 3교시 */
    @Query("select f.meetup.id from MeetupFeedback f where f.member.id = :memberId and f.meetup.id in :meetupIds")
    Set<Long> findMeetupIdsOf(@Param("memberId") Long memberId, @Param("meetupIds") Collection<Long> meetupIds);

    /** 통계: 이 3교시들에 남은 후기 (답한 것만) */
    @Query("select f from MeetupFeedback f where f.meetup.id in :meetupIds and f.met is not null")
    List<MeetupFeedback> findAnsweredIn(@Param("meetupIds") Collection<Long> meetupIds);

    /** 3교시를 지울 때 후기도 함께 */
    @Modifying
    @Query("delete from MeetupFeedback f where f.meetup.id = :meetupId")
    int deleteByMeetupId(@Param("meetupId") Long meetupId);
}
