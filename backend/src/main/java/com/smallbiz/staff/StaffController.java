package com.smallbiz.staff;

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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.smallbiz.auth.AuthDtos.SessionResponse;
import com.smallbiz.auth.AuthService.IssuedSession;
import com.smallbiz.auth.RefreshCookie;
import com.smallbiz.security.CurrentUser;
import com.smallbiz.staff.StaffDtos.AcceptRequest;
import com.smallbiz.staff.StaffDtos.CreateInvitationRequest;
import com.smallbiz.staff.StaffDtos.CreatedInvitation;
import com.smallbiz.staff.StaffDtos.InvitationPreview;
import com.smallbiz.staff.StaffDtos.MemberView;
import com.smallbiz.staff.StaffDtos.MembersResponse;
import com.smallbiz.staff.StaffDtos.RoleRequest;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

/** 员工管理（仅店主）、邀请预览（公开）、接受邀请与退出店铺（已登录用户）。 */
@RestController
@RequestMapping("/api/v1")
public class StaffController {

    private final StaffService service;
    private final RefreshCookie refreshCookie;

    public StaffController(StaffService service, RefreshCookie refreshCookie) {
        this.service = service;
        this.refreshCookie = refreshCookie;
    }

    @GetMapping("/members")
    @PreAuthorize("hasRole('OWNER')")
    public MembersResponse list() {
        return service.list(CurrentUser.requireTenantId(), CurrentUser.userId());
    }

    @PutMapping("/members/{id}/role")
    @PreAuthorize("hasRole('OWNER')")
    public MemberView changeRole(@PathVariable Long id, @Valid @RequestBody RoleRequest req) {
        return service.changeRole(CurrentUser.requireTenantId(), CurrentUser.userId(), id, req.role());
    }

    @PostMapping("/members/{id}/remove")
    @PreAuthorize("hasRole('OWNER')")
    public ResponseEntity<Void> remove(@PathVariable Long id) {
        service.remove(CurrentUser.requireTenantId(), CurrentUser.userId(), id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/invitations")
    @PreAuthorize("hasRole('OWNER')")
    public ResponseEntity<CreatedInvitation> invite(@Valid @RequestBody CreateInvitationRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(service.invite(CurrentUser.requireTenantId(), CurrentUser.userId(), req));
    }

    @PostMapping("/invitations/{id}/revoke")
    @PreAuthorize("hasRole('OWNER')")
    public ResponseEntity<Void> revoke(@PathVariable Long id) {
        service.revoke(CurrentUser.requireTenantId(), id);
        return ResponseEntity.noContent().build();
    }

    /** 无需登录：被邀请人打开链接时显示店铺名和角色。 */
    @GetMapping("/invitations/preview")
    public InvitationPreview preview(@RequestParam String token, HttpServletRequest http) {
        return service.preview(token, http.getRemoteAddr());
    }

    @PostMapping("/invitations/accept")
    public ResponseEntity<SessionResponse> accept(@Valid @RequestBody AcceptRequest req,
            @CookieValue(name = RefreshCookie.NAME, required = false) String refreshToken) {
        return withCookie(service.accept(CurrentUser.userId(), req.token(), refreshToken));
    }

    /** 员工退出某家店铺（可在选择店铺页操作，不需要先进入该店）。 */
    @PostMapping("/tenants/{id}/leave")
    public ResponseEntity<SessionResponse> leave(@PathVariable Long id,
            @CookieValue(name = RefreshCookie.NAME, required = false) String refreshToken) {
        return withCookie(service.leave(CurrentUser.userId(), id, refreshToken));
    }

    private ResponseEntity<SessionResponse> withCookie(IssuedSession session) {
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, refreshCookie.create(session.refreshToken()).toString())
                .body(session.body());
    }
}
