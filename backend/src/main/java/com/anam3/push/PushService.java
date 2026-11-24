package com.anam3.push;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.anam3.auth.LoginMember;
import com.anam3.common.ApiException;
import com.anam3.member.Member;
import com.anam3.member.MemberRepository;
import com.anam3.member.MemberResponse;
import com.anam3.member.MemberStatus;

/**
 * 앱 푸시: 기기 등록, 받을 알림 종류, 보내기.
 * 보내기는 지금 트랜잭션이 커밋된 뒤에 요청 스레드 밖(PushDispatcher)에서 한다 — 롤백된 일은 알리지 않고, 화면은 기다리지 않는다.
 */
@Service
public class PushService {

    /** 한 사람이 알림 받는 기기 수 (넘으면 오래 안 연 기기부터 뺀다) */
    static final int MAX_DEVICES_PER_MEMBER = 5;

    private final PushDeviceRepository devices;
    private final MemberRepository members;
    private final PushSender sender;
    private final PushDispatcher dispatcher;
    private final TransactionTemplate newTx;
    private final Clock clock;

    public PushService(PushDeviceRepository devices, MemberRepository members, PushSender sender,
            PushDispatcher dispatcher, PlatformTransactionManager transactionManager, Clock clock) {
        this.devices = devices;
        this.members = members;
        this.sender = sender;
        this.dispatcher = dispatcher;
        this.newTx = new TransactionTemplate(transactionManager);
        this.newTx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.clock = clock;
    }

    /** 앱을 열 때마다 이 기기 토큰을 알려 준다. 다른 사람이 쓰던 폰이면 이제 내 것 */
    @Transactional
    public void register(LoginMember viewer, String token, String platform) {
        Instant now = clock.instant();
        Member member = members.getReferenceById(viewer.id());
        devices.findByToken(token).ifPresentOrElse(
                device -> device.seen(member, platform, now),
                () -> devices.save(new PushDevice(member, token, platform, now)));
        devices.flush();
        List<PushDevice> mine = devices.findAllOfMember(viewer.id());
        if (mine.size() > MAX_DEVICES_PER_MEMBER) {
            devices.deleteAll(mine.subList(MAX_DEVICES_PER_MEMBER, mine.size()));
        }
    }

    /** 로그아웃한 기기: 더는 보내지 않는다 (로그인이 이미 풀렸어도 토큰만으로 지울 수 있다) */
    @Transactional
    public void unregister(String token) {
        devices.deleteByToken(token);
    }

    /** 끌 알림 종류를 통째로 바꾼다 */
    @Transactional
    public MemberResponse changeSettings(LoginMember viewer, Collection<PushType> off) {
        Member member = members.findWithSchool(viewer.id())
                .orElseThrow(() -> ApiException.notFound("회원 정보를 찾을 수 없어요"));
        member.changePushOff(off);
        return MemberResponse.from(member);
    }

    /** 탈퇴: 이 사람의 기기를 모두 지운다 */
    @Transactional
    public void forget(Long memberId) {
        devices.deleteAllOfMember(memberId);
    }

    /** 이 회원들에게 (활동 중이고 이 종류를 끄지 않은 사람만) */
    public void send(Collection<Long> memberIds, PushMessage message) {
        if (memberIds.isEmpty()) {
            return;
        }
        deliver(devices.findTargets(memberIds, MemberStatus.ACTIVE), message, Set.of());
    }

    /** 한 학교 원우 모두에게 (schoolId 가 null 이면 다섯 학교 모두). except 는 빼고 */
    public void broadcast(Long schoolId, PushMessage message, Set<Long> except) {
        List<PushTarget> targets = schoolId == null
                ? devices.findAllTargets(MemberStatus.ACTIVE)
                : devices.findSchoolTargets(schoolId, MemberStatus.ACTIVE);
        deliver(targets, message, except);
    }

    private void deliver(List<PushTarget> targets, PushMessage message, Set<Long> except) {
        List<String> tokens = targets.stream()
                .filter(t -> !except.contains(t.memberId()))
                .filter(t -> !PushType.parseList(t.pushOff()).contains(message.type()))
                .map(PushTarget::token)
                .distinct()
                .toList();
        if (tokens.isEmpty()) {
            return;
        }
        Runnable task = () -> sendAll(tokens, message);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    dispatcher.dispatch(task);
                }
            });
        } else {
            dispatcher.dispatch(task);
        }
    }

    private void sendAll(List<String> tokens, PushMessage message) {
        List<String> invalid = new ArrayList<>();
        for (String token : tokens) {
            if (sender.send(token, message) == PushSender.Result.INVALID_TOKEN) {
                invalid.add(token);
            }
        }
        if (!invalid.isEmpty()) {
            newTx.executeWithoutResult(status -> invalid.forEach(devices::deleteByToken));
        }
    }
}
