package com.anam3.settlement;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.anam3.auth.LoginMember;
import com.anam3.common.ApiException;
import com.anam3.meetup.Meetup;
import com.anam3.meetup.MeetupDtos.PersonView;
import com.anam3.meetup.MeetupMapper;
import com.anam3.meetup.MeetupRepository;
import com.anam3.meetup.Participation;
import com.anam3.meetup.ParticipationRepository;
import com.anam3.member.Member;
import com.anam3.push.PushNotifier;
import com.anam3.settlement.SettlementDtos.SettlementDue;
import com.anam3.settlement.SettlementDtos.SettlementInfo;
import com.anam3.settlement.SettlementDtos.SettlementRequest;
import com.anam3.settlement.SettlementDtos.SettlementView;
import com.anam3.settlement.SettlementDtos.ShareView;

/**
 * 정산. 성사된 3교시가 모인 뒤 7일 안에, 함께한 사람 누구든 계산한 사람이 되어 만든다.
 * 함께한 사람끼리만 보고, 실명은 3교시 공개 규칙(성사된 3교시의 참여자끼리 실명)을 따른다.
 * 돈은 서비스가 받지 않는다 — 송금은 각자 토스·카카오페이 앱에서 하고, 누가 보냈는지만 적는다.
 */
@Service
@Transactional
public class SettlementService {

    /** 모인 뒤 이 기간 안에 정산을 만들 수 있다 (만든 정산은 기간이 지나도 보고 표시할 수 있다) */
    static final Duration OPEN_FOR = Duration.ofDays(7);
    /** 다시 알리기 간격 */
    static final Duration REMIND_EVERY = Duration.ofHours(24);
    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("M월 d일 HH:mm")
            .withZone(ZoneId.of("Asia/Seoul"));

    private final SettlementRepository settlements;
    private final SettlementShareRepository shares;
    private final MeetupRepository meetups;
    private final ParticipationRepository participations;
    private final MeetupMapper mapper;
    private final PushNotifier notifier;
    private final Clock clock;

    public SettlementService(SettlementRepository settlements, SettlementShareRepository shares,
            MeetupRepository meetups, ParticipationRepository participations, MeetupMapper mapper,
            PushNotifier notifier, Clock clock) {
        this.settlements = settlements;
        this.shares = shares;
        this.meetups = meetups;
        this.participations = participations;
        this.mapper = mapper;
        this.notifier = notifier;
        this.clock = clock;
    }

    /** 정산을 만들 수 있는 때: 성사돼서 여럿이 모였고(혼자 끝난 3교시 제외), 모인 뒤 7일 안 */
    static boolean openFor(Meetup meetup, Instant now) {
        return meetup.isGatheredAt(now) && now.isBefore(meetup.getMeetAt().plus(OPEN_FOR));
    }

    /** 토스 송금 링크에 금액을 붙인다 (toss.me/아이디/금액) */
    static String tossUrl(String tossId, int amount) {
        if (tossId == null) {
            return null;
        }
        return "https://toss.me/" + URLEncoder.encode(tossId, StandardCharsets.UTF_8).replace("+", "%20") + "/"
                + amount;
    }

    // ---------- 보기 ----------

    /** 3교시 상세에 붙일 정산 칸: 함께한 사람에게만 */
    @Transactional(readOnly = true)
    public SettlementInfo info(Meetup meetup, List<Participation> people, LoginMember viewer, boolean joined,
            Instant now) {
        if (!joined) {
            return SettlementInfo.NONE;
        }
        return settlements.findByMeetupId(meetup.getId())
                .map(s -> new SettlementInfo(false, view(s, meetup, people, viewer, now)))
                .orElseGet(() -> new SettlementInfo(openFor(meetup, now), null));
    }

    private SettlementView view(Settlement s, Meetup meetup, List<Participation> people, LoginMember viewer,
            Instant now) {
        boolean revealed = MeetupMapper.canSeeRealNames(meetup, viewer, true);
        Map<Long, Long> refs = new HashMap<>();
        people.forEach(p -> refs.put(p.getMember().getId(), p.getId()));
        List<SettlementShare> list = shares.findWithMember(s.getId());
        List<ShareView> views = list.stream()
                .map(sh -> new ShareView(person(sh.getMember(), meetup, viewer, revealed, refs), sh.isSent(),
                        sh.getSentAt()))
                .toList();
        int sent = (int) list.stream().filter(SettlementShare::isSent).count();
        boolean mine = s.isPaidBy(viewer.id());
        Instant remindableAt = s.getRemindedAt().plus(REMIND_EVERY);
        boolean waiting = sent < list.size();
        Member payer = s.getPayer();
        return new SettlementView(person(payer, meetup, viewer, revealed, refs), mine, s.getTotal(),
                s.getPerPerson(), list.size() + 1, views, sent, tossUrl(payer.getTossId(), s.getPerPerson()),
                payer.getKakaoPayUrl(), mine && sent == 0, mine && waiting && !now.isBefore(remindableAt),
                mine && waiting ? remindableAt : null, s.isDone(), s.getCreatedAt());
    }

