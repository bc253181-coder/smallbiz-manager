package com.smallbiz.support;

import com.smallbiz.common.TenantScopedEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/** 仅测试使用的商户数据实体，用于验证自动隔离。 */
@Entity
@Table(name = "test_notes")
public class TestNote extends TenantScopedEntity {

    @Column(nullable = false, length = 100)
    private String content;

    protected TestNote() {
    }

    public TestNote(String content) {
        this.content = content;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }
}
