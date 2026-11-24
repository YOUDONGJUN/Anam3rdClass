package com.anam3.common;

import java.util.List;
import java.util.Locale;

/**
 * 3교시·공지에 쓰는 글에서 심한 욕설·성적인 말을 걸러낸다 (앱스토어 UGC 정책: 부적절한 콘텐츠 필터).
 * 숫자·기호를 끼워 넣어도(씨1발) 잡도록 한글·영문·띄어쓰기만 남기고 비교한다. 띄어쓰기는 지우지 않는다 —
 * 지우면 "맥주병 신나게"가 걸린다. 평범한 말이 걸리지 않게 뜻이 분명한 낱말만 넣는다 (예: "시발점", "보지 마").
 * 나머지는 신고·차단과 교수·대학원장의 관리로 다룬다.
 */
public final class ContentFilter {

    private static final List<String> BANNED = List.of(
            "씨발", "씨빨", "씨팔", "씨바알", "시발놈", "시발년", "ㅅㅂ놈", "병신", "븅신", "좆", "개새끼", "개새기", "개색기",
            "지랄", "미친놈", "미친년", "니애미", "느금마", "애미뒤", "창녀", "걸레년", "섹스", "fuck");

    private ContentFilter() {
    }

    /** 걸리는 말이 있으면 400 에러 */
    public static void check(String... texts) {
        for (String text : texts) {
            if (text != null && containsBanned(text)) {
                throw ApiException.badRequest("INAPPROPRIATE",
                        "부적절한 표현이 들어 있어요. 함께 쓰는 공간이니 고쳐서 다시 올려 주세요");
            }
        }
    }

    public static boolean containsBanned(String text) {
        String compact = text.toLowerCase(Locale.ROOT).replaceAll("[^a-z가-힣ㄱ-ㅎㅏ-ㅣ\\s]", "");
        return BANNED.stream().anyMatch(compact::contains);
    }
}
