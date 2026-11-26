package com.anam3.memory;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.anam3.auth.LoginMember;
import com.anam3.chat.ChatDtos.UploadTicket;
import com.anam3.common.ApiException;
import com.anam3.common.ContentFilter;
import com.anam3.meetup.Meetup;
import com.anam3.meetup.MeetupMapper;
import com.anam3.meetup.MeetupRepository;
import com.anam3.meetup.ParticipationRepository;
import com.anam3.member.Member;
import com.anam3.member.MemberRepository;
import com.anam3.memory.MemoryDtos.CreateRequest;
import com.anam3.memory.MemoryDtos.MeetupMemories;
import com.anam3.memory.MemoryDtos.MemoryFeed;
import com.anam3.memory.MemoryDtos.MemoryView;
import com.anam3.memory.MemoryDtos.PhotoView;
import com.anam3.push.NotificationService;
import com.anam3.push.PushMessage;
import com.anam3.push.PushService;
import com.anam3.push.PushType;
import com.anam3.push.QuietHours;
import com.anam3.safety.MemberBlockRepository;
import com.anam3.storage.PhotoKeys;
import com.anam3.storage.PhotoLimits;
import com.anam3.storage.PhotoStorage;

/**
 * 추억: 모였던 3교시(성사돼서 여럿이 모임)에 함께한 원우가 사진과 한 줄을 올린다. 계속 보관한다.
 * 기본은 함께한 원우만 보고, 올린 사람이 고르면 그 3교시를 볼 수 있는 원우 모두가 본다.
 * 이름은 3교시 공개 규칙을 따른다 (함께한 원우끼리는 실명, 나머지에게는 가려진 이름).
 * 지우기: 올린 사람, 그 학교 교수, 대학원장. 신고는 3교시 신고로 받는다.
 */
@Service
@Transactional
public class MemoryService {

    static final int FEED_PAGE = 20;

    private final MemoryRepository memories;
    private final MeetupRepository meetups;
    private final ParticipationRepository participations;
    private final MemberRepository members;
    private final MemberBlockRepository blocks;
    private final MeetupMapper mapper;
    private final PhotoStorage storage;
    private final PushService push;
    private final NotificationService notifications;
    private final Clock clock;

    public MemoryService(MemoryRepository memories, MeetupRepository meetups, ParticipationRepository participations,
            MemberRepository members, MemberBlockRepository blocks, MeetupMapper mapper, PhotoStorage storage,
            PushService push, NotificationService notifications, Clock clock) {
        this.memories = memories;
        this.meetups = meetups;
        this.participations = participations;
        this.members = members;
        this.blocks = blocks;
        this.mapper = mapper;
        this.storage = storage;
        this.push = push;
        this.notifications = notifications;
        this.clock = clock;
    }

    // ---------- 보기 ----------

    /** 추억 모아보기 (최근 순, before 보다 오래된 것) */
    @Transactional(readOnly = true)
    public MemoryFeed feed(LoginMember viewer, Long before) {
        List<Memory> found = memories.findFeed(viewer.id(), viewer.schoolId(),
                before == null ? Long.MAX_VALUE : before, PageRequest.of(0, FEED_PAGE));
        Set<Long> joined = joinedOf(viewer, found);
        Set<Long> hidden = blocks.findBlockedIds(viewer.id());
        List<Memory> visible = found.stream()
                .filter(m -> joined.contains(m.getMeetup().getId())
                        || (m.getVisibility() == Memory.Visibility.PUBLIC
                                && m.getMeetup().isOpenTo(viewer.schoolId(), viewer.cohort())))
                .filter(m -> m.isBy(viewer.id()) || !hidden.contains(m.getMember().getId()))
                .toList();
        Long next = found.size() < FEED_PAGE ? null : found.getLast().getId();
        return new MemoryFeed(views(visible, viewer, joined), next);
    }

