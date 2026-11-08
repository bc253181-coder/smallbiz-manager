package com.smallbiz.auth;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.smallbiz.auth.AuthDtos.ChangePasswordRequest;
import com.smallbiz.auth.AuthDtos.LoginRequest;
import com.smallbiz.auth.AuthDtos.RegisterRequest;
import com.smallbiz.auth.AuthDtos.SessionResponse;
import com.smallbiz.auth.AuthDtos.UpdateProfileRequest;
import com.smallbiz.auth.AuthDtos.UserView;
import com.smallbiz.auth.AuthService.IssuedSession;
import com.smallbiz.security.CurrentUser;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;
    private final RefreshCookie refreshCookie;

    public AuthController(AuthService authService, RefreshCookie refreshCookie) {
        this.authService = authService;
        this.refreshCookie = refreshCookie;
    }

    @PostMapping("/register")
    public ResponseEntity<SessionResponse> register(@Valid @RequestBody RegisterRequest req) {
        return withCookie(HttpStatus.CREATED, authService.register(req));
    }

    @PostMapping("/login")
    public ResponseEntity<SessionResponse> login(@Valid @RequestBody LoginRequest req) {
        return withCookie(HttpStatus.OK, authService.login(req.email(), req.password()));
    }

    @PostMapping("/refresh")
    public ResponseEntity<SessionResponse> refresh(
            @CookieValue(name = RefreshCookie.NAME, required = false) String token) {
        return withCookie(HttpStatus.OK, authService.refresh(token));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@CookieValue(name = RefreshCookie.NAME, required = false) String token) {
        authService.logout(token);
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, refreshCookie.clear().toString())
                .build();
    }

    @GetMapping("/me")
    public SessionResponse me() {
        return authService.me(CurrentUser.userId(), CurrentUser.tenantIdOrNull());
    }

    @PutMapping("/me")
    public UserView updateProfile(@Valid @RequestBody UpdateProfileRequest req) {
        return authService.updateProfile(CurrentUser.userId(), req);
    }

    @PutMapping("/me/password")
    public ResponseEntity<Void> changePassword(@Valid @RequestBody ChangePasswordRequest req) {
        authService.changePassword(CurrentUser.userId(), req);
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, refreshCookie.clear().toString())
                .build();
    }

    private ResponseEntity<SessionResponse> withCookie(HttpStatus status, IssuedSession session) {
        return ResponseEntity.status(status)
                .header(HttpHeaders.SET_COOKIE, refreshCookie.create(session.refreshToken()).toString())
                .body(session.body());
    }
}
