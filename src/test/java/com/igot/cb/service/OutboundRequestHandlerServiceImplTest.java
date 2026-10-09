package com.igot.cb.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class OutboundRequestHandlerServiceImplTest {

    // Create a test implementation of RestTemplate to avoid ByteBuddy issues on Java 23
    private static class TestRestTemplate extends RestTemplate {
        private Map<String, Object> getForObjectResponse;
        private ResponseEntity<Map<String, Object>> exchangeResponse;
        private Map<String, Object> postForObjectResponse;
        private RuntimeException exceptionToThrow;

        private Map<String, Object> patchForObjectResponse;

        public void setPatchForObjectResponse(Map<String, Object> response) {
            this.patchForObjectResponse = response;
        }

        @Override
        public <T> T patchForObject(String url, Object request, Class<T> responseType, Object... uriVariables) {
            if (exceptionToThrow != null) {
                throw exceptionToThrow;
            }
            return (T) patchForObjectResponse;
        }


        public void setGetForObjectResponse(Map<String, Object> response) {
            this.getForObjectResponse = response;
        }

        public void setExchangeResponse(ResponseEntity<Map<String, Object>> response) {
            this.exchangeResponse = response;
        }

        public void setPostForObjectResponse(Map<String, Object> response) {
            this.postForObjectResponse = response;
        }

        public void setExceptionToThrow(RuntimeException exception) {
            this.exceptionToThrow = exception;
        }

        @Override
        public <T> T getForObject(String url, Class<T> responseType, Object... uriVariables) {
            if (exceptionToThrow != null) {
                throw exceptionToThrow;
            }
            return (T) getForObjectResponse;
        }

        @Override
        public <T> ResponseEntity<T> exchange(String url, HttpMethod method, HttpEntity<?> requestEntity,
                                               ParameterizedTypeReference<T> responseType, Object... uriVariables) {
            if (exceptionToThrow != null) {
                throw exceptionToThrow;
            }
            return (ResponseEntity<T>) exchangeResponse;
        }

        @Override
        public <T> T postForObject(String url, Object request, Class<T> responseType, Object... uriVariables) {
            if (exceptionToThrow != null) {
                throw exceptionToThrow;
            }
            return (T) postForObjectResponse;
        }
    }

    private TestRestTemplate testRestTemplate;
    private OutboundRequestHandlerServiceImpl outboundService;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setup() throws Exception {
        testRestTemplate = new TestRestTemplate();
        outboundService = new OutboundRequestHandlerServiceImpl(null);
        objectMapper = new ObjectMapper();

        // Inject the test RestTemplate using reflection
        Field restTemplateField = OutboundRequestHandlerServiceImpl.class.getDeclaredField("restTemplate");
        restTemplateField.setAccessible(true);
        restTemplateField.set(outboundService, testRestTemplate);
    }

    @Test
    void testConstructor() {
        RestTemplate mockRestTemplate = new RestTemplate();
        OutboundRequestHandlerServiceImpl service = new OutboundRequestHandlerServiceImpl(mockRestTemplate);
        assertNotNull(service);
    }

    @Test
    void testFetchResult_Success() {
        String uri = "http://test.com/api";
        Map<String, Object> mockResponse = Map.of("key", "value");

        testRestTemplate.setGetForObjectResponse(mockResponse);

        Object result = outboundService.fetchResult(uri);
        
        assertNotNull(result);
        assertEquals(mockResponse, result);
    }

    @Test
    void testFetchResult_HttpClientError_ValidJson() throws Exception {
        String uri = "http://test.com/api";
        Map<String, Object> errorMap = Map.of("error", "Bad Request");
        String errorJson = objectMapper.writeValueAsString(errorMap);

        HttpClientErrorException exception = HttpClientErrorException.create(
                HttpStatus.BAD_REQUEST, "Bad Request", new HttpHeaders(),
                errorJson.getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8);

        testRestTemplate.setExceptionToThrow(exception);

        Object result = outboundService.fetchResult(uri);

        assertNotNull(result);
        assertEquals("Bad Request", ((Map<?, ?>) result).get("error"));
    }

    @Test
    void testFetchResult_HttpClientError_InvalidJson() {
        String uri = "http://test.com/api";
        String invalidJson = "<html>error</html>";

        HttpClientErrorException exception = HttpClientErrorException.create(
                HttpStatus.INTERNAL_SERVER_ERROR, "Server Error", new HttpHeaders(),
                invalidJson.getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8);

        testRestTemplate.setExceptionToThrow(exception);

        Object result = outboundService.fetchResult(uri);

        assertNull(result);
    }

    @Test
    void testFetchResult_GenericException() {
        String uri = "http://test.com/api";

        testRestTemplate.setExceptionToThrow(new RuntimeException("Connection error"));

        Object result = outboundService.fetchResult(uri);

        assertNull(result);
    }

    @Test
    void testFetchResultUsingExchange_Success() {
        String uri = "http://test.com/api";
        Map<String, Object> mockResponse = Map.of("key", "value");
        ParameterizedTypeReference<Map<String, Object>> typeRef = 
            new ParameterizedTypeReference<Map<String, Object>>() {};
        
        ResponseEntity<Map<String, Object>> responseEntity = 
            new ResponseEntity<>(mockResponse, HttpStatus.OK);
        
        testRestTemplate.setExchangeResponse(responseEntity);

        Map<String, Object> result = outboundService.fetchResultUsingExchange(uri, typeRef);
        
        assertNotNull(result);
        assertEquals("value", result.get("key"));
    }

    @Test
    void testFetchResultUsingExchange_HttpClientError_ValidJson() throws Exception {
        String uri = "http://test.com/api";
        Map<String, Object> errorMap = Map.of("error", "Bad Request");
        String errorJson = objectMapper.writeValueAsString(errorMap);
        ParameterizedTypeReference<Map<String, Object>> typeRef = 
            new ParameterizedTypeReference<Map<String, Object>>() {};

        HttpClientErrorException exception = HttpClientErrorException.create(
                HttpStatus.BAD_REQUEST, "Bad Request", new HttpHeaders(),
                errorJson.getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8);

        testRestTemplate.setExceptionToThrow(exception);

        Map<String, Object> result = outboundService.fetchResultUsingExchange(uri, typeRef);

        assertNull(result);
    }

    @Test
    void testFetchResultUsingExchange_HttpClientError_InvalidJson() {
        String uri = "http://test.com/api";
        String invalidJson = "<html>error</html>";
        ParameterizedTypeReference<Map<String, Object>> typeRef = 
            new ParameterizedTypeReference<Map<String, Object>>() {};

        HttpClientErrorException exception = HttpClientErrorException.create(
                HttpStatus.INTERNAL_SERVER_ERROR, "Server Error", new HttpHeaders(),
                invalidJson.getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8);

        testRestTemplate.setExceptionToThrow(exception);

        Map<String, Object> result = outboundService.fetchResultUsingExchange(uri, typeRef);

        assertNull(result);
    }

    @Test
    void testFetchResultUsingExchange_GenericException() {
        String uri = "http://test.com/api";
        ParameterizedTypeReference<Map<String, Object>> typeRef = 
            new ParameterizedTypeReference<Map<String, Object>>() {};

        testRestTemplate.setExceptionToThrow(new RuntimeException("Connection error"));

        Map<String, Object> result = outboundService.fetchResultUsingExchange(uri, typeRef);

        assertNull(result);
    }

    @Test
    void testFetchResult_GenericException_WithNonNullResponse() {
        testFetchResult_GenericException();
    }

    @Test
    void testFetchResultUsingExchange_GenericException_WithNonNullResponse() {
        testFetchResultUsingExchange_GenericException();
    }

    @Test
    void testFetchResult_WithDebugEnabled() {
        String uri = "http://test.com/api";
        Map<String, Object> mockResponse = Map.of("key", "value");

        // Enable debug logging
        ch.qos.logback.classic.Logger logger = (ch.qos.logback.classic.Logger) 
            org.slf4j.LoggerFactory.getLogger(OutboundRequestHandlerServiceImpl.class);
        ch.qos.logback.classic.Level originalLevel = logger.getLevel();
        logger.setLevel(ch.qos.logback.classic.Level.DEBUG);

        try {
            testRestTemplate.setGetForObjectResponse(mockResponse);
            Object result = outboundService.fetchResult(uri);
            assertNotNull(result);
        } finally {
            logger.setLevel(originalLevel);
        }
    }

    @Test
    void testFetchResultUsingExchange_WithDebugEnabled() {
        String uri = "http://test.com/api";
        Map<String, Object> mockResponse = Map.of("key", "value");
        ParameterizedTypeReference<Map<String, Object>> typeRef = 
            new ParameterizedTypeReference<Map<String, Object>>() {};
        
        ResponseEntity<Map<String, Object>> responseEntity = 
            new ResponseEntity<>(mockResponse, HttpStatus.OK);

        // Enable debug logging
        ch.qos.logback.classic.Logger logger = (ch.qos.logback.classic.Logger) 
            org.slf4j.LoggerFactory.getLogger(OutboundRequestHandlerServiceImpl.class);
        ch.qos.logback.classic.Level originalLevel = logger.getLevel();
        logger.setLevel(ch.qos.logback.classic.Level.DEBUG);

        try {
            testRestTemplate.setExchangeResponse(responseEntity);
            Map<String, Object> result = outboundService.fetchResultUsingExchange(uri, typeRef);
            assertNotNull(result);
        } finally {
            logger.setLevel(originalLevel);
        }
    }

    // ==================== Test cases for fetchResultUsingPost ====================

    @Test
    void testFetchResultUsingPost_Success() {
        String uri = "http://test.com/api/post";
        Map<String, Object> request = Map.of("name", "John", "age", 30);
        Map<String, String> headers = Map.of("Authorization", "Bearer token123");
        Map<String, Object> mockResponse = Map.of("status", "success", "id", 123);

        testRestTemplate.setPostForObjectResponse(mockResponse);

        Map<String, Object> result = outboundService.fetchResultUsingPost(uri, request, headers);

        assertNotNull(result);
        assertEquals("success", result.get("status"));
        assertEquals(123, result.get("id"));
    }

    @Test
    void testFetchResultUsingPost_SuccessWithoutHeaders() {
        String uri = "http://test.com/api/post";
        Map<String, Object> request = Map.of("data", "test");
        Map<String, Object> mockResponse = Map.of("result", "ok");

        testRestTemplate.setPostForObjectResponse(mockResponse);

        Map<String, Object> result = outboundService.fetchResultUsingPost(uri, request, null);

        assertNotNull(result);
        assertEquals("ok", result.get("result"));
    }

    @Test
    void testFetchResultUsingPost_SuccessWithEmptyHeaders() {
        String uri = "http://test.com/api/post";
        Map<String, Object> request = Map.of("data", "test");
        Map<String, String> emptyHeaders = Map.of();
        Map<String, Object> mockResponse = Map.of("result", "ok");

        testRestTemplate.setPostForObjectResponse(mockResponse);

        Map<String, Object> result = outboundService.fetchResultUsingPost(uri, request, emptyHeaders);

        assertNotNull(result);
        assertEquals("ok", result.get("result"));
    }

    @Test
    void testFetchResultUsingPost_SuccessWithMultipleHeaders() {
        String uri = "http://test.com/api/post";
        Map<String, Object> request = Map.of("data", "test");
        Map<String, String> headers = Map.of(
            "Authorization", "Bearer token123",
            "X-Request-Id", "12345",
            "X-Custom-Header", "custom-value"
        );
        Map<String, Object> mockResponse = Map.of("result", "ok");

        testRestTemplate.setPostForObjectResponse(mockResponse);

        Map<String, Object> result = outboundService.fetchResultUsingPost(uri, request, headers);

        assertNotNull(result);
        assertEquals("ok", result.get("result"));
    }

    @Test
    void testFetchResultUsingPost_HttpStatusCodeException_ValidJson() throws Exception {
        String uri = "http://test.com/api/post";
        Map<String, Object> request = Map.of("data", "test");
        Map<String, String> headers = Map.of("Authorization", "Bearer token");
        Map<String, Object> errorMap = Map.of("error", "Bad Request", "code", 400);
        String errorJson = objectMapper.writeValueAsString(errorMap);

        HttpClientErrorException exception = HttpClientErrorException.create(
                HttpStatus.BAD_REQUEST, "Bad Request", new HttpHeaders(),
                errorJson.getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8);

        testRestTemplate.setExceptionToThrow(exception);

        Map<String, Object> result = outboundService.fetchResultUsingPost(uri, request, headers);

        assertNotNull(result);
        assertEquals("Bad Request", result.get("error"));
        assertEquals(400, result.get("code"));
    }

    @Test
    void testFetchResultUsingPost_HttpStatusCodeException_InvalidJson() {
        String uri = "http://test.com/api/post";
        Map<String, Object> request = Map.of("data", "test");
        Map<String, String> headers = Map.of("Authorization", "Bearer token");
        String invalidJson = "<html><body>Error</body></html>";

        HttpClientErrorException exception = HttpClientErrorException.create(
                HttpStatus.INTERNAL_SERVER_ERROR, "Internal Server Error", new HttpHeaders(),
                invalidJson.getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8);

        testRestTemplate.setExceptionToThrow(exception);

        Map<String, Object> result = outboundService.fetchResultUsingPost(uri, request, headers);

        assertNull(result);
    }

    @Test
    void testFetchResultUsingPost_HttpStatusCodeException_EmptyResponse() {
        String uri = "http://test.com/api/post";
        Map<String, Object> request = Map.of("data", "test");

        HttpClientErrorException exception = HttpClientErrorException.create(
                HttpStatus.NOT_FOUND, "Not Found", new HttpHeaders(),
                "".getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8);

        testRestTemplate.setExceptionToThrow(exception);

        Map<String, Object> result = outboundService.fetchResultUsingPost(uri, request, null);

        assertNull(result);
    }

    @Test
    void testFetchResultUsingPost_NullResponse() {
        String uri = "http://test.com/api/post";
        Map<String, Object> request = Map.of("data", "test");

        testRestTemplate.setPostForObjectResponse(null);

        Map<String, Object> result = outboundService.fetchResultUsingPost(uri, request, null);

        assertNull(result);
    }

    @Test
    void testFetchResultUsingPost_WithDebugEnabled() {
        String uri = "http://test.com/api/post";
        Map<String, Object> request = Map.of("name", "Test", "value", 100);
        Map<String, String> headers = Map.of("X-Debug", "true");
        Map<String, Object> mockResponse = Map.of("status", "success");

        // Enable debug logging
        ch.qos.logback.classic.Logger logger = (ch.qos.logback.classic.Logger)
            org.slf4j.LoggerFactory.getLogger(OutboundRequestHandlerServiceImpl.class);
        ch.qos.logback.classic.Level originalLevel = logger.getLevel();
        logger.setLevel(ch.qos.logback.classic.Level.DEBUG);

        try {
            testRestTemplate.setPostForObjectResponse(mockResponse);

            Map<String, Object> result = outboundService.fetchResultUsingPost(uri, request, headers);

            assertNotNull(result);
            assertEquals("success", result.get("status"));
        } finally {
            logger.setLevel(originalLevel);
        }
    }

    @Test
    void testFetchResultUsingPost_ComplexRequestObject() {
        String uri = "http://test.com/api/post";
        Map<String, Object> nestedData = Map.of("nested", "value");
        Map<String, Object> request = Map.of(
            "name", "Test",
            "data", nestedData,
            "list", java.util.List.of(1, 2, 3)
        );
        Map<String, Object> mockResponse = Map.of("processed", true);

        testRestTemplate.setPostForObjectResponse(mockResponse);

        Map<String, Object> result = outboundService.fetchResultUsingPost(uri, request, null);

        assertNotNull(result);
        assertTrue((Boolean) result.get("processed"));
    }

    @Test
    void testFetchResultUsingPost_EmptyRequest() {
        String uri = "http://test.com/api/post";
        Map<String, Object> emptyRequest = Map.of();
        Map<String, Object> mockResponse = Map.of("status", "ok");

        testRestTemplate.setPostForObjectResponse(mockResponse);

        Map<String, Object> result = outboundService.fetchResultUsingPost(uri, emptyRequest, null);

        assertNotNull(result);
        assertEquals("ok", result.get("status"));
    }

    @Test
    void testFetchResultUsingPost_UnexpectedException() {
        String uri = "http://test.com/api/post";
        Map<String, Object> request = Map.of("data", "test");

        testRestTemplate.setExceptionToThrow(new RuntimeException("Unexpected error"));

        Map<String, Object> result = outboundService.fetchResultUsingPost(uri, request, null);

        assertNull(result);
    }

    @Test
    void testFetchResultUsingPatch_Success() {
        String uri = "http://test.com/api/patch";
        Map<String, Object> request = Map.of("name", "John");
        Map<String, String> headers = Map.of("Authorization", "token");
        Map<String, Object> mockResponse = Map.of("updated", true);
        testRestTemplate.setPatchForObjectResponse(mockResponse);
        Map<String, Object> result = outboundService.fetchResultUsingPatch(uri, request, headers);
        assertNotNull(result);
        assertEquals(true, result.get("updated"));
    }

    @Test
    void testFetchResultUsingPatch_SuccessNoHeaders() {
        String uri = "http://test.com/api/patch";
        Map<String, Object> request = Map.of("value", 1);
        Map<String, Object> mockResponse = Map.of("ok", true);
        testRestTemplate.setPatchForObjectResponse(mockResponse);
        Map<String, Object> result = outboundService.fetchResultUsingPatch(uri, request, null);
        assertNotNull(result);
        assertEquals(true, result.get("ok"));
    }

    @Test
    void testFetchResultUsingPatch_HttpError_ValidJson() throws Exception {
        String uri = "http://test.com/api/patch";
        Map<String, Object> request = Map.of("data", "test");
        Map<String, Object> errorMap = Map.of("error", "Bad Request");
        String errorJson = objectMapper.writeValueAsString(errorMap);
        HttpClientErrorException ex = HttpClientErrorException.create(
                HttpStatus.BAD_REQUEST, "Bad Request", new HttpHeaders(),
                errorJson.getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8);
        testRestTemplate.setExceptionToThrow(ex);
        Map<String, Object> result = outboundService.fetchResultUsingPatch(uri, request, null);
        assertNotNull(result);
        assertEquals("Bad Request", result.get("error"));
    }

    @Test
    void testFetchResultUsingPatch_HttpError_InvalidJson() {
        String uri = "http://test.com/api/patch";
        Map<String, Object> request = Map.of("data", "test");
        String invalid = "<html>err</html>";
        HttpClientErrorException ex = HttpClientErrorException.create(
                HttpStatus.INTERNAL_SERVER_ERROR, "Fail", new HttpHeaders(),
                invalid.getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8);
        testRestTemplate.setExceptionToThrow(ex);
        Map<String, Object> result = outboundService.fetchResultUsingPatch(uri, request, null);
        assertTrue(result.isEmpty());
    }

    @Test
    void testFetchResultUsingPatch_NullResponse() {
        String uri = "http://test.com/api/patch";
        Map<String, Object> request = Map.of("data", 123);
        testRestTemplate.setPostForObjectResponse(null);
        Map<String, Object> result = outboundService.fetchResultUsingPatch(uri, request, null);
        assertTrue(result.isEmpty());
    }

    @Test
    void testFetchResultUsingPatch_WithDebugEnabled() {
        String uri = "http://test.com/api/patch";
        Map<String, Object> request = Map.of("name", "John");
        Map<String, Object> mockResponse = Map.of("updated", true);

        ch.qos.logback.classic.Logger logger = (ch.qos.logback.classic.Logger)
            org.slf4j.LoggerFactory.getLogger(OutboundRequestHandlerServiceImpl.class);
        ch.qos.logback.classic.Level originalLevel = logger.getLevel();
        logger.setLevel(ch.qos.logback.classic.Level.DEBUG);

        try {
            testRestTemplate.setPatchForObjectResponse(mockResponse);
            Map<String, Object> result = outboundService.fetchResultUsingPatch(uri, request, null);
            assertNotNull(result);
            assertEquals(true, result.get("updated"));
        } finally {
            logger.setLevel(originalLevel);
        }
    }

    // A bean whose getter always throws, used to force a JsonProcessingException
    // out of ObjectMapper#writeValueAsString during debug-logging serialization.
    private static class ThrowingBean {
        public String getValue() {
            throw new RuntimeException("boom");
        }
    }

    @Test
    void testFetchResultUsingPost_JsonProcessingException() {
        String uri = "http://test.com/api/post";
        ThrowingBean request = new ThrowingBean();

        ch.qos.logback.classic.Logger logger = (ch.qos.logback.classic.Logger)
            org.slf4j.LoggerFactory.getLogger(OutboundRequestHandlerServiceImpl.class);
        ch.qos.logback.classic.Level originalLevel = logger.getLevel();
        logger.setLevel(ch.qos.logback.classic.Level.DEBUG);

        try {
            Map<String, Object> result = outboundService.fetchResultUsingPost(uri, request, null);
            assertNull(result);
        } finally {
            logger.setLevel(originalLevel);
        }
    }

    // ==================== Test cases for fetchResultUsingGet ====================

    @Test
    void testFetchResultUsingGet_Success_WithHeaders() {
        String uri = "http://test.com/api/get";
        Map<String, String> headers = Map.of("Authorization", "Bearer token");
        Map<String, Object> mockResponse = Map.of("key", "value");
        ResponseEntity<Map<String, Object>> responseEntity = new ResponseEntity<>(mockResponse, HttpStatus.OK);
        testRestTemplate.setExchangeResponse(responseEntity);

        Map<String, Object> result = outboundService.fetchResultUsingGet(uri, headers);

        assertNotNull(result);
        assertEquals("value", result.get("key"));
    }

    @Test
    void testFetchResultUsingGet_Success_NoHeaders() {
        String uri = "http://test.com/api/get";
        Map<String, Object> mockResponse = Map.of("status", "ok");
        ResponseEntity<Map<String, Object>> responseEntity = new ResponseEntity<>(mockResponse, HttpStatus.OK);
        testRestTemplate.setExchangeResponse(responseEntity);

        Map<String, Object> result = outboundService.fetchResultUsingGet(uri, null);

        assertNotNull(result);
        assertEquals("ok", result.get("status"));
    }

    @Test
    void testFetchResultUsingGet_NullBody_ReturnsEmptyMap() {
        String uri = "http://test.com/api/get";
        ResponseEntity<Map<String, Object>> responseEntity = new ResponseEntity<>(null, HttpStatus.OK);
        testRestTemplate.setExchangeResponse(responseEntity);

        Map<String, Object> result = outboundService.fetchResultUsingGet(uri, null);

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void testFetchResultUsingGet_HttpStatusCodeException_ValidJson() throws Exception {
        String uri = "http://test.com/api/get";
        Map<String, Object> errorMap = Map.of("error", "Not Found");
        String errorJson = objectMapper.writeValueAsString(errorMap);
        HttpClientErrorException exception = HttpClientErrorException.create(
                HttpStatus.NOT_FOUND, "Not Found", new HttpHeaders(),
                errorJson.getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8);
        testRestTemplate.setExceptionToThrow(exception);

        Map<String, Object> result = outboundService.fetchResultUsingGet(uri, null);

        assertNotNull(result);
        assertEquals("Not Found", result.get("error"));
    }

    @Test
    void testFetchResultUsingGet_HttpStatusCodeException_InvalidJson() {
        String uri = "http://test.com/api/get";
        String invalidJson = "<html>error</html>";
        HttpClientErrorException exception = HttpClientErrorException.create(
                HttpStatus.INTERNAL_SERVER_ERROR, "Server Error", new HttpHeaders(),
                invalidJson.getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8);
        testRestTemplate.setExceptionToThrow(exception);

        Map<String, Object> result = outboundService.fetchResultUsingGet(uri, null);

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void testFetchResultUsingGet_GenericException() {
        String uri = "http://test.com/api/get";
        testRestTemplate.setExceptionToThrow(new RuntimeException("Connection error"));

        Map<String, Object> result = outboundService.fetchResultUsingGet(uri, null);

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void testFetchResultUsingGet_WithDebugEnabled() {
        String uri = "http://test.com/api/get";
        Map<String, Object> mockResponse = Map.of("key", "value");
        ResponseEntity<Map<String, Object>> responseEntity = new ResponseEntity<>(mockResponse, HttpStatus.OK);

        ch.qos.logback.classic.Logger logger = (ch.qos.logback.classic.Logger)
            org.slf4j.LoggerFactory.getLogger(OutboundRequestHandlerServiceImpl.class);
        ch.qos.logback.classic.Level originalLevel = logger.getLevel();
        logger.setLevel(ch.qos.logback.classic.Level.DEBUG);

        try {
            testRestTemplate.setExchangeResponse(responseEntity);
            Map<String, Object> result = outboundService.fetchResultUsingGet(uri, Map.of("X-Test", "1"));
            assertNotNull(result);
            assertEquals("value", result.get("key"));
        } finally {
            logger.setLevel(originalLevel);
        }
    }

    // ==================== Test cases for fetchResultUsingDelete ====================

    @Test
    void testFetchResultUsingDelete_Success_WithHeaders() {
        String uri = "http://test.com/api/delete";
        Map<String, Object> request = Map.of("id", 1);
        Map<String, String> headers = Map.of("Authorization", "Bearer token");
        Map<String, Object> mockResponse = Map.of("deleted", true);
        ResponseEntity<Map<String, Object>> responseEntity = new ResponseEntity<>(mockResponse, HttpStatus.OK);
        testRestTemplate.setExchangeResponse(responseEntity);

        Map<String, Object> result = outboundService.fetchResultUsingDelete(uri, request, headers);

        assertNotNull(result);
        assertEquals(true, result.get("deleted"));
    }

    @Test
    void testFetchResultUsingDelete_Success_NoHeaders() {
        String uri = "http://test.com/api/delete";
        Map<String, Object> mockResponse = Map.of("deleted", true);
        ResponseEntity<Map<String, Object>> responseEntity = new ResponseEntity<>(mockResponse, HttpStatus.OK);
        testRestTemplate.setExchangeResponse(responseEntity);

        Map<String, Object> result = outboundService.fetchResultUsingDelete(uri, null, null);

        assertNotNull(result);
        assertEquals(true, result.get("deleted"));
    }

    @Test
    void testFetchResultUsingDelete_NullBody_ReturnsNull() {
        String uri = "http://test.com/api/delete";
        ResponseEntity<Map<String, Object>> responseEntity = new ResponseEntity<>(null, HttpStatus.OK);
        testRestTemplate.setExchangeResponse(responseEntity);

        Map<String, Object> result = outboundService.fetchResultUsingDelete(uri, null, null);

        assertNull(result);
    }

    @Test
    void testFetchResultUsingDelete_HttpStatusCodeException_ValidJson() throws Exception {
        String uri = "http://test.com/api/delete";
        Map<String, Object> errorMap = Map.of("error", "Conflict");
        String errorJson = objectMapper.writeValueAsString(errorMap);
        HttpClientErrorException exception = HttpClientErrorException.create(
                HttpStatus.CONFLICT, "Conflict", new HttpHeaders(),
                errorJson.getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8);
        testRestTemplate.setExceptionToThrow(exception);

        Map<String, Object> result = outboundService.fetchResultUsingDelete(uri, null, null);

        assertNotNull(result);
        assertEquals("Conflict", result.get("error"));
    }

    @Test
    void testFetchResultUsingDelete_HttpStatusCodeException_InvalidJson() {
        String uri = "http://test.com/api/delete";
        String invalidJson = "<html>err</html>";
        HttpClientErrorException exception = HttpClientErrorException.create(
                HttpStatus.INTERNAL_SERVER_ERROR, "Fail", new HttpHeaders(),
                invalidJson.getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8);
        testRestTemplate.setExceptionToThrow(exception);

        Map<String, Object> result = outboundService.fetchResultUsingDelete(uri, null, null);

        assertNull(result);
    }

    @Test
    void testFetchResultUsingDelete_GenericException() {
        String uri = "http://test.com/api/delete";
        testRestTemplate.setExceptionToThrow(new RuntimeException("Connection error"));

        Map<String, Object> result = outboundService.fetchResultUsingDelete(uri, null, null);

        assertNull(result);
    }

    @Test
    void testFetchResultUsingDelete_WithDebugEnabled() {
        String uri = "http://test.com/api/delete";
        Map<String, Object> request = Map.of("id", 5);
        Map<String, Object> mockResponse = Map.of("deleted", true);
        ResponseEntity<Map<String, Object>> responseEntity = new ResponseEntity<>(mockResponse, HttpStatus.OK);

        ch.qos.logback.classic.Logger logger = (ch.qos.logback.classic.Logger)
            org.slf4j.LoggerFactory.getLogger(OutboundRequestHandlerServiceImpl.class);
        ch.qos.logback.classic.Level originalLevel = logger.getLevel();
        logger.setLevel(ch.qos.logback.classic.Level.DEBUG);

        try {
            testRestTemplate.setExchangeResponse(responseEntity);
            Map<String, Object> result = outboundService.fetchResultUsingDelete(uri, request, Map.of("X-Test", "1"));
            assertNotNull(result);
            assertEquals(true, result.get("deleted"));
        } finally {
            logger.setLevel(originalLevel);
        }
    }

    // ==================== Double-failure (nested catch) coverage ====================
    // These exercise the inner "FAILED_TO_PARSE_ERROR_RESPONSE" catch blocks that run
    // when the already-populated response object itself fails Jackson serialization
    // during debug logging, and then fails again on the retry inside the catch block.

    @Test
    void testFetchResultUsingPost_ResponseSerializationDoubleFailure_LogsParseFailure() {
        String uri = "http://test.com/api/post";
        Map<String, Object> request = Map.of("name", "Test");
        Map<String, Object> badResponse = new java.util.HashMap<>();
        badResponse.put("bad", new ThrowingBean());

        ch.qos.logback.classic.Logger logger = (ch.qos.logback.classic.Logger)
            org.slf4j.LoggerFactory.getLogger(OutboundRequestHandlerServiceImpl.class);
        ch.qos.logback.classic.Level originalLevel = logger.getLevel();
        logger.setLevel(ch.qos.logback.classic.Level.DEBUG);

        try {
            testRestTemplate.setPostForObjectResponse(badResponse);
            Map<String, Object> result = outboundService.fetchResultUsingPost(uri, request, null);
            assertNotNull(result);
            assertTrue(result.containsKey("bad"));
        } finally {
            logger.setLevel(originalLevel);
        }
    }

    @Test
    void testFetchResultUsingGet_ResponseSerializationDoubleFailure_LogsParseFailure() {
        String uri = "http://test.com/api/get";
        Map<String, Object> badBody = new java.util.HashMap<>();
        badBody.put("bad", new ThrowingBean());
        ResponseEntity<Map<String, Object>> responseEntity = new ResponseEntity<>(badBody, HttpStatus.OK);

        ch.qos.logback.classic.Logger logger = (ch.qos.logback.classic.Logger)
            org.slf4j.LoggerFactory.getLogger(OutboundRequestHandlerServiceImpl.class);
        ch.qos.logback.classic.Level originalLevel = logger.getLevel();
        logger.setLevel(ch.qos.logback.classic.Level.DEBUG);

        try {
            testRestTemplate.setExchangeResponse(responseEntity);
            Map<String, Object> result = outboundService.fetchResultUsingGet(uri, null);
            assertNotNull(result);
            assertTrue(result.containsKey("bad"));
        } finally {
            logger.setLevel(originalLevel);
        }
    }

    @Test
    void testFetchResultUsingDelete_ResponseSerializationDoubleFailure_LogsParseFailure() {
        String uri = "http://test.com/api/delete";
        Map<String, Object> badBody = new java.util.HashMap<>();
        badBody.put("bad", new ThrowingBean());
        ResponseEntity<Map<String, Object>> responseEntity = new ResponseEntity<>(badBody, HttpStatus.OK);

        ch.qos.logback.classic.Logger logger = (ch.qos.logback.classic.Logger)
            org.slf4j.LoggerFactory.getLogger(OutboundRequestHandlerServiceImpl.class);
        ch.qos.logback.classic.Level originalLevel = logger.getLevel();
        logger.setLevel(ch.qos.logback.classic.Level.DEBUG);

        try {
            testRestTemplate.setExchangeResponse(responseEntity);
            Map<String, Object> result = outboundService.fetchResultUsingDelete(uri, null, null);
            assertNotNull(result);
            assertTrue(result.containsKey("bad"));
        } finally {
            logger.setLevel(originalLevel);
        }
    }

}