package com.anam3.meetup;

import java.time.Instant;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import com.anam3.feedback.FeedbackDtos.FeedbackPrompt;
import com.anam3.school.SchoolDtos.SchoolTag;

public final class MeetupDtos {

    /** 모임 목적은 3개까지, 하나에 12자까지 */
    public static final int MAX_PURPOSES = 3;

    private MeetupDtos() {
    }

    /**
     * 화면에 보이는 사람 정보. masked=true 면 name 은 "김*대" 처럼 가려진 이름이다.
     * owner=true 면 대학원장 — 이름이 가려지지 않고 "대학원장" 표시가 붙는다.
     * (교수는 3교시에서 일반 원우와 똑같이 보인다)
     * school 은 학교 배지 (연합 3교시에서 어느 학교 사람인지 보인다).
     * ref 는 상세 화면 참여자에게만 붙는 이 3교시 안의 참여 번호 — 신고·차단할 때 쓴다 (회원 번호는 내보내지 않는다).
     */
    public record PersonView(String name, String cohortLabel, String departmentLabel, SchoolTag school, boolean me,
            boolean host, boolean owner, boolean masked, Long ref) {
    }

    public record MeetupSummary(
            Long id,
            String title,
            String place,
            List<MeetupCategory> categories,
            List<String> purposes,
            Instant meetAt,
            Instant deadlineAt,
            /** null 이면 최소 인원 없음 (몇 명이 오든 진행) */
            Integer minPeople,
            /** null 이면 인원 제한 없음 */
            Integer maxPeople,
            int participantCount,
            MeetupStatus status,
            boolean joinable,
            boolean joined,
            boolean hosting,
            PersonView host,
            /** 우리 기수만 모집하면 그 기수 (null 이면 누구나) */
            Integer onlyCohort,
            /** SCHOOL(우리 학교) · COHORT(우리 기수) · ALL(연합) */
            MeetupAudience audience,
            /** 연 사람의 학교 */
            SchoolTag school,
            /** 중간 합류 OK */
            boolean lateJoin,
            /** 참여비(원). null 이면 없음 */
            Integer fee,
            /** 참여비 선입금 */
            boolean feePrepaid) {
    }

