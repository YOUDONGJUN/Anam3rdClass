package com.anam3.bootstrap;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.anam3.auth.OwnerPolicy;
import com.anam3.feedback.MeetupFeedback;
import com.anam3.feedback.MeetupFeedback.Rating;
import com.anam3.feedback.MeetupFeedbackRepository;
import com.anam3.meetup.Meetup;
import com.anam3.meetup.MeetupAudience;
import com.anam3.meetup.MeetupCategory;
import com.anam3.meetup.MeetupRepository;
import com.anam3.meetup.Participation;
import com.anam3.meetup.MeetupWait;
import com.anam3.meetup.MeetupWaitRepository;
import com.anam3.meetup.ParticipationRepository;
import com.anam3.member.Member;
import com.anam3.member.MemberRepository;
import com.anam3.member.MemberRole;
import com.anam3.notice.Notice;
import com.anam3.notice.NoticeRepository;
import com.anam3.school.Department;
import com.anam3.school.DepartmentRepository;
import com.anam3.school.School;
import com.anam3.school.SchoolRepository;
import com.anam3.settlement.Settlement;
import com.anam3.settlement.SettlementRepository;
import com.anam3.settlement.SettlementShare;
import com.anam3.settlement.SettlementShareRepository;

/**
 * 로컬 개발(local 프로필)에서만 화면이 비어 보이지 않도록 샘플 데이터를 넣는다.
 * 로그인 화면의 "개발용 로그인"에서 아래 계정을 골라 들어간다 (카카오·메일 없이). 이름은 모두 지어낸 것이다.
 * 지난 3교시는 이번 달 랭킹에, 꽉 찬 "딱 셋이 와인 한 병"은 빈자리 대기에 보인다.
 * "중간고사 끝 소주"는 정산 중이고, 어제 모인 "수업 끝나고 가볍게 한잔"은 정산을 새로 만들 수 있다.
 * <ul>
 *   <li>고려대: 유동준(대학원장), 이안암(교수), 김고대·박호랑·최석탑(원우)</li>
 *   <li>연세대 이신촌, 성균관대 박명륜, 서강대 최노고, 한양대 정왕십 (원우)</li>
 * </ul>
 */
