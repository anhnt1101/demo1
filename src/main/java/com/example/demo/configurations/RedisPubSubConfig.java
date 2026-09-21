package com.example.demo.configurations;

import com.example.demo.realtime.ExportRedisSubscriber;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

@Configuration
public class RedisPubSubConfig {


    @Bean
    public RedisMessageListenerContainer exportRedisListenerContainer(RedisConnectionFactory connectionFactory, ExportRedisSubscriber subscriber, @Value("${export.redis.channel:export-events}") String channel) {

        RedisMessageListenerContainer container = new RedisMessageListenerContainer();


        container.setConnectionFactory(connectionFactory);


        /*
         * Subscribe:
         *
         * export-events
         */
        container.addMessageListener(subscriber, new ChannelTopic(channel));


        return container;
    }
}