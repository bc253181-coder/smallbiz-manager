package com.smallbiz.customer;

import java.time.Instant;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class CustomerDtos {

    private CustomerDtos() {
    }

    static final String PHONE = "^[0-9+\\- ()]*$";

    /** 新增和修改共用；修改时必须带 version。 */
    public record CustomerRequest(
            @NotBlank(message = "请填写客户名称") @Size(max = 100, message = "客户名称最多 100 字") String name,
            @Size(max = 30, message = "电话最多 30 位") @Pattern(regexp = PHONE, message = "电话格式不正确") String phone,
            @Size(max = 191, message = "邮箱过长") @Email(message = "邮箱格式不正确") String email,
            @Size(max = 300, message = "地址最多 300 字") String address,
            @NotNull(message = "请选择客户类型") Customer.Type customerType,
            @NotNull(message = "请选择默认价格") Customer.PriceTier priceTier,
            @Size(max = 2000, message = "备注最多 2000 字") String notes,
            Integer version) {

        public CustomerRequest {
            email = email == null || email.isBlank() ? null : email.trim();
        }
    }

    /** 客户信息。累计消费、订单数将在销售模块上线后加入。 */
    public record CustomerView(
            Long id,
            String name,
            String phone,
            String email,
            String address,
            Customer.Type customerType,
            Customer.PriceTier priceTier,
            String notes,
            int version,
            Instant createdAt,
            Instant updatedAt) {

        static CustomerView of(Customer c) {
            return new CustomerView(c.getId(), c.getName(), c.getPhone(), c.getEmail(), c.getAddress(),
                    c.getCustomerType(), c.getPriceTier(), c.getNotes(), c.getVersion(), c.getCreatedAt(),
                    c.getUpdatedAt());
        }
    }
}
