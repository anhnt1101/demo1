package com.example.demo.service.export;

import com.example.demo.Event.ExportNotification;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;

@Service
@RequiredArgsConstructor
@Slf4j
public class ExportRedisService {

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    @Value("${export.redis.channel:export-events}")
    private String channel;
    @Value("${export.redis.progress-ttl-hours:24}")
    private long progressTtlHours;

    public void updateProgress(Long requestId, int progress) {
        try {
            String key = "export:progress:" + requestId;
            redisTemplate.opsForValue().set(key, String.valueOf(progress), Duration.ofHours(progressTtlHours));
            log.debug("Export #{} progress={}%", requestId, progress);
        } catch (Exception e) {
            log.warn("Không cập nhật được Redis progress cho export #{}", requestId, e);
        }
    }

    public Integer getProgress(Long requestId) {

        try {
            String key = "export:progress:" + requestId;
            String value = redisTemplate.opsForValue().get(key);
            return value == null ? null : Integer.valueOf(value);
        } catch (Exception e) {
            log.warn("Không đọc được Redis progress cho export #{}", requestId, e);
            return null;
        }
    }

    /**
     * Publish event vào Redis Pub/Sub.
     */
    public void publish(ExportNotification notification) {

        try {
            String json = objectMapper.writeValueAsString(notification);
            redisTemplate.convertAndSend(channel, json);
            log.info("Published Redis event export #{} status={}", notification.requestId(), notification.exportStatus());
        } catch (Exception e) {
            log.warn("Không publish được Redis event cho export #{}", notification.requestId(), e);
        }
    }

    public void deleteProgress(Long requestId) {
        try {
            redisTemplate.delete("export:progress:" + requestId);
        } catch (Exception e) {
            log.warn("Không xóa được Redis progress cho export #{}", requestId, e);
        }
    }
}