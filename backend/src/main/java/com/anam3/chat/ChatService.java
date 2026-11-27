package com.anam3.chat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.anam3.auth.LoginMember;
import com.anam3.chat.ChatDtos.ChatPage;
import com.anam3.chat.ChatDtos.ChatUpdates;
import com.anam3.chat.ChatDtos.MessageView;
import com.anam3.chat.ChatDtos.PhotoView;
import com.anam3.chat.ChatDtos.PinResult;
import com.anam3.chat.ChatDtos.QuoteView;
import com.anam3.chat.ChatDtos.ReactionView;
import com.anam3.chat.ChatDtos.RoomList;
import com.anam3.chat.ChatDtos.RoomView;
import com.anam3.chat.ChatDtos.SendRequest;
import com.anam3.chat.ChatDtos.UploadTicket;
import com.anam3.common.ApiException;
import com.anam3.common.NameMasker;
import com.anam3.common.RateLimiter;
import com.anam3.common.RateLimits;
import com.anam3.live.LiveHub;
import com.anam3.meetup.Meetup;
import com.anam3.meetup.MeetupMapper;
import com.anam3.meetup.MeetupRepository;
import com.anam3.meetup.Participation;
import com.anam3.meetup.ParticipationRepository;
import com.anam3.member.Member;
import com.anam3.member.MemberRepository;
import com.anam3.push.PushMessage;
import com.anam3.push.PushService;
import com.anam3.push.PushType;
import com.anam3.push.QuietHours;
import com.anam3.safety.MemberBlockRepository;
import com.anam3.storage.PhotoKeys;
import com.anam3.storage.PhotoLimits;
import com.anam3.storage.PhotoStorage;

/**
 * 3교시 톡방. 지금 함께하는 원우(연 사람 포함)만 들어온다 — 나가거나 내보내지면 더는 못 본다.
 * 이름은 3교시 공개 규칙을 따른다 (성사 전에는 서로 가려진 이름, 성사되면 실명).
 * 취소된 3교시 톡방은 읽기만 된다.
 * <p>
 * 새 메시지는 열린 화면에 실시간으로 알리고(LiveHub), 톡방을 보고 있지 않은 원우에게만 폰 알림을 보낸다.
 * 답장 · 공감 · 톡방 공지(연 사람이 메시지 하나를 위에 올린다)도 있다. 공감·공지가 바뀌면 "chat-change" 로 알린다.
 * 톡 알림은 알림함에 쌓지 않는다 (안 읽은 수는 톡방 목록에서 본다).
 */
@Service
@Transactional
public class ChatService {

    static final int PAGE = 50;
    private static final int UPDATES_MAX = 200;
    private static final int ROOMS_MAX = 50;
    /** 모임 즈음(3시간 전 ~ 6시간 뒤)에는 밤이어도 톡 알림을 보낸다 — 술자리는 밤에 열린다 */
    private static final Duration NIGHT_BEFORE = Duration.ofHours(3);
    private static final Duration NIGHT_AFTER = Duration.ofHours(6);
    private static final Duration KEEP = Duration.ofDays(PhotoLimits.CHAT_KEEP_DAYS);

    private final ChatMessageRepository messages;
    private final ChatStateRepository states;
    private final ChatReactionRepository reactions;
    private final MeetupRepository meetups;
    private final ParticipationRepository participations;
    private final MemberRepository members;
    private final MemberBlockRepository blocks;
    private final MeetupMapper mapper;
    private final PhotoStorage storage;
    private final LiveHub live;
    private final PushService push;
    private final Clock clock;
    private final RateLimiter sendLimit;
    private final RateLimiter pushLimit;
    private final RateLimiter reactLimit;

