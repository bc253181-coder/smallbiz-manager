package com.smallbiz.tenant;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.smallbiz.auth.AuthDtos.SessionResponse;
import com.smallbiz.auth.AuthService;
import com.smallbiz.auth.AuthService.IssuedSession;
import com.smallbiz.auth.RefreshCookie;
import com.smallbiz.security.CurrentUser;
import com.smallbiz.tenant.TenantDtos.CreateTenantRequest;
import com.smallbiz.tenant.TenantDtos.TenantView;
import com.smallbiz.tenant.TenantDtos.UpdateTenantRequest;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1")
public class TenantController {

    private final TenantService tenantService;
    private final AuthService authService;
    private final RefreshCookie refreshCookie;

    public TenantController(TenantService tenantService, AuthService authService, RefreshCookie refreshCookie) {
        this.tenantService = tenantService;
        this.authService = authService;
        this.refreshCookie = refreshCookie;
    }

    /** 创建店铺；返回绑定新店铺的令牌。 */
    @PostMapping("/tenants")
    public ResponseEntity<SessionResponse> create(@Valid @RequestBody CreateTenantRequest req,
            @CookieValue(name = RefreshCookie.NAME, required = false) String refreshToken) {
        return withCookie(HttpStatus.CREATED, tenantService.create(CurrentUser.userId(), req, refreshToken));
    }

    /** 切换当前店铺。 */
    @PostMapping("/tenants/{id}/switch")
    public ResponseEntity<SessionResponse> switchTenant(@PathVariable Long id,
            @CookieValue(name = RefreshCookie.NAME, required = false) String refreshToken) {
        return withCookie(HttpStatus.OK, authService.switchTenant(CurrentUser.userId(), id, refreshToken));
    }

    /** 当前店铺资料（店铺 id 只取自令牌）。 */
    @GetMapping("/tenant")
    public TenantView current() {
        return tenantService.get(CurrentUser.requireTenantId());
    }

    @PutMapping("/tenant")
    @PreAuthorize("hasRole('OWNER')")
    public TenantView update(@Valid @RequestBody UpdateTenantRequest req) {
        return tenantService.update(CurrentUser.requireTenantId(), req);
    }

    private ResponseEntity<SessionResponse> withCookie(HttpStatus status, IssuedSession session) {
        return ResponseEntity.status(status)
                .header(HttpHeaders.SET_COOKIE, refreshCookie.create(session.refreshToken()).toString())
                .body(session.body());
    }
}
