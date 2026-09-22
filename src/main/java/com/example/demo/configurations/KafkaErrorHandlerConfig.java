package com.example.demo.configurations;

import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.common.TopicPartition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

@Configuration
@Slf4j
public class KafkaErrorHandlerConfig {

    /*
     * ==================================================
     * VẤN ĐỀ TRƯỚC ĐÂY
     * ==================================================
     *
     * ExportJobConsumer.consume() không có error handler.
     *
     * ack-mode = record (application.properties)
     * -> nếu @KafkaListener NÉM EXCEPTION ngoài dự kiến
     *    (không phải NumberFormatException đã catch sẵn,
     *     ví dụ: DB tạm mất kết nối khi tryClaim()),
     *    offset KHÔNG được commit
     * -> Kafka REDELIVER message đó VÔ HẠN LẦN
     *    (poison-pill loop), làm nghẽn consumer.
     *
     * ==================================================
     * GIẢI PHÁP
     * ==================================================
     *
     * Spring Boot tự động dò 1 bean CommonErrorHandler
     * (ở đây là DefaultErrorHandler) rồi gắn vào
     * ConcurrentKafkaListenerContainerFactory mặc định
     * -> không cần tự viết lại factory.
     *
     * - Retry tối đa 3 lần, cách nhau 2 giây.
     * - Hết 3 lần vẫn lỗi -> đẩy record sang topic
     *   "<topic>.DLT" (Dead Letter Topic) thay vì lặp vô hạn.
     *
     * Ví dụ: export_jobs lỗi liên tục
     *      -> export_jobs.DLT
     *
     * Cần theo dõi/xử lý thủ công các message rơi vào DLT.
     */
    @Bean
    public DefaultErrorHandler exportKafkaErrorHandler(KafkaOperations<String, String> kafkaOperations) {

        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(kafkaOperations, (record, exception) -> new TopicPartition(record.topic() + ".DLT", record.partition()));

        /*
         * FixedBackOff(2000L, 3):
         *
         * retry cách nhau 2000ms, tối đa 3 lần.
         */
        DefaultErrorHandler errorHandler = new DefaultErrorHandler(recoverer, new FixedBackOff(2000L, 3));

        errorHandler.setRetryListeners((record, ex, deliveryAttempt) -> log.warn("Kafka export_jobs retry lần {} cho key={} do lỗi: {}", deliveryAttempt, record.key(), ex.getMessage()));

        return errorHandler;
    }
}