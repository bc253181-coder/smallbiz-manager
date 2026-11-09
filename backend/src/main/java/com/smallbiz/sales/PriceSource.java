package com.smallbiz.sales;

/** 销售明细的单价来源。 */
public enum PriceSource {
    /** 零售价 */
    RETAIL,
    /** 批发价 */
    WHOLESALE,
    /** 客户协议价 */
    CONTRACT,
    /** 开单时手动修改 */
    MANUAL
}
