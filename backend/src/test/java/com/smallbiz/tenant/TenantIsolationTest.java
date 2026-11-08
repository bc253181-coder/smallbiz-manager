package com.smallbiz.tenant;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.transaction.support.TransactionTemplate;

import com.smallbiz.security.TokenService;
import com.smallbiz.support.IntegrationTest;
import com.smallbiz.support.TestNote;
import com.smallbiz.support.TestNoteRepository;

/**
 * 多商户隔离机制测试：验证 @TenantId 对查询、按 id 查找、JPQL、新增、修改都自动生效。
 * 以后每个业务模块（商品、订单……）还会有各自的接口级隔离测试。
 */
class TenantIsolationTest extends IntegrationTest {

    @Autowired
    TestNoteRepository notes;

    @Autowired
    TransactionTemplate tx;

    @AfterEach
    void clearSecurity() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void dataOfOneTenantIsInvisibleToAnother() throws Exception {
        Long a = currentTenantId(registerWithTenant("iso-a", "店铺A"), mvc);
        Long b = currentTenantId(registerWithTenant("iso-b", "店铺B"), mvc);

        TestNote noteA = TenantContext.runAs(a, () -> tx.execute(s -> notes.save(new TestNote("secret-of-A"))));
        TenantContext.runAs(b, () -> tx.execute(s -> notes.save(new TestNote("secret-of-B"))));

        // 新增时自动写入当前商户
        assertThat(noteA.getTenantId()).isEqualTo(a);

        TenantContext.runAs(b, () -> tx.executeWithoutResult(s -> {
            assertThat(notes.findAll()).extracting(TestNote::getContent).containsExactly("secret-of-B");
            assertThat(notes.findById(noteA.getId())).isEmpty();
            assertThat(notes.findByContent("secret-of-A")).isEmpty();
            assertThat(notes.searchJpql("secret")).extracting(TestNote::getContent).containsExactly("secret-of-B");
            assertThat(notes.count()).isEqualTo(1);
        }));

        TenantContext.runAs(a, () -> tx.executeWithoutResult(s -> {
            assertThat(notes.findAll()).extracting(TestNote::getContent).containsExactly("secret-of-A");
        }));
    }

    @Test
    void anotherTenantCannotModifyOrDelete() throws Exception {
        Long a = currentTenantId(registerWithTenant("iso-c", "店铺C"), mvc);
        Long b = currentTenantId(registerWithTenant("iso-d", "店铺D"), mvc);
        TestNote noteA = TenantContext.runAs(a, () -> tx.execute(s -> notes.save(new TestNote("original"))));

        TenantContext.runAs(b, () -> tx.executeWithoutResult(s -> {
            notes.deleteById(noteA.getId());               // 对 B 而言该记录不存在 → 不会删除
            notes.findById(noteA.getId()).ifPresent(n -> n.setContent("hacked"));
        }));

        TenantContext.runAs(a, () -> tx.executeWithoutResult(s -> {
            assertThat(notes.findById(noteA.getId())).get().extracting(TestNote::getContent).isEqualTo("original");
        }));
    }

    @Test
    void tenantComesFromJwtWhenLoggedIn() throws Exception {
        Long a = currentTenantId(registerWithTenant("iso-e", "店铺E"), mvc);
        Long b = currentTenantId(registerWithTenant("iso-f", "店铺F"), mvc);
        TenantContext.runAs(a, () -> tx.execute(s -> notes.save(new TestNote("jwt-A"))));

        authenticateAs(b);
        List<TestNote> visible = tx.execute(s -> notes.findByContent("jwt-A"));
        assertThat(visible).isEmpty();

        authenticateAs(a);
        List<TestNote> own = tx.execute(s -> notes.findByContent("jwt-A"));
        assertThat(own).hasSize(1);
    }

    @Test
    void noTenantSeesNothing() throws Exception {
        Long a = currentTenantId(registerWithTenant("iso-g", "店铺G"), mvc);
        TenantContext.runAs(a, () -> tx.execute(s -> notes.save(new TestNote("no-tenant-test"))));

        // 未登录 / 未选择店铺：解析为 NO_TENANT(0)，什么都查不到
        List<TestNote> visible = tx.execute(s -> notes.findByContent("no-tenant-test"));
        assertThat(visible).isEmpty();
    }

    private static void authenticateAs(Long tenantId) {
        Jwt jwt = new Jwt("test-token", Instant.now(), Instant.now().plusSeconds(60),
                Map.of("alg", "HS256"),
                Map.of("sub", "1", TokenService.CLAIM_TENANT_ID, tenantId, TokenService.CLAIM_ROLE, "OWNER"));
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
    }
}
