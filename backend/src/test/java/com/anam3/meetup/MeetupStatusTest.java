package com.anam3.meetup;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.Test;

import com.anam3.member.Member;
import com.anam3.member.MemberRole;

/** 번개 상태 계산 규칙 (DB 없이) */
class MeetupStatusTest {

    private static final Instant NOW = Instant.parse("2026-03-02T09:00:00Z");
    private static final Instant DEADLINE = NOW.plus(Duration.ofHours(1));
    private static final Instant MEET_AT = NOW.plus(Duration.ofHours(2));

    private Meetup meetup(int min, int max) {
        Member host = new Member("host", "x", "김민수", 2025, MemberRole.USER, NOW);
        return new Meetup(host, "치맥", "", "안암", MeetupCategory.BEER, MEET_AT, DEADLINE, min, max, null, NOW);
    }

    @Test
    void 최소인원_전에는_모집중() {
        Meetup m = meetup(3, 5);
        assertThat(m.statusAt(NOW)).isEqualTo(MeetupStatus.RECRUITING);
        assertThat(m.isJoinableAt(NOW)).isTrue();
    }

    @Test
    void 최소인원이_차면_성사() {
        Meetup m = meetup(2, 5);
        m.addParticipant();
        assertThat(m.statusAt(NOW)).isEqualTo(MeetupStatus.CONFIRMED);
    }

    @Test
    void 마감까지_미달이면_무산() {
        Meetup m = meetup(3, 5);
        assertThat(m.statusAt(DEADLINE)).isEqualTo(MeetupStatus.FAILED);
        assertThat(m.isJoinableAt(DEADLINE)).isFalse();
    }

    @Test
    void 성사된_번개는_마감후에도_성사_모임시간_지나면_완료() {
        Meetup m = meetup(2, 5);
        m.addParticipant();
        assertThat(m.statusAt(DEADLINE.plusSeconds(1))).isEqualTo(MeetupStatus.CONFIRMED);
        assertThat(m.isJoinableAt(DEADLINE.plusSeconds(1))).isFalse();
        assertThat(m.statusAt(MEET_AT)).isEqualTo(MeetupStatus.DONE);
    }

    @Test
    void 정원이_차면_참여불가() {
        Meetup m = meetup(2, 2);
        m.addParticipant();
        assertThat(m.isJoinableAt(NOW)).isFalse();
    }

    @Test
    void 취소가_최우선() {
        Meetup m = meetup(2, 5);
        m.addParticipant();
        m.cancel(NOW);
        assertThat(m.statusAt(NOW)).isEqualTo(MeetupStatus.CANCELED);
        assertThat(m.isJoinableAt(NOW)).isFalse();
    }
}
