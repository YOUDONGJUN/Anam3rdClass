package com.anam3.meetup;

import java.time.Instant;
import java.util.List;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class MeetupDtos {

    private MeetupDtos() {
    }

    /**
     * 화면에 보이는 사람 정보. masked=true 면 name 은 "김*수" 처럼 가려진 이름이다.
     */
    public record PersonView(String name, String admissionLabel, boolean me, boolean host, boolean masked) {
    }

    public record MeetupSummary(
            Long id,
            String title,
            String place,
            MeetupCategory category,
            Instant meetAt,
            Instant deadlineAt,
            int minPeople,
            int maxPeople,
            int participantCount,
            MeetupStatus status,
            boolean joinable,
            boolean joined,
            boolean hosting,
            PersonView host) {
    }

    public record MeetupDetail(
            Long id,
            String title,
            String description,
            String place,
            MeetupCategory category,
            Instant meetAt,
            Instant deadlineAt,
            int minPeople,
            int maxPeople,
            int participantCount,
            MeetupStatus status,
            boolean joinable,
            boolean joined,
            boolean hosting,
            PersonView host,
            List<PersonView> participants,
            /** 실명이 공개된 상태인지 (성사 후 참여자 또는 운영자) */
            boolean revealed,
            /** 성사 후 참여자/개설자/운영자에게만 내려간다. 그 외엔 null */
            String openChatUrl,
            boolean hasOpenChat,
            boolean canEdit,
            boolean canCancel,
            boolean canJoin,
            boolean canLeave,
            Instant createdAt) {
    }

    public record MeetupCreateRequest(
            @NotBlank(message = "제목을 입력해 주세요")
            @Size(min = 2, max = 40, message = "제목은 2~40자로 입력해 주세요")
            String title,

            @Size(max = 500, message = "설명은 500자까지 쓸 수 있어요")
            String description,

            @NotBlank(message = "장소를 입력해 주세요")
            @Size(max = 60, message = "장소는 60자까지 쓸 수 있어요")
            String place,

            @NotNull(message = "종류를 골라 주세요")
            MeetupCategory category,

            @NotNull(message = "모임 시간을 정해 주세요")
            Instant meetAt,

            @NotNull(message = "모집 마감 시간을 정해 주세요")
            Instant deadlineAt,

            @NotNull(message = "최소 인원을 정해 주세요")
            @Min(value = 2, message = "최소 인원은 2명 이상이에요")
            @Max(value = 20, message = "최소 인원은 20명까지예요")
            Integer minPeople,

            @NotNull(message = "최대 인원을 정해 주세요")
            @Min(value = 2, message = "최대 인원은 2명 이상이에요")
            @Max(value = 30, message = "최대 인원은 30명까지예요")
            Integer maxPeople,

            @Size(max = 300, message = "링크가 너무 길어요")
            @Pattern(regexp = "^$|^https://\\S+$", message = "오픈채팅 링크는 https:// 로 시작해야 해요")
            String openChatUrl) {
    }

    public record MeetupUpdateRequest(
            @NotBlank(message = "제목을 입력해 주세요")
            @Size(min = 2, max = 40, message = "제목은 2~40자로 입력해 주세요")
            String title,

            @Size(max = 500, message = "설명은 500자까지 쓸 수 있어요")
            String description,

            @NotBlank(message = "장소를 입력해 주세요")
            @Size(max = 60, message = "장소는 60자까지 쓸 수 있어요")
            String place,

            @NotNull(message = "종류를 골라 주세요")
            MeetupCategory category,

            @Size(max = 300, message = "링크가 너무 길어요")
            @Pattern(regexp = "^$|^https://\\S+$", message = "오픈채팅 링크는 https:// 로 시작해야 해요")
            String openChatUrl) {
    }
}