    private PersonView person(Member member, Meetup meetup, LoginMember viewer,
            boolean revealed, Map<Long, Long> refs) {
        return mapper.personView(member, viewer, revealed, meetup.isHostedBy(member.getId()),
                refs.get(member.getId()));
    }

    /** 내가 보낼 정산: 아직 안 보냈고 정산이 안 끝난 것 */
    @Transactional(readOnly = true)
    public List<SettlementDue> dues(LoginMember viewer) {
        return shares.findDueOf(viewer.id()).stream().map(sh -> {
            Settlement s = sh.getSettlement();
            Meetup m = s.getMeetup();
            Member payer = s.getPayer();
            boolean revealed = MeetupMapper.canSeeRealNames(m, viewer, true);
            return new SettlementDue(m.getId(), m.getTitle(), m.getMeetAt(), s.getPerPerson(),
                    mapper.personView(payer, viewer, revealed, m.isHostedBy(payer.getId()), null),
                    tossUrl(payer.getTossId(), s.getPerPerson()), payer.getKakaoPayUrl());
        }).toList();
    }

    // ---------- 계산한 사람 ----------

    /** 정산 만들기: 함께한 사람 누구든 (계산한 사람이 된다) */
    public void create(LoginMember viewer, Long meetupId, SettlementRequest req) {
        Instant now = clock.instant();
        // 두 사람이 동시에 만들지 않게 3교시를 잠근다
        Meetup meetup = meetups.findByIdForUpdate(meetupId).orElseThrow(SettlementService::meetupNotFound);
        List<Participation> people = participations.findWithMember(meetupId);
        Member payer = people.stream().map(Participation::getMember).filter(m -> m.getId().equals(viewer.id()))
                .findFirst()
                .orElseThrow(() -> ApiException.forbidden("함께한 3교시만 정산할 수 있어요"));
        if (!openFor(meetup, now)) {
            throw ApiException.badRequest("NOT_SETTLING", "정산은 성사된 3교시가 모인 뒤 7일 안에 할 수 있어요");
        }
        if (settlements.findByMeetupId(meetupId).isPresent()) {
            throw alreadySettled();
        }
        List<Member> debtors = pick(people, viewer, req.refs());
        Settlement settlement = new Settlement(meetup, payer, req.total(), debtors.size() + 1, now);
        try {
            settlements.saveAndFlush(settlement);
        } catch (DataIntegrityViolationException e) {
            throw alreadySettled();
        }
        debtors.forEach(m -> shares.save(new SettlementShare(settlement, m)));
        notifier.settlementRequested(settlement, debtors.stream().map(Member::getId).toList(), false);
    }

    /** 고치기: 계산한 사람, 아직 아무도 안 보냈을 때만 (보낸 사람이 있으면 취소하고 다시 만든다) */
    public void update(LoginMember viewer, Long meetupId, SettlementRequest req) {
        Instant now = clock.instant();
        Settlement settlement = lockOwn(viewer, meetupId, "계산한 원우만 고칠 수 있어요");
        List<SettlementShare> list = shares.findWithMember(settlement.getId());
        if (list.stream().anyMatch(SettlementShare::isSent)) {
            throw ApiException.conflict("SETTLEMENT_LOCKED", "이미 보낸 원우가 있어서 고칠 수 없어요. 취소하고 다시 만들어 주세요");
        }
        List<Member> debtors = pick(participations.findWithMember(meetupId), viewer, req.refs());
        shares.deleteAllInBatch(list);
        settlement.change(req.total(), debtors.size() + 1, now);
        settlement.reminded(now);
        debtors.forEach(m -> shares.save(new SettlementShare(settlement, m)));
        notifier.settlementRequested(settlement, debtors.stream().map(Member::getId).toList(), true);
    }

    /** 취소: 계산한 사람 (보낸 원우가 있어도 된다 — 다시 만들려고) */
    public void cancel(LoginMember viewer, Long meetupId) {
        Settlement settlement = lockOwn(viewer, meetupId, "계산한 원우만 취소할 수 있어요");
        List<Long> debtors = shares.findMemberIds(settlement.getId()); // 이미 보낸 원우도 취소를 알아야 한다
        notifier.settlementCanceled(settlement, debtors);
        shares.deleteBySettlementId(settlement.getId());
        settlements.delete(settlement);
    }

