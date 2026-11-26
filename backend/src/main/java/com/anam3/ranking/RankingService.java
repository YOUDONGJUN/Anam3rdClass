package com.anam3.ranking;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.anam3.auth.LoginMember;
import com.anam3.common.NameMasker;
import com.anam3.meetup.MeetupRepository;
import com.anam3.meetup.ParticipationRepository;
import com.anam3.member.Member;
import com.anam3.member.MemberRepository;
import com.anam3.member.MemberStatus;
import com.anam3.ranking.RankingDtos.CategoryView;
import com.anam3.ranking.RankingDtos.Entry;
import com.anam3.ranking.RankingDtos.RankingView;
import com.anam3.ranking.RankingDtos.SchoolRow;
import com.anam3.school.School;
import com.anam3.school.SchoolDtos.SchoolTag;
import com.anam3.school.SchoolRepository;

/**
 * 랭킹. 그때그때 센다 (원우 수백 명 규모라 따로 저장하지 않는다).
 * <ul>
 *   <li>셀 3교시: 성사된 뒤 모임 시간이 지난 것만 — 열고 취소하기를 반복해 점수를 부풀릴 수 없게</li>
 *   <li>사람: 활동 중이고 "랭킹에 안 나오기"를 켜지 않은 원우. 이름은 가려서 (내 이름·대학원장 이름만 그대로)</li>
 *   <li>상위 10명 + 내 기록만 보여 준다 (꼴찌·전체 순위는 없다)</li>
 *   <li>같은 기록이면 같은 등수 (1, 1, 3)</li>
 * </ul>
 * 이번 달 방장왕·출석왕 1등(2번 이상)은 3교시 화면에서 이름 옆에 배지가 붙는다 (crowns, 1분 캐시).
 */
@Service
@Transactional(readOnly = true)
public class RankingService {

    static final int TOP = 10;
    /** 성사율은 이만큼 이상 연 사람만 */
    static final int RATE_MIN_HOSTED = 3;
    /** 배지는 이만큼 이상일 때만 (달 초에 한 번씩 탄 사람 모두가 1등이 되지 않게) */
    static final int CROWN_MIN = 2;
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    /** 배지를 주는 카테고리 */
    private static final Set<RankingCategory> CROWNED = EnumSet.of(RankingCategory.HOST, RankingCategory.ATTEND);

    private final ParticipationRepository participations;
    private final MeetupRepository meetups;
    private final MemberRepository members;
    private final SchoolRepository schools;
    private final Clock clock;

    private volatile CrownCache crownCache;

    private record CrownCache(long minute, Map<Long, List<RankingCategory>> crowns) {
    }

    /** 한 사람의 카테고리 기록: value(횟수, 성사율은 성사된 수), of(성사율만: 연 수) */
    private record Score(Member member, int value, int of) {
    }

    /** 학교 대항 한 줄 (정렬 전) */
    private record SchoolCount(School school, long members, long riders, long meetups) {

        double rate() {
            return members == 0 ? 0 : (double) riders / members;
        }
    }

    public RankingService(ParticipationRepository participations, MeetupRepository meetups, MemberRepository members,
            SchoolRepository schools, Clock clock) {
        this.participations = participations;
        this.meetups = meetups;
        this.members = members;
        this.schools = schools;
        this.clock = clock;
    }

    /**
     * @param allTime    true 면 전체 기간, false 면 이번 달(KST)
     * @param schoolOnly true 면 우리 학교 원우끼리 (학교 대항은 늘 다섯 학교)
     */
    public RankingView ranking(LoginMember viewer, boolean allTime, boolean schoolOnly) {
        return ranking(viewer, allTime, schoolOnly, null);
    }

    /**
     * @param schoolId 학교 하나만 볼 때 그 학교 (대학원장만 다른 학교를 고를 수 있다 — 아니면 우리 학교)
     */
    public RankingView ranking(LoginMember viewer, boolean allTime, boolean schoolOnly, Long schoolId) {
        Long school = viewer.isOwner() && schoolId != null ? schoolId : viewer.schoolId();
        Instant now = clock.instant();
        Instant since = allTime ? Instant.EPOCH : monthStart(now);
        List<RankingRide> rides = participations.findRides(since, now);
        Map<Long, Member> active = members.findAllActiveWithSchool(MemberStatus.ACTIVE).stream()
                .collect(Collectors.toMap(Member::getId, Function.identity()));

        Map<RankingCategory, Map<Long, int[]>> values = new LinkedHashMap<>();
        values.put(RankingCategory.HOST, countBy(hostRides(rides)));
        values.put(RankingCategory.ATTEND, countBy(rides));
        values.put(RankingCategory.RATE, rates(meetups.findHosted(since, now)));
        values.put(RankingCategory.FEDERATION, countBy(withOtherSchool(rides)));

        List<CategoryView> categories = new ArrayList<>();
        values.forEach((category, v) -> categories.add(category(category, v, active, viewer,
                schoolOnly ? school : null)));
        return new RankingView(allTime ? "all" : "month", schoolOnly ? "school" : "all", allTime ? null : since,
                categories, schoolRows(rides, active.values(), viewer), schoolOnly ? school : null);
    }

