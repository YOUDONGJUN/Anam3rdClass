package com.anam3.member;

import java.util.regex.Pattern;

import com.anam3.common.ApiException;

/**
 * 송금 링크 다듬기 · 확인. 피싱 링크를 막으려고 토스(toss.me)와 카카오페이 송금 링크(qr.kakaopay.com)만 받는다.
 * 계좌번호는 받지 않는다.
 */
public final class PayLinks {

    /** 토스 아이디: 글자 · 숫자 · . _ - (한글 아이디도 있다) */
    private static final Pattern TOSS_ID = Pattern.compile("^[\\p{L}\\p{N}._-]{1,30}$");
    private static final Pattern TOSS_PREFIX = Pattern.compile("^(?i)(https?://)?(www\\.)?toss\\.me/");
    private static final Pattern KAKAOPAY_PREFIX = Pattern.compile("^(?i)(https?://)?qr\\.kakaopay\\.com/");
    private static final Pattern KAKAOPAY_CODE = Pattern.compile("^[A-Za-z0-9]{4,60}$");

    private PayLinks() {
    }

    /** "toss.me/아이디", "https://toss.me/아이디/", "아이디" → "아이디". 비었으면 null */
    public static String tossId(String raw) {
        String s = strip(raw);
        if (s == null) {
            return null;
        }
        s = TOSS_PREFIX.matcher(s).replaceFirst("").replaceFirst("/+$", "");
        if (!TOSS_ID.matcher(s).matches()) {
            throw ApiException.badRequest("BAD_TOSS_ID", "토스 아이디를 확인해 주세요 (예: toss.me/아이디)");
        }
        return s;
    }

    /** "qr.kakaopay.com/Abc123" → "https://qr.kakaopay.com/Abc123". 비었으면 null */
    public static String kakaoPayUrl(String raw) {
        String s = strip(raw);
        if (s == null) {
            return null;
        }
        var prefix = KAKAOPAY_PREFIX.matcher(s);
        String code = prefix.find() ? s.substring(prefix.end()).replaceFirst("/+$", "") : "";
        if (!KAKAOPAY_CODE.matcher(code).matches()) {
            throw ApiException.badRequest("BAD_KAKAOPAY_URL", "카카오페이 송금 링크를 확인해 주세요 (qr.kakaopay.com/… 으로 시작해요)");
        }
        return "https://qr.kakaopay.com/" + code;
    }

    private static String strip(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return raw.strip();
    }
}
