package com.smallbiz.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;

import com.jayway.jsonpath.JsonPath;
import com.smallbiz.support.IntegrationTest;
import com.smallbiz.tenant.MemberRole;
import com.smallbiz.tenant.TenantMember;
import com.smallbiz.tenant.TenantMemberRepository;

import jakarta.servlet.http.Cookie;

class AuthFlowTest extends IntegrationTest {

    @Autowired
    TenantMemberRepository members;

    // ---------- 注册 ----------

    @Test
    void registerReturnsTokenWithoutTenant() throws Exception {
        String email = uniqueEmail("reg");
        MockHttpServletResponse res = mvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json("{\"email\":\"  %s \",\"password\":\"Secret123\",\"displayName\":\"小王\"}",
                        email.toUpperCase())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accessToken").isString())
                .andExpect(jsonPath("$.user.email").value(email))   // 统一转小写、去空格
                .andExpect(jsonPath("$.currentTenant").value(nullValue()))
                .andExpect(jsonPath("$.tenants", hasSize(0)))
                .andReturn().getResponse();

        String cookie = String.join(";", res.getHeaders(HttpHeaders.SET_COOKIE));
        assertThat(cookie).contains("sb_refresh=").contains("HttpOnly").contains("SameSite=Lax")
                .contains("Path=/api/v1/auth");
        assertThat(res.getContentAsString()).doesNotContain("passwordHash").doesNotContain("password");
    }

