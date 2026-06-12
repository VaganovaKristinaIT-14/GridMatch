package com.ox.tictactoe.server.repository;

import com.ox.tictactoe.server.model.GameLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Repository
public interface GameLogRepository extends JpaRepository<GameLog, Long> {

    @Modifying
    @Transactional
    @Query("DELETE FROM GameLog l WHERE l.timestamp < :date")
    int deleteOlderThan(@Param("date") LocalDateTime date);
}
