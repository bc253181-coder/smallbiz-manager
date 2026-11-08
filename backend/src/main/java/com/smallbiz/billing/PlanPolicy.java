package com.smallbiz.billing;

import java.util.EnumSet;
import java.util.Set;

import com.smallbiz.tenant.Plan;

/**
 * 套餐权益：所有限制集中在这里，调整套餐内容只改这个类。
 *
 * <p>说明：设计稿中"导出"和"批发价"属于专业版，但专业版在应收账款、员工账号完成前不开售，
 * 因此暂时放入标准版，避免出现"买不到的功能"。
 */
public final class PlanPolicy {

    private PlanPolicy() {
    }

    public static final int FREE_MAX_PRODUCTS = 100;
    public static final int FREE_REPORT_DAYS = 30;

    private static final Set<Feature> PAID = EnumSet.allOf(Feature.class);

    public static boolean allows(Plan plan, Feature feature) {
        return plan != Plan.FREE || !PAID.contains(feature);
    }

    /** 商品数量上限；null 表示不限。 */
    public static Integer maxProducts(Plan plan) {
        return plan == Plan.FREE ? FREE_MAX_PRODUCTS : null;
    }

    /** 开通某功能所需的最低套餐。 */
    public static Plan requiredPlan(Feature feature) {
        return Plan.STANDARD;
    }
}
