package com.anam3.common;

import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 배포 헬스체크 + 무료 서버 잠들지 않게 깨우는 용도. DB 를 건드리지 않아서 DB 는 쉬게 둔다.
 */
@RestController
public class HealthController {

    @GetMapping("/api/health")
    public Map<String, String> health() {
        return Map.of("status", "ok");
    }
}
