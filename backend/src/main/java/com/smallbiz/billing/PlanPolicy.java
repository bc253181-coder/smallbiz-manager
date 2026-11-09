package com.smallbiz.billing;

import com.smallbiz.billing.membership.FreeLimits;
import com.smallbiz.tenant.Plan;

/**
 * 套餐权益：所有限制集中在这里。
 *
 * <p>会员体系（docs/16-lifetime-membership.md）：
 * <ul>
 * <li>普通会员（FREE）：高级功能是否开放、各项数量上限由平台后台配置（{@link FreeLimits}）；店铺和商品的新建次数按账号累计，
 * 见 {@code MembershipService}</li>
 * <li>高级永久会员：店铺 plan = PRO 且没有到期时间，全部功能</li>
 * <li>以前买的标准版 / 专业版订阅：有效期内按原来的权益（FREE &lt; STANDARD &lt; PRO）</li>
 * </ul>
 */
public final class PlanPolicy {

    private PlanPolicy() {
    }

    /** 普通会员的限制：启动时和后台修改后由 MembershipConfigService 更新。 */
    private static volatile FreeLimits freeLimits = FreeLimits.DEFAULT;

    public static void setFreeLimits(FreeLimits limits) {
        freeLimits = limits;
    }

    public static FreeLimits freeLimits() {
        return freeLimits;
    }

    /** 普通会员报表可查看最近几天。 */
    public static int freeReportDays() {
        return freeLimits.reportDays();
    }

    public static boolean allows(Plan plan, Feature feature) {
        if (plan == Plan.FREE) {
            FreeLimits l = freeLimits;
            return switch (feature.group()) {
                case ADVANCED_TOOLS -> l.advancedTools();
                case ADVANCED_TEMPLATES -> l.advancedTemplates();
                case EXPORT -> l.export();
                case ADVANCED_ANALYTICS -> l.advancedAnalytics();
            };
        }
        return rank(plan) >= rank(requiredPlan(feature));
    }

    /** 店主以外的员工名额：专业版 / 永久会员 5 人；普通会员在后台开放高级工具时同样 5 人。 */
    public static int maxStaff(Plan plan) {
        return allows(plan, Feature.STAFF_ACCOUNTS) ? PRO_MAX_STAFF : 0;
    }

    public static final int PRO_MAX_STAFF = 5;

    public static final int PRO_MAX_WAREHOUSES = 5;

    /** 启用的仓库数量上限（含默认仓库）。 */
    public static int maxWarehouses(Plan plan) {
        return allows(plan, Feature.WAREHOUSES) ? PRO_MAX_WAREHOUSES : 1;
    }

    /** 店主总能进入自己的店；员工只在店铺包含员工账号功能时能进入（到期后成员关系保留，续费即恢复）。 */
    public static boolean memberCanEnter(Plan plan, com.smallbiz.tenant.MemberRole role) {
        return role == com.smallbiz.tenant.MemberRole.OWNER || allows(plan, Feature.STAFF_ACCOUNTS);
    }

    /** 以前的订阅中，开通某功能所需的最低套餐（永久会员是 PRO，全部包含）。 */
    public static Plan requiredPlan(Feature feature) {
        return switch (feature) {
            case RECEIVABLES, STAFF_ACCOUNTS, EXCEL_EXPORT, CUSTOMER_PRICES, WAREHOUSES -> Plan.PRO;
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
            case FREE -> "普通会员";
            case STANDARD -> "标准版";
            case PRO -> "专业版";
        };
    }
}