    public ChatService(ChatMessageRepository messages, ChatStateRepository states, ChatReactionRepository reactions,
            MeetupRepository meetups, ParticipationRepository participations, MemberRepository members,
            MemberBlockRepository blocks, MeetupMapper mapper, PhotoStorage storage, LiveHub live, PushService push,
            Clock clock, RateLimits limits) {
        this.messages = messages;
        this.states = states;
        this.reactions = reactions;
        this.meetups = meetups;
        this.participations = participations;
        this.members = members;
        this.blocks = blocks;
        this.mapper = mapper;
        this.storage = storage;
        this.live = live;
        this.push = push;
        this.clock = clock;
        // 1분에 30개까지 (붙여넣기 도배 방지)
        this.sendLimit = limits.create(30, Duration.ofMinutes(1));
        // 같은 톡방 알림은 한 사람에게 30초에 한 번 (몰아서 와도 폰이 계속 울리지 않게)
        this.pushLimit = limits.create(1, Duration.ofSeconds(30));
        // 공감은 1분에 60번까지 (눌렀다 뗐다 도배 방지)
        this.reactLimit = limits.create(60, Duration.ofMinutes(1));
    }

    /** 톡방에 들어갈 수 있는 3교시와 지금 함께하는 사람들 */
    private record Room(Meetup meetup, List<Participation> people) {

        Set<Long> memberIds() {
            return people.stream().map(p -> p.getMember().getId()).collect(Collectors.toSet());
        }

        Map<Long, Long> refs() {
            Map<Long, Long> refs = new HashMap<>();
            people.forEach(p -> refs.put(p.getMember().getId(), p.getId()));
            return refs;
        }

        boolean canWrite() {
            return !meetup.isCanceled();
        }
    }

    private Room room(LoginMember viewer, Long meetupId) {
        Meetup meetup = meetups.findWithHost(meetupId)
                .orElseThrow(() -> ApiException.notFound("3교시를 찾을 수 없어요"));
        List<Participation> people = participations.findWithMember(meetupId);
        if (people.stream().noneMatch(p -> p.getMember().getId().equals(viewer.id()))) {
            throw ApiException.forbidden("함께하는 원우만 톡방에 들어갈 수 있어요");
        }
        return new Room(meetup, people);
    }

    // ---------- 보기 ----------

    /** 가장 최근 50개, 또는 before 보다 오래된 50개 */
    @Transactional(readOnly = true)
    public ChatPage page(LoginMember viewer, Long meetupId, Long before) {
        Room room = room(viewer, meetupId);
        Instant now = clock.instant();
        PageRequest limit = PageRequest.of(0, PAGE + 1);
        List<ChatMessage> found = new ArrayList<>(before == null
                ? messages.findLatest(meetupId, limit)
                : messages.findBefore(meetupId, before, limit));
        boolean hasMore = found.size() > PAGE;
        if (hasMore) {
            found = found.subList(0, PAGE);
        }
        Collections.reverse(found);
        ChatState state = states.find(meetupId, viewer.id()).orElse(null);
        Meetup m = room.meetup();
        return new ChatPage(m.getId(), m.getTitle(), m.statusAt(now), m.getMeetAt(), room.people().size(),
                m.isFederated(), views(found, room, viewer, now), hasMore, room.canWrite(),
                state == null ? 0 : state.getLastReadId(), state != null && state.isMuted(),
                PhotoLimits.CHAT_KEEP_DAYS, photosLeftToday(viewer.id(), now), reads(room, viewer),
                pinned(room, viewer), room.canWrite() && m.isHostedBy(viewer.id()));
    }

    /** after 다음에 온 메시지 (실시간 알림을 받거나 화면으로 돌아왔을 때) */
    @Transactional(readOnly = true)
    public ChatUpdates updates(LoginMember viewer, Long meetupId, Long after) {
        Room room = room(viewer, meetupId);
        List<ChatMessage> found = messages.findAfter(meetupId, after, PageRequest.of(0, UPDATES_MAX));
        return new ChatUpdates(views(found, room, viewer, clock.instant()), reads(room, viewer), pinned(room, viewer));
    }

    /** 메시지 하나 (공감이 바뀌었다는 실시간 알림을 받았을 때) */
    @Transactional(readOnly = true)
    public MessageView message(LoginMember viewer, Long meetupId, Long messageId) {
        Room room = room(viewer, meetupId);
        return views(List.of(messageIn(meetupId, messageId)), room, viewer, clock.instant()).getFirst();
    }

