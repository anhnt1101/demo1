package com.example.demo.Kafka;

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

    @Value("${export.kafka.publish-timeout-seconds:5}")
    private long publishTimeoutSeconds;

    public void publish(Long requestId) {

        String message = String.valueOf(requestId);

        try {
            kafkaTemplate.send(topic, message, message).get(publishTimeoutSeconds, TimeUnit.SECONDS);

            log.info("Published export #{} to Kafka topic {}", requestId, topic);

        } catch (InterruptedException e) {

            Thread.currentThread().interrupt();

            throw new IllegalStateException("Bị interrupt khi gửi export #" + requestId + " vào Kafka", e);

        } catch (Exception e) {

            throw new IllegalStateException("Không gửi được export #" + requestId + " vào Kafka", e);
        }
    }
}