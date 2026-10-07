package com.collab.platform.repository;

import com.collab.platform.model.ConflictRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ConflictRecordRepository extends JpaRepository<ConflictRecord, String> {
    List<ConflictRecord> findTop15ByRoomIdOrderByCreatedAtDesc(String roomId);
}