    /** 나 말고 함께하는 원우들이 각자 마지막으로 읽은 메시지 번호 (톡방을 아직 안 연 원우는 0) */
    private List<Long> reads(Room room, LoginMember viewer) {
        Map<Long, Long> readIds = new HashMap<>();
        states.findReadIds(room.meetup().getId()).forEach(r -> readIds.put((Long) r[0], (Long) r[1]));
        return room.memberIds().stream()
                .filter(id -> !id.equals(viewer.id()))
                .map(id -> readIds.getOrDefault(id, 0L))
                .sorted()
                .toList();
    }

    private List<MessageView> views(List<ChatMessage> list, Room room, LoginMember viewer, Instant now) {
        if (list.isEmpty()) {
            return List.of();
        }
        List<Long> ids = list.stream().map(ChatMessage::getId).toList();
        Map<Long, List<ChatPhoto>> photos = messages.findPhotos(ids)
                .stream().collect(Collectors.groupingBy(ChatPhoto::getMessageId));
        Set<Long> hidden = blocks.findBlockedIds(viewer.id());
        boolean revealed = MeetupMapper.canSeeRealNames(room.meetup(), viewer, true);
        Map<Long, Long> refs = room.refs();
        // 답장의 원래 메시지 (이 화면에 없는 오래된 것일 수도 있다)
        List<Long> replyIds = list.stream().map(ChatMessage::getReplyToId).filter(Objects::nonNull).distinct().toList();
        Map<Long, QuoteView> quotes = new HashMap<>();
        if (!replyIds.isEmpty()) {
            messages.findWithSender(replyIds)
                    .forEach(o -> quotes.put(o.getId(), quote(o, room, viewer, revealed, hidden)));
        }
        Map<Long, List<ReactionView>> reacted = reactionsOf(ids, viewer.id());
        return list.stream()
                .map(m -> view(m, photos.getOrDefault(m.getId(), List.of()), viewer, revealed, refs, hidden, now,
                        m.getReplyToId() == null ? null : quotes.get(m.getReplyToId()),
                        reacted.getOrDefault(m.getId(), List.of())))
                .toList();
    }

    private MessageView view(ChatMessage m, List<ChatPhoto> photos, LoginMember viewer, boolean revealed,
            Map<Long, Long> refs, Set<Long> hiddenSenders, Instant now, QuoteView reply,
            List<ReactionView> reacted) {
        Member sender = m.getMember();
        boolean mine = sender.getId().equals(viewer.id());
        boolean hidden = !mine && hiddenSenders.contains(sender.getId());
        boolean expired = !now.isBefore(m.getCreatedAt().plus(KEEP));
        boolean gone = hidden || m.isDeleted();
        List<PhotoView> photoViews = hidden ? List.of() : photos.stream()
                .map(p -> new PhotoView(p.getStorageKey(), expired ? null : storage.viewUrl(p.getStorageKey()),
                        expired))
                .toList();
        return new MessageView(m.getId(), m.getKind(),
                mapper.personView(sender, viewer, revealed, m.getMeetup().isHostedBy(sender.getId()),
                        refs.get(sender.getId())),
                mine, hidden ? null : m.getBody(), hidden ? null : m.getSticker(), photoViews, m.isDeleted(),
                hidden, m.getCreatedAt(), gone ? null : reply, gone ? List.of() : reacted);
    }

    /** 다른 메시지를 한 줄로 (답장의 원래 메시지 · 톡방 공지) */
    private QuoteView quote(ChatMessage m, Room room, LoginMember viewer, boolean revealed, Set<Long> hiddenSenders) {
        Member sender = m.getMember();
        boolean mine = sender.getId().equals(viewer.id());
        String name = mapper.personView(sender, viewer, revealed, room.meetup().isHostedBy(sender.getId()), null)
                .name();
        String preview = m.isDeleted() ? "지운 메시지예요"
                : !mine && hiddenSenders.contains(sender.getId()) ? "차단한 원우의 메시지예요"
                : m.getSticker() != null ? ChatStickers.preview(m.getSticker())
                : m.getBody() != null ? shorten(m.getBody(), 80)
                : "사진";
        return new QuoteView(m.getId(), name, mine, preview);
    }

