package com.anam3.storage;

import java.nio.file.Path;
import java.time.Clock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.anam3.common.AppProperties;

@Configuration
public class StorageConfig {

    private static final Logger log = LoggerFactory.getLogger(StorageConfig.class);

    @Bean
    public PhotoStorage photoStorage(StorageProperties props, AppProperties app, Clock clock) {
        if (props != null && props.usesR2()) {
            log.info("사진 저장소: Cloudflare R2 ({})", props.r2().bucket());
            return new R2PhotoStorage(props.r2(), clock);
        }
        String dir = props == null ? null : props.localDir();
        Path root = dir == null || dir.isBlank()
                ? Path.of(System.getProperty("java.io.tmpdir"), "anam3-photos")
                : Path.of(dir);
        log.warn("사진 저장소: 폴더 {} (R2 설정이 없어요. 운영에서는 R2_* 환경변수를 넣어 주세요)", root);
        return new LocalPhotoStorage(root, app.jwt().secret(), clock);
    }
}
