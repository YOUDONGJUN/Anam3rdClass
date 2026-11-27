package com.anam3.common;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

/**
 * 배포 시 React 빌드 결과(static/)를 이 서버가 함께 서빙한다.
 * /meetups/3 같은 화면 주소로 새로고침해도 index.html 을 돌려줘서 React Router 가 처리하게 한다.
 */
@Configuration
public class SpaWebConfig implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // 파일명에 해시가 붙는 빌드 산출물은 오래 캐시
        registry.addResourceHandler("/assets/**")
                .addResourceLocations("classpath:/static/assets/")
                .setCacheControl(CacheControl.maxAge(365, TimeUnit.DAYS).cachePublic());

        // 톡방 이모티콘 그림: 자주 보이니 하루 동안 저장해 둔다 (그림을 바꾸면 하루 안에 바뀐다)
        registry.addResourceHandler("/stickers/**")
                .addResourceLocations("classpath:/static/stickers/")
                .setCacheControl(CacheControl.maxAge(1, TimeUnit.DAYS).cachePublic());

        // index.html(과 화면 주소)은 브라우저에 저장하지 않는다. 빌드할 때마다 파일 시각이 1980-02-01 로 같아서
        // 시각으로 비교하면(If-Modified-Since → 304) 브라우저가 예전 index.html 과 예전 화면을 계속 쓴다.
        // 시각 비교를 끄면 예전 index.html 을 가진 브라우저도 다음에 열 때 새 화면을 받는다.
        registry.addResourceHandler("/**")
                .addResourceLocations("classpath:/static/")
                .setCacheControl(CacheControl.noStore())
                .setUseLastModified(false)
                .resourceChain(true)
                .addResolver(new PathResourceResolver() {
                    @Override
                    protected Resource getResource(String resourcePath, Resource location) throws IOException {
                        Resource requested = location.createRelative(resourcePath);
                        if (requested.exists() && requested.isReadable()) {
                            return requested;
                        }
                        if (resourcePath.startsWith("api/") || resourcePath.contains(".")) {
                            return null; // 진짜 404
                        }
                        Resource index = new ClassPathResource("/static/index.html");
                        return index.exists() ? index : null;
                    }
                });
    }
}
