package com.example.codetools;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface VectorRepository extends JpaRepository<VectorRecord, String> {
    List<VectorRecord> findByApplicationId(String applicationId);
}