    /** 메시지마다 공감 (공감 순서대로, 몇 명 · 나도 눌렀는지) */
    private Map<Long, List<ReactionView>> reactionsOf(List<Long> ids, Long viewerId) {
        Map<Long, EnumMap<ChatReactionKind, Integer>> counts = new HashMap<>();
        Map<Long, ChatReactionKind> mine = new HashMap<>();
        for (Object[] r : reactions.findOf(ids)) {
            Long messageId = (Long) r[0];
            ChatReactionKind kind = (ChatReactionKind) r[2];
            counts.computeIfAbsent(messageId, k -> new EnumMap<>(ChatReactionKind.class)).merge(kind, 1, Integer::sum);
            if (viewerId.equals(r[1])) {
                mine.put(messageId, kind);
            }
        }
        Map<Long, List<ReactionView>> out = new HashMap<>();
        counts.forEach((id, byKind) -> out.put(id, byKind.entrySet().stream()
                .map(e -> new ReactionView(e.getKey(), e.getValue(), e.getKey() == mine.get(id)))
                .toList()));
        return out;
    }

    /** 지금 톡방 공지 (없으면 null) */
    private QuoteView pinned(Room room, LoginMember viewer) {
        return messages.findPinned(room.meetup().getId()).stream()
                .filter(m -> !m.isDeleted())
                .findFirst()
                .map(m -> quote(m, room, viewer, MeetupMapper.canSeeRealNames(room.meetup(), viewer, true),
                        blocks.findBlockedIds(viewer.id())))
                .orElse(null);
    }

    /** 이 톡방의 메시지 하나 */
    private ChatMessage messageIn(Long meetupId, Long messageId) {
        return messages.findWithSender(List.of(messageId)).stream()
                .filter(m -> m.getMeetup().getId().equals(meetupId))
                .findFirst()
                .orElseThrow(() -> ApiException.notFound("메시지를 찾을 수 없어요"));
    }

    private int photosLeftToday(Long memberId, Instant now) {
        long sent = messages.countPhotosSince(memberId, now.minus(Duration.ofDays(1)));
        return (int) Math.max(0, PhotoLimits.CHAT_PER_DAY - sent);
    }

    // ---------- 보내기 ----------

    /** 사진 올릴 주소. 올린 뒤 send 에 이름(key)을 넣어 보낸다 */
    public List<UploadTicket> uploads(LoginMember viewer, Long meetupId, List<String> contentTypes) {
        Room room = room(viewer, meetupId);
        requireWritable(room);
        checkTypes(contentTypes);
        if (contentTypes.size() > photosLeftToday(viewer.id(), clock.instant())) {
            throw dailyLimit();
        }
        return contentTypes.stream().map(type -> {
            String key = PhotoKeys.create(PhotoKeys.Purpose.CHAT, meetupId, type);
            return new UploadTicket(key, storage.uploadUrl(key, type), type);
        }).toList();
    }

    public MessageView send(LoginMember viewer, Long meetupId, SendRequest req) {
        Room room = room(viewer, meetupId);
        requireWritable(room);
        Instant now = clock.instant();
        if (!sendLimit.tryAcquire(viewer.id().toString(), now)) {
            throw ApiException.badRequest("TOO_FAST", "너무 빨리 보내고 있어요. 잠시 후 다시 보내 주세요");
        }
        String body = req.body() == null || req.body().isBlank() ? null : req.body().strip();
        List<String> keys = req.photoKeys() == null ? List.of() : List.copyOf(new LinkedHashSet<>(req.photoKeys()));
        Long replyTo = req.replyToId() == null ? null : replyTarget(meetupId, req.replyToId()).getId();
        if (req.sticker() != null) {
            return sendSticker(room, viewer, req.sticker(), body != null || !keys.isEmpty(), replyTo, now);
        }
        if (body == null && keys.isEmpty()) {
            throw ApiException.badRequest("EMPTY", "보낼 메시지를 써 주세요");
        }
        if (!keys.isEmpty()) {
            if (keys.size() > PhotoLimits.PER_SEND) {
                throw ApiException.badRequest("TOO_MANY_PHOTOS", "사진은 한 번에 5장까지 보낼 수 있어요");
            }
            if (keys.size() > photosLeftToday(viewer.id(), now)) {
                throw dailyLimit();
            }
            for (String key : keys) {
                checkUploaded(key, PhotoKeys.Purpose.CHAT, meetupId, PhotoLimits.CHAT_MAX_BYTES);
            }
        }
        Member me = members.getReferenceById(viewer.id());
        ChatMessage message = new ChatMessage(room.meetup(), me, body, now);
        message.replyTo(replyTo);
        keys.forEach(message::addPhoto);
        try {
            messages.saveAndFlush(message);
        } catch (DataIntegrityViolationException e) {
            throw ApiException.badRequest("PHOTO_USED", "이미 보낸 사진이에요. 다시 골라 주세요");
        }
        return sent(room, viewer, message, now);
    }

