package com.smallbiz.staff;

import java.time.Instant;
import java.util.List;

import com.smallbiz.tenant.MemberRole;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public final class StaffDtos {

    private StaffDtos() {
    }

    public record MemberView(Long id, Long userId, String displayName, String email, MemberRole role,
            Instant joinedAt, Instant lastLoginAt, boolean self) {
    }

    public record InvitationView(Long id, MemberRole role, String note, Instant expiresAt, Instant createdAt) {
        static InvitationView of(Invitation i) {
            return new InvitationView(i.getId(), i.getRole(), i.getNote(), i.getExpiresAt(), i.getCreatedAt());
        }
    }

    /** used = 在职员工 + 未过期的待接受邀请；max = 当前套餐的员工名额；allowed = 当前套餐能否邀请员工。 */
    public record MembersResponse(List<MemberView> members, List<InvitationView> invitations, int used, int max,
            boolean allowed) {
    }

    public record CreateInvitationRequest(@NotNull(message = "请选择角色") MemberRole role,
            @Size(max = 50, message = "备注最多 50 字") String note) {
    }

    /** token 原文只在创建时返回一次，数据库不保存。 */
    public record CreatedInvitation(InvitationView invitation, String token) {
    }

    public record InvitationPreview(String tenantName, MemberRole role, String inviterName, Instant expiresAt) {
    }

    public record AcceptRequest(@NotBlank(message = "邀请链接无效") @Size(max = 100, message = "邀请链接无效") String token) {
    }

    public record RoleRequest(@NotNull(message = "请选择角色") MemberRole role) {
    }
}
