package com.anam3.sanction;

import java.time.Instant;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import com.anam3.safety.Report;

public final class SanctionDtos {

    private SanctionDtos() {
    }

    /** 경고 주기 (교수·대학원장). 단계는 서버가 정한다. reportId 가 있으면 그 신고도 처리 완료로 */
    public record WarnRequest(
            @NotNull(message = "경고 이유를 골라 주세요")
            Report.Reason reason,

            /** 당사자에게 보이는 설명 (선택) */
            @Size(max = 300, message = "설명은 300자까지 쓸 수 있어요")
            String note,

            Long reportId) {
    }

    /**
     * 관리 화면의 제재 기록 한 줄. strike: 몇 번째 경고였는지 (취소한 것은 null).
     * actorName: 준 사람 실명 (관리 화면)
     */
    public record SanctionView(Long id, MemberSanction.Kind kind, String kindLabel, Report.Reason reason,
            String reasonLabel, String note, Long reportId, String actorName, Instant createdAt, Instant endsAt,
            Instant acknowledgedAt, Instant revokedAt, Integer strike) {
    }

    /**
     * 경고 받은 원우에게 보이는 창 (앱을 열면 확인할 때까지).
     * strikes: 지금까지 받은 경고 수 (취소한 것 빼고) · nextLabel: 한 번 더 받으면 (예: "30일 정지")
     */
    public record SanctionNotice(Long id, MemberSanction.Kind kind, String kindLabel, String reasonLabel, String note,
            Instant createdAt, Instant endsAt, int strikes, String nextLabel) {
    }

    /** 경고를 준 결과: 이번 단계와 그 원우의 지금 정지 상태 */
    public record WarnResult(MemberSanction.Kind kind, String kindLabel, int strike, Instant endsAt) {
    }
}