    /**
     * 이번 달 방장왕·출석왕 1등 (연합 전체, CROWN_MIN 번 이상). 회원 번호 → 받은 배지.
     * 3교시 화면마다 부르므로 1분 동안 같은 값을 쓴다
     */
    public Map<Long, List<RankingCategory>> crowns() {
        Instant now = clock.instant();
        long minute = now.getEpochSecond() / 60;
        CrownCache cached = crownCache;
        if (cached != null && cached.minute() == minute) {
            return cached.crowns();
        }
        List<RankingRide> rides = participations.findRides(monthStart(now), now);
        Map<RankingCategory, Map<Long, int[]>> values = Map.of(
                RankingCategory.HOST, countBy(hostRides(rides)),
                RankingCategory.ATTEND, countBy(rides));
        Set<Long> ids = new HashSet<>();
        values.values().forEach(v -> ids.addAll(v.keySet()));
        Set<Long> ranked = ids.isEmpty() ? Set.of() : new HashSet<>(members.findRankedIds(ids, MemberStatus.ACTIVE));

        Map<Long, List<RankingCategory>> crowns = new HashMap<>();
        for (RankingCategory category : CROWNED) {
            Map<Long, int[]> v = values.get(category);
            int best = v.entrySet().stream().filter(e -> ranked.contains(e.getKey()))
                    .mapToInt(e -> e.getValue()[0]).max().orElse(0);
            if (best < CROWN_MIN) {
                continue;
            }
            v.forEach((id, value) -> {
                if (ranked.contains(id) && value[0] == best) {
                    crowns.computeIfAbsent(id, k -> new ArrayList<>()).add(category);
                }
            });
        }
        Map<Long, List<RankingCategory>> frozen = new HashMap<>();
        crowns.forEach((id, list) -> frozen.put(id, list.stream().sorted().toList()));
        CrownCache next = new CrownCache(minute, Map.copyOf(frozen));
        crownCache = next;
        return next.crowns();
    }

    /** 테스트에서 데이터를 지운 뒤 부른다 (시각이 같으면 캐시가 남아서) */
    public void clearCache() {
        crownCache = null;
    }

    /** 이번 달 1일 0시 (KST) */
    static Instant monthStart(Instant now) {
        return ZonedDateTime.ofInstant(now, SEOUL).withDayOfMonth(1).truncatedTo(ChronoUnit.DAYS).toInstant();
    }

    private CategoryView category(RankingCategory category, Map<Long, int[]> values, Map<Long, Member> active,
            LoginMember viewer, Long onlySchool) {
        boolean rate = category == RankingCategory.RATE;
        List<Score> scores = new ArrayList<>();
        values.forEach((id, v) -> {
            Member m = active.get(id);
            if (m == null || m.isRankingHidden() || (onlySchool != null && !m.isSameSchool(onlySchool))) {
                return;
            }
            if (counts(v, rate)) {
                scores.add(new Score(m, v[0], v[1]));
            }
        });
        Comparator<Score> byRecord = rate
                ? Comparator.<Score>comparingDouble(s -> (double) s.value() / s.of()).reversed()
                        .thenComparing(Comparator.comparingInt(Score::of).reversed())
                : Comparator.comparingInt(Score::value).reversed();
        scores.sort(byRecord.thenComparing(s -> s.member().getId()));

        List<Entry> top = new ArrayList<>();
        Entry mine = null;
        int rank = 0;
        for (int i = 0; i < scores.size(); i++) {
            Score s = scores.get(i);
            if (i == 0 || byRecord.compare(scores.get(i - 1), s) != 0) {
                rank = i + 1;
            }
            Entry entry = entry(rank, s, viewer, rate);
            if (i < TOP) {
                top.add(entry);
            }
            if (s.member().getId().equals(viewer.id())) {
                mine = entry;
            }
        }
        if (mine == null) {
            // 랭킹에 안 나오는 중이어도 내 기록은 보여 준다 (순위 없이)
            int[] v = values.get(viewer.id());
            Member me = active.get(viewer.id());
            // 대학원장이 다른 학교를 볼 때는 내 기록을 섞지 않는다
            boolean inScope = me != null && (onlySchool == null || me.isSameSchool(onlySchool));
            if (v != null && inScope && counts(v, rate)) {
                mine = entry(null, new Score(me, v[0], v[1]), viewer, rate);
            }
        }
        return new CategoryView(category, top, mine);
    }

