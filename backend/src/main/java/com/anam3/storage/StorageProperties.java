package com.anam3.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 사진 저장소 (app.storage.*). R2 네 값이 다 있으면 R2, 아니면 localDir 폴더(비우면 임시 폴더)에 둔다.
 * 폴더 저장은 내 PC·테스트용 — Render 는 다시 배포하면 디스크가 지워진다.
 */
@ConfigurationProperties("app.storage")
public record StorageProperties(R2 r2, String localDir) {

    /** Cloudflare R2: 계정 ID · API 토큰의 Access Key ID / Secret Access Key · 버킷 이름 */
    public record R2(String accountId, String accessKeyId, String secretAccessKey, String bucket) {

        boolean configured() {
            return filled(accountId) && filled(accessKeyId) && filled(secretAccessKey) && filled(bucket);
        }

        private static boolean filled(String s) {
            return s != null && !s.isBlank();
        }
    }

    public boolean usesR2() {
        return r2 != null && r2.configured();
    }
}
