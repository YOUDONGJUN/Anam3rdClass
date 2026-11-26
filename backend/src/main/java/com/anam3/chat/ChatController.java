package com.anam3.chat;

import java.util.List;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.anam3.auth.LoginMember;
import com.anam3.chat.ChatDtos.ChatPage;
import com.anam3.chat.ChatDtos.ChatUpdates;
import com.anam3.chat.ChatDtos.MessageView;
import com.anam3.chat.ChatDtos.MuteRequest;
import com.anam3.chat.ChatDtos.ReadRequest;
import com.anam3.chat.ChatDtos.RoomList;
import com.anam3.chat.ChatDtos.SendRequest;
import com.anam3.chat.ChatDtos.UploadRequest;
import com.anam3.chat.ChatDtos.UploadTicket;

/** 3교시 톡방 */
@RestController
public class ChatController {

    private final ChatService chat;

    public ChatController(ChatService chat) {
        this.chat = chat;
    }

    /** 가장 최근 메시지 (before 가 있으면 그보다 오래된 것) */
    @GetMapping("/api/meetups/{id}/chat")
    public ChatPage page(@AuthenticationPrincipal LoginMember me, @PathVariable Long id,
            @RequestParam(required = false) Long before) {
        return chat.page(me, id, before);
    }

    /** after 다음에 온 메시지 */
    @GetMapping("/api/meetups/{id}/chat/updates")
    public ChatUpdates updates(@AuthenticationPrincipal LoginMember me, @PathVariable Long id,
            @RequestParam long after) {
        return chat.updates(me, id, after);
    }

    @PostMapping("/api/meetups/{id}/chat")
    public MessageView send(@AuthenticationPrincipal LoginMember me, @PathVariable Long id,
            @Valid @RequestBody SendRequest req) {
        return chat.send(me, id, req);
    }

    @PostMapping("/api/meetups/{id}/chat/uploads")
    public List<UploadTicket> uploads(@AuthenticationPrincipal LoginMember me, @PathVariable Long id,
            @Valid @RequestBody UploadRequest req) {
        return chat.uploads(me, id, req.contentTypes());
    }

    @DeleteMapping("/api/meetups/{id}/chat/{messageId}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal LoginMember me, @PathVariable Long id,
            @PathVariable Long messageId) {
        chat.delete(me, id, messageId);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/api/meetups/{id}/chat/read")
    public ResponseEntity<Void> read(@AuthenticationPrincipal LoginMember me, @PathVariable Long id,
            @Valid @RequestBody ReadRequest req) {
        chat.read(me, id, req.lastId());
        return ResponseEntity.noContent().build();
    }

    /** 톡방 화면을 닫았다 (이제 새 메시지는 폰 알림으로) */
    @PostMapping("/api/meetups/{id}/chat/leave")
    public ResponseEntity<Void> leave(@AuthenticationPrincipal LoginMember me, @PathVariable Long id) {
        chat.leaveRoom(me, id);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/api/meetups/{id}/chat/mute")
    public ResponseEntity<Void> mute(@AuthenticationPrincipal LoginMember me, @PathVariable Long id,
            @Valid @RequestBody MuteRequest req) {
        chat.mute(me, id, req.muted());
        return ResponseEntity.noContent().build();
    }

    /** 내 톡방 목록과 안 읽은 메시지 수 */
    @GetMapping("/api/me/chats")
    public RoomList rooms(@AuthenticationPrincipal LoginMember me) {
        return chat.rooms(me);
    }
}
