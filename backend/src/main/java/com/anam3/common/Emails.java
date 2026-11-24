package com.anam3.common;

import java.util.Locale;

/** 이메일 다루는 도우미. 저장·비교는 늘 소문자로 한다 */
public final class Emails {

    /** 화면·API 입력 검사용 (아주 엄격하지 않게: 실제 확인은 인증코드로 한다). 앞뒤 공백은 normalize 가 지운다 */
    public static final String PATTERN = "^\\s*[A-Za-z0-9._%+-]+@[A-Za-z0-9-]+(\\.[A-Za-z0-9-]+)+\\s*$";

    private Emails() {
    }

    public static String normalize(String email) {
        return email.strip().toLowerCase(Locale.ROOT);
    }

    /**
     * hong+1@korea.ac.kr 처럼 + 뒤에 아무거나 붙인 주소 (같은 메일함으로 간다).
     * 이런 주소로는 가입·인증하지 못하게 해서 한 사람이 계정을 여러 개 만들거나, 이용이 막힌 뒤 다시 들어오지 못하게 한다.
     */
    public static boolean hasAlias(String email) {
        int at = email.indexOf('@');
        return at > 0 && email.substring(0, at).contains("+");
    }

    public static String normalizeOrNull(String email) {
        return email == null || email.isBlank() ? null : normalize(email);
    }

    /**
     * 교수에게 보여줄 때 앞 두 글자만 남기고 가린다.
     * <pre>
     * hong@korea.ac.kr  -> ho**@korea.ac.kr
     * ab@korea.ac.kr    -> a*@korea.ac.kr
     * </pre>
     */
    public static String mask(String email) {
        if (email == null) {
            return null;
        }
        int at = email.indexOf('@');
        if (at <= 0) {
            return "***";
        }
        String local = email.substring(0, at);
        int keep = local.length() <= 2 ? 1 : 2;
        return local.substring(0, keep) + "*".repeat(local.length() - keep) + email.substring(at);
    }
}
