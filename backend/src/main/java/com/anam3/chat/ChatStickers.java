package com.anam3.chat;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 톡방 이모티콘 (KU 호랑이). 그림은 화면이 가진다 (frontend/public/stickers/tiger/*.svg).
 * 여기 있는 이름만 보낼 수 있고, 알림·톡방 목록에는 이름(한 줄)으로 보인다.
 */
public final class ChatStickers {

    public static final int MAX_KEY = 40;

    private static final Map<String, String> LABELS = new LinkedHashMap<>();

    static {
        LABELS.put("tiger-going", "가는 중");
        LABELS.put("tiger-where", "어디야?");
        LABELS.put("tiger-thanks", "고마워요");
        LABELS.put("tiger-arrived", "도착!");
        LABELS.put("tiger-late", "늦어요");
        LABELS.put("tiger-cheers", "건배!");
        LABELS.put("tiger-good", "수고했어요");
        LABELS.put("tiger-lol", "ㅋㅋㅋ");
        LABELS.put("tiger-sorry", "미안해요");
        LABELS.put("tiger-ok", "좋아요");
        LABELS.put("tiger-bye", "또 봐요");
        LABELS.put("tiger-hungry", "배고파");
    }

    private ChatStickers() {
    }

    public static boolean exists(String key) {
        return key != null && LABELS.containsKey(key);
    }

    /** 알림·톡방 목록에 보일 한 줄: "(이모티콘) 가는 중" */
    public static String preview(String key) {
        String label = LABELS.get(key);
        return label == null ? "(이모티콘)" : "(이모티콘) " + label;
    }
}