@Component
@Profile("local")
@Order(2)
public class LocalSeedData implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(LocalSeedData.class);
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final MemberRepository members;
    private final SchoolRepository schools;
    private final DepartmentRepository departments;
    private final MeetupRepository meetups;
    private final ParticipationRepository participations;
    private final NoticeRepository notices;
    private final MeetupFeedbackRepository feedbacks;
    private final MeetupWaitRepository waits;
    private final SettlementRepository settlements;
    private final SettlementShareRepository settlementShares;
    private final OwnerPolicy ownerPolicy;
    private final Clock clock;

    public LocalSeedData(MemberRepository members, SchoolRepository schools, DepartmentRepository departments,
            MeetupRepository meetups, ParticipationRepository participations, NoticeRepository notices,
            MeetupFeedbackRepository feedbacks, MeetupWaitRepository waits, SettlementRepository settlements,
            SettlementShareRepository settlementShares, OwnerPolicy ownerPolicy, Clock clock) {
        this.members = members;
        this.schools = schools;
        this.departments = departments;
        this.meetups = meetups;
        this.participations = participations;
        this.notices = notices;
        this.feedbacks = feedbacks;
        this.waits = waits;
        this.settlements = settlements;
        this.settlementShares = settlementShares;
        this.ownerPolicy = ownerPolicy;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (meetups.count() > 0) {
            return;
        }
        Instant now = clock.instant();
        School korea = school("KU");
        String ownerEmail = ownerPolicy.ownerEmail() == null ? "owner@korea.ac.kr" : ownerPolicy.ownerEmail();

        Member owner = members.findByEmail(ownerEmail).orElseGet(() -> members.save(
                new Member(null, ownerEmail, korea, "유동준", 61, department(korea, "인융"), MemberRole.OWNER, now)));
        Member godae = member(korea, "godae@korea.ac.kr", "김고대", 61, "인융", MemberRole.USER, now);
        Member anam = member(korea, "anam@korea.ac.kr", "이안암", 60, "빅융", MemberRole.MANAGER, now);
        Member horang = member(korea, "horang@korea.ac.kr", "박호랑", 61, "소보", MemberRole.USER, now);
        Member seoktap = member(korea, "seoktap@korea.ac.kr", "최석탑", 62, "인융", MemberRole.USER, now);

        School yonsei = school("YONSEI");
        School skku = school("SKKU");
        Member sinchon = member(yonsei, "sinchon@yonsei.ac.kr", "이신촌", 3, "인컴", MemberRole.USER, now);
        Member myeongnyun = member(skku, "myeongnyun@g.skku.edu", "박명륜", 12, "정보", MemberRole.USER, now);
        member(school("SOGANG"), "nogo@sogang.ac.kr", "최노고", 8, "데사", MemberRole.USER, now);
        member(school("HANYANG"), "wangsip@hanyang.ac.kr", "정왕십", 5, "인시", MemberRole.USER, now);
        // 카카오 없이 이메일로만 가입한 원우: 첫 화면 "이메일로 로그인"에서 imeil@korea.ac.kr / anam1234
        member(korea, "imeil@korea.ac.kr", "이메일", 62, "소보", MemberRole.USER, now)
                .setEmailPassword(new BCryptPasswordEncoder().encode("anam1234"));
        for (Member m : members.findAll()) {
            m.agreeTerms(now);
        }
        // 예전(아이디·비밀번호 시절) 회원: 가입 화면에서 "예전 계정 잇기"로 chamsari / anam1234 를 넣으면 이어진다
        members.save(Member.legacy("chamsari", new BCryptPasswordEncoder().encode("anam1234"), korea, "이참살", 60,
                department(korea, "소보"), now));

        LocalDate today = LocalDate.now(clock.withZone(KST));
        Instant tonight = today.atTime(LocalTime.of(22, 0)).atZone(KST).toInstant();
        if (!tonight.isAfter(now.plus(Duration.ofHours(1)))) {
            tonight = now.plus(Duration.ofHours(3));
        }
        Instant tomorrow = tonight.plus(Duration.ofDays(1)).minus(Duration.ofHours(3));

        Meetup m1 = meetup(godae, MeetupAudience.SCHOOL, "수업 끝나고 치맥 한잔?",
                "오늘 머신러닝 수업 끝나고 가볍게 한잔해요. 늦게 와도 OK", "안암역 2번 출구 근처 치킨집",
                List.of(MeetupCategory.BEER), List.of("친목", "잡담"), tonight, tonight.minus(Duration.ofMinutes(30)), 3,
                6, "https://open.kakao.com/o/example", now);
        m1.applyOptions(true, null, false); // 중간 합류 OK
        join(m1, anam, now);

        Meetup m2 = meetup(anam, MeetupAudience.SCHOOL, "과제 제출 기념 소주", "팀플 끝난 사람들 모여요 🙌", "참살이길 포차",
                List.of(MeetupCategory.SOJU, MeetupCategory.BEER), List.of("팀플 뒤풀이"), tomorrow,
                tomorrow.minus(Duration.ofHours(1)), 2, 4, null, now);
        join(m2, horang, now);

        meetup(horang, MeetupAudience.SCHOOL, "술 말고 밥 먹어요", "술은 못 하지만 사람은 좋아요", "고대 정문 앞 국밥집",
                List.of(MeetupCategory.NO_ALCOHOL), List.of("친목", "진로·커리어"), tomorrow.plus(Duration.ofHours(1)),
                tomorrow, null, null, null, now); // 최소 인원 없음 + 인원 제한 없음

        meetup(horang, MeetupAudience.COHORT, "61기끼리 하이볼", "우리 기수만 조용히 한잔해요", "참살이길 바",
                List.of(MeetupCategory.HIGHBALL), List.of("친목"), tonight.plus(Duration.ofMinutes(30)),
                tonight.minus(Duration.ofHours(1)), 2, 8, null, now); // 고려대 61기 전용

        // 연합 3교시: 다섯 학교 누구나 보고 참여한다
        Meetup united = meetup(sinchon, MeetupAudience.ALL, "IT연합회 첫 연합 번개",
                "학교 상관없이 편하게 와요. 다들 무슨 연구 하는지 궁금해요", "신촌역 3번 출구 호프",
                List.of(MeetupCategory.BEER, MeetupCategory.ANYTHING), List.of("친목", "네트워킹"),
                tomorrow.plus(Duration.ofHours(2)), tomorrow, 4, 12, null, now);
        united.applyOptions(true, 10000, false); // 중간 합류 OK · 참여비 1만 원(현장)
        join(united, myeongnyun, now);

        Meetup study = meetup(myeongnyun, MeetupAudience.ALL, "보안 스터디 뒤풀이", "정보보호 쪽 분들 같이 얘기해요", "혜화역 근처",
                List.of(MeetupCategory.SOJU), List.of("학업"), tonight.plus(Duration.ofHours(1)),
                tonight.minus(Duration.ofMinutes(10)), 2, 6, null, now);
        study.applyOptions(false, 20000, true); // 참여비 2만 원 선입금

        // 정원이 꽉 찬 3교시: 빈자리 대기(자리 나면 알림) · 정원 늘리기 확인용. 최석탑이 열었고 이안암이 기다린다
        Meetup full = meetup(seoktap, MeetupAudience.SCHOOL, "딱 셋이 와인 한 병", "조용히 와인 한 병만 나눠요", "참살이길 와인바",
                List.of(MeetupCategory.WINE), List.of("잡담"), tomorrow.plus(Duration.ofMinutes(30)),
                tomorrow.minus(Duration.ofHours(1)), 2, 3, null, now);
        join(full, godae, now);
        join(full, owner, now);
        waits.save(new MeetupWait(full, anam, now));

        meetup(sinchon, MeetupAudience.SCHOOL, "연세대끼리 저녁", "우리 학교 사람만 보는 3교시예요", "신촌 굴다리",
                List.of(MeetupCategory.ANYTHING), List.of(), tonight, tonight.minus(Duration.ofMinutes(20)), 2, 5,
                null, now);

        // 지난 3교시와 후기: 어제 성사된 3교시는 앱을 열면 후기를 묻고, 그 전 것들은 대학원장 통계에 숫자로 보인다
        LocalDate yesterday = today.minusDays(1);
        Meetup lastNight = meetup(godae, MeetupAudience.SCHOOL, "수업 끝나고 가볍게 한잔", "어제 모였던 3교시예요",
                "참살이길 호프", List.of(MeetupCategory.BEER), List.of("친목"), at(yesterday, 19, 30), at(yesterday, 18, 0),
                3, 6, "https://open.kakao.com/o/example", now);
        join(lastNight, anam, now);
        join(lastNight, horang, now);
        join(lastNight, seoktap, now);

        Meetup lastWeek = meetup(horang, MeetupAudience.SCHOOL, "중간고사 끝 소주", "시험 끝난 기념", "안암역 포차",
                List.of(MeetupCategory.SOJU), List.of("친목"), at(today.minusDays(5), 20, 0), at(today.minusDays(5), 18, 0),
                2, 6, null, now);
        join(lastWeek, godae, now);
        join(lastWeek, seoktap, now);
        join(lastWeek, owner, now);
        answered(lastWeek, horang, true, Rating.GOOD, now);
        answered(lastWeek, godae, true, Rating.GOOD, now);
        answered(lastWeek, seoktap, true, Rating.OK, now);
        answered(lastWeek, owner, true, Rating.BAD, now);
        // 정산: 박호랑이 계산했고(토스·카카오페이 링크), 김고대만 보냈다 — 대학원장·최석탑은 "보낼 정산"이 보인다
        horang.changePayLinks("horang_park", "https://qr.kakaopay.com/FSeedHorang01");
        Settlement soju = settlements.save(new Settlement(lastWeek, horang, 128_000, 4, now));
        SettlementShare paid = settlementShares.save(new SettlementShare(soju, godae));
        paid.mark(true, now);
        settlementShares.save(new SettlementShare(soju, seoktap));
        settlementShares.save(new SettlementShare(soju, owner));

        Meetup study2 = meetup(anam, MeetupAudience.SCHOOL, "논문 얘기 말고 수다만", "", "고대 정문 앞 카페",
                List.of(MeetupCategory.NO_ALCOHOL), List.of("잡담"), at(today.minusDays(9), 19, 0),
                at(today.minusDays(9), 17, 0), 2, 4, null, now);
        join(study2, godae, now);
        join(study2, horang, now);
        answered(study2, anam, true, Rating.GOOD, now);
        answered(study2, godae, true, Rating.OK, now);
        answered(study2, horang, false, null, now);

        Meetup unitedPast = meetup(sinchon, MeetupAudience.ALL, "연합 첫 번개 뒤풀이", "", "신촌역 호프",
                List.of(MeetupCategory.BEER), List.of("네트워킹"), at(today.minusDays(6), 19, 0),
                at(today.minusDays(6), 17, 0), 2, 8, null, now);
        join(unitedPast, myeongnyun, now);
        answered(unitedPast, sinchon, true, Rating.GOOD, now);
        answered(unitedPast, myeongnyun, true, Rating.GOOD, now);

        meetup(seoktap, MeetupAudience.SCHOOL, "인원 모자랐던 하이볼", "", "참살이길 바",
                List.of(MeetupCategory.HIGHBALL), List.of("친목"), at(today.minusDays(3), 21, 0),
                at(today.minusDays(3), 19, 0), 4, 6, null, now); // 무산

        Notice welcome = notices.save(new Notice(owner, null, "안암3교시에 오신 걸 환영해요 🍻",
                "IT연합회 다섯 학교가 함께 써요. 수업 끝나고 한잔하고 싶을 때, 3교시를 열어 보세요.\n\n"
                        + "• 최소 인원이 모이면 '성사'! 그때 참여자끼리 이름과 오픈채팅 링크가 공개돼요.\n"
                        + "• 우리 학교끼리, 또는 연합(다섯 학교 모두)으로 열 수 있어요.\n"
                        + "• 인원이 안 모이면 조용히 사라지니 부담 갖지 마세요.\n"
                        + "• 과음은 금물, 귀가는 안전하게!", now));
        welcome.pin(true);
        notices.save(new Notice(anam, korea, "다음 주 종강 파티 수요조사",
                "금요일 저녁 종강 파티 하실 분 3교시로 열어 볼게요. 장소 추천 받아요!", now));
        // 공지는 교수·대학원장만 쓴다: 대학원장이 연세대에만 보이게 올린 공지
        notices.save(new Notice(owner, yonsei, "연세대 원우 모임 안내", "이번 학기 연세대 원우 모임은 다음 주 목요일이에요.", now));

        log.info("로컬 샘플 데이터를 넣었습니다. 로그인 화면의 '개발용 로그인'으로 들어가세요");
    }

    private School school(String code) {
        return schools.findByCode(code).orElseThrow();
    }

    private Department department(School school, String shortName) {
        return departments.findAllByOrderBySortOrderAscIdAsc().stream()
                .filter(d -> d.belongsTo(school) && d.getShortName().equals(shortName))
                .findFirst()
                .orElseThrow();
    }

    private Member member(School school, String email, String name, int cohort, String departmentShortName,
            MemberRole role, Instant now) {
        return members.save(new Member(null, email, school, name, cohort, department(school, departmentShortName),
                role, now));
    }

    private Meetup meetup(Member host, MeetupAudience audience, String title, String desc, String place,
            List<MeetupCategory> categories, List<String> purposes, Instant meetAt, Instant deadline, Integer min,
            Integer max, String openChat, Instant now) {
        Meetup m = meetups.save(new Meetup(host, audience, title, desc, place, categories, purposes, meetAt, deadline,
                min, max, openChat, now));
        participations.save(new Participation(m, host, now));
        return m;
    }

    private static Instant at(LocalDate day, int hour, int minute) {
        return day.atTime(LocalTime.of(hour, minute)).atZone(KST).toInstant();
    }

    private void answered(Meetup meetup, Member member, boolean met, Rating rating, Instant now) {
        MeetupFeedback f = new MeetupFeedback(meetup, member, now);
        f.answer(met, rating, now);
        feedbacks.save(f);
    }

    private void join(Meetup meetup, Member member, Instant now) {
        participations.save(new Participation(meetup, member, now));
        meetup.addParticipant();
    }
}
