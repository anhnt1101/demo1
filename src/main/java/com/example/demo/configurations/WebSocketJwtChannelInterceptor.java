package com.example.demo.configurations;

import com.example.demo.jwt.JwtUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class WebSocketJwtChannelInterceptor implements ChannelInterceptor {

    private final JwtUtil jwtUtil;

    private final UserDetailsService userDetailsService;


    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {

        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);


        if (accessor == null) {
            return message;
        }


        /*
         * Chỉ kiểm tra JWT khi client CONNECT WebSocket.
         */
        if (StompCommand.CONNECT.equals(accessor.getCommand())) {

            String authorization = accessor.getFirstNativeHeader(HttpHeaders.AUTHORIZATION);


            if (authorization == null || !authorization.startsWith("Bearer ")) {

                throw new AccessDeniedException("WebSocket thiếu Authorization Bearer token");
            }


            String token = authorization.substring(7);


            /*
             * JwtUtil hiện tại của bạn có:
             *
             * isTokenValid(token)
             * extractUsername(token)
             */
            if (!jwtUtil.isTokenValid(token)) {

                throw new AccessDeniedException("JWT WebSocket không hợp lệ hoặc đã hết hạn");
            }


            String username = jwtUtil.extractUsername(token);


            UserDetails userDetails = userDetailsService.loadUserByUsername(username);


            UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());


            /*
             * Rất quan trọng.
             *
             * Sau dòng này:
             *
             * Principal.getName()
             * =
             * username
             *
             * nên convertAndSendToUser(username, ...)
             * mới tìm đúng WebSocket session.
             */
            accessor.setUser(authentication);


            log.info("WebSocket CONNECT authenticated: {}", username);
        }


        return message;
    }
}