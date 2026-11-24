package com.anam3.common;

import java.util.List;
import java.util.Map;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 안드로이드 앱 링크 확인 파일. 이 파일에 앱 서명 인증서가 있어야 공유한 3교시 링크(https://…/meetups/12)를
 * 눌렀을 때 브라우저 대신 앱이 바로 열린다 (ANDROID_CERT_SHA256 — 없으면 빈 목록 → 브라우저로 열린다).
 */
@RestController
public class AppLinksController {

    private final AppProperties props;

    public AppLinksController(AppProperties props) {
        this.props = props;
    }

    @GetMapping(value = "/.well-known/assetlinks.json", produces = MediaType.APPLICATION_JSON_VALUE)
    public List<Map<String, Object>> assetLinks() {
        List<String> certs = props.androidCertSha256();
        if (certs.isEmpty()) {
            return List.of();
        }
        return List.of(Map.of(
                "relation", List.of("delegate_permission/common.handle_all_urls"),
                "target", Map.of(
                        "namespace", "android_app",
                        "package_name", props.androidPackage(),
                        "sha256_cert_fingerprints", certs)));
    }
}
