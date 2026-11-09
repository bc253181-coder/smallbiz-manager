package com.smallbiz.billing;

import com.smallbiz.tenant.Plan;

/**
 * 套餐权益：所有限制集中在这里，调整套餐内容只改这个类。
 *
 * <p>套餐等级 FREE &lt; STANDARD &lt; PRO，高等级包含低等级的全部功能。
 * 应收账款、员工账号为专业版功能；其余付费功能标准版即可使用。
 */
public final class PlanPolicy {

    private PlanPolicy() {
    }

    public static final int FREE_MAX_PRODUCTS = 100;
    public static final int FREE_REPORT_DAYS = 30;

    public static boolean allows(Plan plan, Feature feature) {
        return rank(plan) >= rank(requiredPlan(feature));
    }

    /** 商品数量上限；null 表示不限。 */
    public static Integer maxProducts(Plan plan) {
        return plan == Plan.FREE ? FREE_MAX_PRODUCTS : null;
    }

    /** 店主以外的员工名额：专业版 5 人，其他套餐 0。 */
    public static int maxStaff(Plan plan) {
        return plan == Plan.PRO ? PRO_MAX_STAFF : 0;
    }

    public static final int PRO_MAX_STAFF = 5;

    /** 店主总能进入自己的店；员工只在店铺套餐包含员工账号时能进入（到期后成员关系保留，续费即恢复）。 */
    public static boolean memberCanEnter(Plan plan, com.smallbiz.tenant.MemberRole role) {
        return role == com.smallbiz.tenant.MemberRole.OWNER || allows(plan, Feature.STAFF_ACCOUNTS);
    }

    /** 开通某功能所需的最低套餐。 */
    public static Plan requiredPlan(Feature feature) {
        return switch (feature) {
            case RECEIVABLES, STAFF_ACCOUNTS -> Plan.PRO;
            default -> Plan.STANDARD;
        };
    }

    public static int rank(Plan plan) {
        return switch (plan) {
            case FREE -> 0;
            case STANDARD -> 1;
            case PRO -> 2;
        };
    }

    public static String label(Plan plan) {
        return switch (plan) {
            case FREE -> "免费版";
            case STANDARD -> "标准版";
            case PRO -> "专业版";
        };
    }
}