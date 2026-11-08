package com.smallbiz.config;

import java.time.Duration;

import org.springframework.context.annotation.Configuration;
import org.springframework.http.CacheControl;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import com.smallbiz.catalog.ImageStorage;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final ImageStorage imageStorage;

    public WebConfig(ImageStorage imageStorage) {
        this.imageStorage = imageStorage;
    }

    /** 商品图片：文件名为随机 UUID 且内容不可变，可以长期缓存。 */
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler(ImageStorage.URL_PREFIX + "**")
                .addResourceLocations(imageStorage.root().toUri().toString())
                .setCacheControl(CacheControl.maxAge(Duration.ofDays(365)).cachePublic().immutable());
    }
}
