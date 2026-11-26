package com.anam3.meetup;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** 연 사람이 내보낸 참여자 (V18). 같은 3교시에는 다시 참여·대기할 수 없다 */
@Entity
@Table(name = "meetup_kicks")
public class MeetupKick {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "meetup_id", nullable = false)
    private Long meetupId;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Column(name = "kicked_at", nullable = false)
    private Instant kickedAt;

    protected MeetupKick() {
    }

    public MeetupKick(Long meetupId, Long memberId, Instant kickedAt) {
        this.meetupId = meetupId;
        this.memberId = memberId;
        this.kickedAt = kickedAt;
    }
}