    /** 이모티콘: 정해진 것만, 글·사진 없이 하나만 */
    private MessageView sendSticker(Room room, LoginMember viewer, String sticker, boolean withOthers, Long replyTo,
            Instant now) {
        if (!ChatStickers.exists(sticker)) {
            throw ApiException.badRequest("STICKER", "없는 이모티콘이에요");
        }
        if (withOthers) {
            throw ApiException.badRequest("STICKER_ALONE", "이모티콘은 글·사진과 따로 보내 주세요");
        }
        ChatMessage message = ChatMessage.sticker(room.meetup(), members.getReferenceById(viewer.id()), sticker, now);
        message.replyTo(replyTo);
        messages.saveAndFlush(message);
        return sent(room, viewer, message, now);
    }

    /** 답장할 메시지: 이 톡방의, 지우지 않은 원우 메시지 */
    private ChatMessage replyTarget(Long meetupId, Long messageId) {
        return messages.findById(messageId)
                .filter(m -> m.getMeetup().getId().equals(meetupId) && !m.isNotice() && !m.isDeleted())
                .orElseThrow(() -> ApiException.badRequest("REPLY_GONE", "답장할 메시지를 찾을 수 없어요"));
    }

    /** 보낸 뒤: 보낸 사람은 거기까지 읽은 것으로 · 열린 화면에 알리고 · 폰 알림 */
    private MessageView sent(Room room, LoginMember viewer, ChatMessage message, Instant now) {
        Long meetupId = room.meetup().getId();
        stateOf(room.meetup(), viewer.id()).readUpTo(message.getId());
        live.publish(room.memberIds(), "chat", Map.of("meetupId", meetupId, "messageId", message.getId()));
        notifyNew(room, message);
        return views(List.of(message), room, viewer, now).getFirst();
    }

    // ---------- 공감 · 공지 ----------

    /** 공감 누르기 · 바꾸기 · 빼기(reaction 이 null). 메시지마다 한 사람이 하나 */
    public MessageView react(LoginMember viewer, Long meetupId, Long messageId, ChatReactionKind reaction) {
        Room room = room(viewer, meetupId);
        requireWritable(room);
        Instant now = clock.instant();
        if (!reactLimit.tryAcquire(viewer.id().toString(), now)) {
            throw ApiException.badRequest("TOO_FAST", "너무 빨리 누르고 있어요. 잠시 후 다시 눌러 주세요");
        }
        ChatMessage message = messageIn(meetupId, messageId);
        if (message.isNotice() || message.isDeleted() || hiddenFor(viewer, message)) {
            throw ApiException.badRequest("REACTION", "공감할 수 없는 메시지예요");
        }
        ChatReaction current = reactions.find(messageId, viewer.id()).orElse(null);
        if (reaction == null) {
            if (current != null) {
                reactions.delete(current);
            }
        } else if (current != null) {
            current.change(reaction, now);
        } else {
            reactions.save(new ChatReaction(message, members.getReferenceById(viewer.id()), reaction, now));
        }
        reactions.flush();
        live.publish(room.memberIds(), "chat-change", Map.of("meetupId", meetupId, "messageId", messageId));
        return views(List.of(message), room, viewer, now).getFirst();
    }

    /** 톡방 공지 올리기(messageId) · 내리기(null). 연 사람만, 톡방마다 하나 */
    public PinResult pin(LoginMember viewer, Long meetupId, Long messageId) {
        Room room = room(viewer, meetupId);
        if (!room.meetup().isHostedBy(viewer.id())) {
            throw ApiException.forbidden("톡방 공지는 연 사람만 올릴 수 있어요");
        }
        requireWritable(room);
        ChatMessage target = messageId == null ? null : messageIn(meetupId, messageId);
        if (target != null && (target.isNotice() || target.isDeleted() || hiddenFor(viewer, target))) {
            throw ApiException.badRequest("PIN", "공지로 올릴 수 없는 메시지예요");
        }
        messages.findPinned(meetupId).stream().filter(m -> m != target).forEach(ChatMessage::unpin);
        if (target != null) {
            target.pin(clock.instant());
        }
        messages.flush();
        live.publish(room.memberIds(), "chat-change", Map.of("meetupId", meetupId));
        return new PinResult(pinned(room, viewer));
    }

