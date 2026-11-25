package com.anam3.settlement;

import java.time.Instant;
import java.util.List;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import com.anam3.meetup.MeetupDtos.PersonView;

public final class SettlementDtos {

    private SettlementDtos() {
    }

    /**
     * 정산 만들기 · 고치기. refs: 나눌 사람의 참여 번호 (3교시 상세 participants[].ref, 계산한 사람은 빼고).
     * 1인 금액은 계산한 사람 포함 인원으로 나눠 100원 단위로 올림
     */
    public record SettlementRequest(
            @NotNull(message = "총액을 적어 주세요")
            @Min(value = 1_000, message = "총액은 1,000원 ~ 300만 원으로 적어 주세요")
            @Max(value = 3_000_000, message = "총액은 1,000원 ~ 300만 원으로 적어 주세요")
            Integer total,

            @NotNull(message = "나눌 원우를 골라 주세요")
            @Size(min = 1, max = 60, message = "나 말고 한 명 이상 골라 주세요")
            List<@NotNull Long> refs) {
    }

    /** 보냈어요 · 받았어요 (false 면 취소) */
    public record SentRequest(@NotNull(message = "보냈는지 골라 주세요") Boolean sent) {
    }

    /** 한 사람 몫 */
    public record ShareView(PersonView person, boolean sent, Instant sentAt) {
    }

    /**
     * 3교시 상세의 정산 (함께한 사람에게만).
     * tossUrl: 계산한 사람의 토스 링크에 1인 금액을 붙인 것 · kakaoPayUrl: 카카오페이 송금 링크 (금액은 직접)
     * editable: 계산한 사람, 아직 아무도 안 보냈을 때 · canRemind: 계산한 사람, 하루에 한 번
     */
    public record SettlementView(
            PersonView payer,
            boolean mine,
            int total,
            int perPerson,
            /** 계산한 사람 포함 나눈 인원 */
            int headcount,
            List<ShareView> shares,
            int sentCount,
            String tossUrl,
            String kakaoPayUrl,
            boolean editable,
            boolean canRemind,
            /** 다시 알리기를 할 수 있는 때 (계산한 사람, 안 끝났을 때만) */
            Instant remindableAt,
            boolean done,
            Instant createdAt) {
    }

    /** 3교시 상세에 붙는 정산 칸 */
    public record SettlementInfo(boolean canSettle, SettlementView view) {

        public static final SettlementInfo NONE = new SettlementInfo(false, null);
    }

    /** 내가 보낼 정산 (내 3교시 탭) */
    public record SettlementDue(Long meetupId, String title, Instant meetAt, int amount, PersonView payer,
            String tossUrl, String kakaoPayUrl) {
    }
}
