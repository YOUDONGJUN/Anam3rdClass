package com.anam3.chat;

import java.time.Instant;
import java.util.List;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import com.anam3.meetup.MeetupDtos.PersonView;
import com.anam3.meetup.MeetupStatus;

public final class ChatDtos {

    private ChatDtos() {
    }

    /**
     * 보내기: 글이나 사진 중 하나는 있어야 한다. 사진은 먼저 받은 주소로 올린 뒤 그 이름(key)을 넣는다.
     * 이모티콘(sticker)은 글·사진 없이 그것만 보낸다. replyToId: 답장할 메시지 번호
     */
    public record SendRequest(
            @Size(max = ChatMessage.MAX_LENGTH, message = "메시지는 1000자까지 보낼 수 있어요")
            String body,
            @Size(max = 5, message = "사진은 한 번에 5장까지 보낼 수 있어요")
            List<String> photoKeys,
            @Size(max = ChatStickers.MAX_KEY)
            String sticker,
            Long replyToId) {
    }

    public record ReadRequest(@NotNull Long lastId) {
    }

    public record MuteRequest(@NotNull Boolean muted) {
    }

    /** 공감 누르기 (null 이면 내 공감을 뺀다) */
    public record ReactRequest(ChatReactionKind reaction) {
    }

    /** 톡방 공지로 올리기 (null 이면 내린다) */
    public record PinRequest(Long messageId) {
    }

    /** 지금 톡방 공지 (없으면 null) */
    public record PinResult(QuoteView pinned) {
    }

    /**
     * 다른 메시지를 짧게 보일 때 (답장의 원래 메시지 · 톡방 공지).
     * sender: 보낸 원우 이름(3교시 공개 규칙대로) · mine: 내가 보낸 것 · preview: 한 줄
     * (지웠으면 "지운 메시지예요", 차단한 원우 것이면 "차단한 원우의 메시지예요")
     */
    public record QuoteView(Long id, String sender, boolean mine, String preview) {
    }

    /** 공감 하나: 몇 명이 눌렀는지, 나도 눌렀는지 (누가 눌렀는지는 보이지 않는다) */
    public record ReactionView(ChatReactionKind reaction, int count, boolean mine) {
    }

    /** 사진 올릴 주소 받기: 올릴 사진마다 형식 하나 (image/jpeg · image/webp) */
    public record UploadRequest(
            @NotNull @Size(min = 1, max = 5, message = "사진은 한 번에 5장까지 올릴 수 있어요")
            List<String> contentTypes) {
    }

    public record UploadTicket(String key, String uploadUrl, String contentType) {
    }

    /** 사진 한 장. expired: 보관 기간(30일)이 지나 저장소에서 지워졌다 */
    public record PhotoView(String key, String url, boolean expired) {
    }

    /**
     * 메시지 한 개.
     * kind: TEXT(원우가 보낸 메시지) · JOIN/LEAVE(sender 가 들어왔다/나갔다는 안내 줄)
     * sticker: 이모티콘 이름 (이모티콘 메시지만)
     * hidden: 내가 차단한 원우의 메시지 (글·사진·이모티콘을 내려보내지 않는다)
     * reply: 답장이면 원래 메시지 · reactions: 공감 (지운·가린 메시지는 비어 있다)
     */
    public record MessageView(Long id, ChatKind kind, PersonView sender, boolean mine, String body,
            String sticker, List<PhotoView> photos, boolean deleted, boolean hidden, Instant createdAt,
            QuoteView reply, List<ReactionView> reactions) {
    }

    /**
     * 톡방 한 화면 분량.
     * hasMore: 더 오래된 메시지가 있다 · canWrite: 보낼 수 있다 (취소된 3교시는 읽기만)
     * federated: 연합 3교시 — 화면이 보낸 사람의 학교를 함께 보인다
     * reads: 나 말고 함께하는 원우들이 각자 어디까지 읽었는지 (마지막으로 읽은 메시지 번호, 누구인지는 없다).
     * 화면이 메시지마다 "안 읽은 사람 수"를 센다. 들어온 사람은 들어온 순간까지 읽은 것으로 친다
     * pinned: 톡방 공지 (없으면 null) · canPin: 공지를 올리고 내릴 수 있다 (연 사람)
     */
    public record ChatPage(Long meetupId, String title, MeetupStatus status, Instant meetAt, int people,
            boolean federated,
            List<MessageView> messages, boolean hasMore, boolean canWrite, long lastReadId, boolean muted,
            int photoKeepDays, int photosLeftToday, List<Long> reads, QuoteView pinned, boolean canPin) {
    }

    /** 새로 온 메시지만 (after 이후) + 지금 읽은 자리 + 지금 톡방 공지 */
    public record ChatUpdates(List<MessageView> messages, List<Long> reads, QuoteView pinned) {
    }

    /** 내 톡방 목록 한 줄 */
    public record RoomView(Long meetupId, String title, MeetupStatus status, Instant meetAt, long unread,
            boolean muted, String lastPreview, String lastSender, Instant lastAt) {
    }

    public record RoomList(long totalUnread, List<RoomView> rooms) {
    }
}
