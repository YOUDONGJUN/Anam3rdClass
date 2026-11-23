package com.anam3.member;

/** SW·AI융합대학원 학과 */
public enum Department {
    SOFTWARE_SECURITY("소프트웨어보안학과", "소보"),
    BIG_DATA("빅데이터융합학과", "빅융"),
    AI("인공지능융합학과", "인융");

    private final String label;
    private final String shortLabel;

    Department(String label, String shortLabel) {
        this.label = label;
        this.shortLabel = shortLabel;
    }

    /** 전체 이름: 인공지능융합학과 */
    public String label() {
        return label;
    }

    /** 이름 옆에 붙는 짧은 이름: 인융 */
    public String shortLabel() {
        return shortLabel;
    }
}
