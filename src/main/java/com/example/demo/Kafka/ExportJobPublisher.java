package com.example.demo.kafka;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Component
@RequiredArgsConstructor
@Slf4j
public class ExportJobPublisher {

    private final KafkaTemplate<String, String> kafkaTemplate;

    @Value("${export.kafka.topic:export_jobs}")
    private String topic;

    public void publish(Long requestId) {

        String message = String.valueOf(requestId);

        try {

            /*
             * key   = requestId
             * value = requestId
             *
             * Ví dụ:
             *
             * key   = "28"
             * value = "28"
             */
            kafkaTemplate.send(topic, message, message).get(10, TimeUnit.SECONDS);

            log.info("Published export #{} to Kafka topic {}", requestId, topic);

        } catch (InterruptedException e) {

            Thread.currentThread().interrupt();

            throw new IllegalStateException("Bị interrupt khi gửi export #" + requestId + " vào Kafka", e);

        } catch (Exception e) {

            throw new IllegalStateException("Không gửi được export #" + requestId + " vào Kafka", e);
        }
    }
}