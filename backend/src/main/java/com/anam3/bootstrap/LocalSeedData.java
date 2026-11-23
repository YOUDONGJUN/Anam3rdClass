package com.anam3.bootstrap;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.anam3.meetup.Meetup;
import com.anam3.meetup.MeetupCategory;
import com.anam3.meetup.MeetupRepository;
import com.anam3.meetup.Participation;
import com.anam3.meetup.ParticipationRepository;
import com.anam3.member.Member;
import com.anam3.member.MemberRepository;
import com.anam3.member.MemberRole;
import com.anam3.notice.Notice;
import com.anam3.notice.NoticeRepository;

/**
 * 로컬 개발(local 프로필)에서만 화면이 비어 보이지 않도록 샘플 데이터를 넣는다.
 * 계정: admin / admin1234 (운영자), minsu / password1234, jiyoung / password1234, hyunwoo / password1234
 */
@Component
@Profile("local")
@Order(2)
public class LocalSeedData implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(LocalSeedData.class);
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final MemberRepository members;
    private final MeetupRepository meetups;
    private final ParticipationRepository participations;
    private final NoticeRepository notices;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;

    public LocalSeedData(MemberRepository members, MeetupRepository meetups, ParticipationRepository participations,
            NoticeRepository notices, PasswordEncoder passwordEncoder, Clock clock) {
        this.members = members;
        this.meetups = meetups;
        this.participations = participations;
        this.notices = notices;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (meetups.count() > 0) {
            return;
        }
        Instant now = clock.instant();
        String pw = passwordEncoder.encode("password1234");
        Member minsu = members.save(new Member("minsu", pw, "김민수", 2025, MemberRole.USER, now));
        Member jiyoung = members.save(new Member("jiyoung", pw, "이지영", 2024, MemberRole.USER, now));
        Member hyunwoo = members.save(new Member("hyunwoo", pw, "박현우", 2025, MemberRole.USER, now));
        Member admin = members.findByLoginId("admin").orElse(minsu);

        LocalDate today = LocalDate.now(clock.withZone(KST));
        Instant tonight = today.atTime(LocalTime.of(22, 0)).atZone(KST).toInstant();
        if (!tonight.isAfter(now.plus(Duration.ofHours(1)))) {
            tonight = now.plus(Duration.ofHours(3));
        }
        Instant tomorrow = tonight.plus(Duration.ofDays(1)).minus(Duration.ofHours(3));

        Meetup m1 = meetup(minsu, "수업 끝나고 치맥 한잔?", "오늘 머신러닝 수업 끝나고 가볍게 한잔해요. 늦게 와도 OK",
                "안암역 2번 출구 근처 치킨집", MeetupCategory.BEER, tonight, tonight.minus(Duration.ofMinutes(30)),
                3, 6, "https://open.kakao.com/o/example", now);
        join(m1, jiyoung, now);

        Meetup m2 = meetup(jiyoung, "과제 제출 기념 소주", "팀플 끝난 사람들 모여요 🙌", "참살이길 포차",
                MeetupCategory.SOJU, tomorrow, tomorrow.minus(Duration.ofHours(1)), 2, 4, null, now);
        join(m2, hyunwoo, now);

        meetup(hyunwoo, "술 말고 밥 먹어요", "술은 못 하지만 사람은 좋아요", "고대 정문 앞 국밥집",
                MeetupCategory.NO_ALCOHOL, tomorrow.plus(Duration.ofHours(1)), tomorrow, 3, 5, null, now);

        Notice welcome = notices.save(new Notice(admin, "안암3교시에 오신 걸 환영해요 🍻",
                "수업 끝나고 한잔하고 싶을 때, 번개를 열어 보세요.\n\n"
                        + "• 최소 인원이 모이면 '성사'! 그때 참여자끼리 이름과 오픈채팅 링크가 공개돼요.\n"
                        + "• 인원이 안 모이면 조용히 사라지니 부담 갖지 마세요.\n"
                        + "• 과음은 금물, 귀가는 안전하게!", now));
        welcome.pin(true);
        notices.save(new Notice(jiyoung, "다음 주 종강 파티 수요조사",
                "금요일 저녁 종강 파티 하실 분 번개로 열어 볼게요. 장소 추천 받아요!", now));

        log.info("로컬 샘플 데이터를 넣었습니다 (minsu / jiyoung / hyunwoo, 비밀번호 password1234)");
    }

    private Meetup meetup(Member host, String title, String desc, String place, MeetupCategory category,
            Instant meetAt, Instant deadline, int min, int max, String openChat, Instant now) {
        Meetup m = meetups.save(new Meetup(host, title, desc, place, category, meetAt, deadline, min, max, openChat,
                now));
        participations.save(new Participation(m, host, now));
        return m;
    }

    private void join(Meetup meetup, Member member, Instant now) {
        participations.save(new Participation(meetup, member, now));
        meetup.addParticipant();
    }
}
