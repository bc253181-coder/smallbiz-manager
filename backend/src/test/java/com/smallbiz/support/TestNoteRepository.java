package com.smallbiz.support;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface TestNoteRepository extends JpaRepository<TestNote, Long> {

    List<TestNote> findByContent(String content);

    @Query("select n from TestNote n where n.content like :prefix%")
    List<TestNote> searchJpql(String prefix);
}
