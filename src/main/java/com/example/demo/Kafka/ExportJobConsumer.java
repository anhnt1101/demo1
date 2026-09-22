package com.example.demo.Kafka;

import com.example.demo.service.export.ExportService;
import com.example.demo.service.export.ExportWorker;
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

        /*
         * 1. Kafka gửi String.
         *
         * Ví dụ:
         *
         * "28"
         *
         * -> Long 28
         */
        try {

            requestId = Long.valueOf(message);

        } catch (NumberFormatException e) {

            log.error("Kafka message không hợp lệ: {}", message);

            return;
        }


        log.info("Kafka consumer received export #{}", requestId);


        /*
         * 2. Atomic claim.
         *
         * EXPORT_REQUEST:
         *
         * NEW
         * ↓
         * PROCESSING
         */
        if (!exportService.tryClaim(requestId)) {

            log.warn("Export #{} không claim được", requestId);

            return;
        }


        /*
         * 3. Sau khi PROCESSING
         * mới bắt đầu tạo Excel.
         */
        exportWorker.process(requestId);
    }
}