package com.smallbiz.billing;

/** 需要付费套餐的功能。 */
public enum Feature {
    CUSTOMERS("客户管理"),
    SUPPLIERS("供应商管理"),
    PURCHASING("采购管理"),
    WHOLESALE_PRICE("批发价开单"),
    FULL_REPORTS("高级数据分析（趋势、排行、毛利）"),
    EXPORT("数据导出"),
    /** 专业版：赊账、收款记录、客户欠款与对账单。 */
    RECEIVABLES("应收账款"),
    /** 专业版：邀请员工（店主以外最多 5 人）。 */
    STAFF_ACCOUNTS("员工账号"),
    /** 专业版：导出 Excel（.xlsx）。CSV 导出仍属于标准版的 EXPORT。 */
    EXCEL_EXPORT("Excel 导出"),
    /** 标准版：批量打印条码标签（扫码本身所有套餐可用）。 */
    LABEL_PRINTING("打印条码标签"),
    /** 专业版：客户协议价（批发价仍为标准版的 WHOLESALE_PRICE）。 */
    CUSTOMER_PRICES("客户协议价"),
    /** 专业版：多仓库（最多 5 个）与调拨。 */
    WAREHOUSES("多仓库");

    private final String label;

    Feature(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    /** 普通会员的开关分组（平台后台按组开放给普通会员）。 */
    public enum Group { ADVANCED_TOOLS, ADVANCED_TEMPLATES, EXPORT, ADVANCED_ANALYTICS }

    public Group group() {
        return switch (this) {
            case LABEL_PRINTING -> Group.ADVANCED_TEMPLATES;
            case EXPORT, EXCEL_EXPORT -> Group.EXPORT;
            case FULL_REPORTS -> Group.ADVANCED_ANALYTICS;
            default -> Group.ADVANCED_TOOLS;
        };
    }
}
