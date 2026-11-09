package com.smallbiz.auth;

import java.time.Instant;
import java.util.List;

import com.smallbiz.tenant.BusinessType;
import com.smallbiz.tenant.MemberRole;
import com.smallbiz.tenant.Plan;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** 认证相关的请求与响应。 */
public final class AuthDtos {

    private AuthDtos() {
    }

    public record RegisterRequest(
            @NotBlank(message = "请填写邮箱") @Email(message = "邮箱格式不正确") @Size(max = 191) String email,
            @NotBlank(message = "请填写密码") @Size(min = 8, max = 64, message = "密码长度需为 8–64 位") String password,
            @NotBlank(message = "请填写昵称") @Size(max = 100, message = "昵称最多 100 字") String displayName,
            @NotNull(message = "请阅读并同意用户协议和隐私政策") @AssertTrue(message = "请阅读并同意用户协议和隐私政策") Boolean agreeTerms) {

        /** 校验前去掉邮箱前后空格（用户常从别处复制粘贴）。 */
        public RegisterRequest {
            email = email == null ? null : email.trim();
        }
    }

    public record LoginRequest(
            @NotBlank(message = "请填写邮箱") String email,
            @NotBlank(message = "请填写密码") String password) {

        public LoginRequest {
            email = email == null ? null : email.trim();
        }
    }

    public record UpdateProfileRequest(
            @NotBlank(message = "请填写昵称") @Size(max = 100, message = "昵称最多 100 字") String displayName,
            @Size(max = 30, message = "手机号最多 30 位")
            @Pattern(regexp = "^[0-9+\\- ]*$", message = "手机号格式不正确") String phone) {
    }

    public record ChangePasswordRequest(
            @NotBlank(message = "请填写原密码") String oldPassword,
            @NotBlank(message = "请填写新密码") @Size(min = 8, max = 64, message = "密码长度需为 8–64 位") String newPassword) {
    }

    /** locale：界面语言（zh-CN / en），为空表示按浏览器语言。 */
    public record UserView(Long id, String email, String displayName, String phone, boolean platformAdmin, String locale) {
        static UserView of(User u) {
            return new UserView(u.getId(), u.getEmail(), u.getDisplayName(), u.getPhone(), u.isPlatformAdmin(),
                    u.getLocale());
        }
    }

    public record LocaleRequest(@NotBlank @Pattern(regexp = "zh-CN|en", message = "不支持这种语言") String locale) {
    }

    /** accessible = false：员工所在店铺的专业版已到期，暂时不能进入（店主续费后恢复）。 */
    public record TenantSummary(Long id, String name, MemberRole role, boolean accessible) {
    }

    public record CurrentTenantView(
            Long id,
            String name,
            BusinessType businessType,
            MemberRole role,
            Plan plan,
            Instant planExpiresAt,
            String currency,
            String timezone) {
    }

    /** 登录、注册、刷新、切换商户的统一响应。 */
    public record SessionResponse(
            String accessToken,
            long expiresIn,
            UserView user,
            CurrentTenantView currentTenant,
            List<TenantSummary> tenants) {
    }
}
