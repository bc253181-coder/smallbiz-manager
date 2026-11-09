package com.smallbiz.tenant;

import java.time.Instant;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class TenantDtos {

    private TenantDtos() {
    }

    /** currency 可选（默认人民币）；timezone 可选（默认按币种所在地区）。 */
    public record CreateTenantRequest(
            @NotBlank(message = "请填写店铺名称") @Size(max = 100, message = "店铺名称最多 100 字") String name,
            @NotNull(message = "请选择经营类型") BusinessType businessType,
            @Size(max = 3) String currency,
            @Size(max = 50) String timezone) {

        public CreateTenantRequest(String name, BusinessType businessType) {
            this(name, businessType, null, null);
        }
    }

    public record UpdateTenantRequest(
            @NotBlank(message = "请填写店铺名称") @Size(max = 100, message = "店铺名称最多 100 字") String name,
            @NotNull(message = "请选择经营类型") BusinessType businessType,
            @NotBlank(message = "请选择时区") @Size(max = 50) String timezone,
            @Size(max = 30, message = "电话最多 30 位")
            @Pattern(regexp = "^[0-9+\\- ]*$", message = "电话格式不正确") String contactPhone,
            @Size(max = 300, message = "地址最多 300 字") String address,
            boolean allowNegativeStock,
            /** 为空表示不修改。 */
            @Size(max = 3) String currency) {
    }

    public record TenantView(
            Long id,
            String name,
            BusinessType businessType,
            String currency,
            String timezone,
            boolean allowNegativeStock,
            Plan plan,
            Instant planExpiresAt,
            String contactPhone,
            String address,
            /** 已有业务数据且币种已确认过：不能再改币种。 */
            boolean currencyLocked,
            boolean currencyConfirmed) {

        static TenantView of(Tenant t, Instant now, boolean currencyLocked) {
            return new TenantView(t.getId(), t.getName(), t.getBusinessType(), t.getCurrency(), t.getTimezone(),
                    t.isAllowNegativeStock(), t.effectivePlan(now), t.getPlanExpiresAt(), t.getContactPhone(),
                    t.getAddress(), currencyLocked, t.isCurrencyConfirmed());
        }
    }
}
