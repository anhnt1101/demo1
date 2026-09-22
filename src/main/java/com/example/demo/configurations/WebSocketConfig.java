package com.example.demo.configurations;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final WebSocketJwtChannelInterceptor jwtChannelInterceptor;

    /*
     * ==================================================
     * TỐI ƯU: cho phép cấu hình allowed origin theo môi trường.
     * ==================================================
     *
     * Trước đây hardcode "http://localhost:4200" - deploy
     * staging/prod phải build lại code mới đổi được.
     *
     * Nhiều origin thì set dạng:
     * export.websocket.allowed-origins=https://a.com,https://b.com
     */
    @Value("${export.websocket.allowed-origins:http://localhost:4200}")
    private String[] allowedOrigins;


    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {

        /*
         * Server gửi message xuống client.
         *
         * Client sẽ subscribe:
         *
         * /user/queue/exports
         */

        /*
         * ==================================================
         * TỐI ƯU: bật heartbeat phía server cho Simple Broker.
         * ==================================================
         *
         * FE (transaction-log.component.ts) đã set
         * heartbeatIncoming/Outgoing = 10000ms, nhưng
         * server trước đây không set heartbeat tương ứng
         * -> STOMP heartbeat chỉ hoạt động 1 chiều, server
         * không chủ động phát hiện kết nối chết.
         *
         * Cần TaskScheduler riêng cho broker (không dùng
         * chung executor mặc định) theo đúng khuyến nghị
         * của Spring.
         */
        ThreadPoolTaskScheduler heartbeatScheduler = new ThreadPoolTaskScheduler();
        heartbeatScheduler.setPoolSize(1);
        heartbeatScheduler.setThreadNamePrefix("ws-heartbeat-");
        heartbeatScheduler.initialize();

        registry.enableSimpleBroker("/queue").setHeartbeatValue(new long[]{10000, 10000}).setTaskScheduler(heartbeatScheduler);


        /*
         * Prefix dành riêng cho từng user.
         */
        registry.setUserDestinationPrefix("/user");
    }


    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {

        /*
         * Angular connect tới:
         *
         * ws://localhost:8080/ws
         */

        /*
         * ==================================================
         * TỐI ƯU: thêm SockJS fallback.
         * ==================================================
         *
         * Một số proxy/mạng doanh nghiệp chặn raw WebSocket.
         * SockJS tự động fallback sang HTTP long-polling khi
         * WebSocket thuần không kết nối được.
         *
         * Giữ endpoint /ws gốc (raw WebSocket) cho client hiện
         * tại, thêm /ws-sockjs làm endpoint dự phòng - client
         * FE có thể thử /ws trước, rớt thì chuyển /ws-sockjs.
         */
        registry.addEndpoint("/ws").setAllowedOrigins(allowedOrigins);

        registry.addEndpoint("/ws-sockjs").setAllowedOrigins(allowedOrigins).withSockJS();
    }


    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {

        /*
         * JWT được kiểm tra khi STOMP CONNECT.
         */
        registration.interceptors(jwtChannelInterceptor);
    }
}