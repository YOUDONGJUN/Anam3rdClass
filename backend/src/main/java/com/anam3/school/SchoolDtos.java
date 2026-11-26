package com.anam3.school;

import java.util.List;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class SchoolDtos {

    /** 이메일 도메인 입력 검사 (앞의 @ 는 있어도 된다) */
    static final String DOMAIN_PATTERN = "^\\s*@?[A-Za-z0-9-]+(\\.[A-Za-z0-9-]+)+\\s*$";

    private SchoolDtos() {
    }

    /**
     * 사람·3교시 옆에 붙는 학교 표시. code 로 화면 색(배지·테마)을 고른다 (null 이면 기본 색).
     */
    /** place: 화면 이름의 동네 (신촌 → "신촌3교시"). 없으면 null — 화면이 공식 이름을 쓴다 */
    public record SchoolTag(Long id, String code, String name, String shortName, String place) {

        public static SchoolTag from(School s) {
            return s == null ? null
                    : new SchoolTag(s.getId(), s.getCode(), s.getName(), s.getShortName(), s.getPlace());
        }
    }

    /** graduateSchool: 학교의 대학원과 다를 때만 값이 있다 */
    public record DepartmentView(Long id, String name, String shortName, String graduateSchool, boolean active) {

        public static DepartmentView from(Department d) {
            return new DepartmentView(d.getId(), d.getName(), d.getShortName(), d.getGraduateSchool(), d.isActive());
        }
    }

    public record DomainView(Long id, String domain) {

        public static DomainView from(SchoolEmailDomain d) {
            return new DomainView(d.getId(), d.getDomain());
        }
    }

    /**
     * 학교 하나의 모든 것: 가입 화면(숨긴 학과 빼고)과 관리 화면(숨긴 학과 포함)이 같이 쓴다.
     */
    public record SchoolView(Long id, String code, String name, String shortName, String graduateSchool,
            int latestCohort, String place, List<DomainView> emailDomains, List<DepartmentView> departments) {
    }

    // ---------------- 대학원장 전용 입력 ----------------

    public record SchoolRequest(
            @NotBlank(message = "학교 이름을 입력해 주세요")
            @Size(max = 40, message = "학교 이름은 40자까지 쓸 수 있어요")
            String name,

            @NotBlank(message = "짧은 이름을 입력해 주세요 (예: 고려대)")
            @Size(max = 10, message = "짧은 이름은 10자까지 쓸 수 있어요")
            String shortName,

            @Size(max = 40, message = "대학원 이름은 40자까지 쓸 수 있어요")
            String graduateSchool,

            @NotNull(message = "최신 기수를 입력해 주세요")
            @Min(value = 1, message = "기수를 확인해 주세요")
            @Max(value = 999, message = "기수를 확인해 주세요")
            Integer latestCohort,

            @Size(max = 6, message = "동네 이름은 6자까지 쓸 수 있어요 (예: 신촌)")
            String place) {
    }

    /** 학교 추가: 첫 이메일 도메인도 같이 받는다 */
    public record NewSchoolRequest(
            @NotBlank(message = "학교 이름을 입력해 주세요")
            @Size(max = 40, message = "학교 이름은 40자까지 쓸 수 있어요")
            String name,

            @NotBlank(message = "짧은 이름을 입력해 주세요 (예: 고려대)")
            @Size(max = 10, message = "짧은 이름은 10자까지 쓸 수 있어요")
            String shortName,

            @Size(max = 40, message = "대학원 이름은 40자까지 쓸 수 있어요")
            String graduateSchool,

            @NotNull(message = "최신 기수를 입력해 주세요")
            @Min(value = 1, message = "기수를 확인해 주세요")
            @Max(value = 999, message = "기수를 확인해 주세요")
            Integer latestCohort,

            @Size(max = 6, message = "동네 이름은 6자까지 쓸 수 있어요 (예: 신촌)")
            String place,

            @NotBlank(message = "이메일 도메인을 입력해 주세요")
            @Size(max = 60, message = "이메일 도메인이 너무 길어요")
            @Pattern(regexp = DOMAIN_PATTERN, message = "이메일 도메인을 확인해 주세요 (예: korea.ac.kr)")
            String emailDomain) {
    }

    public record DomainRequest(
            @NotBlank(message = "이메일 도메인을 입력해 주세요")
            @Size(max = 60, message = "이메일 도메인이 너무 길어요")
            @Pattern(regexp = DOMAIN_PATTERN, message = "이메일 도메인을 확인해 주세요 (예: korea.ac.kr)")
            String domain) {
    }

    public record DepartmentRequest(
            @NotBlank(message = "학과 이름을 입력해 주세요")
            @Size(max = 40, message = "학과 이름은 40자까지 쓸 수 있어요")
            String name,

            @NotBlank(message = "짧은 이름을 입력해 주세요 (예: 인융)")
            @Size(max = 10, message = "짧은 이름은 10자까지 쓸 수 있어요")
            String shortName,

            /** 학교의 대학원과 다를 때만 (예: 공학대학원). 비우면 학교의 대학원 */
            @Size(max = 40, message = "대학원 이름은 40자까지 쓸 수 있어요")
            String graduateSchool,

            /** 숨기기(false)·다시 보이기(true). 추가할 때는 비워도 된다 */
            Boolean active) {
    }
}
