package com.smallbiz.pricing;

import java.math.BigDecimal;
import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonFormat;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public final class CustomerPriceDtos {

    private CustomerPriceDtos() {
    }

    public record PriceRequest(
            @NotNull(message = "请填写协议价") @DecimalMin(value = "0", message = "价格不能为负数")
            @Digits(integer = 12, fraction = 2, message = "价格最多两位小数") BigDecimal price,
            @Size(max = 200, message = "备注最多 200 字") String notes) {
    }

    /** 客户的协议价列表行。belowCost：协议价低于当前进货成本。 */
    public record CustomerPriceView(
            Long productId,
            String productName,
            String sku,
            String barcode,
            String unit,
            @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal retailPrice,
            @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal wholesalePrice,
            @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal costPrice,
            @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal price,
            String notes,
            boolean belowCost,
            Instant updatedAt) {
    }

    /** 某商品有协议价的客户。 */
    public record ProductCustomerPriceView(
            Long customerId,
            String customerName,
            String phone,
            @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal price,
            String notes,
            boolean belowCost,
            Instant updatedAt) {
    }

    /** 开单取价：只返回有协议价的商品。 */
    public record Quote(Long productId, @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal price) {
    }
}
