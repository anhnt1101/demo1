package com.example.demo.realtime;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;

@Component
@RequiredArgsConstructor
@Slf4j
public class ExportRedisSubscriber implements MessageListener {

    private final ObjectMapper objectMapper;

    private final SimpMessagingTemplate messagingTemplate;


    @Override
    public void onMessage(Message message, byte[] pattern) {

        try {

            /*
             * Redis message JSON.
             */
            String json = new String(message.getBody(), StandardCharsets.UTF_8);


            ExportNotification notification = objectMapper.readValue(json, ExportNotification.class);


            /*
             * ==================================================
             * TỐI ƯU: bỏ userRepository.findById() ở đây.
             * ==================================================
             *
             * username giờ được ExportWorker gắn sẵn
             * vào notification lúc publish (1 lần / export,
             * không phải 1 lần / progress tick).
             *
             * Không còn query DB trên hot path này nữa.
             */
            if (notification.username() == null || notification.username().isBlank()) {

                log.warn("Notification export #{} thiếu username, bỏ qua push WebSocket", notification.requestId());

                return;
            }


            /*
             * Ví dụ:
             *
             * username = nta
             *
             * destination:
             *
             * /user/queue/exports
             */
            messagingTemplate.convertAndSendToUser(notification.username(), "/queue/exports", notification);


            log.info("Push WebSocket export #{} status={} -> user={}", notification.requestId(), notification.exportStatus(), notification.username());

        } catch (Exception e) {

            log.error("Không xử lý được Redis export event", e);
        }
    }
}