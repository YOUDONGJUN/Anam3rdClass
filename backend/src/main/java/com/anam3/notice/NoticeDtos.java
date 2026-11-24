package com.anam3.notice;

import java.time.Instant;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import com.anam3.school.SchoolDtos.SchoolTag;

public final class NoticeDtos {

    private NoticeDtos() {
    }

    /** official=true 면 대학원장 글 (실명 + "대학원장" 표시). school 은 글쓴이 학교 배지 */
    public record AuthorView(String name, String cohortLabel, String departmentLabel, SchoolTag school,
            boolean official, boolean me, boolean masked) {
    }

    /** federated=true 면 연합 공지 (다섯 학교 모두에게 보인다). school: 이 학교 원우에게만 보이는 공지 (연합이면 null) */
    public record NoticeSummary(Long id, String title, String preview, AuthorView author, boolean pinned,
            boolean federated, SchoolTag school, Instant createdAt, Instant updatedAt) {
    }

    public record NoticeDetail(Long id, String title, String content, AuthorView author, boolean pinned,
            boolean federated, SchoolTag school, boolean canEdit, boolean canPin, Instant createdAt,
            Instant updatedAt) {
    }

    public record NoticeRequest(
            @NotBlank(message = "제목을 입력해 주세요")
            @Size(max = 60, message = "제목은 60자까지 쓸 수 있어요")
            String title,

            @NotBlank(message = "내용을 입력해 주세요")
            @Size(max = 3000, message = "내용은 3000자까지 쓸 수 있어요")
            String content,

            /** 연합 공지로 쓰기 (대학원장만, 새로 쓸 때만). 비우면 학교 공지 */
            Boolean federated,

            /** 학교 공지를 올릴 학교 (대학원장만 다른 학교를 고른다, 새로 쓸 때만). 비우면 우리 학교 */
            Long schoolId) {
    }

    public record PinRequest(@NotNull Boolean pinned) {
    }
}
