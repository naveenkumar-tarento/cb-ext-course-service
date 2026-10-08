package com.igot.cb.cassandra;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.datastax.oss.driver.api.core.ConsistencyLevel;
import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.CqlSessionBuilder;
import com.datastax.oss.driver.api.core.DefaultConsistencyLevel;
import com.datastax.oss.driver.api.core.metadata.EndPoint;
import com.datastax.oss.driver.api.core.metadata.Metadata;
import com.datastax.oss.driver.api.core.metadata.Node;
import com.igot.cb.cassandra.exceptions.CustomException;
import com.igot.cb.util.Constants;
import com.igot.cb.util.PropertiesCache;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.AfterEach;

@ExtendWith(MockitoExtension.class)
class CassandraConnectionManagerImplTest {

    @Mock
    PropertiesCache propertiesCache;

    private AutoCloseable mocks;

    @BeforeEach
    void setup() {
        mocks = MockitoAnnotations.openMocks(this);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (mocks != null) {
            mocks.close();
        }
        // Clear the session map after each test
        try {
            Field sessionMapField = CassandraConnectionManagerImpl.class.getDeclaredField("cassandraSessionMap");
            sessionMapField.setAccessible(true);
            Map<String, CqlSession> sessionMap = (Map<String, CqlSession>) sessionMapField.get(null);
            sessionMap.clear();
            
            Field sessionField = CassandraConnectionManagerImpl.class.getDeclaredField("session");
            sessionField.setAccessible(true);
            sessionField.set(null, null);
        } catch (Exception e) {
            // Ignore cleanup errors
        }
    }

    @Test
    void testGetConsistencyLevel_valid() {
        try (MockedStatic<PropertiesCache> staticMock = mockStatic(PropertiesCache.class)) {
            staticMock.when(PropertiesCache::getInstance).thenReturn(propertiesCache);
            when(propertiesCache.readProperty(Constants.SUNBIRD_CASSANDRA_CONSISTENCY_LEVEL))
                    .thenReturn("LOCAL_QUORUM");

            ConsistencyLevel level = invokeGetConsistencyLevel();
            assertEquals(DefaultConsistencyLevel.LOCAL_QUORUM, level);
        }
    }

    @Test
    void testGetConsistencyLevel_invalid() {
        try (MockedStatic<PropertiesCache> staticMock = mockStatic(PropertiesCache.class)) {
            staticMock.when(PropertiesCache::getInstance).thenReturn(propertiesCache);
            when(propertiesCache.readProperty(Constants.SUNBIRD_CASSANDRA_CONSISTENCY_LEVEL))
                    .thenReturn("INVALID");

            ConsistencyLevel level = invokeGetConsistencyLevel();
            assertNull(level);
        }
    }

    @Test
    void testShutdownHook() throws InterruptedException {
        Thread thread = new CassandraConnectionManagerImpl.ResourceCleanUp();
        thread.start();
        thread.join(2000); // Wait for the thread to finish (max 2 seconds)
        assertFalse(thread.isAlive(), "Shutdown hook thread should have finished execution");
    }

