package com.smallbiz.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.smallbiz.support.SupportAi.Result;
import com.smallbiz.support.SupportAi.Status;

/** AI 客服：用模拟的 AI（不需要 API 密钥），验证路由、校验、退回、限额、记录与后台。 */
@TestPropertySource(properties = { "smallbiz.ai-support.per-ip-daily-limit=3", "smallbiz.ai-support.daily-limit=200" })
class SupportAiApiTest extends IntegrationTest {

    private static final AtomicInteger IP = new AtomicInteger(1);

    @MockitoBean
    SupportAi ai;

    @Autowired
    SupportAiLogRepository logs;

    @Autowired
    SupportFaqRepository faqs;

    @Autowired
    SupportService service;

    @Autowired
    JdbcTemplate jdbc;

    @BeforeEach
    void aiOn() {
        reset(ai);
        when(ai.available()).thenReturn(true);
        when(ai.model()).thenReturn("test-model");
    }

    private static String nextIp() {
        return "10.9." + (IP.get() / 250) + "." + (IP.getAndIncrement() % 250);
    }

    private MockHttpServletResponse ask(String body, String ip) throws Exception {
        return mvc.perform(post("/api/v1/support/ask").contentType(MediaType.APPLICATION_JSON).content(body).with(r -> {
            r.setRemoteAddr(ip);
            return r;
        })).andReturn().getResponse();
    }

    private MockHttpServletResponse ask(String message) throws Exception {
        return ask("{\"message\":\"%s\"}".formatted(message), nextIp());
    }

    private long faqId(String question) {
        return faqs.findAll().stream().filter(f -> f.getQuestion().equals(question)).findFirst().orElseThrow().getId();
    }

    private void aiReplies(Status status, String answer, List<Long> ids, long action) {
        when(ai.answer(anyString(), anyList(), anyString()))
                .thenReturn(new Result(status, answer, ids, action, 900, 8000, 120));
    }

    @Test
    void welcomeSaysAiIsOn_andEnglishHasNoChineseQuickReplies() throws Exception {
        MockHttpServletResponse zh = mvc.perform(get("/api/v1/support/welcome")).andReturn().getResponse();
        assertThat((Boolean) read(zh, "$.ai")).isTrue();
        assertThat((List<?>) read(zh, "$.quickReplies")).isNotEmpty();
        MockHttpServletResponse en = mvc.perform(get("/api/v1/support/welcome?lang=en")).andReturn().getResponse();
        assertThat((String) read(en, "$.greeting")).startsWith("Hi!");
        assertThat((List<?>) read(en, "$.quickReplies")).isEmpty();
    }

    @Test
    void clearKeywordMatchIsAnsweredWithoutAi() throws Exception {
        MockHttpServletResponse res = ask("请问怎么开单？");
        assertThat((String) read(res, "$.source")).isEqualTo("KEYWORD");
        assertThat((String) read(res, "$.question")).isEqualTo("怎么开单？");
        verify(ai, never()).answer(anyString(), anyList(), anyString());
    }

    @Test
    void otherWordingGoesToAi_answerAndButtonComeFromCitedFaqs() throws Exception {
        long sale = faqId("怎么开单？");
        long price = faqId("套餐和价格");
        aiReplies(Status.ANSWERED, "点底部中间的 ＋ 按钮，搜商品加入订单，确认后点「完成开单」。", List.of(price, sale), sale);
        long before = logs.count();

        MockHttpServletResponse res = ask("客人买东西我在哪记一下");
        assertThat((Boolean) read(res, "$.matched")).isTrue();
        assertThat((String) read(res, "$.source")).isEqualTo("AI");
        assertThat((String) read(res, "$.answer")).contains("完成开单");
        assertThat((String) read(res, "$.actionPath")).isEqualTo("/sales/new");
        assertThat((Object) read(res, "$.handoff")).isNull();

        assertThat(logs.count()).isEqualTo(before + 1);
        SupportAiLog l = logs.findById(((Number) read(res, "$.logId")).longValue()).orElseThrow();
        assertThat(l.getStatus()).isEqualTo("ANSWERED");
        assertThat(l.getFaqIds()).isEqualTo(price + "," + sale);
        assertThat(l.getQuestion()).isEqualTo("客人买东西我在哪记一下");
        assertThat(l.getInputTokens()).isEqualTo(900);
        assertThat(l.getCacheReadTokens()).isEqualTo(8000);
        assertThat(l.getModel()).isEqualTo("test-model");

        // 回答可以评价一次
        String logId = String.valueOf(l.getId());
        mvc.perform(post("/api/v1/support/ai/" + logId + "/feedback").contentType(MediaType.APPLICATION_JSON)
                .content("{\"helpful\":false}")).andReturn();
        mvc.perform(post("/api/v1/support/ai/" + logId + "/feedback").contentType(MediaType.APPLICATION_JSON)
                .content("{\"helpful\":true}")).andReturn();
        assertThat(logs.findById(l.getId()).orElseThrow().getHelpful()).isFalse();
    }