    /** 내가 차단한 원우의 메시지 */
    private boolean hiddenFor(LoginMember viewer, ChatMessage message) {
        return !message.isSentBy(viewer.id())
                && blocks.findBlockedIds(viewer.id()).contains(message.getMember().getId());
    }

    // ---------- 들어옴 · 나감 ----------

    /**
     * 참여했다 → 톡방에 "○○님이 들어왔어요". 들어온 사람은 그 순간까지 읽은 것으로 친다
     * (예전 메시지 옆 "안 읽은 사람 수"에 세지 않는다 — 카톡처럼). 폰 알림은 보내지 않는다.
     */
    public void joined(Meetup meetup, Long memberId) {
        ChatMessage notice = notice(meetup, memberId, ChatKind.JOIN);
        stateOf(meetup, memberId).readUpTo(notice.getId());
    }

    /** 나갔거나 내보내졌다 → 톡방에 "○○님이 나갔어요" (내보낸 것은 드러내지 않는다) */
    public void left(Meetup meetup, Long memberId) {
        notice(meetup, memberId, ChatKind.LEAVE);
    }

    private ChatMessage notice(Meetup meetup, Long memberId, ChatKind kind) {
        ChatMessage notice = messages.saveAndFlush(
                ChatMessage.notice(meetup, members.getReferenceById(memberId), kind, clock.instant()));
        List<Long> people = participations.findMemberIds(meetup.getId());
        Long meetupId = meetup.getId();
        afterCommit(() -> live.publish(people, "chat", Map.of("meetupId", meetupId, "messageId", notice.getId())));
        return notice;
    }

    /** 내 메시지 지우기: 자리는 남고 "지운 메시지"로 보인다. 사진은 저장소에서도 지운다 */
    public void delete(LoginMember viewer, Long meetupId, Long messageId) {
        Room room = room(viewer, meetupId);
        ChatMessage message = messages.findById(messageId)
                .filter(m -> m.getMeetup().getId().equals(meetupId))
                .orElseThrow(() -> ApiException.notFound("메시지를 찾을 수 없어요"));
        if (!message.isSentBy(viewer.id()) || message.isNotice()) {
            throw ApiException.forbidden("내가 보낸 메시지만 지울 수 있어요");
        }
        if (message.isDeleted()) {
            return;
        }
        List<String> keys = message.getPhotos().stream().map(ChatPhoto::getStorageKey).toList();
        message.delete(clock.instant());
        afterCommit(() -> keys.forEach(storage::delete));
        live.publish(room.memberIds(), "chat",
                Map.of("meetupId", meetupId, "messageId", messageId, "deleted", true));
    }

    // ---------- 읽음 · 알림 ----------

    /** 여기까지 읽었다. 톡방을 보고 있는 동안 화면이 계속 보낸다 (그동안은 폰 알림을 보내지 않는다) */
    public void read(LoginMember viewer, Long meetupId, long lastId) {
        Room room = room(viewer, meetupId);
        Long last = messages.findLastId(meetupId);
        ChatState state = stateOf(room.meetup(), viewer.id());
        long before = state.getLastReadId();
        state.readUpTo(Math.min(lastId, last == null ? 0 : last));
        live.viewing(viewer.id(), meetupId);
        // 내 다른 기기(폰·PC)의 안 읽은 수도 맞춘다
        live.publish(List.of(viewer.id()), "chat-read", Map.of("meetupId", meetupId));
        // 더 읽었으면 톡방을 연 다른 원우 화면의 "안 읽은 사람 수"를 줄인다 (1분마다 오는 '보고 있어요'는 빼고)
        if (state.getLastReadId() > before) {
            Set<Long> others = new HashSet<>(room.memberIds());
            others.remove(viewer.id());
            live.publish(others, "chat-reads", Map.of("meetupId", meetupId));
        }
    }

