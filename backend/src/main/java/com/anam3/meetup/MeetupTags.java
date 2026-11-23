package com.anam3.meetup;

import java.util.Arrays;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 마실 것(종류)과 모임 목적은 여러 개를 고를 수 있다.
 * 따로 테이블을 두지 않고 DB 한 칸에 "SOJU,BEER", "친목,학업" 처럼 쉼표로 이어 저장한다.
 * (그래서 목적에는 쉼표를 쓸 수 없다 — MeetupDtos 에서 막는다)
 */
final class MeetupTags {

    static final String SEPARATOR = ",";

    private MeetupTags() {
    }

    /** 고른 순서와 상관없이 늘 같은 순서(enum 선언 순)로 저장한다 */
    static String joinCategories(Collection<MeetupCategory> categories) {
        if (categories == null || categories.isEmpty()) {
            throw new IllegalArgumentException("종류는 하나 이상이어야 해요");
        }
        return EnumSet.copyOf(categories).stream().map(Enum::name).collect(Collectors.joining(SEPARATOR));
    }

    static List<MeetupCategory> splitCategories(String stored) {
        return Arrays.stream(stored.split(SEPARATOR))
                .map(String::strip)
                .filter(s -> !s.isEmpty())
                .map(MeetupCategory::valueOf)
                .sorted()
                .toList();
    }

    static String joinPurposes(List<String> purposes) {
        return purposes == null || purposes.isEmpty() ? null : String.join(SEPARATOR, purposes);
    }

    static List<String> splitPurposes(String stored) {
        if (stored == null || stored.isBlank()) {
            return List.of();
        }
        return Arrays.stream(stored.split(SEPARATOR)).map(String::strip).filter(s -> !s.isEmpty()).toList();
    }
}
