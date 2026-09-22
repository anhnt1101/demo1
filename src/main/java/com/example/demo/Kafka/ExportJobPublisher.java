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

    /*
     * ==================================================
     * TỐI ƯU: timeout cấu hình được, mặc định hạ từ 10s -> 5s.
     * ==================================================
     *
     * publish() chạy đồng bộ ngay trong thread HTTP của
     * POST /api/export-requests (createRequest phải biết
     * publish thành công hay không để quyết định
     * markNewError, nên KHÔNG thể bỏ .get() này).
     *
     * Nhưng 10s là quá dài để giữ 1 thread Tomcat - ack
     * Kafka nội bộ (network gần) thường chỉ mất vài chục ms
     * đến vài trăm ms. Nếu Kafka thật sự có vấn đề, nên fail
     * nhanh hơn để trả lỗi cho FE và không làm cạn pool
     * thread HTTP khi có nhiều request dồn dập.
     */
    @Value("${export.kafka.publish-timeout-seconds:5}")
    private long publishTimeoutSeconds;

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