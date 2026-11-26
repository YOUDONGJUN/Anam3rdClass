package com.anam3.storage;

import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * 사진 이름(저장소 key): {종류}/{3교시 번호}/{무작위}.{확장자}.
 * 톡방 사진은 chat/ — 저장소의 수명 규칙이 올린 날부터 30일 뒤 지운다. 추억은 memories/ — 계속 둔다.
 */
public final class PhotoKeys {

    public enum Purpose {
        /** 톡방 사진 (30일) */
        CHAT("chat"),
        /** 추억 사진 (계속) */
        MEMORY("memories");

        private final String prefix;

        Purpose(String prefix) {
            this.prefix = prefix;
        }

        public String prefix() {
            return prefix;
        }
    }

    /** 폰이 줄여서 보내는 형식 */
    public static final Map<String, String> TYPES = Map.of("image/jpeg", "jpg", "image/webp", "webp");

    private static final Pattern VALID = Pattern.compile(
            "^(chat|memories)/\\d{1,18}/[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.(jpg|webp)$");

    private PhotoKeys() {
    }

    public static String create(Purpose purpose, Long meetupId, String contentType) {
        return purpose.prefix() + "/" + meetupId + "/" + UUID.randomUUID() + "." + TYPES.get(contentType);
    }

    public static boolean isValid(String key) {
        return key != null && VALID.matcher(key).matches();
    }

    /** 이 3교시의 이 종류 사진 이름인지 */
    public static boolean belongsTo(String key, Purpose purpose, Long meetupId) {
        return isValid(key) && key.startsWith(purpose.prefix() + "/" + meetupId + "/");
    }

    public static String contentTypeOf(String key) {
        return key.endsWith(".webp") ? "image/webp" : "image/jpeg";
    }
}