    @Test
    void buttonOnlyFromCitedFaqs_neverFromUncitedOrMadeUp() throws Exception {
        long price = faqId("套餐和价格");
        long sale = faqId("怎么开单？");
        // actionFaqId 指向没有引用的话术：改用引用话术里带按钮的那条
        aiReplies(Status.ANSWERED, "标准版 6.99 元/月。", List.of(price), sale);
        MockHttpServletResponse res = ask("一个月要多少钱呀");
        assertThat((String) read(res, "$.actionPath")).isEqualTo("/settings/billing");

        // 引用的话术都没有按钮：不显示按钮
        long security = faqId("我的数据安全吗？");
        aiReplies(Status.ANSWERED, "每个店铺的数据都是隔离的。", List.of(security), sale);
        res = ask("别家店能看到我的东西吗");
        assertThat((Object) read(res, "$.actionPath")).isNull();
    }

    @Test
    void invalidAiAnswersHandOffToHuman() throws Exception {
        // 引用不存在的话术
        aiReplies(Status.ANSWERED, "可以用比特币付款。", List.of(999999L), 0);
        MockHttpServletResponse res = ask("可以用比特币付款吗");
        assertThat((Boolean) read(res, "$.matched")).isFalse();
        assertThat((Object) read(res, "$.answer")).isNull();
        assertThat((String) read(res, "$.handoff.wechatId")).isEqualTo("TEST_WECHAT");
        SupportAiLog l = logs.findById(((Number) read(res, "$.logId")).longValue()).orElseThrow();
        assertThat(l.getStatus()).isEqualTo("INVALID");

        // 没有引用任何话术
        aiReplies(Status.ANSWERED, "我觉得可以。", List.of(), 0);
        assertThat((Boolean) read(ask("能不能用支付宝花呗分期"), "$.matched")).isFalse();

        // 一部分引用不存在
        aiReplies(Status.ANSWERED, "可以。", List.of(faqId("怎么开单？"), 888888L), 0);
        assertThat((Boolean) read(ask("能开发票给客人吗"), "$.matched")).isFalse();

        // 未匹配问题记录下来，方便补充话术
        Integer n = jdbc.queryForObject("select count(*) from support_unmatched_queries where query_text = ?",
                Integer.class, "可以用比特币付款吗");
        assertThat(n).isEqualTo(1);
    }

    @Test
    void modelHandoffShowsShortNoteAndWechat() throws Exception {
        aiReplies(Status.HANDOFF, "退款需要人工客服处理。", List.of(), 0);
        MockHttpServletResponse res = ask("我要退款，钱什么时候退给我");
        assertThat((Boolean) read(res, "$.matched")).isFalse();
        assertThat((String) read(res, "$.answer")).isEqualTo("退款需要人工客服处理。");
        assertThat((String) read(res, "$.handoff.wechatId")).isEqualTo("TEST_WECHAT");
        assertThat((String) read(res, "$.source")).isEqualTo("AI");
    }

    @Test
    void aiErrorFallsBackToKeywordMatching() throws Exception {
        when(ai.answer(anyString(), anyList(), anyString())).thenThrow(new RuntimeException("timeout"));
        long before = logs.count();
        MockHttpServletResponse res = ask("我想新增商品"); // 关键词得分不够直接回答，但够关键词兜底
        assertThat(res.getStatus()).isEqualTo(200);
        assertThat((String) read(res, "$.source")).isEqualTo("KEYWORD");
        assertThat((String) read(res, "$.question")).isEqualTo("怎么添加商品？");
        assertThat(logs.count()).isEqualTo(before);

        res = ask("你们收不收木星贝壳呀");
        assertThat((Boolean) read(res, "$.matched")).isFalse();
        assertThat((String) read(res, "$.handoff.wechatId")).isEqualTo("TEST_WECHAT");
    }

