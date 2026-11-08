package com.smallbiz.supplier;

import java.time.Instant;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class SupplierDtos {

    private SupplierDtos() {
    }

    /** 新增和修改共用；修改时必须带 version。 */
    public record SupplierRequest(
            @NotBlank(message = "请填写供应商名称") @Size(max = 100, message = "供应商名称最多 100 字") String name,
            @Size(max = 50, message = "联系人最多 50 字") String contactPerson,
            @Size(max = 30, message = "电话最多 30 位") @Pattern(regexp = "^[0-9+\\- ()]*$", message = "电话格式不正确") String phone,
            @Size(max = 191, message = "邮箱过长") @Email(message = "邮箱格式不正确") String email,
            @Size(max = 300, message = "地址最多 300 字") String address,
            @Size(max = 500, message = "供应商品说明最多 500 字") String suppliedProductsNote,
            @Size(max = 2000, message = "备注最多 2000 字") String notes,
            Integer version) {

        public SupplierRequest {
            email = email == null || email.isBlank() ? null : email.trim();
        }
    }

    public record SupplierView(
            Long id,
            String name,
            String contactPerson,
            String phone,
            String email,
            String address,
            String suppliedProductsNote,
            String notes,
            int version,
            Instant createdAt,
            Instant updatedAt) {

        static SupplierView of(Supplier s) {
            return new SupplierView(s.getId(), s.getName(), s.getContactPerson(), s.getPhone(), s.getEmail(),
                    s.getAddress(), s.getSuppliedProductsNote(), s.getNotes(), s.getVersion(), s.getCreatedAt(),
                    s.getUpdatedAt());
        }
    }
}