    /** 톡방을 닫았다 — 이제 새 메시지는 폰 알림으로 */
    public void leaveRoom(LoginMember viewer, Long meetupId) {
        live.leftRoom(viewer.id(), meetupId);
    }

    public void mute(LoginMember viewer, Long meetupId, boolean muted) {
        Room room = room(viewer, meetupId);
        stateOf(room.meetup(), viewer.id()).mute(muted);
    }

    private ChatState stateOf(Meetup meetup, Long memberId) {
        return states.find(meetup.getId(), memberId).orElseGet(() -> {
            try {
                return states.saveAndFlush(new ChatState(meetup, members.getReferenceById(memberId)));
            } catch (DataIntegrityViolationException e) {
                // 같은 사람이 동시에 두 번 (폰·PC) — 먼저 만든 것을 쓴다
                return states.find(meetup.getId(), memberId).orElseThrow(() -> e);
            }
        });
    }

    /** 내 톡방 목록 (메시지가 있는 곳만, 최근 순) */
    @Transactional(readOnly = true)
    public RoomList rooms(LoginMember viewer) {
        List<Object[]> found = messages.findRoomsOf(viewer.id(), PageRequest.of(0, ROOMS_MAX));
        if (found.isEmpty()) {
            return new RoomList(0, List.of());
        }
        List<Long> meetupIds = found.stream().map(r -> (Long) r[0]).toList();
        List<Long> lastIds = found.stream().map(r -> (Long) r[1]).toList();
        Set<Long> hidden = blocks.findBlockedIds(viewer.id());
        Map<Long, Long> unread = new HashMap<>();
        messages.countUnread(viewer.id(), meetupIds, hidden.isEmpty() ? List.of(-1L) : hidden)
                .forEach(r -> unread.put((Long) r[0], (Long) r[1]));
        Map<Long, ChatState> stateBy = new HashMap<>();
        states.findOf(viewer.id(), meetupIds).forEach(s -> stateBy.put(s.getMeetupId(), s));
        Map<Long, ChatMessage> lastBy = new HashMap<>();
        messages.findWithMeetup(lastIds).forEach(m -> lastBy.put(m.getMeetup().getId(), m));
        Map<Long, Integer> photoCount = new HashMap<>();
        messages.findPhotos(lastIds).forEach(p -> photoCount.merge(p.getMessageId(), 1, Integer::sum));

        Instant now = clock.instant();
        List<RoomView> rooms = new ArrayList<>();
        long total = 0;
        for (Long id : meetupIds) {
            ChatMessage last = lastBy.get(id);
            if (last == null) {
                continue;
            }
            Meetup m = last.getMeetup();
            ChatState state = stateBy.get(id);
            boolean muted = state != null && state.isMuted();
            long count = unread.getOrDefault(id, 0L);
            if (!muted) {
                total += count;
            }
            Member sender = last.getMember();
            boolean mine = sender.getId().equals(viewer.id());
            String senderName = mine ? "나"
                    : mapper.personView(sender, viewer, MeetupMapper.canSeeRealNames(m, viewer, true),
                            m.isHostedBy(sender.getId()), null).name();
            String preview = last.isNotice() ? noticeText(last.getKind(), senderName)
                    : last.isDeleted() ? "지운 메시지예요"
                    : !mine && hidden.contains(sender.getId()) ? "차단한 원우의 메시지예요"
                    : last.getSticker() != null ? ChatStickers.preview(last.getSticker())
                    : last.getBody() != null ? shorten(last.getBody(), 60)
                    : "사진 " + photoCount.getOrDefault(last.getId(), 1) + "장";
            // 들어옴/나감 줄은 보낸 사람 없이 한 줄로 ("○○님이 들어왔어요")
            rooms.add(new RoomView(id, m.getTitle(), m.statusAt(now), m.getMeetAt(), count, muted, preview,
                    last.isNotice() ? null : senderName, last.getCreatedAt()));
        }
        return new RoomList(total, rooms);
    }