    @Test
    void englishQuestionsAlwaysUseAi_andHandoffTipIsEnglish() throws Exception {
        aiReplies(Status.HANDOFF, "A refund needs our support team.", List.of(), 0);
        MockHttpServletResponse res = ask("{\"message\":\"怎么开单 refund please\",\"lang\":\"en\"}", nextIp());
        assertThat((String) read(res, "$.source")).isEqualTo("AI");
        assertThat((String) read(res, "$.handoff.tip")).startsWith("When adding us");
    }

    @Test
    @SuppressWarnings("unchecked")
    void followUpsSendCleanedHistory_andPromptHasLivePricesAndFaqs() throws Exception {
        long price = faqId("套餐和价格");
        aiReplies(Status.ANSWERED, "年付 69.9 元。", List.of(price), price);
        String body = """
                {"message":"那年付呢？","history":[
                  {"role":"bot","text":"你好！我是 SmallBiz AI 小助手"},
                  {"role":"user","text":"标准版多少钱"},
                  {"role":"bot","text":"标准版 6.99 元/月。"}]}
                """;
        ask(body, nextIp());

        ArgumentCaptor<String> system = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<List<SupportAi.Turn>> history = ArgumentCaptor.forClass(List.class);
        ArgumentCaptor<String> question = ArgumentCaptor.forClass(String.class);
        verify(ai).answer(system.capture(), history.capture(), question.capture());
        assertThat(question.getValue()).isEqualTo("那年付呢？");
        assertThat(history.getValue()).containsExactly(new SupportAi.Turn(true, "标准版多少钱"),
                new SupportAi.Turn(false, "标准版 6.99 元/月。"));
        assertThat(system.getValue()).contains("标准版 月付（30 天）：CNY 6.99", "专业版 年付（365 天）：MYR 69.9",
                "[文档 " + price + "]", "跳转按钮：查看套餐");

        // 调价后提示里的价格跟着变；提示内容稳定（相同输入两次生成完全一样，利于缓存）
        jdbc.update("update plan_prices set amount = 7.99 where plan = 'STANDARD' and period = 'MONTH' and currency = 'CNY'");
        try {
            SupportPrompt p = context.getBean(SupportPrompt.class);
            String a = p.build(p.enabledFaqs());
            assertThat(a).contains("标准版 月付（30 天）：CNY 7.99").isEqualTo(p.build(p.enabledFaqs()));
        } finally {
            jdbc.update("update plan_prices set amount = 6.99 where plan = 'STANDARD' and period = 'MONTH' and currency = 'CNY'");
        }
    }

    @Test
    void historyIsTrimmedAndMustAlternate() {
        List<SupportAi.Turn> in = new ArrayList<>();
        in.add(new SupportAi.Turn(false, "欢迎"));
        for (int i = 0; i < 10; i++) {
            in.add(new SupportAi.Turn(true, "问" + i));
            in.add(new SupportAi.Turn(false, "答" + i));
        }
        in.add(new SupportAi.Turn(true, "x".repeat(2000)));
        List<SupportAi.Turn> out = SupportService.sanitize(in);
        assertThat(out).isNotEmpty();
        assertThat(out.get(0).fromUser()).isTrue();
        assertThat(out.get(out.size() - 1).fromUser()).isFalse();
        for (int i = 1; i < out.size(); i++) {
            assertThat(out.get(i).fromUser()).isNotEqualTo(out.get(i - 1).fromUser());
        }
        assertThat(out.size()).isLessThanOrEqualTo(SupportService.MAX_HISTORY_TURNS);
        assertThat(out).allMatch(t -> t.text().length() <= SupportService.MAX_TURN_CHARS);
    }

    @Test
    void perIpDailyLimitFallsBackToKeywords() throws Exception {
        long sale = faqId("怎么开单？");
        aiReplies(Status.ANSWERED, "点「开单」。", List.of(sale), sale);
        String ip = nextIp();
        for (int i = 0; i < 3; i++) {
            assertThat((String) read(ask("{\"message\":\"我想新增商品\"}", ip), "$.source")).isEqualTo("AI");
        }
        // 第 4 次：退回关键词，不再调用 AI
        MockHttpServletResponse res = ask("{\"message\":\"我想新增商品\"}", ip);
        assertThat((String) read(res, "$.source")).isEqualTo("KEYWORD");
        assertThat((String) read(res, "$.question")).isEqualTo("怎么添加商品？");
        // 其他 IP 不受影响
        assertThat((String) read(ask("{\"message\":\"我想新增商品\"}", nextIp()), "$.source")).isEqualTo("AI");
    }

