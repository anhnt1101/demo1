package com.example.demo.realtime;

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
public class ExportRealtimeService {

    private final StringRedisTemplate redisTemplate;

    private final ObjectMapper objectMapper;

    @Value("${export.redis.channel:export-events}")
    private String channel;

    @Value("${export.redis.progress-ttl-hours:24}")
    private long progressTtlHours;


    /**
     * Lưu progress tạm thời vào Redis.
     * <p>
     * Key:
     * export:progress:35
     * <p>
     * Value:
     * 80
     */
    public void updateProgress(Long requestId, int progress) {

        try {

            String key = "export:progress:" + requestId;

            redisTemplate.opsForValue().set(key, String.valueOf(progress), Duration.ofHours(progressTtlHours));

            log.debug("Export #{} progress={}%", requestId, progress);

        } catch (Exception e) {

            /*
             * Redis chỉ phục vụ realtime/progress.
             *
             * Redis chết KHÔNG được làm
             * export Excel chuyển ERROR.
             */
            log.warn("Không cập nhật được Redis progress cho export #{}", requestId, e);
        }
    }


    /**
     * ==================================================
     * TỐI ƯU: đọc lại progress từ Redis.
     * ==================================================
     * <p>
     * Trước đây key export:progress:{id} được ghi
     * nhưng KHÔNG có chỗ nào đọc lại — Redis Pub/Sub
     * là fire-and-forget, nếu FE mất kết nối đúng lúc
     * publish() chạy thì event đó mất vĩnh viễn.
     * <p>
     * Dùng method này làm fallback: FE reconnect/mở lại
     * trang thì gọi 1 API để lấy progress hiện tại thay vì
     * chỉ ngồi chờ WebSocket.
     * <p>
     * Trả về null nếu Redis miss hoặc lỗi — caller phải
     * tự fallback tiếp theo trạng thái DB.
     */
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

            /*
             * File export đã thành công thì
             * Redis lỗi cũng KHÔNG được
             * đổi export thành ERROR.
             */
            log.warn("Không publish được Redis event cho export #{}", notification.requestId(), e);
        }
    }
}