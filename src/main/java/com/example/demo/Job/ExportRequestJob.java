package com.example.demo.Job;

import com.example.demo.service.ExportService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@Slf4j
public class ExportRequestJob {

    private final ExportService exportService;

    private final ExportWorker exportWorker;

    private final ThreadPoolTaskExecutor executor;


    public ExportRequestJob(ExportService exportService, ExportWorker exportWorker, @Qualifier("exportTaskExecutor") ThreadPoolTaskExecutor executor) {

        this.exportService = exportService;

        this.exportWorker = exportWorker;

        this.executor = executor;
    }

    @Scheduled(fixedDelayString = "${export.poll-interval-ms:5000}")
    public void poll() {

        int freeWorkers = executor.getMaxPoolSize() - executor.getActiveCount();

        if (freeWorkers <= 0) {
            return;
        }

        List<Long> ids = exportService.findNextPendingIds(freeWorkers);

        for (Long id : ids) {
            /*
             * Atomic NEW -> PROCESSING.
             */
            if (!exportService.tryClaim(id)) {
                continue;
            }

            try {
                executor.execute(() -> exportWorker.process(id));
            } catch (TaskRejectedException e) {

                /*
                 * Đã PROCESSING nhưng task
                 * chưa thực sự chạy.
                 */
                exportService.resetToNew(id);
                log.warn("Export #{} bị executor reject, reset NEW", id);
            }
        }
    }


    @Scheduled(fixedDelayString = "${export.stale-check-interval-ms:60000}")
    public void recoverStale() {
        exportService.recoverStale();
    }
}
