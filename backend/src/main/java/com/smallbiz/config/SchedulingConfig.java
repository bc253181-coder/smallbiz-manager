package com.smallbiz.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** 定时任务（在线支付对账）。测试环境关闭，测试里直接调用。 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "smallbiz.scheduling.enabled", havingValue = "true", matchIfMissing = true)
public class SchedulingConfig {
}
