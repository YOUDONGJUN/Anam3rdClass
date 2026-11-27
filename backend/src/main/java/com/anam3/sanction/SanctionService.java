package com.anam3.sanction;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.anam3.auth.LoginMember;
import com.anam3.common.ApiException;
import com.anam3.member.AccountService;
import com.anam3.member.Member;
import com.anam3.member.MemberStatus;
import com.anam3.push.PushNotifier;
import com.anam3.safety.Report;
import com.anam3.sanction.MemberSanction.Kind;
import com.anam3.sanction.SanctionDtos.SanctionNotice;
import com.anam3.sanction.SanctionDtos.SanctionView;

/**
 * 제재. 교수·대학원장이 경고를 주면 쌓인 수에 따라 단계가 정해진다 (1번째 경고 · 2번째 7일 정지 · 3번째 30일 정지 ·
 * 4번째부터 영구 정지). 신고만으로는 제재하지 않는다 — 허위 신고로 남을 막지 못하게, 사람이 확인하고 준다.
 * 누가 줄 수 있는지는 AdminService 가 먼저 본다 (교수는 자기 학교 원우, 대학원장은 모두).
 */
@Service
@Transactional
public class SanctionService {

    private static final DateTimeFormatter UNTIL = DateTimeFormatter.ofPattern("M/d(E) HH:mm", Locale.KOREAN)
            .withZone(ZoneId.of("Asia/Seoul"));

    private final MemberSanctionRepository sanctions;
    private final AccountService accountService;
    private final PushNotifier notifier;
    private final Clock clock;

    public SanctionService(MemberSanctionRepository sanctions, AccountService accountService, PushNotifier notifier,
            Clock clock) {
        this.sanctions = sanctions;
        this.accountService = accountService;
        this.notifier = notifier;
        this.clock = clock;
    }

    /** 기간 정지 중일 때 로그인·요청을 막는 오류 */
    public static ApiException suspended(Instant until) {
        return new ApiException(HttpStatus.FORBIDDEN, "SUSPENDED", suspendedMessage(until));
    }

    public static String suspendedMessage(Instant until) {
        return "이용이 정지된 계정이에요. " + UNTIL.format(until) + "부터 다시 쓸 수 있어요";
    }

    /** 경고 주기: 단계를 정해 기록하고, 정지면 바로 막는다. 당사자에게 알린다 */
    public MemberSanction warn(Member actor, Member target, Report.Reason reason, String note, Report report) {
        if (target.getStatus() == MemberStatus.BANNED) {
            throw ApiException.badRequest("ALREADY_BANNED", "이미 영구 정지된 원우예요");
        }
        Instant now = clock.instant();
        int strike = (int) sanctions.countByMemberIdAndRevokedAtIsNull(target.getId()) + 1;
        Kind kind = Kind.forStrike(strike);
        MemberSanction sanction = sanctions.save(new MemberSanction(target, kind, reason, blankToNull(note), report,
                actor, now));
        switch (kind) {
            case WARNING -> {
            }
            case SUSPEND_7, SUSPEND_30 -> {
                target.suspendUntil(sanction.getEndsAt());
                // 정지 동안 못 오니, 연 3교시는 취소하고 모이기 전인 3교시에서는 뺀다 (영구 정지·탈퇴와 같다)
                accountService.releaseOpenMeetups(target, now);
            }
            case BAN -> {
                target.ban();
                accountService.releaseOpenMeetups(target, now);
            }
        }
        notifier.sanctioned(target.getId(), kind.label(), reason.label(), sanction.getEndsAt());
        return sanction;
    }

    /** 관리 화면에서 바로 "이용 차단"한 것도 기록에 남긴다 (영구 정지 한 줄) */
    public void recordBan(Member actor, Member target) {
        sanctions.save(new MemberSanction(target, Kind.BAN, Report.Reason.OTHER, null, null, actor, clock.instant()));
    }

