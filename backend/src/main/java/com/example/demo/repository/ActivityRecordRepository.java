package com.example.demo.repository;

import com.example.demo.common.CacheNames;
import com.example.demo.entity.ActivityRecord;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ActivityRecordRepository extends JpaRepository<ActivityRecord, Long> {

    @Override
    @Cacheable(value = CacheNames.ACTIVITY_RECORD, key = "#id")
    Optional<ActivityRecord> findById(Long id);
}
