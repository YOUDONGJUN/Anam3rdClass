package com.anam3.storage;

import java.util.Optional;

/**
 * 사진 저장소. 운영은 Cloudflare R2, 내 PC·테스트는 폴더.
 * 사진은 서버를 거치지 않는다 — 폰이 줄인 사진을 서명된 주소로 바로 올리고, 화면도 서명된 주소로 바로 본다.
 */
public interface PhotoStorage {

    /** 올릴 주소. 이 주소로 PUT 하면서 Content-Type 을 contentType 그대로 보내야 한다 */
    String uploadUrl(String key, String contentType);

    /** 볼 주소 (몇 시간 동안 같은 주소라 브라우저가 캐시한다) */
    String viewUrl(String key);

    /** 올라온 사진 크기 (아직 안 올라왔으면 비어 있다) */
    Optional<Long> size(String key);

    void delete(String key);
}
