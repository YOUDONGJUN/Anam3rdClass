package com.anam3.storage;

import java.io.IOException;
import java.nio.file.Files;
import java.time.Duration;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 폴더 저장소(내 PC·테스트)의 사진 올리기·보기. 로그인 대신 서명된 주소로 확인한다 (R2 와 같은 방식).
 * R2 를 쓰면 쓰지 않는다 (404).
 */
@RestController
public class LocalFileController {

    private final PhotoStorage storage;

    public LocalFileController(PhotoStorage storage) {
        this.storage = storage;
    }

    @PutMapping("/api/files/{kind}/{meetupId}/{name}")
    public ResponseEntity<Void> upload(@PathVariable String kind, @PathVariable String meetupId,
            @PathVariable String name, @RequestParam long exp, @RequestParam String sig,
            @RequestHeader(value = "Content-Type", required = false) String contentType, HttpServletRequest request)
            throws IOException {
        if (!(storage instanceof LocalPhotoStorage local)) {
            return ResponseEntity.notFound().build();
        }
        String key = kind + "/" + meetupId + "/" + name;
        // "image/jpeg;charset=..." 처럼 붙은 값은 떼고 비교한다 (R2 는 브라우저가 보낸 그대로 서명을 확인한다)
        String type = contentType == null ? null : contentType.split(";")[0].strip();
        if (!PhotoKeys.isValid(key) || !local.verify("PUT", key, exp, type, sig)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        try {
            local.write(key, request.getInputStream(), PhotoLimits.MAX_BYTES);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.CONTENT_TOO_LARGE).build();
        }
        return ResponseEntity.ok().build();
    }

    @GetMapping("/api/files/{kind}/{meetupId}/{name}")
    public ResponseEntity<byte[]> view(@PathVariable String kind, @PathVariable String meetupId,
            @PathVariable String name, @RequestParam long exp, @RequestParam String sig) throws IOException {
        if (!(storage instanceof LocalPhotoStorage local)) {
            return ResponseEntity.notFound().build();
        }
        String key = kind + "/" + meetupId + "/" + name;
        if (!PhotoKeys.isValid(key) || !local.verify("GET", key, exp, "", sig)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        var file = local.read(key);
        if (file.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(PhotoKeys.contentTypeOf(key)))
                .cacheControl(CacheControl.maxAge(Duration.ofHours(6)).cachePrivate())
                .body(Files.readAllBytes(file.get()));
    }
}
