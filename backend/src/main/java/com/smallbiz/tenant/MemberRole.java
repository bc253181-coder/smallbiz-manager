package com.smallbiz.tenant;

/** 商户内角色。 */
public enum MemberRole {
    /** 店主：全部权限。 */
    OWNER,
    /** 管理员：除店铺设置、员工、套餐外的全部权限。 */
    ADMIN,
    /** 店员：开单、查看商品库存，不能看成本和利润。 */
    STAFF
}
