package com.anam3.push;

import java.util.Arrays;
import java.util.Collection;
import java.util.EnumSet;
import java.util.Set;
import java.util.stream.Collectors;

/** 앱 푸시 알림 종류. 내 정보에서 종류별로 끌 수 있다 (members.push_off) */
public enum PushType {
    /** 내가 연 3교시에 누가 참여하거나 나감 */
    JOIN,
    /** 참여한 3교시가 성사됨 (실명·단톡방 공개) */
    REACHED,
    /** 참여한 3교시 모집 마감 30분 전인데 아직 인원이 모자람 */
    DEADLINE,
    /** 참여한 3교시의 시간·장소가 바뀌거나 취소됨 */
    CHANGED,
    /** 새 공지 */
    NOTICE,
    /** 새 연합 3교시 */
    FEDERATION;

    /** "NOTICE,FEDERATION" → 종류 (모르는 이름은 건너뛴다) */
    public static Set<PushType> parseList(String csv) {
        Set<PushType> types = EnumSet.noneOf(PushType.class);
        if (csv == null || csv.isBlank()) {
            return types;
        }
        for (String name : csv.split(",")) {
            Arrays.stream(values()).filter(t -> t.name().equals(name.strip())).findFirst().ifPresent(types::add);
        }
        return types;
    }

    /** 종류 → "JOIN,NOTICE" (정해진 순서, 비어 있으면 null) */
    public static String joinList(Collection<PushType> types) {
        if (types == null || types.isEmpty()) {
            return null;
        }
        return EnumSet.copyOf(types).stream().map(Enum::name).collect(Collectors.joining(","));
    }
}
