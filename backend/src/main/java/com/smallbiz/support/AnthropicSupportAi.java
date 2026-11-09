package com.smallbiz.support;

import java.util.List;

import org.springframework.stereotype.Component;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.models.messages.CacheControlEphemeral;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.OutputConfig;
import com.anthropic.models.messages.StopReason;
import com.anthropic.models.messages.StructuredMessage;
import com.anthropic.models.messages.StructuredMessageCreateParams;
import com.anthropic.models.messages.StructuredOutputConfig;
import com.anthropic.models.messages.TextBlockParam;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

/** 通过 Anthropic 官方 Java SDK 调用模型；没有配置密钥时不创建客户端。 */
@Component
public class AnthropicSupportAi implements SupportAi {

    /** 模型必须按这个结构回答（结构化输出），服务端再校验内容。 */
    record AiOutput(
            @JsonPropertyDescription("ANSWERED if the help documents answer the question, otherwise HANDOFF") Status status,
            @JsonPropertyDescription("The reply shown to the user") String answer,
            @JsonPropertyDescription("Numbers of the help documents used for the answer; empty for HANDOFF") List<Long> faqIds,
            @JsonPropertyDescription("Number of the most relevant document that has a button, or 0") long actionFaqId) {
    }

    private final AiSupportProperties props;
    private final AnthropicClient client;

    public AnthropicSupportAi(AiSupportProperties props) {
        this.props = props;
        this.client = props.hasKey()
                ? AnthropicOkHttpClient.builder().apiKey(props.apiKey()).timeout(props.timeout()).maxRetries(1).build()
                : null;
    }

    @Override
    public boolean available() {
        return client != null;
    }

    @Override
    public String model() {
        return props.model();
    }

    @Override
    public Result answer(String systemPrompt, List<Turn> history, String question) {
        if (client == null) {
            throw new IllegalStateException("AI support is not configured");
        }
        StructuredMessageCreateParams.Builder<AiOutput> b = MessageCreateParams.builder()
                .outputConfig(StructuredOutputConfig.<AiOutput>builder()
                        .effort(OutputConfig.Effort.LOW)
                        .format(AiOutput.class)
                        .build())
                .model(props.model())
                .maxTokens(2048L)
                // 规则 + 话术库是固定前缀：开启提示缓存
                .systemOfTextBlockParams(List.of(TextBlockParam.builder()
                        .text(systemPrompt)
                        .cacheControl(CacheControlEphemeral.builder().build())
                        .build()));
        for (Turn t : history) {
            if (t.fromUser()) {
                b.addUserMessage(t.text());
            } else {
                b.addAssistantMessage(t.text());
            }
        }
        b.addUserMessage(question);

        StructuredMessage<AiOutput> res = client.messages().create(b.build());
        StopReason stop = res.stopReason().orElse(null);
        if (stop != null && !StopReason.END_TURN.equals(stop)) {
            throw new IllegalStateException("Unexpected stop reason: " + stop);
        }
        AiOutput out = res.content().stream()
                .flatMap(c -> c.text().stream())
                .map(t -> t.text())
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Empty AI reply"));
        var usage = res.usage();
        return new Result(out.status(), out.answer(), out.faqIds() == null ? List.of() : out.faqIds(),
                out.actionFaqId(), usage.inputTokens(), usage.cacheReadInputTokens().orElse(0L), usage.outputTokens());
    }
}
