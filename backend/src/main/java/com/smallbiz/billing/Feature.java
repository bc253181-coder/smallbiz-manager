package com.smallbiz.billing;

/** 需要付费套餐的功能。 */
public enum Feature {
    CUSTOMERS("客户管理"),
    SUPPLIERS("供应商管理"),
    PURCHASING("采购管理"),
    WHOLESALE_PRICE("批发价开单"),
    FULL_REPORTS("完整报表（超过 30 天）"),
    EXPORT("数据导出"),
    /** 专业版：赊账、收款记录、客户欠款与对账单。 */
    RECEIVABLES("应收账款");

    private final String label;

    Feature(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