    /** 다시 알리기: 계산한 사람, 아직 안 보낸 원우에게, 하루에 한 번 */
    public void remind(LoginMember viewer, Long meetupId) {
        Instant now = clock.instant();
        Settlement settlement = lockOwn(viewer, meetupId, "계산한 원우만 다시 알릴 수 있어요");
        List<Long> unsent = shares.findUnsentMemberIds(settlement.getId());
        if (unsent.isEmpty()) {
            throw ApiException.badRequest("SETTLEMENT_DONE", "모두 보냈어요");
        }
        Instant next = settlement.getRemindedAt().plus(REMIND_EVERY);
        if (now.isBefore(next)) {
            throw ApiException.badRequest("REMIND_LIMIT",
                    "다시 알리기는 하루에 한 번이에요. " + WHEN.format(next) + "부터 다시 알릴 수 있어요");
        }
        settlement.reminded(now);
        notifier.settlementReminded(settlement, unsent);
    }

    /** 계산한 사람이 한 사람 몫을 받았어요(또는 취소)로 표시 — 현금으로 받았을 때 */
    public void markShare(LoginMember viewer, Long meetupId, Long ref, boolean sent) {
        Settlement settlement = lockOwn(viewer, meetupId, "계산한 원우만 받았는지 표시할 수 있어요");
        Long memberId = participations.findById(ref)
                .filter(p -> p.getMeetup().getId().equals(meetupId))
                .map(p -> p.getMember().getId())
                .orElseThrow(SettlementService::shareNotFound);
        mark(settlement, viewer, memberId, sent);
    }

    // ---------- 보낼 사람 ----------

    /** 보냈어요 (또는 취소) */
    public void markMine(LoginMember viewer, Long meetupId, boolean sent) {
        Settlement settlement = settlements.findByMeetupIdForUpdate(meetupId)
                .orElseThrow(SettlementService::settlementNotFound);
        mark(settlement, viewer, viewer.id(), sent);
    }

    private void mark(Settlement settlement, LoginMember viewer, Long memberId, boolean sent) {
        Instant now = clock.instant();
        List<SettlementShare> list = shares.findWithMember(settlement.getId());
        SettlementShare share = list.stream().filter(sh -> sh.getMember().getId().equals(memberId)).findFirst()
                .orElseThrow(() -> memberId.equals(viewer.id())
                        ? ApiException.forbidden("내가 보낼 정산이 아니에요")
                        : shareNotFound());
        share.mark(sent, now);
        boolean all = list.stream().allMatch(SettlementShare::isSent);
        if (all && !settlement.isDone()) {
            settlement.done(now);
            notifier.settlementDone(settlement, viewer.id());
        } else if (!all && settlement.isDone()) {
            settlement.reopen();
        }
    }

    /** 3교시를 지울 때 */
    public void deleteFor(Long meetupId) {
        shares.deleteByMeetupId(meetupId);
        settlements.deleteByMeetupId(meetupId);
    }

    // ---------- 도우미 ----------

    /** 나눌 사람: 이 3교시의 참여자(참여 번호로), 계산한 사람은 빼고, 탈퇴한 원우는 넣을 수 없다 */
    private static List<Member> pick(List<Participation> people, LoginMember viewer, List<Long> refs) {
        Map<Long, Participation> byRef = new HashMap<>();
        people.forEach(p -> byRef.put(p.getId(), p));
        List<Member> picked = new ArrayList<>();
        for (Long ref : new LinkedHashSet<>(refs)) {
            Participation p = byRef.get(ref);
            if (p == null) {
                throw ApiException.badRequest("BAD_SHARE", "함께한 원우만 고를 수 있어요");
            }
            Member m = p.getMember();
            if (m.getId().equals(viewer.id())) {
                continue; // 계산한 사람 몫은 요청하지 않는다
            }
            if (m.isWithdrawn()) {
                throw ApiException.badRequest("WITHDRAWN_SHARE", "탈퇴한 원우는 정산에 넣을 수 없어요");
            }
            picked.add(m);
        }
        if (picked.isEmpty()) {
            throw ApiException.badRequest("NO_SHARE", "나 말고 한 명 이상 골라 주세요");
        }
        return picked;
    }

    /** 내가 계산한 정산을 잠그고 가져온다 */
    private Settlement lockOwn(LoginMember viewer, Long meetupId, String notPayer) {
        Settlement settlement = settlements.findByMeetupIdForUpdate(meetupId)
                .orElseThrow(SettlementService::settlementNotFound);
        if (!settlement.isPaidBy(viewer.id())) {
            throw ApiException.forbidden(notPayer);
        }
        return settlement;
    }

    private static ApiException meetupNotFound() {
        return ApiException.notFound("3교시를 찾을 수 없어요");
    }

    private static ApiException settlementNotFound() {
        return ApiException.notFound("정산이 없어요. 새로고침해 주세요");
    }

    private static ApiException shareNotFound() {
        return ApiException.notFound("정산에 없는 원우예요");
    }

    private static ApiException alreadySettled() {
        return ApiException.conflict("ALREADY_SETTLED", "이미 다른 원우가 정산을 만들었어요. 새로고침해 주세요");
    }
}