    @Test
    void platformDailyLimitFallsBackToKeywords() throws Exception {
        long sale = faqId("怎么开单？");
        aiReplies(Status.ANSWERED, "点「开单」。", List.of(sale), sale);
        long today = logs.countByCreatedAtGreaterThanEqual(
                java.time.LocalDate.now(SupportService.DAY_ZONE).atStartOfDay(SupportService.DAY_ZONE).toInstant());
        List<Long> filler = new ArrayList<>();
        try {
            for (long i = today; i < 200; i++) {
                filler.add(logs.save(new SupportAiLog("filler", null, "ANSWERED", null, "zh-CN", "m", 0, 0, 0, 0,
                        Instant.now())).getId());
            }
            assertThat((String) read(ask("我想新增商品"), "$.source")).isEqualTo("KEYWORD");
        } finally {
            logs.deleteAllById(filler);
        }
        assertThat((String) read(ask("我想新增商品"), "$.source")).isEqualTo("AI");
    }

    @Test
    void logsOlderThan90DaysArePurged() {
        SupportAiLog old = logs.save(new SupportAiLog("old", "a", "ANSWERED", null, "zh-CN", "m", 1, 0, 1, 1,
                Instant.now().minus(Duration.ofDays(91))));
        SupportAiLog recent = logs.save(new SupportAiLog("recent", "a", "ANSWERED", null, "zh-CN", "m", 1, 0, 1, 1,
                Instant.now().minus(Duration.ofDays(89))));
        assertThat(service.purgeExpiredLogs()).isGreaterThanOrEqualTo(1);
        assertThat(logs.findById(old.getId())).isEmpty();
        assertThat(logs.findById(recent.getId())).isPresent();
        logs.delete(recent);
    }

    @Test
    void adminSeesLogsStatsAndCanFlag_othersCannot() throws Exception {
        long sale = faqId("怎么开单？");
        aiReplies(Status.ANSWERED, "点「开单」。", List.of(sale), sale);
        long id = ((Number) read(ask("在哪里卖东西记账"), "$.logId")).longValue();

        Session admin = adminSession();
        MockHttpServletResponse list = send(get("/api/v1/admin/support/ai/logs"), admin, null);
        assertThat(list.getStatus()).isEqualTo(200);
        List<Integer> ids = read(list, "$.items[*].id");
        assertThat(ids).contains((int) id);

        assertThat(send(post("/api/v1/admin/support/ai/logs/" + id + "/flag"), admin, "{\"flagged\":true}").getStatus())
                .isEqualTo(204);
        List<Integer> flagged = read(send(get("/api/v1/admin/support/ai/logs?flagged=true"), admin, null), "$.items[*].id");
        assertThat(flagged).contains((int) id);

        MockHttpServletResponse stats = send(get("/api/v1/admin/support/ai/stats"), admin, null);
        assertThat((Boolean) read(stats, "$.enabled")).isTrue();
        assertThat(((Number) read(stats, "$.today.calls")).longValue()).isGreaterThanOrEqualTo(1);
        assertThat(new BigDecimal(read(stats, "$.today.estimatedUsd").toString())).isPositive();

        Session merchant = register("aiuser");
        assertThat(send(get("/api/v1/admin/support/ai/logs"), merchant, null).getStatus()).isEqualTo(403);
        assertThat(send(get("/api/v1/admin/support/ai/stats"), merchant, null).getStatus()).isEqualTo(403);
    }

    private Session adminSession() throws Exception {
        MockHttpServletResponse res = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"admin@platform.test\",\"password\":\"AdminPass123\"}")).andReturn().getResponse();
        if (res.getStatus() != 200) {
            res = mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                    .content("{\"email\":\"admin@platform.test\",\"password\":\"AdminPass123\",\"displayName\":\"平台\",\"agreeTerms\":true}"))
                    .andReturn().getResponse();
        }
        return new Session("admin@platform.test", "AdminPass123", read(res, "$.accessToken"), null,
                ((Number) read(res, "$.user.id")).longValue());
    }
}
