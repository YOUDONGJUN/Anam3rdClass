package com.anam3.meetup;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 성사된 3교시에서 나간 기록 (V17). 성사되면 실명·단톡방이 보이므로, 참여했다 바로 나가기를 되풀이하지 못하게 센다
 */
@Entity
@Table(name = "meetup_leaves")
public class MeetupLeave {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Column(name = "meetup_id", nullable = false)
    private Long meetupId;

    @Column(name = "left_at", nullable = false)
    private Instant leftAt;

    protected MeetupLeave() {
    }

    public MeetupLeave(Long memberId, Long meetupId, Instant leftAt) {
        this.memberId = memberId;
        this.meetupId = meetupId;
        this.leftAt = leftAt;
    }
}
