package com.smallbiz.billing;

import com.smallbiz.tenant.Plan;

/**
 * 套餐权益：所有限制集中在这里，调整套餐内容只改这个类。
 *
 * <p>套餐等级 FREE &lt; STANDARD &lt; PRO，高等级包含低等级的全部功能。
 * 应收账款为专业版功能；其余付费功能标准版即可使用。
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

    /** 开通某功能所需的最低套餐。 */
    public static Plan requiredPlan(Feature feature) {
        return feature == Feature.RECEIVABLES ? Plan.PRO : Plan.STANDARD;
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