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
     * 이모티콘(sticker)은 글·사진 없이 그것만 보낸다
     */
    public record SendRequest(
            @Size(max = ChatMessage.MAX_LENGTH, message = "메시지는 1000자까지 보낼 수 있어요")
            String body,
            @Size(max = 5, message = "사진은 한 번에 5장까지 보낼 수 있어요")
            List<String> photoKeys,
            @Size(max = ChatStickers.MAX_KEY)
            String sticker) {

        public SendRequest(String body, List<String> photoKeys) {
            this(body, photoKeys, null);
        }
    }

    public record ReadRequest(@NotNull Long lastId) {
    }

    public record MuteRequest(@NotNull Boolean muted) {
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
     */
    public record MessageView(Long id, ChatKind kind, PersonView sender, boolean mine, String body,
            String sticker, List<PhotoView> photos, boolean deleted, boolean hidden, Instant createdAt) {
    }

    /**
     * 톡방 한 화면 분량.
     * hasMore: 더 오래된 메시지가 있다 · canWrite: 보낼 수 있다 (취소된 3교시는 읽기만)
     * federated: 연합 3교시 — 화면이 보낸 사람의 학교를 함께 보인다
     * reads: 나 말고 함께하는 원우들이 각자 어디까지 읽었는지 (마지막으로 읽은 메시지 번호, 누구인지는 없다).
     * 화면이 메시지마다 "안 읽은 사람 수"를 센다. 들어온 사람은 들어온 순간까지 읽은 것으로 친다
     */
    public record ChatPage(Long meetupId, String title, MeetupStatus status, Instant meetAt, int people,
            boolean federated,
            List<MessageView> messages, boolean hasMore, boolean canWrite, long lastReadId, boolean muted,
            int photoKeepDays, int photosLeftToday, List<Long> reads) {
    }

    /** 새로 온 메시지만 (after 이후) + 지금 읽은 자리 */
    public record ChatUpdates(List<MessageView> messages, List<Long> reads) {
    }

    /** 내 톡방 목록 한 줄 */
    public record RoomView(Long meetupId, String title, MeetupStatus status, Instant meetAt, long unread,
            boolean muted, String lastPreview, String lastSender, Instant lastAt) {
    }

    public record RoomList(long totalUnread, List<RoomView> rooms) {
    }
}
