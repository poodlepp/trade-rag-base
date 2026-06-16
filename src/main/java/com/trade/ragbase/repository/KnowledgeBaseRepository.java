package com.trade.ragbase.repository;

import java.util.List;

import com.trade.ragbase.entity.KnowledgeBase;
import org.springframework.data.jpa.repository.JpaRepository;

public interface KnowledgeBaseRepository extends JpaRepository<KnowledgeBase, Long> {

    List<KnowledgeBase> findByIsDeletedFalse();

    List<KnowledgeBase> findByDepartmentIdAndIsDeletedFalse(String departmentId);

    List<KnowledgeBase> findByIsPublicTrueAndIsDeletedFalse();
}