    public record MeetupDetail(
            Long id,
            String title,
            String description,
            String place,
            List<MeetupCategory> categories,
            List<String> purposes,
            Instant meetAt,
            Instant deadlineAt,
            /** null 이면 최소 인원 없음 (몇 명이 오든 진행) */
            Integer minPeople,
            /** null 이면 인원 제한 없음 */
            Integer maxPeople,
            int participantCount,
            MeetupStatus status,
            boolean joinable,
            boolean joined,
            boolean hosting,
            PersonView host,
            List<PersonView> participants,
            /** 실명이 공개된 상태인지 (성사 후 참여자 또는 관리자) */
            boolean revealed,
            /** 성사 후 참여자/개설자/관리자에게만 내려간다. 그 외엔 null */
            String openChatUrl,
            boolean hasOpenChat,
            boolean canEdit,
            boolean canCancel,
            boolean canJoin,
            boolean canLeave,
            Instant createdAt,
            /** 우리 기수만 모집하면 그 기수 (null 이면 누구나) */
            Integer onlyCohort,
            /** SCHOOL(우리 학교) · COHORT(우리 기수) · ALL(연합) */
            MeetupAudience audience,
            /** 연 사람의 학교 */
            SchoolTag school,
            /** 중간 합류 OK */
            boolean lateJoin,
            /** 참여비(원). null 이면 없음 */
            Integer fee,
            /** 참여비 선입금 */
            boolean feePrepaid,
            /** 지우기: 연 사람 · 그 학교 교수 · 대학원장 */
            boolean canDelete,
            /** 지금 인원으로 확정 (무산된 3교시도 모임 전이면 되살리기): 연 사람 · 대학원장, 2명 이상일 때 */
            boolean canConfirm,
            /** 3교시 후기: 성사된 3교시 다음 날부터 7일 동안 함께한 사람에게만. 그 밖엔 null */
            FeedbackPrompt feedback) {
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

            @NotEmpty(message = "무엇을 마실지 하나 이상 골라 주세요")
            List<@NotNull(message = "종류를 다시 골라 주세요") MeetupCategory> categories,

            @Size(max = MAX_PURPOSES, message = "모임 목적은 3개까지 고를 수 있어요")
            List<@NotBlank(message = "빈 목적은 넣을 수 없어요")
                 @Size(max = 12, message = "모임 목적은 하나에 12자까지 쓸 수 있어요")
                 @Pattern(regexp = "^[^,]*$", message = "모임 목적에는 쉼표(,)를 쓸 수 없어요") String> purposes,

            @NotNull(message = "모임 시간을 정해 주세요")
            Instant meetAt,

            @NotNull(message = "모집 마감 시간을 정해 주세요")
            Instant deadlineAt,

            /** 비우면(null) 최소 인원 없음 — 몇 명이 오든 진행 */
            @Min(value = 2, message = "최소 인원은 2명 이상이에요")
            @Max(value = 20, message = "최소 인원은 20명까지예요")
            Integer minPeople,

            /** 비우면(null) 인원 제한 없음 */
            @Min(value = 2, message = "최대 인원은 2명 이상이에요")
            @Max(value = 30, message = "최대 인원은 30명까지예요. 더 많으면 '제한 없음'으로 열어 주세요")
            Integer maxPeople,

            @Size(max = 300, message = "링크가 너무 길어요")
            @Pattern(regexp = "^$|^https://\\S+$", message = "오픈채팅 링크는 https:// 로 시작해야 해요")
            String openChatUrl,

            /** 누구에게 여는지: 우리 학교(기본, 비워도 됨) · 우리 기수 · 연합. 수정에서 바꿀 수 있다 (이미 참여한 원우가 빠지게 좁힐 수는 없다) */
            MeetupAudience audience,

            /** 중간 합류 OK (비우면 아니오) */
            Boolean lateJoin,

            /** 참여비(원). 비우면 참여비 없음 */
            @Min(value = 1000, message = "참여비는 1,000원 이상으로 적어 주세요")
            @Max(value = 1000000, message = "참여비는 100만 원까지 적을 수 있어요")
            Integer fee,

            /** 참여비 선입금 (참여비가 있을 때만) */
            Boolean feePrepaid) {
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

            @NotEmpty(message = "무엇을 마실지 하나 이상 골라 주세요")
            List<@NotNull(message = "종류를 다시 골라 주세요") MeetupCategory> categories,

            @Size(max = MAX_PURPOSES, message = "모임 목적은 3개까지 고를 수 있어요")
            List<@NotBlank(message = "빈 목적은 넣을 수 없어요")
                 @Size(max = 12, message = "모임 목적은 하나에 12자까지 쓸 수 있어요")
                 @Pattern(regexp = "^[^,]*$", message = "모임 목적에는 쉼표(,)를 쓸 수 없어요") String> purposes,

            @Size(max = 300, message = "링크가 너무 길어요")
            @Pattern(regexp = "^$|^https://\\S+$", message = "오픈채팅 링크는 https:// 로 시작해야 해요")
            String openChatUrl,

            // 아래는 비우면(null) 그대로 둔다 (이 칸들이 없던 예전 화면의 요청도 그대로 받는다)

            /** 인원 바꾸기 */
            @Valid
            PeopleChange people,

            /** 시간 바꾸기 */
            @Valid
            ScheduleChange schedule,

            /** 대상 바꾸기. 이미 참여한 원우가 빠지게 좁힐 수는 없다 */
            MeetupAudience audience,

            /** 참여 방식(중간 합류 · 참여비) 바꾸기 */
            @Valid
            OptionsChange options) {
    }

    /** 수정할 때 바꾸는 시간. 열 때와 같은 규칙으로 검사한다 (바뀌지 않았으면 검사하지 않는다) */
    public record ScheduleChange(
            @NotNull(message = "모임 시간을 정해 주세요")
            Instant meetAt,

            @NotNull(message = "모집 마감 시간을 정해 주세요")
            Instant deadlineAt) {
    }

    /** 수정할 때 바꾸는 참여 방식 */
    public record OptionsChange(
            Boolean lateJoin,

            @Min(value = 1000, message = "참여비는 1,000원 이상으로 적어 주세요")
            @Max(value = 1000000, message = "참여비는 100만 원까지 적을 수 있어요")
            Integer fee,

            Boolean feePrepaid) {
    }

    /** 수정할 때 바꾸는 인원. 각각 비우면(null) 최소 인원 없음 · 최대 인원 없음 */
    public record PeopleChange(
            @Min(value = 2, message = "최소 인원은 2명 이상이에요")
            @Max(value = 20, message = "최소 인원은 20명까지예요")
            Integer minPeople,

            @Min(value = 2, message = "최대 인원은 2명 이상이에요")
            @Max(value = 30, message = "최대 인원은 30명까지예요. 더 많으면 '제한 없음'으로 바꿔 주세요")
            Integer maxPeople) {
    }
}
