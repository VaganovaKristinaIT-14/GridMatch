package com.ox.tictactoe.server.service;

import com.ox.tictactoe.server.repository.GameLogRepository;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Component
public class LogCleanupScheduler {

    private final GameLogRepository gameLogRepository;

    public LogCleanupScheduler(GameLogRepository gameLogRepository) {
        this.gameLogRepository = gameLogRepository;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void cleanupOnStartup() {
        LocalDateTime cutoffDate = LocalDateTime.now().minusDays(7);
        int deletedCount = gameLogRepository.deleteOlderThan(cutoffDate);
        System.out.println("[LOG CLEANUP] При запуске удалено " + deletedCount + " логов старше 7 дней");
    }

    @Scheduled(cron = "0 0 3 * * ?")
    @Transactional
    public void cleanupOldLogs() {
        LocalDateTime cutoffDate = LocalDateTime.now().minusDays(7);
        int deletedCount = gameLogRepository.deleteOlderThan(cutoffDate);
        System.out.println("[LOG CLEANUP] Удалено " + deletedCount + " логов старше 7 дней");
    }
}