    /**
     * 잘못 준 경고 취소: 단계에서 빠지고, 이 경고로 걸린 정지는 푼다
     * (다른 정지가 남아 있으면 그 기간으로, 영구 정지는 남은 영구 정지가 없을 때 푼다)
     */
    public MemberSanction revoke(Member actor, MemberSanction sanction) {
        if (sanction.isRevoked()) {
            return sanction;
        }
        Instant now = clock.instant();
        sanction.revoke(actor, now);
        Member member = sanction.getMember();
        List<MemberSanction> active = sanctions.findByMemberIdAndRevokedAtIsNull(member.getId()).stream()
                .filter(s -> !s.getId().equals(sanction.getId()))
                .toList();
        if (sanction.getKind() == Kind.BAN && member.getStatus() == MemberStatus.BANNED
                && active.stream().noneMatch(s -> s.getKind() == Kind.BAN)) {
            member.unban();
        }
        if (sanction.getKind().length() != null) {
            Instant until = active.stream()
                    .map(MemberSanction::getEndsAt)
                    .filter(end -> end != null && end.isAfter(now))
                    .max(Comparator.naturalOrder())
                    .orElse(null);
            member.changeSuspension(until);
        }
        return sanction;
    }

    @Transactional(readOnly = true)
    public Optional<MemberSanction> find(Long sanctionId) {
        return sanctions.findById(sanctionId);
    }

    /** 지금 단계 (취소하지 않은 경고 수) */
    @Transactional(readOnly = true)
    public int strikes(Long memberId) {
        return (int) sanctions.countByMemberIdAndRevokedAtIsNull(memberId);
    }

    @Transactional(readOnly = true)
    public Map<Long, Integer> strikes(Collection<Long> memberIds) {
        Map<Long, Integer> result = new HashMap<>();
        if (memberIds.isEmpty()) {
            return result;
        }
        for (Object[] row : sanctions.countActive(memberIds)) {
            result.put((Long) row[0], ((Number) row[1]).intValue());
        }
        return result;
    }

    /** 관리 화면: 한 원우의 제재 기록 (최근 것부터). 취소하지 않은 것에는 몇 번째였는지 붙인다 */
    @Transactional(readOnly = true)
    public List<SanctionView> history(Long memberId) {
        List<MemberSanction> found = sanctions.findHistory(memberId);
        Map<Long, Integer> strikeOf = new HashMap<>();
        int n = 0;
        List<MemberSanction> oldestFirst = new ArrayList<>(found);
        Collections.reverse(oldestFirst);
        for (MemberSanction s : oldestFirst) {
            if (!s.isRevoked()) {
                strikeOf.put(s.getId(), ++n);
            }
        }
        return found.stream().map(s -> new SanctionView(s.getId(), s.getKind(), s.getKind().label(), s.getReason(),
                s.getReason().label(), s.getNote(), s.getReport() == null ? null : s.getReport().getId(),
                s.getActor().getName(), s.getCreatedAt(), s.getEndsAt(), s.getAcknowledgedAt(), s.getRevokedAt(),
                strikeOf.get(s.getId()))).toList();
    }

    /** 경고 받은 원우: 아직 확인하지 않은 경고 (오래된 것 하나). 없으면 null */
    @Transactional(readOnly = true)
    public SanctionNotice notice(LoginMember viewer) {
        List<MemberSanction> pending = sanctions.findUnacknowledged(viewer.id());
        if (pending.isEmpty()) {
            return null;
        }
        MemberSanction s = pending.getFirst();
        int strikes = strikes(viewer.id());
        return new SanctionNotice(s.getId(), s.getKind(), s.getKind().label(), s.getReason().label(), s.getNote(),
                s.getCreatedAt(), s.getEndsAt(), strikes, Kind.forStrike(strikes + 1).label());
    }

    /** 경고 확인 (본인 것만) */
    public void acknowledge(LoginMember viewer, Long sanctionId) {
        MemberSanction s = sanctions.findById(sanctionId)
                .filter(x -> x.getMember().getId().equals(viewer.id()))
                .orElseThrow(() -> ApiException.notFound("경고를 찾을 수 없어요"));
        s.acknowledge(clock.instant());
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.strip();
    }
}