    /** 랭킹에 올릴 기록인지: 횟수는 1번 이상, 성사율은 3번 이상 열고 한 번이라도 성사 */
    private static boolean counts(int[] v, boolean rate) {
        return rate ? v[1] >= RATE_MIN_HOSTED && v[0] > 0 : v[0] > 0;
    }

    private static Entry entry(Integer rank, Score s, LoginMember viewer, boolean rate) {
        Member m = s.member();
        boolean me = m.getId().equals(viewer.id());
        boolean showReal = me || m.isOwner();
        return new Entry(rank, showReal ? m.getName() : NameMasker.mask(m.getName()), !showReal, me, m.isOwner(),
                SchoolTag.from(m.getSchool()), m.departmentShortLabel(), s.value(), rate ? s.of() : null);
    }

    /** 학교 대항: 참여율 높은 순 (같으면 탄 원우가 많은 순). 참여율이 같으면 같은 등수 */
    private List<SchoolRow> schoolRows(List<RankingRide> rides, Collection<Member> active, LoginMember viewer) {
        Map<Long, Long> membersBySchool = active.stream()
                .collect(Collectors.groupingBy(m -> m.getSchool().getId(), Collectors.counting()));
        Set<Long> activeIds = active.stream().map(Member::getId).collect(Collectors.toSet());
        Map<Long, Set<Long>> ridersBySchool = new HashMap<>();
        Map<Long, Set<Long>> meetupsBySchool = new HashMap<>();
        for (RankingRide r : rides) {
            if (activeIds.contains(r.memberId())) {
                ridersBySchool.computeIfAbsent(r.memberSchoolId(), k -> new HashSet<>()).add(r.memberId());
            }
            meetupsBySchool.computeIfAbsent(r.meetupSchoolId(), k -> new HashSet<>()).add(r.meetupId());
        }
        List<SchoolCount> rows = new ArrayList<>();
        for (School s : schools.findAllByOrderBySortOrderAscIdAsc()) {
            rows.add(new SchoolCount(s, membersBySchool.getOrDefault(s.getId(), 0L),
                    ridersBySchool.getOrDefault(s.getId(), Set.of()).size(),
                    meetupsBySchool.getOrDefault(s.getId(), Set.of()).size()));
        }
        Comparator<SchoolCount> byRate = Comparator.comparingDouble(SchoolCount::rate).reversed();
        rows.sort(byRate.thenComparing(Comparator.comparingLong(SchoolCount::riders).reversed()));
        List<SchoolRow> result = new ArrayList<>();
        int rank = 0;
        for (int i = 0; i < rows.size(); i++) {
            SchoolCount r = rows.get(i);
            if (i == 0 || byRate.compare(rows.get(i - 1), r) != 0) {
                rank = i + 1;
            }
            result.add(new SchoolRow(rank, SchoolTag.from(r.school()), r.members(), r.riders(), r.meetups(),
                    r.school().getId().equals(viewer.schoolId())));
        }
        return result;
    }

    /** 연 사람의 기록만 (방장왕) */
    private static List<RankingRide> hostRides(List<RankingRide> rides) {
        return rides.stream().filter(r -> r.memberId().equals(r.hostId())).toList();
    }

    /** 회원 번호 → [횟수, 0] */
    private static Map<Long, int[]> countBy(List<RankingRide> rides) {
        Map<Long, int[]> counts = new HashMap<>();
        for (RankingRide r : rides) {
            counts.computeIfAbsent(r.memberId(), k -> new int[2])[0]++;
        }
        return counts;
    }

    /** 회원 번호 → [성사된 수, 연 수] */
    private static Map<Long, int[]> rates(List<RankingHosted> hosted) {
        Map<Long, int[]> rates = new HashMap<>();
        for (RankingHosted h : hosted) {
            int[] v = rates.computeIfAbsent(h.hostId(), k -> new int[2]);
            v[1]++;
            if (h.reached()) {
                v[0]++;
            }
        }
        return rates;
    }

    /** 연합왕: 탄 사람의 학교가 둘 이상인 연합 3교시의 기록만 (그런 3교시면 누구에게나 다른 학교 원우가 있다) */
    private static List<RankingRide> withOtherSchool(List<RankingRide> rides) {
        Map<Long, Set<Long>> schoolsByMeetup = new HashMap<>();
        for (RankingRide r : rides) {
            if (r.federated()) {
                schoolsByMeetup.computeIfAbsent(r.meetupId(), k -> new HashSet<>()).add(r.memberSchoolId());
            }
        }
        return rides.stream()
                .filter(r -> r.federated() && schoolsByMeetup.getOrDefault(r.meetupId(), Set.of()).size() >= 2)
                .toList();
    }
}
