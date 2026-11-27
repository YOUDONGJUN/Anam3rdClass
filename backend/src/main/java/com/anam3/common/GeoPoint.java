package com.anam3.common;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

/**
 * 지도 위 한 점 (위도·경도). 우리나라 안만 받는다 (3교시 장소 · 학교 캠퍼스).
 * 내 위치(원우가 지금 있는 곳)는 서버로 보내지 않는다 — 거리는 폰에서 계산한다
 */
public record GeoPoint(
        @NotNull(message = "위치를 다시 골라 주세요")
        @DecimalMin(value = "33.0", message = "우리나라 안의 장소만 고를 수 있어요")
        @DecimalMax(value = "39.0", message = "우리나라 안의 장소만 고를 수 있어요")
        Double lat,

        @NotNull(message = "위치를 다시 골라 주세요")
        @DecimalMin(value = "124.0", message = "우리나라 안의 장소만 고를 수 있어요")
        @DecimalMax(value = "132.0", message = "우리나라 안의 장소만 고를 수 있어요")
        Double lng) {

    /** 둘 다 있을 때만 점 (아니면 null) */
    public static GeoPoint of(Double lat, Double lng) {
        return lat == null || lng == null ? null : new GeoPoint(lat, lng);
    }
}
