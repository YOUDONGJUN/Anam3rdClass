package com.anam3.storage;

/**
 * 사진 장수·크기 제한. 사진은 폰에서 줄여서 올린다 (톡방 긴 변 1280px ≈ 200KB, 추억 2048px ≈ 400KB) —
 * 여기 크기는 줄이지 않은 사진을 막는 넉넉한 상한이다.
 */
public final class PhotoLimits {

    /** 한 번에 올릴 수 있는 장수 (톡방 · 추억) */
    public static final int PER_SEND = 5;
    /** 톡방: 한 사람이 하루에 보낼 수 있는 장수 */
    public static final int CHAT_PER_DAY = 30;
    /** 추억: 3교시 하나에 모두 합쳐 */
    public static final int MEMORY_PER_MEETUP = 10;
    /** 톡방 사진은 올린 날부터 30일 보관 (저장소 수명 규칙과 같게) */
    public static final int CHAT_KEEP_DAYS = 30;

    public static final long CHAT_MAX_BYTES = 1_500_000;
    public static final long MEMORY_MAX_BYTES = 3_000_000;
    public static final long MAX_BYTES = MEMORY_MAX_BYTES;

    private PhotoLimits() {
    }

    public static long maxBytes(PhotoKeys.Purpose purpose) {
        return purpose == PhotoKeys.Purpose.CHAT ? CHAT_MAX_BYTES : MEMORY_MAX_BYTES;
    }
}
