package com.trade.ragbase.repository;

import java.util.Optional;

import com.trade.ragbase.entity.IndexTask;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IndexTaskRepository extends JpaRepository<IndexTask, Long> {

    Optional<IndexTask> findTopByDocIdOrderByCreatedAtDesc(Long docId);
}
