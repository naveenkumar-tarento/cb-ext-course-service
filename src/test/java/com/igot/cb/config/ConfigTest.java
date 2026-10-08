package com.igot.cb.config;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.test.util.ReflectionTestUtils;
import redis.clients.jedis.JedisPool;

import com.igot.cb.util.Constants;
import com.igot.cb.util.PropertiesCache;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

class ConfigTest {

    @Test
    void testConsumerConfiguration() {
        ConsumerConfiguration consumerConfiguration = new ConsumerConfiguration();
        ReflectionTestUtils.setField(consumerConfiguration, "kafkabootstrapAddress", "localhost:9092");
        ReflectionTestUtils.setField(consumerConfiguration, "kafkaOffsetResetValue", "earliest");
        ReflectionTestUtils.setField(consumerConfiguration, "kafkaMaxPollInterval", 300000);
        ReflectionTestUtils.setField(consumerConfiguration, "kafkaMaxPollRecords", 500);
        ReflectionTestUtils.setField(consumerConfiguration, "kafkaAutoCommitInterval", 1000);

        Map<String, Object> configs = consumerConfiguration.consumerConfigs();
        assertEquals("localhost:9092", configs.get(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG));
        assertEquals("earliest", configs.get(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG));

        ConsumerFactory<String, String> consumerFactory = consumerConfiguration.consumerFactory();
        assertNotNull(consumerFactory);
    }

    @Test
    void testProducerConfiguration() {
        ProducerConfiguration producerConfiguration = new ProducerConfiguration();
        ReflectionTestUtils.setField(producerConfiguration, "kafkabootstrapAddress", "localhost:9092");

        ProducerFactory<String, String> producerFactory = producerConfiguration.producerFactory();
        assertNotNull(producerFactory);
        assertEquals("localhost:9092",
                producerFactory.getConfigurationProperties().get(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG));

        KafkaTemplate<String, String> kafkaTemplate = producerConfiguration.kafkaTemplate();
        assertNotNull(kafkaTemplate);
    }

    @Test
    void testRedisConfig() {
        try (MockedStatic<PropertiesCache> mockedStatic = mockStatic(PropertiesCache.class)) {
            PropertiesCache mockCache = mock(PropertiesCache.class);
            mockedStatic.when(PropertiesCache::getInstance).thenReturn(mockCache);
            when(mockCache.getProperty(Constants.REDIS_HOST)).thenReturn("localhost");
            when(mockCache.getProperty(Constants.REDIS_PORT)).thenReturn("6379");

            RedisConfig redisConfig = new RedisConfig();
            JedisPool jedisPool = redisConfig.jedisPool();

            assertNotNull(jedisPool);
        }
    }
}
