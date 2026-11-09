package com.smallbiz.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;

class SupportApiTest extends IntegrationTest {

    private MockHttpServletResponse ask(String message, String ip) throws Exception {
        return mvc.perform(post("/api/v1/support/ask").contentType(MediaType.APPLICATION_JSON)
                .content("{\"message\":\"%s\"}".formatted(message)).with(r -> {
                    r.setRemoteAddr(ip);
                    return r;
                })).andReturn().getResponse();
    }

    @Test
    void anonymousVisitorsGetQuickRepliesAndAnswers() throws Exception {
        MockHttpServletResponse welcome = mvc.perform(get("/api/v1/support/welcome")).andReturn().getResponse();
        assertThat(welcome.getStatus()).isEqualTo(200);
        assertThat((List<?>) read(welcome, "$.quickReplies")).hasSizeGreaterThanOrEqualTo(4);
        // 测试环境没有配置 API 密钥：AI 关闭，回答全部来自关键词匹配
        assertThat((Boolean) read(welcome, "$.ai")).isFalse();
        assertThat((String) read(ask("客人买东西我在哪记一下", "10.0.0.9"), "$.source")).isEqualTo("KEYWORD");

        // 各种常见问法都能命中
        for (String[] c : new String[][] {
                {"请问怎么开单？", "怎么开单？"},
                {"我想新增商品", "怎么添加商品？"},
                {"永久会员多少钱", "会员和价格"},
                {"激活码在哪里输入", "激活码怎么用？"},
                {"忘记密码了登不上", "忘记密码怎么办？"},
                {"采购下单了但库存没变", "采购单下单后库存没有增加？"},
                {"顾客要退货怎么办", "怎么取消订单 / 退货？"},
                {"能不能导出excel", "可以导出 Excel 吗？"}}) {
            MockHttpServletResponse res = ask(c[0], "10.0.0.1");
            assertThat((Boolean) read(res, "$.matched")).as(c[0]).isTrue();
            assertThat((String) read(res, "$.question")).as(c[0]).isEqualTo(c[1]);
        }
    }

    @Test
    void unmatchedQuestionsHandOffToWechatAndAreRecorded() throws Exception {
        MockHttpServletResponse res = ask("你们收不收木星贝壳", "10.0.0.2");
        assertThat((Boolean) read(res, "$.matched")).isFalse();
        assertThat((String) read(res, "$.handoff.wechatId")).isEqualTo("TEST_WECHAT");
        ask("你们收不收 木星贝壳？", "10.0.0.2"); // 标点空格不同视为同一问题

        Session admin = adminSession();
        MockHttpServletResponse list = send(get("/api/v1/admin/support/unmatched"), admin, null);
        List<Integer> occurrences = read(list, "$[?(@.queryText == '你们收不收木星贝壳')].occurrences");
        assertThat(occurrences).containsExactly(2);
    }

    @Test
    void askingIsRateLimitedPerIp() throws Exception {
        for (int i = 0; i < 20; i++) {
            assertThat(ask("怎么开单", "10.0.0.3").getStatus()).isEqualTo(200);
        }
        assertThat(ask("怎么开单", "10.0.0.3").getStatus()).isEqualTo(429);
        assertThat(ask("怎么开单", "10.0.0.4").getStatus()).isEqualTo(200);
    }

    @Test
    void adminCanManageFaqs_andNewFaqIsMatched() throws Exception {
        Session admin = adminSession();
        MockHttpServletResponse created = send(post("/api/v1/admin/support/faqs"), admin, """
                {"category":"其他","question":"支持火星币吗？","keywords":"火星币","answer":"暂不支持。",
                 "quickReply":false,"sortOrder":99,"enabled":true}
                """);
        assertThat(created.getStatus()).isEqualTo(201);
        assertThat((String) read(ask("火星币可以用吗", "10.0.0.5"), "$.answer")).isEqualTo("暂不支持。");
        assertThat(send(post("/api/v1/admin/support/faqs"), admin, """
                {"category":"x","question":"x","keywords":"x","answer":"x","actionPath":"https://evil.example","quickReply":false,"sortOrder":1,"enabled":true}
                """).getStatus()).isEqualTo(400);
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
