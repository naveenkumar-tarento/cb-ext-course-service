package com.igot.cb.cache;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

@ExtendWith(MockitoExtension.class)
class RedisDataCacheMgrTest {

    @Mock
    private JedisPool jedisDataPool;

    @Mock
    private Jedis jedis;

    @Test
    void testConstructor() {
        RedisDataCacheMgr mgr = new RedisDataCacheMgr(jedisDataPool);
        assertNotNull(mgr);
    }

    @Test
    void testGetAllHashFields_defaultDb_success() {
        RedisDataCacheMgr mgr = new RedisDataCacheMgr(jedisDataPool);
        String key = "hashKey";
        Map<String, String> expected = new HashMap<>();
        expected.put("field1", "value1");

        when(jedisDataPool.getResource()).thenReturn(jedis);
        when(jedis.hgetAll(key)).thenReturn(expected);

        Map<String, String> result = mgr.getAllHashFields(key);

        assertEquals(expected, result);
        verify(jedis).select(0);
        verify(jedis).hgetAll(key);
        verify(jedis).close();
    }

    @Test
    void testGetAllHashFields_specificDb_success() {
        RedisDataCacheMgr mgr = new RedisDataCacheMgr(jedisDataPool);
        String key = "hashKey";
        int dbIndex = 3;
        Map<String, String> expected = new HashMap<>();
        expected.put("field1", "value1");
        expected.put("field2", "value2");

        when(jedisDataPool.getResource()).thenReturn(jedis);
        when(jedis.hgetAll(key)).thenReturn(expected);

        Map<String, String> result = mgr.getAllHashFields(key, dbIndex);

        assertEquals(expected, result);
        verify(jedis).select(dbIndex);
        verify(jedis).hgetAll(key);
        verify(jedis).close();
    }

    @Test
    void testGetAllHashFields_nullResult() {
        RedisDataCacheMgr mgr = new RedisDataCacheMgr(jedisDataPool);
        String key = "hashKey";

        when(jedisDataPool.getResource()).thenReturn(jedis);
        when(jedis.hgetAll(key)).thenReturn(null);

        Map<String, String> result = mgr.getAllHashFields(key, 0);

        assertNull(result);
        verify(jedis).close();
    }

    @Test
    void testGetAllHashFields_exception() {
        RedisDataCacheMgr mgr = new RedisDataCacheMgr(jedisDataPool);
        String key = "hashKey";

        when(jedisDataPool.getResource()).thenThrow(new RuntimeException("Redis unavailable"));

        Map<String, String> result = mgr.getAllHashFields(key, 1);

        assertNotNull(result);
        assertTrue(result.isEmpty());
        verify(jedisDataPool).getResource();
    }

    @Test
    void testGetHashField_defaultDb_success() {
        RedisDataCacheMgr mgr = new RedisDataCacheMgr(jedisDataPool);
        String key = "hashKey";
        String field = "field1";
        String value = "value1";

        when(jedisDataPool.getResource()).thenReturn(jedis);
        when(jedis.hget(key, field)).thenReturn(value);

        String result = mgr.getHashField(key, field);

        assertEquals(value, result);
        verify(jedis).select(0);
        verify(jedis).hget(key, field);
        verify(jedis).close();
    }

    @Test
    void testGetHashField_specificDb_success() {
        RedisDataCacheMgr mgr = new RedisDataCacheMgr(jedisDataPool);
        String key = "hashKey";
        String field = "field1";
        int dbIndex = 5;
        String value = "value1";

        when(jedisDataPool.getResource()).thenReturn(jedis);
        when(jedis.hget(key, field)).thenReturn(value);

        String result = mgr.getHashField(key, field, dbIndex);

        assertEquals(value, result);
        verify(jedis).select(dbIndex);
        verify(jedis).hget(key, field);
        verify(jedis).close();
    }

    @Test
    void testGetHashField_notFound() {
        RedisDataCacheMgr mgr = new RedisDataCacheMgr(jedisDataPool);
        String key = "hashKey";
        String field = "missingField";

        when(jedisDataPool.getResource()).thenReturn(jedis);
        when(jedis.hget(key, field)).thenReturn(null);

        String result = mgr.getHashField(key, field, 0);

        assertNull(result);
        verify(jedis).close();
    }

    @Test
    void testGetHashField_exception() {
        RedisDataCacheMgr mgr = new RedisDataCacheMgr(jedisDataPool);
        String key = "hashKey";
        String field = "field1";

        when(jedisDataPool.getResource()).thenThrow(new RuntimeException("Redis error"));

        String result = mgr.getHashField(key, field, 2);

        assertNull(result);
        verify(jedisDataPool).getResource();
    }
}
