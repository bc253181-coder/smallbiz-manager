package com.smallbiz.excel;

import java.util.List;

public final class ImportDtos {

    private ImportDtos() {
    }

    public enum Type { PRODUCTS, CUSTOMERS, SUPPLIERS, CUSTOMER_PRICES }

    /** CREATE_ONLY：重复的跳过；UPSERT：重复的更新资料（商品不改库存）。 */
    public enum Mode { CREATE_ONLY, UPSERT }

    /** row 为表格行号（表头是第 1 行）；row = 0 表示整个文件的问题。 */
    public record RowError(int row, String column, String message) {
    }

    /**
     * 预检或导入结果。imported = true 表示已写入数据库。
     * newCategories：导入时会自动新建的分类。
     */
    public record ImportResult(boolean dryRun, boolean imported, int total, int created, int updated, int skipped,
            List<RowError> errors, List<String> newCategories, List<String> notes) {
    }
}
