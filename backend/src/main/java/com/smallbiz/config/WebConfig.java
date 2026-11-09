package com.smallbiz.config;

import java.time.Duration;

import org.springframework.context.annotation.Configuration;
import org.springframework.http.CacheControl;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import org.springframework.web.servlet.config.annotation.InterceptorRegistry;

import com.smallbiz.billing.PlanGuard;
import com.smallbiz.catalog.ImageStorage;
import com.smallbiz.staff.MemberGuard;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final ImageStorage imageStorage;
    private final PlanGuard planGuard;

    private final MemberGuard memberGuard;

    public WebConfig(ImageStorage imageStorage, PlanGuard planGuard, MemberGuard memberGuard) {
        this.imageStorage = imageStorage;
        this.planGuard = planGuard;
        this.memberGuard = memberGuard;
    }

    /** 先核对成员身份（移除/改角色/专业版到期立即生效），再做套餐功能拦截（@RequiresFeature）。 */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(memberGuard).addPathPatterns("/api/**")
                .excludePathPatterns("/api/v1/auth/**", "/api/v1/tenants", "/api/v1/tenants/*/switch",
                        "/api/v1/tenants/*/leave", "/api/v1/invitations/preview", "/api/v1/invitations/accept",
                        "/api/v1/support/**");
        registry.addInterceptor(planGuard).addPathPatterns("/api/**");
    }

    /** 商品图片：文件名为随机 UUID 且内容不可变，可以长期缓存。 */
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler(ImageStorage.URL_PREFIX + "**")
                .addResourceLocations(imageStorage.root().toUri().toString())
                .setCacheControl(CacheControl.maxAge(Duration.ofDays(365)).cachePublic().immutable());
    }
}
