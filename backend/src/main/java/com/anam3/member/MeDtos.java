package com.anam3.member;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public final class MeDtos {

    private MeDtos() {
    }

    /**
     * 내 정보 고치기. 학과·기수는 누구나 (원우는 30일에 한 번).
     * 이름은 대학원장만 바꿀 수 있다 — 비우거나(null) 지금 이름 그대로 보내면 이름은 그대로 둔다.
     */
    public record MyProfileRequest(
            @Pattern(regexp = "^[가-힣a-zA-Z ]{2,20}$", message = "이름은 한글 또는 영문 2~20자로 입력해 주세요")
            String name,

            @NotNull(message = "기수를 선택해 주세요")
            @Min(value = 1, message = "기수를 확인해 주세요")
            @Max(value = 999, message = "기수를 확인해 주세요")
            Integer cohort,

            @NotNull(message = "학과를 선택해 주세요")
            Long departmentId) {
    }

    /** 랭킹에 안 나오기 */
    public record RankingSettingRequest(@NotNull(message = "설정을 골라 주세요") Boolean hidden) {
    }
}
