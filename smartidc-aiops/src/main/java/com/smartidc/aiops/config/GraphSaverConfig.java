package com.smartidc.aiops.config;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.checkpoint.savers.redis.RedisSaver;
import com.alibaba.cloud.ai.graph.serializer.StateSerializer;
import com.alibaba.cloud.ai.graph.serializer.plain_text.jackson.SpringAIJacksonStateSerializer;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.impl.LaissezFaireSubTypeValidator;
import org.redisson.api.RedissonClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * 智维云 AIOps - StateGraph 状态快照持久化配置 (对标 implementation_plan4.1.md 步骤 4)
 * 装配支持 Jackson DefaultTyping 多态类型信息的 RedisSaver 与 RedisTemplate
 * 注意：DefaultTyping 的 ObjectMapper 必须为专有实例，绝不可作为全局 Bean 覆盖 Web/RestClient 的反序列化行为
 */
@Configuration
public class GraphSaverConfig {

    private ObjectMapper createCheckpointObjectMapper() {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.activateDefaultTyping(
            LaissezFaireSubTypeValidator.instance,
            ObjectMapper.DefaultTyping.NON_FINAL,
            JsonTypeInfo.As.PROPERTY
        );
        return objectMapper;
    }

    @Bean
    public RedisTemplate<String, Object> checkpointRedisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);

        GenericJackson2JsonRedisSerializer serializer = new GenericJackson2JsonRedisSerializer(createCheckpointObjectMapper());

        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(serializer);
        template.setHashKeySerializer(new StringRedisSerializer());
        template.setHashValueSerializer(serializer);
        template.afterPropertiesSet();
        return template;
    }

    @Bean
    public StateSerializer checkpointStateSerializer() {
        return new SpringAIJacksonStateSerializer(OverAllState::new, createCheckpointObjectMapper());
    }

    @Bean
    public RedisSaver redisSaver(RedissonClient redissonClient, StateSerializer checkpointStateSerializer) {
        return RedisSaver.builder()
            .redisson(redissonClient)
            .stateSerializer(checkpointStateSerializer)
            .build();
    }
}
