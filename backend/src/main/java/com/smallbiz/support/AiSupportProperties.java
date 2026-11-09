package com.smallbiz.support;

import java.math.BigDecimal;
import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * AI 客服配置（环境变量见 application.yml）。没有 API 密钥时 AI 关闭，客服只用关键词匹配。
 *
 * @param apiKey            Anthropic API 密钥（不要写进代码库）
 * @param model             模型 id
 * @param dailyLimit        全平台每天最多 AI 回答次数
 * @param perIpDailyLimit   每个 IP 每天最多 AI 回答次数
 * @param timeout           单次调用超时
 * @param inputPrice        估算费用用：每百万输入 token 美元
 * @param cacheReadPrice    每百万缓存读取 token 美元
 * @param outputPrice       每百万输出 token 美元
 */
@ConfigurationProperties(prefix = "smallbiz.ai-support")
public record AiSupportProperties(String apiKey, String model, Integer dailyLimit, Integer perIpDailyLimit,
        Duration timeout, BigDecimal inputPrice, BigDecimal cacheReadPrice, BigDecimal outputPrice) {

    public AiSupportProperties {
        apiKey = apiKey == null ? "" : apiKey.trim();
        model = model == null || model.isBlank() ? "claude-haiku-5-5" : model.trim();
        dailyLimit = dailyLimit == null ? 1000 : dailyLimit;
        perIpDailyLimit = perIpDailyLimit == null ? 30 : perIpDailyLimit;
        timeout = timeout == null ? Duration.ofSeconds(20) : timeout;
        inputPrice = inputPrice == null ? new BigDecimal("0.10") : inputPrice;
        cacheReadPrice = cacheReadPrice == null ? new BigDecimal("0.01") : cacheReadPrice;
        outputPrice = outputPrice == null ? new BigDecimal("0.50") : outputPrice;
    }

    public boolean hasKey() {
        return !apiKey.isEmpty();
    }
}
