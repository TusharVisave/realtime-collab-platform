package com.collab.platform.repository;

import com.collab.platform.model.BoardItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BoardItemRepository extends JpaRepository<BoardItem, String> {
    List<BoardItem> findByRoomIdOrderByCreatedAtAsc(String roomId);
    Optional<BoardItem> findByRoomIdAndId(String roomId, String id);
    void deleteByRoomIdAndId(String roomId, String id);
}
