package com.anam3.common;

/**
 * 이름 가운데를 * 로 가린다.
 * <pre>
 * 김민수   -> 김*수
 * 이수     -> 이*
 * 남궁민수 -> 남**수
 * </pre>
 */
public final class NameMasker {

    private NameMasker() {
    }

    public static String mask(String name) {
        if (name == null || name.isBlank()) {
            return "익명";
        }
        int[] cps = name.strip().codePoints().toArray();
        if (cps.length == 1) {
            return "*";
        }
        if (cps.length == 2) {
            return new String(cps, 0, 1) + "*";
        }
        StringBuilder sb = new StringBuilder().appendCodePoint(cps[0]);
        for (int i = 1; i < cps.length - 1; i++) {
            sb.append(cps[i] == ' ' ? ' ' : '*');
        }
        return sb.appendCodePoint(cps[cps.length - 1]).toString();
    }
}
