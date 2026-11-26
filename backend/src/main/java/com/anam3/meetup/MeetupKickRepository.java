package com.anam3.meetup;

import org.springframework.data.jpa.repository.JpaRepository;

public interface MeetupKickRepository extends JpaRepository<MeetupKick, Long> {

    boolean existsByMeetupIdAndMemberId(Long meetupId, Long memberId);
}
