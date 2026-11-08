package com.smallbiz.billing;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** 标注在 Controller 类或方法上：当前店铺的套餐必须包含该功能，否则返回 403 PLAN_FEATURE_REQUIRED。 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface RequiresFeature {
    Feature value();
}