    /**
     * 폰 알림: 보낸 사람 · 이 톡방 알림을 끈 사람 · 지금 톡방을 보고 있는 사람 · 보낸 사람을 차단한 사람은 빼고.
     * 밤(23~8시)에는 보내지 않는다 — 모임 즈음은 예외.
     */
    private void notifyNew(Room room, ChatMessage message) {
        Meetup m = room.meetup();
        Instant now = clock.instant();
        boolean aroundMeetup = !now.isBefore(m.getMeetAt().minus(NIGHT_BEFORE))
                && now.isBefore(m.getMeetAt().plus(NIGHT_AFTER));
        if (QuietHours.contains(now) && !aroundMeetup) {
            return;
        }
        Member sender = message.getMember();
        Set<Long> muted = new HashSet<>(states.findMutedMemberIds(m.getId()));
        Set<Long> blockedSender = blocks.findBlockerIds(sender.getId());
        List<Long> to = room.memberIds().stream()
                .filter(id -> !id.equals(sender.getId()))
                .filter(id -> !muted.contains(id) && !blockedSender.contains(id))
                .filter(id -> !live.isViewing(id, m.getId()))
                .filter(id -> pushLimit.tryAcquire(id + ":" + m.getId(), now))
                .toList();
        if (to.isEmpty()) {
            return;
        }
        // 성사 전에는 서로 가려진 이름 (3교시 공개 규칙). 같은 톡방 사람은 모두 같은 규칙이라 한 번에 보낸다
        Member full = members.findById(sender.getId()).orElse(null);
        String name = full == null ? "원우"
                : (m.isReached() && !m.isCanceled()) || full.isOwner() || full.isWithdrawn() ? full.getName()
                : NameMasker.mask(full.getName());
        String text = message.getSticker() != null ? ChatStickers.preview(message.getSticker())
                : message.getBody() != null ? shorten(message.getBody(), 80)
                : "사진 " + message.getPhotos().size() + "장을 보냈어요";
        push.sendPushOnly(to, new PushMessage(PushType.CHAT, m.getTitle(), name + ": " + text,
                "/meetups/" + m.getId() + "/chat", "chat-" + m.getId()));
    }

    // ---------- 도우미 ----------

    private static void requireWritable(Room room) {
        if (!room.canWrite()) {
            throw ApiException.badRequest("CHAT_CLOSED", "취소된 3교시 톡방이라 읽기만 할 수 있어요");
        }
    }

    static void checkTypes(List<String> contentTypes) {
        if (contentTypes.size() > PhotoLimits.PER_SEND) {
            throw ApiException.badRequest("TOO_MANY_PHOTOS", "사진은 한 번에 5장까지 올릴 수 있어요");
        }
        for (String type : contentTypes) {
            if (!PhotoKeys.TYPES.containsKey(type)) {
                throw ApiException.badRequest("PHOTO_TYPE", "사진(JPG·WEBP)만 올릴 수 있어요");
            }
        }
    }

    /** 이 3교시에 받은 주소로 올라왔고, 너무 크지 않은지 */
    public void checkUploaded(String key, PhotoKeys.Purpose purpose, Long meetupId, long maxBytes) {
        if (!PhotoKeys.belongsTo(key, purpose, meetupId)) {
            throw ApiException.badRequest("PHOTO_KEY", "사진을 다시 올려 주세요");
        }
        Long size = storage.size(key).orElseThrow(
                () -> ApiException.badRequest("PHOTO_MISSING", "사진이 아직 올라가지 않았어요. 다시 올려 주세요"));
        if (size > maxBytes) {
            storage.delete(key);
            throw ApiException.badRequest("PHOTO_TOO_LARGE", "사진이 너무 커요. 다시 골라 주세요");
        }
    }

    private static ApiException dailyLimit() {
        return ApiException.badRequest("PHOTO_DAILY_LIMIT",
                "톡방 사진은 하루에 " + PhotoLimits.CHAT_PER_DAY + "장까지 보낼 수 있어요");
    }

    static String noticeText(ChatKind kind, String name) {
        return name + (kind == ChatKind.JOIN ? "님이 들어왔어요" : "님이 나갔어요");
    }

    static String shorten(String text, int max) {
        String oneLine = text.replaceAll("\\s+", " ").strip();
        return oneLine.length() > max ? oneLine.substring(0, max) + "…" : oneLine;
    }

    static void afterCommit(Runnable task) {
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
}
