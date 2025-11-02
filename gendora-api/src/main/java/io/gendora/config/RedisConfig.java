package io.gendora.config;

import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
public class RedisConfig {

    private static final Logger logger = LoggerFactory.getLogger(RedisConfig.class);

    @Value("${redis.host}")
    private String redisHost;

    @Value("${redis.port}")
    private int redisPort;

    private RedisClient redisClient;
    private StatefulRedisConnection<String, String> redisConnection;

    @Bean
    public RedisClient redisClient() {
        RedisURI redisUri = RedisURI.builder()
                .withHost(redisHost)
                .withPort(redisPort)
                .withTimeout(Duration.ofSeconds(10))
                .build();

        this.redisClient = RedisClient.create(redisUri);
        return this.redisClient;
    }

    @Bean
    public StatefulRedisConnection<String, String> redisConnection(RedisClient redisClient) {
        this.redisConnection = redisClient.connect();
        return this.redisConnection;
    }

    @Bean
    public RedisCommands<String, String> redis(StatefulRedisConnection<String, String> redisConnection) {
        return redisConnection.sync();
    }

    @PreDestroy
    public void destroy() {
        logger.info("Closing Redis connections...");
        
        if (redisConnection != null) {
            try {
                redisConnection.close();
                logger.info("Redis connection closed successfully");
            } catch (Exception e) {
                logger.error("Error closing Redis connection", e);
            }
        }

        if (redisClient != null) {
            try {
                redisClient.shutdown();
                logger.info("Redis client shut down successfully");
            } catch (Exception e) {
                logger.error("Error shutting down Redis client", e);
            }
        }
    }
}
