package com.example.demo.service.export;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class ExportJobConsumer {

    private final ExportService exportService;

    private final ExportWorker exportWorker;


    @KafkaListener(topics = "${export.kafka.topic:export_jobs}", concurrency = "${export.kafka.concurrency:2}")
    public void consume(String message) {
        Long requestId;
        try {
            requestId = Long.valueOf(message);
        } catch (NumberFormatException e) {
            log.error("Kafka message không hợp lệ: {}", message);
            return;
        }
        log.info("Kafka consumer received export #{}", requestId);

        if (!exportService.tryClaim(requestId)) {
            log.warn("Export #{} không claim được", requestId);
            return;
        }
        exportWorker.process(requestId);
    }
}