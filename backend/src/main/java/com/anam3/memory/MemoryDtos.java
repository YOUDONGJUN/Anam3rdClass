package com.anam3.memory;

import java.time.Instant;
import java.util.List;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import com.anam3.meetup.MeetupDtos.PersonView;

public final class MemoryDtos {

    private MemoryDtos() {
    }

    /** 추억 올리기: 사진 1~5장 (먼저 받은 주소로 올린 뒤 이름을 넣는다) + 한 줄 + 공개 범위 */
    public record CreateRequest(
            @Size(max = Memory.CAPTION_MAX, message = "한 줄은 200자까지 쓸 수 있어요")
            String caption,
            @NotNull(message = "누구에게 보여줄지 골라 주세요")
            Memory.Visibility visibility,
            @NotNull(message = "사진을 골라 주세요")
            @Size(min = 1, max = 5, message = "사진은 한 번에 1~5장 올릴 수 있어요")
            List<String> photoKeys) {
    }

    public record PhotoView(String key, String url) {
    }

    public record MemoryView(Long id, Long meetupId, String meetupTitle, Instant meetAt, PersonView author,
            boolean mine, String caption, Memory.Visibility visibility, List<PhotoView> photos, Instant createdAt,
            boolean canDelete) {
    }

    public record MemoryFeed(List<MemoryView> items, Long nextBefore) {
    }

    /**
     * 3교시 상세의 추억 칸.
     * canAdd: 지금 올릴 수 있다 (함께했고 모였던 3교시) · photosLeft: 이 3교시에 더 올릴 수 있는 장수
     */
    public record MeetupMemories(List<MemoryView> items, boolean canAdd, int photosLeft) {
    }
}