    /** 3교시 상세의 추억 칸: 볼 수 있는 추억과 올릴 수 있는지 */
    @Transactional(readOnly = true)
    public MeetupMemories ofMeetup(LoginMember viewer, Long meetupId) {
        Meetup meetup = meetups.findWithHost(meetupId)
                .orElseThrow(() -> ApiException.notFound("3교시를 찾을 수 없어요"));
        boolean joined = participations.existsByMeetupIdAndMemberId(meetupId, viewer.id());
        if (!joined && !meetup.isOpenTo(viewer.schoolId(), viewer.cohort()) && !viewer.isOwner()) {
            throw ApiException.notFound("3교시를 찾을 수 없어요");
        }
        Set<Long> hidden = blocks.findBlockedIds(viewer.id());
        List<Memory> visible = memories.findOfMeetup(meetupId).stream()
                .filter(m -> joined || m.getVisibility() == Memory.Visibility.PUBLIC)
                .filter(m -> m.isBy(viewer.id()) || !hidden.contains(m.getMember().getId()))
                .toList();
        Set<Long> joinedIds = joined ? Set.of(meetupId) : Set.of();
        int left = photosLeft(meetupId);
        return new MeetupMemories(views(visible, viewer, joinedIds), joined && canAdd(meetup), left);
    }

    private Set<Long> joinedOf(LoginMember viewer, List<Memory> list) {
        if (list.isEmpty()) {
            return Set.of();
        }
        return participations.findJoinedMeetupIds(viewer.id(),
                list.stream().map(m -> m.getMeetup().getId()).collect(Collectors.toSet()));
    }

    private List<MemoryView> views(List<Memory> list, LoginMember viewer, Set<Long> joined) {
        if (list.isEmpty()) {
            return List.of();
        }
        Map<Long, List<MemoryPhoto>> photos = memories.findPhotos(list.stream().map(Memory::getId).toList())
                .stream().collect(Collectors.groupingBy(MemoryPhoto::getMemoryId));
        return list.stream().map(m -> {
            Meetup meetup = m.getMeetup();
            Member author = m.getMember();
            boolean revealed = MeetupMapper.canSeeRealNames(meetup, viewer, joined.contains(meetup.getId()));
            List<PhotoView> pics = photos.getOrDefault(m.getId(), List.of()).stream()
                    .map(p -> new PhotoView(p.getStorageKey(), storage.viewUrl(p.getStorageKey())))
                    .toList();
            return new MemoryView(m.getId(), meetup.getId(), meetup.getTitle(), meetup.getMeetAt(),
                    mapper.personView(author, viewer, revealed, meetup.isHostedBy(author.getId()), null),
                    m.isBy(viewer.id()), m.getCaption(), m.getVisibility(), pics, m.getCreatedAt(),
                    canDelete(m, viewer));
        }).toList();
    }

    // ---------- 올리기 ----------

    /** 추억은 성사돼서 여럿이 모였던 3교시에, 모인 뒤에 올린다 */
    private boolean canAdd(Meetup meetup) {
        return meetup.isGatheredAt(clock.instant());
    }

    private int photosLeft(Long meetupId) {
        return (int) Math.max(0, PhotoLimits.MEMORY_PER_MEETUP - memories.countPhotosOf(meetupId));
    }

    private Meetup requireCanAdd(LoginMember viewer, Long meetupId, boolean lock) {
        Meetup meetup = (lock ? meetups.findByIdForUpdate(meetupId) : meetups.findWithHost(meetupId))
                .orElseThrow(() -> ApiException.notFound("3교시를 찾을 수 없어요"));
        if (!participations.existsByMeetupIdAndMemberId(meetupId, viewer.id())) {
            throw ApiException.forbidden("함께한 원우만 추억을 올릴 수 있어요");
        }
        if (!canAdd(meetup)) {
            throw ApiException.badRequest("NOT_GATHERED", "추억은 성사된 3교시가 모인 뒤에 올릴 수 있어요");
        }
        return meetup;
    }

    /** 사진 올릴 주소. 3교시마다 모두 합쳐 10장 */
    public List<UploadTicket> uploads(LoginMember viewer, Long meetupId, List<String> contentTypes) {
        requireCanAdd(viewer, meetupId, false);
        for (String type : contentTypes) {
            if (!PhotoKeys.TYPES.containsKey(type)) {
                throw ApiException.badRequest("PHOTO_TYPE", "사진(JPG·WEBP)만 올릴 수 있어요");
            }
        }
        if (contentTypes.size() > photosLeft(meetupId)) {
            throw meetupLimit(photosLeft(meetupId));
        }
        return contentTypes.stream().map(type -> {
            String key = PhotoKeys.create(PhotoKeys.Purpose.MEMORY, meetupId, type);
            return new UploadTicket(key, storage.uploadUrl(key, type), type);
        }).toList();
    }

