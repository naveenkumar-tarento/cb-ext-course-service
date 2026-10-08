package com.igot.cb.service;

import com.igot.cb.cassandra.CassandraOperation;
import com.igot.cb.cassandra.exceptions.CustomException;
import com.igot.cb.model.ApiResponse;
import com.igot.cb.util.AccessTokenValidator;
import com.igot.cb.util.Constants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ContentStateServiceImplTest {

    @Mock
    private CassandraOperation cassandraOperation;

    @Mock
    private AccessTokenValidator accessTokenValidator;

    @InjectMocks
    private ContentStateServiceImpl service;

    @BeforeEach
    void setup() {
        // Set required fields via reflection (since @Value is not injected in unit tests)
        TestUtils.setField(service, "allowedFieldsConfig", "userId,contentId,lastAccessTime,lastCompletedTime,lastUpdatedTime,progress,progressdetails,status,completionPercentage");
        TestUtils.setField(service, "requiredFieldsConfig", "contentId,status,completionPercentage");
    }

    @Test
    void testReadContentState_success() {
        Map<String, Object> requestMap = new HashMap<>();
        requestMap.put(Constants.CONTENT_IDS, List.of("c1"));
        requestMap.put(Constants.FIELDS, List.of("userId", "contentId", "progress"));
        Map<String, Object> requestBody = Map.of(Constants.REQUEST, requestMap);

        when(accessTokenValidator.fetchUserIdFromAccessToken(anyString(), any())).thenReturn("user-1");
        when(cassandraOperation.getRecordsByProperties(any(), any(), any(), any(), any()))
                .thenReturn(List.of(Map.of(Constants.USER_ID_LOWER_CASE, "user-1", Constants.RESOURCE_ID, "c1", Constants.PROGRESS, 50)));

        ApiResponse response = service.readContentState(requestBody, "token");
        assertEquals(HttpStatus.OK, response.getResponseCode());
        assertTrue(response.getResult().containsKey(Constants.CONTENT_LIST));
    }

    @Test
    void testReadContentState_emptyRequestBody() {
        when(accessTokenValidator.fetchUserIdFromAccessToken(anyString(), any())).thenReturn("user-1");
        ApiResponse response = service.readContentState(Collections.emptyMap(), "token");
        assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
    }

    @Test
    void testReadContentState_invalidRequestObject() {
        Map<String, Object> requestBody = Map.of(Constants.REQUEST, "notAMap");
        when(accessTokenValidator.fetchUserIdFromAccessToken(anyString(), any())).thenReturn("user-1");
        ApiResponse response = service.readContentState(requestBody, "token");
        assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
    }

    @Test
    void testReadContentState_missingContentIds() {
        Map<String, Object> requestMap = new HashMap<>();
        Map<String, Object> requestBody = Map.of(Constants.REQUEST, requestMap);
        when(accessTokenValidator.fetchUserIdFromAccessToken(anyString(), any())).thenReturn("user-1");
        ApiResponse response = service.readContentState(requestBody, "token");
        assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
    }

    @Test
    void testReadContentState_invalidFields() {
        Map<String, Object> requestMap = new HashMap<>();
        requestMap.put(Constants.CONTENT_IDS, List.of("c1"));
        requestMap.put(Constants.FIELDS, List.of("invalidField"));
        Map<String, Object> requestBody = Map.of(Constants.REQUEST, requestMap);

        when(accessTokenValidator.fetchUserIdFromAccessToken(anyString(), any())).thenReturn("user-1");
        ApiResponse response = service.readContentState(requestBody, "token");
        assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
    }

    @Test
    void testReadContentState_invalidToken() {
        Map<String, Object> requestMap = new HashMap<>();
        requestMap.put(Constants.CONTENT_IDS, List.of("c1"));
        Map<String, Object> requestBody = Map.of(Constants.REQUEST, requestMap);

        when(accessTokenValidator.fetchUserIdFromAccessToken(anyString(), any())).thenReturn("");
        ApiResponse response = service.readContentState(requestBody, "token");
        assertEquals(HttpStatus.OK, response.getResponseCode()); // Fix: expect OK, not null
    }

    @Test
    void testReadContentState_exception() {
        Map<String, Object> requestMap = new HashMap<>();
        requestMap.put(Constants.CONTENT_IDS, List.of("c1"));
        Map<String, Object> requestBody = Map.of(Constants.REQUEST, requestMap);

        when(accessTokenValidator.fetchUserIdFromAccessToken(anyString(), any())).thenReturn("user-1");
        when(cassandraOperation.getRecordsByProperties(any(), any(), any(), any(), any()))
                .thenThrow(new RuntimeException("DB error"));

        ApiResponse response = service.readContentState(requestBody, "token");
        assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
    }

    @Test
    void testUpdateContentState_success() {
        Map<String, Object> content = new HashMap<>();
        content.put(Constants.CONTENT_ID, "c1");
        content.put(Constants.STATUS, 2);
        content.put(Constants.COMPLETION_PERCENTAGE, 100);
        Map<String, Object> requestMap = new HashMap<>();
        requestMap.put(Constants.CONTENTS, List.of(content));
        Map<String, Object> requestBody = Map.of(Constants.REQUEST, requestMap);

        when(accessTokenValidator.fetchUserIdFromAccessToken(anyString(), any())).thenReturn("user-1");
        when(cassandraOperation.getRecordsByProperties(any(), any(), any(), any(), any()))
                .thenReturn(Collections.emptyList());

        ApiResponse response = service.updateContentState(requestBody, "token");
        assertEquals(HttpStatus.OK, response.getResponseCode()); // Not set in success path
        assertEquals(Constants.SUCCESS, response.getResult().get("c1"));
    }

    @Test
    void testUpdateContentState_emptyRequestBody() {
        when(accessTokenValidator.fetchUserIdFromAccessToken(anyString(), any())).thenReturn("user-1");
        ApiResponse response = service.updateContentState(Collections.emptyMap(), "token");
        assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
    }

    @Test
    void testUpdateContentState_invalidPayload() {
        Map<String, Object> requestBody = Map.of(Constants.REQUEST, "notAMap");
        when(accessTokenValidator.fetchUserIdFromAccessToken(anyString(), any())).thenReturn("user-1");
        ApiResponse response = service.updateContentState(requestBody, "token");
        assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
    }

    @Test
    void testUpdateContentState_invalidToken() {
        Map<String, Object> requestMap = new HashMap<>();
        requestMap.put(Constants.CONTENTS, List.of());
        Map<String, Object> requestBody = Map.of(Constants.REQUEST, requestMap);

        when(accessTokenValidator.fetchUserIdFromAccessToken(anyString(), any())).thenReturn("");
        ApiResponse response = service.updateContentState(requestBody, "token");
        assertEquals(HttpStatus.OK, response.getResponseCode());
    }

    @Test
    void testUpdateContentState_exception() {
        Map<String, Object> content = new HashMap<>();
        content.put(Constants.CONTENT_ID, "c1");
        content.put(Constants.STATUS, 2);
        content.put(Constants.COMPLETION_PERCENTAGE, 100);
        Map<String, Object> requestMap = new HashMap<>();
        requestMap.put(Constants.CONTENTS, List.of(content));
        Map<String, Object> requestBody = Map.of(Constants.REQUEST, requestMap);

        when(accessTokenValidator.fetchUserIdFromAccessToken(anyString(), any())).thenReturn("user-1");
        when(cassandraOperation.getRecordsByProperties(any(), any(), any(), any(), any()))
                .thenThrow(new RuntimeException("DB error"));

        ApiResponse response = service.updateContentState(requestBody, "token");
        assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
    }

    @Test
    void testValidateContentStateUpdatePayload_missingFields() {
        Map<String, Object> requestBody = Map.of(Constants.REQUEST, Map.of(Constants.CONTENTS, List.of(Map.of())));
        String result = TestUtils.invokePrivate(service, "validateContentStateUpdatePayload", Map.class, requestBody);
        assertTrue(result.contains("Missing or invalid fields"));
    }

    @Test
    void testProcessContentConsumption_newContent_completed() throws Exception {
        Map<String, Object> inputContent = new HashMap<>();
        inputContent.put(Constants.STATUS, 2);
        inputContent.put(Constants.COMPLETION_PERCENTAGE, 100);
        inputContent.put(Constants.CONTENT_ID, "c1");
        inputContent.put(Constants.LAST_COMPLETED_TIME, "2024-06-01 10:00:00:000+0000");
        inputContent.put(Constants.LAST_ACCESS_TIME, "2024-06-01 09:00:00:000+0000");
        Map<String, Object> result = service.processContentConsumption(inputContent, null, "user-1");
        assertEquals(100.0, result.get(Constants.COMPLETION_PERCENTAGE));
        assertEquals(2, result.get(Constants.STATUS));
        assertEquals("user-1", result.get(Constants.USER_ID));
    }

    @Test
    void testProcessContentConsumption_existingContent_incomplete() throws Exception {
        Map<String, Object> inputContent = new HashMap<>();
        inputContent.put(Constants.STATUS, 1);
        inputContent.put(Constants.COMPLETION_PERCENTAGE, 50);
        inputContent.put(Constants.CONTENT_ID, "c1");
        inputContent.put(Constants.LAST_COMPLETED_TIME, "2024-06-01 10:00:00:000+0000");
        inputContent.put(Constants.LAST_ACCESS_TIME, "2024-06-01 09:00:00:000+0000");

        Map<String, Object> existingContent = new HashMap<>();
        existingContent.put(Constants.STATUS, 2);
        existingContent.put(Constants.COMPLETION_PERCENTAGE, 100);
        existingContent.put(Constants.LAST_COMPLETED_TIME, "2024-06-01 08:00:00:000+0000");
        existingContent.put(Constants.LAST_ACCESS_TIME, "2024-06-01 07:00:00:000+0000");
        existingContent.put(Constants.PROGRESS, 80);

        Map<String, Object> result = service.processContentConsumption(inputContent, existingContent, "user-1");
        assertEquals(2, result.get(Constants.STATUS));
        assertEquals(80, result.get(Constants.PROGRESS));
    }

    @Test
    void testProcessContentConsumption_invalidCompletionPercentageType() {
        Map<String, Object> inputContent = new HashMap<>();
        inputContent.put(Constants.STATUS, 1);
        inputContent.put(Constants.COMPLETION_PERCENTAGE, "notANumber");
        inputContent.put(Constants.CONTENT_ID, "c1");
        assertThrows(CustomException.class, () -> service.processContentConsumption(inputContent, null, "user-1"));
    }

    @Test
    void testProcessContentConsumption_invalidCompletionPercentageValue() {
        Map<String, Object> inputContent = new HashMap<>();
        inputContent.put(Constants.STATUS, 1);
        inputContent.put(Constants.COMPLETION_PERCENTAGE, 200);
        inputContent.put(Constants.CONTENT_ID, "c1");
        assertThrows(CustomException.class, () -> service.processContentConsumption(inputContent, null, "user-1"));
    }

    @Test
    void testParseDate_validAndInvalid() {
        Date date = service.parseDate("2024-06-01 10:00:00:000+0000");
        assertNotNull(date);
        assertNull(service.parseDate("invalid-date"));
        assertNull(service.parseDate(null));
        assertNull(service.parseDate("null"));
    }

    @Test
    void testMapPayloadToCassandraColumns() {
        Map<String, Object> payload = Map.of("foo", "bar");
        Map<String, String> mapping = Map.of("foo", "baz");
        Map<String, Object> result = ContentStateServiceImpl.mapPayloadToCassandraColumns(payload, mapping);
        assertEquals("bar", result.get("baz"));
    }

    @Test
    void testConvertInstantsToString_singleInstant() {
        Instant now = Instant.now();
        Object result = invokeConvert(now);

        assertTrue(result instanceof String);
        assertEquals(now.toString(), result);
    }

    @Test
    void testConvertInstantsToString_mapWithInstant() {
        Instant now = Instant.now();
        Map<String, Object> input = Map.of("time", now);

        Object result = invokeConvert(input);

        assertTrue(result instanceof Map);
        assertEquals(now.toString(), ((Map<?, ?>) result).get("time"));
    }

    @Test
    void testConvertInstantsToString_listWithInstant() {
        Instant now = Instant.now();
        List<Object> input = List.of(now, "test");

        Object result = invokeConvert(input);

        assertTrue(result instanceof List);
        assertEquals(now.toString(), ((List<?>) result).get(0));
        assertEquals("test", ((List<?>) result).get(1));
    }

    @Test
    void testConvertInstantsToString_nestedMapAndList() {
        Instant now = Instant.now();
        Map<String, Object> input = Map.of(
                "list", List.of(Map.of("time", now))
        );

        Object result = invokeConvert(input);

        assertEquals(
                now.toString(),
                ((Map<?, ?>) ((List<?>) ((Map<?, ?>) result).get("list")).get(0)).get("time")
        );
    }

    @Test
    void testConvertInstantsToString_nonInstantValue() {
        String value = "not a time";
        Object result = invokeConvert(value);

        assertSame(value, result);
    }

    private Object invokeConvert(Object value) {
        // Call the private static method using ReflectionTestUtils
        return ReflectionTestUtils.invokeMethod(
                ContentStateServiceImpl.class,
                "convertInstantsToString",
                value
        );
    }

    // ==================== Additional tests: readContentState branches ====================

    @Test
    void testReadContentState_noFieldsKey() {
        Map<String, Object> requestMap = new HashMap<>();
        requestMap.put(Constants.CONTENT_IDS, List.of("c1"));
        Map<String, Object> requestBody = Map.of(Constants.REQUEST, requestMap);

        when(accessTokenValidator.fetchUserIdFromAccessToken(anyString(), any())).thenReturn("user-1");
        when(cassandraOperation.getRecordsByProperties(any(), any(), any(), any(), any()))
                .thenReturn(List.of(Map.of(Constants.USER_ID_LOWER_CASE, "user-1", Constants.RESOURCE_ID, "c1")));

        ApiResponse response = service.readContentState(requestBody, "token");
        assertEquals(HttpStatus.OK, response.getResponseCode());
        assertTrue(response.getResult().containsKey(Constants.CONTENT_LIST));
    }

    @Test
    void testReadContentState_contentIdsEmptyList() {
        Map<String, Object> requestMap = new HashMap<>();
        requestMap.put(Constants.CONTENT_IDS, List.of());
        Map<String, Object> requestBody = Map.of(Constants.REQUEST, requestMap);

        when(accessTokenValidator.fetchUserIdFromAccessToken(anyString(), any())).thenReturn("user-1");
        ApiResponse response = service.readContentState(requestBody, "token");
        assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
    }

    @Test
    void testReadContentState_progressDetailsValidJson() {
        Map<String, Object> requestMap = new HashMap<>();
        requestMap.put(Constants.CONTENT_IDS, List.of("c1"));
        Map<String, Object> requestBody = Map.of(Constants.REQUEST, requestMap);

        when(accessTokenValidator.fetchUserIdFromAccessToken(anyString(), any())).thenReturn("user-1");
        when(cassandraOperation.getRecordsByProperties(any(), any(), any(), any(), any()))
                .thenReturn(List.of(Map.of(
                        Constants.USER_ID_LOWER_CASE, "user-1",
                        Constants.RESOURCE_ID, "c1",
                        Constants.PROGRESSDETAILS, "{\"pct\":50}")));

        ApiResponse response = service.readContentState(requestBody, "token");
        assertEquals(HttpStatus.OK, response.getResponseCode());

        List<?> list = (List<?>) response.getResult().get(Constants.CONTENT_LIST);
        Map<?, ?> rec = (Map<?, ?>) list.get(0);
        Object pd = rec.get(Constants.PROGRESSDETAILS);
        assertTrue(pd instanceof Map);
        assertTrue(((Map<?, ?>) pd).containsKey("pct"));
    }

    @Test
    void testReadContentState_progressDetailsInvalidJson() {
        Map<String, Object> requestMap = new HashMap<>();
        requestMap.put(Constants.CONTENT_IDS, List.of("c1"));
        Map<String, Object> requestBody = Map.of(Constants.REQUEST, requestMap);

        when(accessTokenValidator.fetchUserIdFromAccessToken(anyString(), any())).thenReturn("user-1");
        when(cassandraOperation.getRecordsByProperties(any(), any(), any(), any(), any()))
                .thenReturn(List.of(Map.of(
                        Constants.USER_ID_LOWER_CASE, "user-1",
                        Constants.RESOURCE_ID, "c1",
                        Constants.PROGRESSDETAILS, "not-valid-json")));

        ApiResponse response = service.readContentState(requestBody, "token");
        assertEquals(HttpStatus.OK, response.getResponseCode());

        List<?> list = (List<?>) response.getResult().get(Constants.CONTENT_LIST);
        Map<?, ?> rec = (Map<?, ?>) list.get(0);
        assertEquals("not-valid-json", rec.get(Constants.PROGRESSDETAILS));
    }

    // ==================== Additional tests: updateContentState branches ====================

    @Test
    void testUpdateContentState_emptyContentsList() {
        Map<String, Object> requestMap = new HashMap<>();
        requestMap.put(Constants.CONTENTS, List.of());
        Map<String, Object> requestBody = Map.of(Constants.REQUEST, requestMap);

        when(accessTokenValidator.fetchUserIdFromAccessToken(anyString(), any())).thenReturn("user-1");

        ApiResponse response = service.updateContentState(requestBody, "token");
        assertEquals(HttpStatus.OK, response.getResponseCode());
        assertTrue(response.getResult().isEmpty());
    }

    // ==================== Additional tests: validateContentStateUpdatePayload branches ====================

    @Test
    void testValidateContentStateUpdatePayload_requestNotMap() {
        Map<String, Object> requestBody = Map.of(Constants.REQUEST, "notAMap");
        String result = TestUtils.invokePrivate(service, "validateContentStateUpdatePayload", Map.class, requestBody);
        assertTrue(result.contains(Constants.REQUEST));
    }

    @Test
    void testValidateContentStateUpdatePayload_contentsNotList() {
        Map<String, Object> requestBody = Map.of(Constants.REQUEST, Map.of(Constants.CONTENTS, "notAList"));
        String result = TestUtils.invokePrivate(service, "validateContentStateUpdatePayload", Map.class, requestBody);
        assertTrue(result.contains(Constants.CONTENTS));
    }

    @Test
    void testValidateContentStateUpdatePayload_contentsElementNotMap() {
        Map<String, Object> requestBody = Map.of(Constants.REQUEST, Map.of(Constants.CONTENTS, List.of("notAMap")));
        String result = TestUtils.invokePrivate(service, "validateContentStateUpdatePayload", Map.class, requestBody);
        assertTrue(result.contains("contents[0]"));
    }

    @Test
    void testValidateContentStateUpdatePayload_valid() {
        Map<String, Object> content = new HashMap<>();
        content.put(Constants.CONTENT_ID, "c1");
        content.put(Constants.STATUS, 1);
        content.put(Constants.COMPLETION_PERCENTAGE, 10);
        Map<String, Object> requestBody = Map.of(Constants.REQUEST, Map.of(Constants.CONTENTS, List.of(content)));
        String result = TestUtils.invokePrivate(service, "validateContentStateUpdatePayload", Map.class, requestBody);
        assertEquals("", result);
    }

    // ==================== Additional tests: processContentConsumption branches ====================

    @Test
    void testProcessContentConsumption_withProgressDetails() throws Exception {
        Map<String, Object> inputContent = new HashMap<>();
        inputContent.put(Constants.STATUS, 1);
        inputContent.put(Constants.COMPLETION_PERCENTAGE, 50);
        inputContent.put(Constants.CONTENT_ID, "c1");
        inputContent.put(Constants.PROGRESSDETAILS, Map.of("pct", 50));

        Map<String, Object> result = service.processContentConsumption(inputContent, null, "user-1");

        assertEquals("{\"pct\":50}", result.get(Constants.PROGRESSDETAILS));
    }

    @Test
    void testProcessContentConsumption_mergeStatusLessThanTwo() throws Exception {
        Map<String, Object> inputContent = new HashMap<>();
        inputContent.put(Constants.STATUS, 1);
        inputContent.put(Constants.COMPLETION_PERCENTAGE, 30);
        inputContent.put(Constants.CONTENT_ID, "c1");

        Map<String, Object> existingContent = new HashMap<>();
        existingContent.put(Constants.STATUS, 0);
        existingContent.put(Constants.PROGRESS, 10);

        Map<String, Object> result = service.processContentConsumption(inputContent, existingContent, "user-1");

        assertEquals(1, result.get(Constants.STATUS));
        assertEquals(10, result.get(Constants.PROGRESS));
        assertFalse(result.containsKey(Constants.LAST_COMPLETED_TIME));
    }

    @Test
    void testProcessContentConsumption_existingDateAsDateObject() throws Exception {
        Map<String, Object> inputContent = new HashMap<>();
        inputContent.put(Constants.STATUS, 0);
        inputContent.put(Constants.COMPLETION_PERCENTAGE, 10);
        inputContent.put(Constants.CONTENT_ID, "c1");

        Date existingDate = new Date();
        Map<String, Object> existingContent = new HashMap<>();
        existingContent.put(Constants.STATUS, 0);
        existingContent.put(Constants.LAST_ACCESS_TIME, existingDate);

        Map<String, Object> result = service.processContentConsumption(inputContent, existingContent, "user-1");

        assertEquals(existingDate.toInstant(), result.get(Constants.LAST_ACCESS_TIME));
    }

    @Test
    void testProcessContentConsumption_newContent_incomplete_blankDates() throws Exception {
        Map<String, Object> inputContent = new HashMap<>();
        inputContent.put(Constants.STATUS, 1);
        inputContent.put(Constants.COMPLETION_PERCENTAGE, 20);
        inputContent.put(Constants.CONTENT_ID, "c1");

        Map<String, Object> result = service.processContentConsumption(inputContent, null, "user-1");

        assertEquals(0, result.get(Constants.PROGRESS));
        assertTrue(result.get(Constants.LAST_ACCESS_TIME) instanceof Instant);
        assertFalse(result.containsKey(Constants.LAST_COMPLETED_TIME));
    }

    // ==================== Tests for readUserContentConsumptionV2 ====================

    private Map<String, Object> validConsumptionRequestMap() {
        Map<String, Object> requestMap = new HashMap<>();
        requestMap.put(Constants.USER_ID, "user-1");
        requestMap.put(Constants.COURSE_ID, "course-1");
        requestMap.put(Constants.BATCH_ID, "batch-1");
        return requestMap;
    }

    @Test
    void testReadUserContentConsumptionV2_success_withFields() {
        Map<String, Object> requestMap = validConsumptionRequestMap();
        requestMap.put(Constants.FIELDS, List.of("userId", "progress"));
        Map<String, Object> requestBody = Map.of(Constants.REQUEST, requestMap);

        when(cassandraOperation.getRecordsByProperties(any(), any(), any(), any(), any()))
                .thenReturn(List.of(Map.of(Constants.USER_ID, "user-1", Constants.PROGRESS, 50)));

        ApiResponse response = service.readUserContentConsumptionV2(requestBody);

        assertEquals(HttpStatus.OK, response.getResponseCode());
        assertTrue(response.getResult().containsKey(Constants.CONSUMPTION_RECORDS));
    }

    @Test
    void testReadUserContentConsumptionV2_success_noFields() {
        Map<String, Object> requestMap = validConsumptionRequestMap();
        Map<String, Object> requestBody = Map.of(Constants.REQUEST, requestMap);

        when(cassandraOperation.getRecordsByProperties(any(), any(), any(), any(), any()))
                .thenReturn(Collections.emptyList());

        ApiResponse response = service.readUserContentConsumptionV2(requestBody);

        assertEquals(HttpStatus.OK, response.getResponseCode());
        assertTrue(response.getResult().containsKey(Constants.CONSUMPTION_RECORDS));
    }

    @Test
    void testReadUserContentConsumptionV2_emptyRequestBody() {
        ApiResponse response = service.readUserContentConsumptionV2(Collections.emptyMap());
        assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
    }

    @Test
    void testReadUserContentConsumptionV2_invalidRequestObject() {
        Map<String, Object> requestBody = Map.of(Constants.REQUEST, "notAMap");
        ApiResponse response = service.readUserContentConsumptionV2(requestBody);
        assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
    }

    @Test
    void testReadUserContentConsumptionV2_missingMandatoryField() {
        Map<String, Object> requestMap = new HashMap<>();
        requestMap.put(Constants.USER_ID, "user-1");
        requestMap.put(Constants.COURSE_ID, "course-1");
        // batchId intentionally omitted
        Map<String, Object> requestBody = Map.of(Constants.REQUEST, requestMap);

        ApiResponse response = service.readUserContentConsumptionV2(requestBody);
        assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
    }

    @Test
    void testReadUserContentConsumptionV2_exception() {
        Map<String, Object> requestMap = validConsumptionRequestMap();
        Map<String, Object> requestBody = Map.of(Constants.REQUEST, requestMap);

        when(cassandraOperation.getRecordsByProperties(any(), any(), any(), any(), any()))
                .thenThrow(new RuntimeException("DB error"));

        ApiResponse response = service.readUserContentConsumptionV2(requestBody);
        assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
    }

    @Test
    void testReadUserContentConsumptionV2_transformRecord_validJson() {
        Map<String, Object> requestMap = validConsumptionRequestMap();
        Map<String, Object> requestBody = Map.of(Constants.REQUEST, requestMap);

        when(cassandraOperation.getRecordsByProperties(any(), any(), any(), any(), any()))
                .thenReturn(List.of(Map.of(Constants.PROGRESSDETAILS, "{\"done\":true}")));

        ApiResponse response = service.readUserContentConsumptionV2(requestBody);

        assertEquals(HttpStatus.OK, response.getResponseCode());
        List<?> list = (List<?>) response.getResult().get(Constants.CONSUMPTION_RECORDS);
        Map<?, ?> rec = (Map<?, ?>) list.get(0);
        assertTrue(rec.get(Constants.PROGRESSDETAILS) instanceof Map);
    }

    @Test
    void testReadUserContentConsumptionV2_transformRecord_invalidJson() {
        Map<String, Object> requestMap = validConsumptionRequestMap();
        Map<String, Object> requestBody = Map.of(Constants.REQUEST, requestMap);

        when(cassandraOperation.getRecordsByProperties(any(), any(), any(), any(), any()))
                .thenReturn(List.of(Map.of(Constants.PROGRESSDETAILS, "not-valid-json")));

        ApiResponse response = service.readUserContentConsumptionV2(requestBody);

        assertEquals(HttpStatus.OK, response.getResponseCode());
        List<?> list = (List<?>) response.getResult().get(Constants.CONSUMPTION_RECORDS);
        Map<?, ?> rec = (Map<?, ?>) list.get(0);
        assertEquals("not-valid-json", rec.get(Constants.PROGRESSDETAILS));
    }
}
