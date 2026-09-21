package com.example.demo.realtime;

import com.example.demo.entity.User;
import com.example.demo.repository.UserRepository;
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

    private final UserRepository userRepository;


    @Override
    public void onMessage(Message message, byte[] pattern) {

        try {

            /*
             * Redis message JSON.
             */
            String json = new String(message.getBody(), StandardCharsets.UTF_8);


            ExportNotification notification = objectMapper.readValue(json, ExportNotification.class);


            /*
             * Notification hiện tại chứa userId.
             *
             * WebSocket lại xác định user theo username.
             *
             * Vì vậy lấy username từ DB.
             */
            User user = userRepository.findById(notification.userId()).orElse(null);


            if (user == null) {

                log.warn("Không tìm thấy userId={} để push WebSocket export #{}", notification.userId(), notification.requestId());

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
            messagingTemplate.convertAndSendToUser(user.getUsername(), "/queue/exports", notification);


            log.info("Push WebSocket export #{} status={} -> user={}", notification.requestId(), notification.exportStatus(), user.getUsername());

        } catch (Exception e) {

            log.error("Không xử lý được Redis export event", e);
        }
    }
}