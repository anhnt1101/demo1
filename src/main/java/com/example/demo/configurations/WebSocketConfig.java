package com.example.demo.configurations;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final WebSocketJwtChannelInterceptor jwtChannelInterceptor;


    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {

        /*
         * Server gửi message xuống client.
         *
         * Client sẽ subscribe:
         *
         * /user/queue/exports
         */
        registry.enableSimpleBroker("/queue");


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
        registry.addEndpoint("/ws").setAllowedOrigins("http://localhost:4200");
    }


    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {

        /*
         * JWT được kiểm tra khi STOMP CONNECT.
         */
        registration.interceptors(jwtChannelInterceptor);
    }
}