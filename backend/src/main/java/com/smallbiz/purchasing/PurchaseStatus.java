package com.smallbiz.purchasing;

/** 采购单状态。 */
public enum PurchaseStatus {
    /** 草稿：可修改、可删除。 */
    DRAFT,
    /** 已下单：已发给供应商，等待到货；可修改、可取消。 */
    ORDERED,
    /** 已收货：库存已增加，单据锁定。 */
    RECEIVED,
    /** 已取消。 */
    CANCELLED;

    public boolean isEditable() {
        return this == DRAFT || this == ORDERED;
    }
}
