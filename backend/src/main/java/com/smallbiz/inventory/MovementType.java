package com.smallbiz.inventory;

/** 库存变动类型。 */
public enum MovementType {
    /** 新建商品时的初始库存。 */
    INITIAL,
    /** 手工入库。 */
    STOCK_IN,
    /** 手工出库（损耗、自用等）。 */
    STOCK_OUT,
    /** 盘点调整。 */
    ADJUSTMENT,
    /** 销售出库。 */
    SALE,
    /** 销售取消，恢复库存。 */
    SALE_CANCEL,
    /** 采购收货。 */
    PURCHASE_RECEIVE,
    /** 调拨调出。 */
    TRANSFER_OUT,
    /** 调拨调入。 */
    TRANSFER_IN
}
