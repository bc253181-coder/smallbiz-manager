-- SmallBiz Manager · V12：同一店铺内未删除商品的条码唯一（扫码时能唯一确定商品）

-- 空字符串统一为 NULL
UPDATE products SET barcode = NULL WHERE barcode IS NOT NULL AND TRIM(barcode) = '';

-- 已有重复条码：保留最早的商品，其余清空（商户可在商品页重新填写或生成店内码）
UPDATE products p
JOIN (SELECT tenant_id, barcode, MIN(id) AS keep_id
      FROM products
      WHERE deleted_at IS NULL AND barcode IS NOT NULL
      GROUP BY tenant_id, barcode
      HAVING COUNT(*) > 1) d
  ON p.tenant_id = d.tenant_id AND p.barcode = d.barcode
SET p.barcode = NULL
WHERE p.deleted_at IS NULL AND p.id <> d.keep_id;

-- 已删除的商品不占用条码：只对未删除商品的条码建唯一索引（MySQL 唯一索引允许多个 NULL）
ALTER TABLE products
    ADD COLUMN barcode_active VARCHAR(64)
        GENERATED ALWAYS AS (IF(deleted_at IS NULL, barcode, NULL)) STORED,
    ADD UNIQUE KEY uk_products_tenant_barcode_active (tenant_id, barcode_active);
