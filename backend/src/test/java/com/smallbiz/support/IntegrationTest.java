package com.smallbiz.support;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import com.jayway.jsonpath.JsonPath;

/**
 * 集成测试基类：真实 MySQL 测试库 + 完整 Spring Security 过滤链。
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestFlywayConfig.class)
public abstract class IntegrationTest {

    @Autowired
    protected WebApplicationContext context;

    protected MockMvc mvc;

    @BeforeEach
    void setUpMockMvc() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    /** 一个已登录的测试用户及其令牌。 */
    public record Session(String email, String password, String accessToken, String refreshToken, Long userId) {
    }

    protected static String uniqueEmail(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().substring(0, 8) + "@test.local";
    }

    protected static String json(String template, Object... args) {
        return String.format(template, args);
    }

    /** 注册一个新用户。 */
    protected Session register(String prefix) throws Exception {
        String email = uniqueEmail(prefix);
        String password = "Passw0rd!" + prefix;
        MockHttpServletResponse res = mvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json("{\"email\":\"%s\",\"password\":\"%s\",\"displayName\":\"%s\",\"agreeTerms\":true}", email, password, prefix)))
                .andReturn().getResponse();
        if (res.getStatus() != 201) {
            throw new AssertionError("register failed: " + res.getStatus() + " " + res.getContentAsString());
        }
        String body = res.getContentAsString();
        return new Session(email, password, JsonPath.read(body, "$.accessToken"), refreshCookie(res),
                ((Number) JsonPath.read(body, "$.user.id")).longValue());
    }

    @Autowired
    protected org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    /**
     * 注册一个新用户并创建店铺，返回绑定店铺的会话。
     * 店铺默认开通一年标准版（大部分业务测试需要采购、客户等付费功能）；测试套餐限制请用 {@link #registerWithFreeTenant}。
     */
    protected Session registerWithTenant(String prefix, String tenantName) throws Exception {
        Session s = createTenant(register(prefix), tenantName);
        jdbcTemplate.update("update tenants set plan = 'STANDARD', plan_expires_at = date_add(utc_timestamp(6), interval 365 day) "
                + "where id = ?", currentTenantId(s, mvc));
        return s;
    }

    /** 注册并创建专业版店铺（一年）。 */
    protected Session registerWithProTenant(String prefix, String tenantName) throws Exception {
        Session s = createTenant(register(prefix), tenantName);
        jdbcTemplate.update("update tenants set plan = 'PRO', plan_expires_at = date_add(utc_timestamp(6), interval 365 day) "
                + "where id = ?", currentTenantId(s, mvc));
        return s;
    }

    /** 员工账号是专业版功能：直接插入员工的测试先把标准版店铺升为专业版（到期时间不变）。 */
    protected void allowStaff(Long tenantId) {
        jdbcTemplate.update("update tenants set plan = 'PRO' where id = ? and plan = 'STANDARD'", tenantId);
    }

    /** 注册并创建免费版店铺。 */
    protected Session registerWithFreeTenant(String prefix, String tenantName) throws Exception {
        return createTenant(register(prefix), tenantName);
    }

    protected Session createTenant(Session s, String tenantName) throws Exception {
        MockHttpServletResponse res = mvc.perform(withAuth(post("/api/v1/tenants"), s)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json("{\"name\":\"%s\",\"businessType\":\"RETAIL\"}", tenantName)))
                .andReturn().getResponse();
        if (res.getStatus() != 201) {
            throw new AssertionError("create tenant failed: " + res.getStatus() + " " + res.getContentAsString());
        }
        return new Session(s.email(), s.password(), JsonPath.read(res.getContentAsString(), "$.accessToken"),
                refreshCookie(res), s.userId());
    }

    protected static Long currentTenantId(Session s, MockMvc mvc) throws Exception {
        String body = mvc.perform(withAuth(get("/api/v1/auth/me"), s)).andReturn().getResponse().getContentAsString();
        Number id = JsonPath.read(body, "$.currentTenant.id");
        return id.longValue();
    }

    /** 以某会话发送 JSON 请求，返回响应。 */
    protected MockHttpServletResponse send(MockHttpServletRequestBuilder b, Session s, String body) throws Exception {
        MockHttpServletRequestBuilder req = withAuth(b, s);
        if (body != null) {
            req = req.contentType(MediaType.APPLICATION_JSON).content(body);
        }
        MockHttpServletResponse res = mvc.perform(req).andReturn().getResponse();
        res.setCharacterEncoding("UTF-8");
        return res;
    }

    protected static <T> T read(MockHttpServletResponse res, String path) throws Exception {
        return JsonPath.read(res.getContentAsString(), path);
    }

    protected static MockHttpServletRequestBuilder withAuth(MockHttpServletRequestBuilder b, Session s) {
        return b.header(HttpHeaders.AUTHORIZATION, "Bearer " + s.accessToken());
    }

    private static final Pattern REFRESH = Pattern.compile("sb_refresh=([^;]*)");

    protected static String refreshCookie(MockHttpServletResponse res) {
        for (String h : res.getHeaders(HttpHeaders.SET_COOKIE)) {
            Matcher m = REFRESH.matcher(h);
            if (m.find()) {
                return m.group(1);
            }
        }
        return null;
    }
}