    public MemoryView create(LoginMember viewer, Long meetupId, CreateRequest req) {
        // 둘이 동시에 올려 10장을 넘지 않게 3교시를 잠근다
        Meetup meetup = requireCanAdd(viewer, meetupId, true);
        String caption = req.caption() == null || req.caption().isBlank() ? null : req.caption().strip();
        ContentFilter.check(caption);
        List<String> keys = List.copyOf(new LinkedHashSet<>(req.photoKeys()));
        if (keys.size() > photosLeft(meetupId)) {
            throw meetupLimit(photosLeft(meetupId));
        }
        for (String key : keys) {
            if (!PhotoKeys.belongsTo(key, PhotoKeys.Purpose.MEMORY, meetupId)) {
                throw ApiException.badRequest("PHOTO_KEY", "사진을 다시 올려 주세요");
            }
            long size = storage.size(key).orElseThrow(() -> ApiException.badRequest("PHOTO_MISSING",
                    "사진이 아직 올라가지 않았어요. 다시 올려 주세요"));
            if (size > PhotoLimits.MEMORY_MAX_BYTES) {
                storage.delete(key);
                throw ApiException.badRequest("PHOTO_TOO_LARGE", "사진이 너무 커요. 다시 골라 주세요");
            }
        }
        Instant now = clock.instant();
        Memory memory = new Memory(meetup, members.getReferenceById(viewer.id()), caption, req.visibility(), now);
        keys.forEach(memory::addPhoto);
        try {
            memories.saveAndFlush(memory);
        } catch (DataIntegrityViolationException e) {
            throw ApiException.badRequest("PHOTO_USED", "이미 올린 사진이에요. 다시 골라 주세요");
        }
        notifyAdded(meetup, viewer.id());
        return views(List.of(memory), viewer, Set.of(meetupId)).getFirst();
    }

    /** 새 추억: 함께한 다른 원우에게 (밤에는 알림함에만 담고 폰은 울리지 않는다) */
    private void notifyAdded(Meetup meetup, Long authorId) {
        List<Long> to = new ArrayList<>(participations.findMemberIds(meetup.getId()));
        to.remove(authorId);
        to.removeAll(blocks.findBlockerIds(authorId));
        if (to.isEmpty()) {
            return;
        }
        PushMessage message = new PushMessage(PushType.MEMORY, meetup.getTitle(), "새 추억이 올라왔어요 📸",
                "/meetups/" + meetup.getId() + "#memories", "memory-" + meetup.getId());
        if (QuietHours.contains(clock.instant())) {
            notifications.record(to, message);
        } else {
            push.send(to, message);
        }
    }

    // ---------- 지우기 ----------

    private static boolean canDelete(Memory m, LoginMember viewer) {
        return m.isBy(viewer.id()) || viewer.manages(m.getMeetup().getSchool().getId());
    }

    public void delete(LoginMember viewer, Long memoryId) {
        Memory memory = memories.findById(memoryId)
                .orElseThrow(() -> ApiException.notFound("추억을 찾을 수 없어요"));
        if (!canDelete(memory, viewer)) {
            throw ApiException.forbidden("올린 원우만 지울 수 있어요");
        }
        remove(List.of(memory));
    }

    /** 탈퇴: 내가 올린 추억과 사진을 모두 지운다 */
    public void forgetMember(Long memberId) {
        remove(memories.findAllOfMember(memberId));
    }

    private void remove(List<Memory> list) {
        if (list.isEmpty()) {
            return;
        }
        List<String> keys = list.stream().flatMap(m -> m.getPhotos().stream()).map(MemoryPhoto::getStorageKey)
                .toList();
        memories.deleteAll(list);
        Runnable task = () -> keys.forEach(storage::delete);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    task.run();
                }
            });
        } else {
            task.run();
        }
    }

    private static ApiException meetupLimit(int left) {
        return ApiException.badRequest("MEMORY_LIMIT", left == 0
                ? "이 3교시의 추억 사진이 " + PhotoLimits.MEMORY_PER_MEETUP + "장을 모두 채웠어요"
                : "이 3교시에는 " + left + "장만 더 올릴 수 있어요");
    }
}
