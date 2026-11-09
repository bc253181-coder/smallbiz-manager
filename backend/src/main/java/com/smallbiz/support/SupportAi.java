package com.smallbiz.support;

import java.util.List;

/**
 * AI 回答接口：只把系统规则 + 话术库 + 最近几轮对话 + 当前问题交给模型，不接触任何店铺数据。
 * 生产实现见 {@link AnthropicSupportAi}；测试中用模拟实现替换。
 */
public interface SupportAi {

    enum Status {
        /** 能根据话术回答。 */
        ANSWERED,
        /** 话术里没有 / 无关 / 需要人工处理。 */
        HANDOFF
    }

    /** 一轮对话：用户的话或助手的回答。 */
    record Turn(boolean fromUser, String text) {
    }

    record Result(Status status, String answer, List<Long> faqIds, long actionFaqId, long inputTokens,
            long cacheReadTokens, long outputTokens) {
    }

    /** 是否可用（配置了 API 密钥）。 */
    boolean available();

    String model();

    /** 调用模型；出错、超时、拒绝时抛出异常，由调用方退回关键词匹配。 */
    Result answer(String systemPrompt, List<Turn> history, String question);
}