    @Test
    void duplicateEmailIsRejected() throws Exception {
        Session s = register("dup");
        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content(json("{\"email\":\"%s\",\"password\":\"Secret123\",\"displayName\":\"x\"}", s.email())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMAIL_EXISTS"));
    }

    @Test
    void invalidRegistrationReturnsFieldErrors() throws Exception {
        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"not-an-email\",\"password\":\"short\",\"displayName\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.details.fields.email").exists())
                .andExpect(jsonPath("$.details.fields.password").exists())
                .andExpect(jsonPath("$.details.fields.displayName").exists());
    }

    // ---------- 登录 ----------

    @Test
    void loginWithWrongPasswordFails_andLocksAfterFiveAttempts() throws Exception {
        Session s = register("lock");
        for (int i = 0; i < 5; i++) {
            mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                    .content(json("{\"email\":\"%s\",\"password\":\"wrong-password\"}", s.email())))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("BAD_CREDENTIALS"));
        }
        // 第 6 次即使密码正确也被锁定
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(json("{\"email\":\"%s\",\"password\":\"%s\"}", s.email(), s.password())))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("LOGIN_LOCKED"));
    }

    @Test
    void loginWithSingleTenantSelectsItAutomatically() throws Exception {
        Session s = registerWithTenant("login1", "我的小店");
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(json("{\"email\":\"%s\",\"password\":\"%s\"}", s.email(), s.password())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentTenant.name").value("我的小店"))
                .andExpect(jsonPath("$.currentTenant.role").value("OWNER"))
                .andExpect(jsonPath("$.currentTenant.plan").value("FREE"));
    }

    @Test
    void loginWithTwoTenantsRequiresChoosing_thenSwitchWorks() throws Exception {
        Session s = registerWithTenant("multi", "店一");
        createTenant(s, "店二");

        String body = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(json("{\"email\":\"%s\",\"password\":\"%s\"}", s.email(), s.password())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentTenant").value(nullValue()))
                .andExpect(jsonPath("$.tenants", hasSize(2)))
                .andReturn().getResponse().getContentAsString();
        Session noTenant = new Session(s.email(), s.password(), JsonPath.read(body, "$.accessToken"), null, s.userId());
        Number secondId = JsonPath.read(body, "$.tenants[1].id");

        mvc.perform(withAuth(get("/api/v1/tenant"), noTenant))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("TENANT_REQUIRED"));

        mvc.perform(withAuth(post("/api/v1/tenants/" + secondId + "/switch"), noTenant))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentTenant.name").value("店二"));
    }

    // ---------- 令牌 ----------

    @Test
    void protectedEndpointsRequireValidToken() throws Exception {
        mvc.perform(get("/api/v1/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
        mvc.perform(get("/api/v1/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer forged.token.value"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void refreshRotatesToken_andOldRefreshTokenCannotBeReused() throws Exception {
        Session s = registerWithTenant("refresh", "刷新测试店");

        MockHttpServletResponse res = mvc.perform(post("/api/v1/auth/refresh")
                .cookie(new Cookie("sb_refresh", s.refreshToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentTenant.name").value("刷新测试店"))   // 刷新后沿用当前店铺
                .andReturn().getResponse();
        String newRefresh = refreshCookie(res);
        assertThat(newRefresh).isNotBlank().isNotEqualTo(s.refreshToken());

        mvc.perform(post("/api/v1/auth/refresh").cookie(new Cookie("sb_refresh", s.refreshToken())))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("REFRESH_INVALID"));
    }

    @Test
    void logoutRevokesRefreshToken() throws Exception {
        Session s = register("logout");
        mvc.perform(post("/api/v1/auth/logout").cookie(new Cookie("sb_refresh", s.refreshToken())))
                .andExpect(status().isNoContent());
        mvc.perform(post("/api/v1/auth/refresh").cookie(new Cookie("sb_refresh", s.refreshToken())))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void changePasswordRequiresOldPassword_andRevokesSessions() throws Exception {
        Session s = register("pwd");
        mvc.perform(withAuth(put("/api/v1/auth/me/password"), s).contentType(MediaType.APPLICATION_JSON)
                .content("{\"oldPassword\":\"wrong\",\"newPassword\":\"NewSecret123\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_OLD_PASSWORD"));

        mvc.perform(withAuth(put("/api/v1/auth/me/password"), s).contentType(MediaType.APPLICATION_JSON)
                .content(json("{\"oldPassword\":\"%s\",\"newPassword\":\"NewSecret123\"}", s.password())))
                .andExpect(status().isNoContent());

        mvc.perform(post("/api/v1/auth/refresh").cookie(new Cookie("sb_refresh", s.refreshToken())))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(json("{\"email\":\"%s\",\"password\":\"NewSecret123\"}", s.email())))
                .andExpect(status().isOk());
    }

    // ---------- 店铺 ----------

    @Test
    void ownerCanReadAndUpdateTenant() throws Exception {
        Session s = registerWithTenant("owner", "原店名");
        mvc.perform(withAuth(get("/api/v1/tenant"), s))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("原店名"))
                .andExpect(jsonPath("$.timezone").value("Asia/Shanghai"));

        mvc.perform(withAuth(put("/api/v1/tenant"), s).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"新店名\",\"businessType\":\"WHOLESALE\",\"timezone\":\"Asia/Kuala_Lumpur\","
                        + "\"contactPhone\":\"+60 12-345 6789\",\"address\":\"KL\",\"allowNegativeStock\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("新店名"))
                .andExpect(jsonPath("$.timezone").value("Asia/Kuala_Lumpur"))
                .andExpect(jsonPath("$.allowNegativeStock").value(true));

        mvc.perform(withAuth(put("/api/v1/tenant"), s).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"x\",\"businessType\":\"RETAIL\",\"timezone\":\"Mars/Base\",\"allowNegativeStock\":false}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_TIMEZONE"));
    }

    @Test
    void cannotSwitchIntoSomeoneElsesTenant() throws Exception {
        Session owner = registerWithTenant("victim", "别人的店");
        Long victimTenant = currentTenantId(owner, mvc);
        Session attacker = registerWithTenant("attacker", "我的店");

        mvc.perform(withAuth(post("/api/v1/tenants/" + victimTenant + "/switch"), attacker))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TENANT_NOT_FOUND"));

        // 攻击者读取到的始终是自己的店铺
        mvc.perform(withAuth(get("/api/v1/tenant"), attacker))
                .andExpect(jsonPath("$.name").value("我的店"));
    }

    @Test
    void staffCannotUpdateTenantSettings() throws Exception {
        Session owner = registerWithTenant("boss", "老板的店");
        Long tenantId = currentTenantId(owner, mvc);
        Session staff = register("staff");
        members.save(new TenantMember(tenantId, staff.userId(), MemberRole.STAFF));

        String body = mvc.perform(withAuth(post("/api/v1/tenants/" + tenantId + "/switch"), staff))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentTenant.role").value("STAFF"))
                .andReturn().getResponse().getContentAsString();
        Session staffInShop = new Session(staff.email(), staff.password(), JsonPath.read(body, "$.accessToken"),
                null, staff.userId());

        mvc.perform(withAuth(get("/api/v1/tenant"), staffInShop)).andExpect(status().isOk());
        mvc.perform(withAuth(put("/api/v1/tenant"), staffInShop).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"篡改\",\"businessType\":\"RETAIL\",\"timezone\":\"Asia/Shanghai\",\"allowNegativeStock\":false}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void ownedTenantLimitIsEnforced() throws Exception {
        Session s = register("limit");
        for (int i = 1; i <= 5; i++) {
            s = createTenant(s, "店" + i);
        }
        mvc.perform(withAuth(post("/api/v1/tenants"), s).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"店6\",\"businessType\":\"RETAIL\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("TENANT_LIMIT_REACHED"));
    }
}
