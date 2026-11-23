package com.anam3.notice;

import java.time.Instant;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public final class NoticeDtos {

    private NoticeDtos() {
    }

    /** official=true 면 운영자 글 (name 은 "운영자") */
    public record AuthorView(String name, String admissionLabel, boolean official, boolean me, boolean masked) {
    }

    public record NoticeSummary(Long id, String title, String preview, AuthorView author, boolean pinned,
            Instant createdAt, Instant updatedAt) {
    }

    public record NoticeDetail(Long id, String title, String content, AuthorView author, boolean pinned,
            boolean canEdit, Instant createdAt, Instant updatedAt) {
    }

    public record NoticeRequest(
            @NotBlank(message = "제목을 입력해 주세요")
            @Size(max = 60, message = "제목은 60자까지 쓸 수 있어요")
            String title,

            @NotBlank(message = "내용을 입력해 주세요")
            @Size(max = 3000, message = "내용은 3000자까지 쓸 수 있어요")
            String content) {
    }

    public record PinRequest(@NotNull Boolean pinned) {
    }
}
