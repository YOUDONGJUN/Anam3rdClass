package com.anam3.memory;

import java.util.List;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.anam3.auth.LoginMember;
import com.anam3.chat.ChatDtos.UploadRequest;
import com.anam3.chat.ChatDtos.UploadTicket;
import com.anam3.memory.MemoryDtos.CreateRequest;
import com.anam3.memory.MemoryDtos.MeetupMemories;
import com.anam3.memory.MemoryDtos.MemoryFeed;
import com.anam3.memory.MemoryDtos.MemoryView;

/** 추억 (모였던 3교시의 사진과 한 줄) */
@RestController
public class MemoryController {

    private final MemoryService memories;

    public MemoryController(MemoryService memories) {
        this.memories = memories;
    }

    @GetMapping("/api/memories")
    public MemoryFeed feed(@AuthenticationPrincipal LoginMember me, @RequestParam(required = false) Long before) {
        return memories.feed(me, before);
    }

    @GetMapping("/api/meetups/{id}/memories")
    public MeetupMemories ofMeetup(@AuthenticationPrincipal LoginMember me, @PathVariable Long id) {
        return memories.ofMeetup(me, id);
    }

    @PostMapping("/api/meetups/{id}/memories/uploads")
    public List<UploadTicket> uploads(@AuthenticationPrincipal LoginMember me, @PathVariable Long id,
            @Valid @RequestBody UploadRequest req) {
        return memories.uploads(me, id, req.contentTypes());
    }

    @PostMapping("/api/meetups/{id}/memories")
    @ResponseStatus(HttpStatus.CREATED)
    public MemoryView create(@AuthenticationPrincipal LoginMember me, @PathVariable Long id,
            @Valid @RequestBody CreateRequest req) {
        return memories.create(me, id, req);
    }

    @DeleteMapping("/api/memories/{id}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal LoginMember me, @PathVariable Long id) {
        memories.delete(me, id);
        return ResponseEntity.noContent().build();
    }
}
