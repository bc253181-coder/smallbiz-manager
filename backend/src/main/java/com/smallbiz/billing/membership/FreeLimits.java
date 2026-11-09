package com.smallbiz.billing.membership;

/**
 * 普通会员的限制（不可变快照）。
 *
 * @param maxStores         最多创建几家店铺（按账号累计）
 * @param maxProducts       最多新建几个商品（按账号累计，删除不返还）
 * @param reportDays        报表可查看最近几天
 * @param advancedTools     是否开放高级工具（客户、供应商、采购、批发价、赊账、员工、多仓库、协议价）
 * @param advancedTemplates 是否开放高级模板（打印条码标签）
 * @param export            是否开放导出
 * @param advancedAnalytics 是否开放高级数据分析（趋势、排行、毛利）
 */
public record FreeLimits(int maxStores, int maxProducts, int reportDays, boolean advancedTools, boolean advancedTemplates,
        boolean export, boolean advancedAnalytics) {

    /** 方案中的初始值（数据库还没加载时使用）。 */
    public static final FreeLimits DEFAULT = new FreeLimits(2, 3, 5, false, false, false, false);
}