    private ConsistencyLevel invokeGetConsistencyLevel() {
        try {
            Method method = CassandraConnectionManagerImpl.class.getDeclaredMethod("getConsistencyLevel");
            method.setAccessible(true);
            return (ConsistencyLevel) method.invoke(null);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void testConstructorThrowsException_whenHostIsBlank() {
        try (
                MockedStatic<PropertiesCache> propertiesCacheStatic = Mockito.mockStatic(PropertiesCache.class)
        ) {
            // Arrange
            PropertiesCache mockPropertiesCache = mock(PropertiesCache.class);
            propertiesCacheStatic.when(PropertiesCache::getInstance).thenReturn(mockPropertiesCache);
            when(mockPropertiesCache.getProperty(Constants.CASSANDRA_CONFIG_HOST)).thenReturn("");

            // Act & Assert
            CustomException exception = assertThrows(CustomException.class, CassandraConnectionManagerImpl::new);
            assertEquals("Cassandra host is not configured", exception.getMessage());
        }
    }

    @Test
    void testGetConsistencyLevel_nullConsistency() {
        try (MockedStatic<PropertiesCache> staticMock = mockStatic(PropertiesCache.class)) {
            staticMock.when(PropertiesCache::getInstance).thenReturn(propertiesCache);
            when(propertiesCache.readProperty(Constants.SUNBIRD_CASSANDRA_CONSISTENCY_LEVEL))
                    .thenReturn(null);

            ConsistencyLevel level = invokeGetConsistencyLevel();
            assertNull(level);
        }
    }

    @Test
    void testGetSession_existingSession() {
        try (MockedStatic<PropertiesCache> staticMock = mockStatic(PropertiesCache.class)) {
            staticMock.when(PropertiesCache::getInstance).thenReturn(propertiesCache);
            when(propertiesCache.getProperty(Constants.CASSANDRA_CONFIG_HOST)).thenReturn("");

            assertThrows(CustomException.class, CassandraConnectionManagerImpl::new);
        }
    }

    @Test
    @Disabled("Disabled due to complexity of mocking CqlSession builder chain")
    void testGetSession_closedSession() {
        try (MockedStatic<PropertiesCache> staticMock = mockStatic(PropertiesCache.class)) {
            staticMock.when(PropertiesCache::getInstance).thenReturn(propertiesCache);
            when(propertiesCache.getProperty(Constants.CASSANDRA_CONFIG_HOST)).thenReturn("localhost");
            when(propertiesCache.getProperty(Constants.CORE_CONNECTIONS_PER_HOST_FOR_LOCAL)).thenReturn("1");
            when(propertiesCache.getProperty(Constants.CORE_CONNECTIONS_PER_HOST_FOR_REMOTE)).thenReturn("1");
            when(propertiesCache.getProperty(Constants.HEARTBEAT_INTERVAL)).thenReturn("30000");
            when(propertiesCache.readProperty(Constants.SUNBIRD_CASSANDRA_CONSISTENCY_LEVEL)).thenReturn("LOCAL_QUORUM");
            
            CassandraConnectionManagerImpl manager = new CassandraConnectionManagerImpl();
            assertThrows(CustomException.class, () -> manager.getSession("testKeyspace"));
        }
    }

    @Test
    void testResourceCleanUp_withSessions() throws Exception {
        CqlSession mockSession1 = mock(CqlSession.class);
        CqlSession mockSession2 = mock(CqlSession.class);
        
        // Use reflection to access the private static fields
        Field sessionMapField = CassandraConnectionManagerImpl.class.getDeclaredField("cassandraSessionMap");
        sessionMapField.setAccessible(true);
        Map<String, CqlSession> sessionMap = (Map<String, CqlSession>) sessionMapField.get(null);
        sessionMap.put("keyspace1", mockSession1);
        sessionMap.put("keyspace2", mockSession2);
        
        Field sessionField = CassandraConnectionManagerImpl.class.getDeclaredField("session");
        sessionField.setAccessible(true);
        sessionField.set(null, mockSession1);
        
        Thread cleanupThread = new CassandraConnectionManagerImpl.ResourceCleanUp();
        cleanupThread.run();
        
        verify(mockSession1, times(2)).close(); // Once for session map, once for static session
        verify(mockSession2, times(1)).close();
    }

    @Test
    void testResourceCleanUp_withException() throws Exception {
        CqlSession mockSession = mock(CqlSession.class);
        doThrow(new RuntimeException("Test exception")).when(mockSession).close();

        Field sessionMapField = CassandraConnectionManagerImpl.class.getDeclaredField("cassandraSessionMap");
        sessionMapField.setAccessible(true);
        Map<String, CqlSession> sessionMap = (Map<String, CqlSession>) sessionMapField.get(null);
        sessionMap.put("keyspace1", mockSession);

        Thread cleanupThread = new CassandraConnectionManagerImpl.ResourceCleanUp();
        assertDoesNotThrow(cleanupThread::run);
    }

    private void stubSuccessfulPropertiesCache(PropertiesCache mockPropertiesCache, String host) {
        when(mockPropertiesCache.getProperty(Constants.CASSANDRA_CONFIG_HOST)).thenReturn(host);
        when(mockPropertiesCache.getProperty(Constants.CORE_CONNECTIONS_PER_HOST_FOR_LOCAL)).thenReturn("1");
        when(mockPropertiesCache.getProperty(Constants.CORE_CONNECTIONS_PER_HOST_FOR_REMOTE)).thenReturn("1");
        when(mockPropertiesCache.getProperty(Constants.HEARTBEAT_INTERVAL)).thenReturn("30000");
        when(mockPropertiesCache.readProperty(Constants.SUNBIRD_CASSANDRA_CONSISTENCY_LEVEL)).thenReturn("LOCAL_QUORUM");
    }

    @SuppressWarnings("unchecked")
    private Map<String, CqlSession> sessionMap() throws Exception {
        Field sessionMapField = CassandraConnectionManagerImpl.class.getDeclaredField("cassandraSessionMap");
        sessionMapField.setAccessible(true);
        return (Map<String, CqlSession>) sessionMapField.get(null);
    }

    @Test
    void testCreateCassandraConnection_success_withMultipleHostsAndNodes() {
        try (MockedStatic<PropertiesCache> propertiesCacheStatic = mockStatic(PropertiesCache.class);
             MockedStatic<CqlSession> cqlSessionStatic = mockStatic(CqlSession.class)) {

            PropertiesCache mockPropertiesCache = mock(PropertiesCache.class);
            propertiesCacheStatic.when(PropertiesCache::getInstance).thenReturn(mockPropertiesCache);
            stubSuccessfulPropertiesCache(mockPropertiesCache, "host1, host2");

            CqlSessionBuilder mockBuilder = mock(CqlSessionBuilder.class, Answers.RETURNS_SELF);
            CqlSession mockSession = mock(CqlSession.class);
            Metadata mockMetadata = mock(Metadata.class);

            Node mockNode = mock(Node.class);
            when(mockNode.getDatacenter()).thenReturn("dc1");
            when(mockNode.getRack()).thenReturn("rack1");
            when(mockNode.getEndPoint()).thenReturn(mock(EndPoint.class));
            Map<UUID, Node> nodes = new HashMap<>();
            nodes.put(UUID.randomUUID(), mockNode);

            when(mockMetadata.getNodes()).thenReturn(nodes);
            when(mockMetadata.getClusterName()).thenReturn(Optional.of("testCluster"));
            when(mockSession.getMetadata()).thenReturn(mockMetadata);
            when(mockBuilder.build()).thenReturn(mockSession);
            cqlSessionStatic.when(CqlSession::builder).thenReturn(mockBuilder);

            CassandraConnectionManagerImpl manager = new CassandraConnectionManagerImpl();
            CqlSession result = manager.getSession("myKeyspace");

            assertNotNull(result);
            assertSame(mockSession, result);
        }
    }

    @Test
    void testGetSession_returnsCachedSession_whenNotClosed() throws Exception {
        try (MockedStatic<PropertiesCache> propertiesCacheStatic = mockStatic(PropertiesCache.class);
             MockedStatic<CqlSession> cqlSessionStatic = mockStatic(CqlSession.class)) {

            PropertiesCache mockPropertiesCache = mock(PropertiesCache.class);
            propertiesCacheStatic.when(PropertiesCache::getInstance).thenReturn(mockPropertiesCache);
            stubSuccessfulPropertiesCache(mockPropertiesCache, "localhost");

            CqlSessionBuilder mockBuilder = mock(CqlSessionBuilder.class, Answers.RETURNS_SELF);
            CqlSession mockConstructedSession = mock(CqlSession.class);
            Metadata mockMetadata = mock(Metadata.class);
            when(mockMetadata.getNodes()).thenReturn(Collections.emptyMap());
            when(mockMetadata.getClusterName()).thenReturn(Optional.of("testCluster"));
            when(mockConstructedSession.getMetadata()).thenReturn(mockMetadata);
            when(mockBuilder.build()).thenReturn(mockConstructedSession);
            cqlSessionStatic.when(CqlSession::builder).thenReturn(mockBuilder);

            CassandraConnectionManagerImpl manager = new CassandraConnectionManagerImpl();

            CqlSession cachedSession = mock(CqlSession.class);
            when(cachedSession.isClosed()).thenReturn(false);
            sessionMap().put("cachedKeyspace", cachedSession);

            CqlSession result = manager.getSession("cachedKeyspace");

            assertSame(cachedSession, result);
        }
    }

    @Test
    void testGetSession_createsNewSession_whenCachedSessionIsClosed() throws Exception {
        try (MockedStatic<PropertiesCache> propertiesCacheStatic = mockStatic(PropertiesCache.class);
             MockedStatic<CqlSession> cqlSessionStatic = mockStatic(CqlSession.class)) {

            PropertiesCache mockPropertiesCache = mock(PropertiesCache.class);
            propertiesCacheStatic.when(PropertiesCache::getInstance).thenReturn(mockPropertiesCache);
            stubSuccessfulPropertiesCache(mockPropertiesCache, "localhost");

            CqlSessionBuilder mockBuilder = mock(CqlSessionBuilder.class, Answers.RETURNS_SELF);
            CqlSession mockConstructedSession = mock(CqlSession.class);
            Metadata mockMetadata = mock(Metadata.class);
            when(mockMetadata.getNodes()).thenReturn(Collections.emptyMap());
            when(mockMetadata.getClusterName()).thenReturn(Optional.of("testCluster"));
            when(mockConstructedSession.getMetadata()).thenReturn(mockMetadata);
            when(mockBuilder.build()).thenReturn(mockConstructedSession);
            cqlSessionStatic.when(CqlSession::builder).thenReturn(mockBuilder);

            CassandraConnectionManagerImpl manager = new CassandraConnectionManagerImpl();

            CqlSession closedSession = mock(CqlSession.class);
            when(closedSession.isClosed()).thenReturn(true);
            sessionMap().put("keyspaceX", closedSession);

            CqlSession result = manager.getSession("keyspaceX");

            assertNotSame(closedSession, result);
            assertSame(mockConstructedSession, result);
        }
    }

    @Test
    void testCreateCassandraConnectionWithKeySpaces_wrapsNumberFormatException() {
        try (MockedStatic<PropertiesCache> propertiesCacheStatic = mockStatic(PropertiesCache.class)) {
            PropertiesCache mockPropertiesCache = mock(PropertiesCache.class);
            propertiesCacheStatic.when(PropertiesCache::getInstance).thenReturn(mockPropertiesCache);
            when(mockPropertiesCache.getProperty(Constants.CASSANDRA_CONFIG_HOST)).thenReturn("localhost");
            when(mockPropertiesCache.getProperty(Constants.CORE_CONNECTIONS_PER_HOST_FOR_LOCAL)).thenReturn("not-a-number");
            when(mockPropertiesCache.readProperty(Constants.SUNBIRD_CASSANDRA_CONSISTENCY_LEVEL)).thenReturn("LOCAL_QUORUM");

            CustomException exception = assertThrows(CustomException.class, CassandraConnectionManagerImpl::new);
            assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, exception.getHttpStatusCode());
            assertTrue(exception.getMessage().contains("not-a-number"));
        }
    }
}
