package com.igot.cb.consumer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.igot.cb.cassandra.CassandraOperation;
import com.igot.cb.cassandra.exceptions.CustomException;
import com.igot.cb.model.ApiRespParam;
import com.igot.cb.model.ApiResponse;
import com.igot.cb.service.ContentInfoServiceImpl;
import com.igot.cb.service.NotificationService;
import com.igot.cb.service.OutboundRequestHandlerServiceImpl;
import com.igot.cb.service.impl.ExternalTrainingCertificateServiceImpl;
import com.igot.cb.storage.service.StorageService;
import com.igot.cb.util.CbExtServerProperties;
import com.igot.cb.util.Constants;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ExternalTrainingBulkUploadConsumerTest {

    private ExternalTrainingBulkUploadConsumer consumer;

    @Mock
    private CassandraOperation cassandraOperation;

    @Mock
    private StorageService storageService;

    @Mock
    private OutboundRequestHandlerServiceImpl outboundService;

    @Mock
    private CbExtServerProperties props;

    @Mock
    private NotificationService notificationService;

    @Mock
    private ExternalTrainingCertificateServiceImpl certService;

    @Mock
    private ContentInfoServiceImpl contentInfoService;

    @BeforeEach
    void setup() throws Exception {

        consumer = Mockito.spy(new ExternalTrainingBulkUploadConsumer(notificationService, props, cassandraOperation,
                storageService, outboundService, certService, contentInfoService));
        lenient().when(props.getLocalBasePath()).thenReturn(Constants.LOCAL_BASE_PATH);

        inject("objectMapper", new ObjectMapper());
    }

    private void inject(String field, Object value) throws Exception {
        var f = ExternalTrainingBulkUploadConsumer.class.getDeclaredField(field);
        f.setAccessible(true);
        f.set(consumer, value);
    }

    private Object invokePrivate(String method, Class<?>[] types, Object... args) throws Exception {
        Method m = ExternalTrainingBulkUploadConsumer.class.getDeclaredMethod(method, types);
        m.setAccessible(true);
        return m.invoke(consumer, args);
    }

    // ===========================
    // PRIVATE METHODS
    // ===========================

    @Test
    void testCleanHeaders() throws Exception {
        List<String> headers = new ArrayList<>(List.of("\"Email\"", "\"Name\""));
        invokePrivate("cleanHeaders", new Class[]{List.class}, headers);

        assertEquals("Email", headers.get(0));
        assertEquals("Name", headers.get(1));
    }

    @Test
    void testValidateNotNullOrEmpty_valid() {
        Map<String, Object> map = Map.of("key", "value");

        assertDoesNotThrow(() ->
                invokePrivate("validateNotNullOrEmpty", new Class[]{Map.class}, map)
        );
    }

    @Test
    void testValidateNotNullOrEmpty_null() {
        Map<String, Object> map = new HashMap<>();
        map.put("key", null);

        Exception ex = assertThrows(Exception.class, () ->
                invokePrivate("validateNotNullOrEmpty", new Class[]{Map.class}, map)
        );

        assertTrue(ex.getCause().getMessage().contains("null"));
    }

    // ===========================
    // FINALIZE STATUS
    // ===========================

    @Test
    void testFinalizeStatus_success() throws Exception {

        File file = File.createTempFile("test", ".csv");

        ApiResponse response = new ApiResponse();
        response.setResponseCode(org.springframework.http.HttpStatus.OK);

        when(storageService.uploadFile(any(), any(), any()))
                .thenReturn(response);

        when(props.getExternalTrainingBulkUploadContainerName()).thenReturn("container");
        when(props.getCloudContainerName()).thenReturn("cloud");

        String result = (String) invokePrivate(
                "finalizeStatus",
                new Class[]{int.class, int.class, int.class, File.class},
                10, 10, 0, file
        );

        assertEquals(Constants.SUCCESS, result);
    }

    @Test
    void testFinalizeStatus_failed() throws Exception {

        File file = File.createTempFile("test", ".csv");

        ApiResponse response = new ApiResponse();
        response.setResponseCode(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR);

        ApiRespParam params = new ApiRespParam();
        params.setErrMsg("Upload failed");
        response.setParams(params);

        when(storageService.uploadFile(any(), any(), any()))
                .thenReturn(response);

        when(props.getExternalTrainingBulkUploadContainerName()).thenReturn("container");
        when(props.getCloudContainerName()).thenReturn("cloud");

        String result = (String) invokePrivate(
                "finalizeStatus",
                new Class[]{int.class, int.class, int.class, File.class},
                10, 5, 5, file
        );

        assertEquals(Constants.FAILED, result);
    }

    @Test
    void testFinalizeStatus_uploadSuccessButFailedCountNonZero_returnsFailed() throws Exception {
        File file = File.createTempFile("test", ".csv");

        ApiResponse response = new ApiResponse();
        response.setResponseCode(org.springframework.http.HttpStatus.OK);
        when(storageService.uploadFile(any(), any(), any())).thenReturn(response);
        when(props.getExternalTrainingBulkUploadContainerName()).thenReturn("container");
        when(props.getCloudContainerName()).thenReturn("cloud");

        String result = (String) invokePrivate(
                "finalizeStatus",
                new Class[]{int.class, int.class, int.class, File.class},
                10, 9, 1, file
        );

        assertEquals(Constants.FAILED, result);
    }

    @Test
    void testFinalizeStatus_uploadSuccessButZeroRecords_returnsFailed() throws Exception {
        File file = File.createTempFile("test", ".csv");

        ApiResponse response = new ApiResponse();
        response.setResponseCode(org.springframework.http.HttpStatus.OK);
        when(storageService.uploadFile(any(), any(), any())).thenReturn(response);
        when(props.getExternalTrainingBulkUploadContainerName()).thenReturn("container");
        when(props.getCloudContainerName()).thenReturn("cloud");

        String result = (String) invokePrivate(
                "finalizeStatus",
                new Class[]{int.class, int.class, int.class, File.class},
                0, 0, 0, file
        );

        assertEquals(Constants.FAILED, result);
    }

    // ===========================
    // PROCESS RECORD
    // ===========================

    @Test
    void testProcessRecord_successFlow() throws Exception {

        CSVRecord csvRecord = mock(CSVRecord.class);
        when(csvRecord.size()).thenReturn(1);
        when(csvRecord.get("Email")).thenReturn("test@mail.com");
        when(csvRecord.toMap()).thenReturn(new HashMap<>());

        Map<String, Object> userInfo = Map.of(Constants.USER_ID, "user1");
        Map<String, Object> emailMap = Map.of("test@mail.com", userInfo);

        when(cassandraOperation.getRecordsByProperties(any(), any(), any(), any(), any()))
                .thenReturn(Collections.emptyList());

        ApiResponse success = new ApiResponse();
        success.put(Constants.RESPONSE, Constants.SUCCESS);

        when(cassandraOperation.insertRecord(any(), any(), any()))
                .thenReturn(success);

        doNothing().when(certService)
                .generateCertificateEventAndPushToKafka(any(), any());

        Map<String, Object> eventDetails = new HashMap<>();
        eventDetails.put(Constants.DURATION, 100);
        eventDetails.put(Constants.START_DATE, new Date());
        eventDetails.put(Constants.END_DATE_CAMEL, new Date());

        List<String> notifications = new ArrayList<>();

        Map<String, String> result = (Map<String, String>) invokePrivate(
                "processRecord",
                new Class[]{CSVRecord.class, int.class, String.class, String.class, Map.class, Map.class, List.class},
                csvRecord, 5, "event", "batch", emailMap, eventDetails, notifications
        );

        assertFalse(result.containsKey("Status"));
        assertEquals(1, notifications.size());
    }

    @Test
    void testProcessRecord_invalidEmail() throws Exception {

        CSVRecord csvRecord = mock(CSVRecord.class);
        when(csvRecord.get("Email")).thenReturn("invalid");
        when(csvRecord.toMap()).thenReturn(new HashMap<>());
        when(csvRecord.size()).thenReturn(1);

        Map<String, String> result = (Map<String, String>) invokePrivate(
                "processRecord",
                new Class[]{CSVRecord.class, int.class, String.class, String.class, Map.class, Map.class, List.class},
                csvRecord, 5, "event", "batch", new HashMap<>(), new HashMap<>(), new ArrayList<>()
        );

        assertEquals("FAILED", result.get("Status"));
    }

    // ===========================
    // ERROR CASE
    // ===========================

    @Test
    void testGetUserIdList_nullParser() {

        Exception ex = assertThrows(Exception.class, () ->
                invokePrivate("getUserIdList",
                        new Class[]{org.apache.commons.csv.CSVParser.class, String.class, Map.class},
                        null, "Email", new HashMap<>())
        );

        assertTrue(ex.getCause().getMessage().contains("Invalid input"));
    }

    // ===========================
    // GET USER ID LIST - additional branches
    // ===========================

    private CSVParser buildCsvParser(String content) throws Exception {
        CSVFormat format = CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true).build();
        return CSVParser.parse(content, format);
    }

    @Test
    void testGetUserIdList_columnNotMapped_throwsIOException() throws Exception {
        CSVParser parser = buildCsvParser("Name\nJohn\n");

        Exception ex = assertThrows(Exception.class, () ->
                invokePrivate("getUserIdList",
                        new Class[]{org.apache.commons.csv.CSVParser.class, String.class, Map.class},
                        parser, "Email", new HashMap<>())
        );

        assertTrue(ex.getCause() instanceof IOException);
        assertTrue(ex.getCause().getMessage().contains("Failed to process CSV file"));
    }

    @Test
    void testGetUserIdList_blankEmailSkipped_noUserLookup() throws Exception {
        CSVParser parser = buildCsvParser("Email\n \n");
        Map<String, Object> emailUserMap = new HashMap<>();

        invokePrivate("getUserIdList",
                new Class[]{org.apache.commons.csv.CSVParser.class, String.class, Map.class},
                parser, "Email", emailUserMap);

        assertTrue(emailUserMap.isEmpty());
        verifyNoInteractions(outboundService);
    }

    @Test
    void testGetUserIdList_success_populatesEmailUserMap() throws Exception {
        CSVParser parser = buildCsvParser("Email\na@a.com\n");
        Map<String, Object> emailUserMap = new HashMap<>();

        Map<String, Object> personalDetails = new HashMap<>();
        personalDetails.put(Constants.PRIMARY_EMAIL, "a@a.com");
        Map<String, Object> profileDetails = new HashMap<>();
        profileDetails.put(Constants.PERSONAL_DETAILS, personalDetails);
        Map<String, Object> contentEntry = new HashMap<>();
        contentEntry.put(Constants.ROOT_ORG_ID, "org1");
        contentEntry.put(Constants.FIRSTNAME, "John");
        contentEntry.put(Constants.USER_ID, "user1");
        contentEntry.put(Constants.PROFILE_DETAILS, profileDetails);

        Map<String, Object> innerResponse = new HashMap<>();
        innerResponse.put(Constants.CONTENT, List.of(contentEntry));
        Map<String, Object> resultMap = new HashMap<>();
        resultMap.put(Constants.RESPONSE, innerResponse);
        Map<String, Object> outerResponse = new HashMap<>();
        outerResponse.put(Constants.RESPONSE_CODE, Constants.OK);
        outerResponse.put(Constants.RESULT, resultMap);

        when(props.getSbUrl()).thenReturn("http://sb");
        when(props.getUserSearchEndPoint()).thenReturn("/search");
        when(outboundService.fetchResultUsingPost(anyString(), anyMap(), anyMap())).thenReturn(outerResponse);

        invokePrivate("getUserIdList",
                new Class[]{org.apache.commons.csv.CSVParser.class, String.class, Map.class},
                parser, "Email", emailUserMap);

        assertTrue(emailUserMap.containsKey("a@a.com"));
        @SuppressWarnings("unchecked")
        Map<String, Object> userInfo = (Map<String, Object>) emailUserMap.get("a@a.com");
        assertEquals("user1", userInfo.get(Constants.USER_ID));
    }

    // ===========================
    // GET USER INFO
    // ===========================

    @Test
    void testGetUserInfo_multipleBatches_callsOutboundTwice() throws Exception {
        List<String> values = new ArrayList<>();
        for (int i = 0; i < 150; i++) {
            values.add("user" + i + "@test.com");
        }
        when(props.getSbUrl()).thenReturn("http://sb");
        when(props.getUserSearchEndPoint()).thenReturn("/search");
        when(outboundService.fetchResultUsingPost(anyString(), anyMap(), anyMap())).thenReturn(null);

        Object result = invokePrivate("getUserInfo", new Class[]{String.class, List.class}, "email", values);

        assertTrue(((Map<?, ?>) result).isEmpty());
        verify(outboundService, times(2)).fetchResultUsingPost(anyString(), anyMap(), anyMap());
    }

    @Test
    void testGetUserInfo_exceptionCaught_returnsEmptyMap() throws Exception {
        when(props.getSbUrl()).thenReturn("http://sb");
        when(props.getUserSearchEndPoint()).thenReturn("/search");
        when(outboundService.fetchResultUsingPost(anyString(), anyMap(), anyMap()))
                .thenThrow(new RuntimeException("down"));

        Object result = invokePrivate("getUserInfo", new Class[]{String.class, List.class}, "email", List.of("a@a.com"));

        assertTrue(((Map<?, ?>) result).isEmpty());
    }

    // ===========================
    // POPULATE EMAIL USER MAP FROM RESPONSE
    // ===========================

    @Test
    void testPopulateEmailUserMapFromResponse_nullResponse_noOp() throws Exception {
        Map<String, Object> emailUserMap = new HashMap<>();
        invokePrivate("populateEmailUserMapFromResponse", new Class[]{Map.class, Map.class}, null, emailUserMap);
        assertTrue(emailUserMap.isEmpty());
    }

    @Test
    void testPopulateEmailUserMapFromResponse_notOkResponseCode_noOp() throws Exception {
        Map<String, Object> emailUserMap = new HashMap<>();
        Map<String, Object> response = new HashMap<>();
        response.put(Constants.RESPONSE_CODE, "ERROR");
        invokePrivate("populateEmailUserMapFromResponse", new Class[]{Map.class, Map.class}, response, emailUserMap);
        assertTrue(emailUserMap.isEmpty());
    }

    @Test
    void testPopulateEmailUserMapFromResponse_noResponseKeyInResult_noOp() throws Exception {
        Map<String, Object> emailUserMap = new HashMap<>();
        Map<String, Object> response = new HashMap<>();
        response.put(Constants.RESPONSE_CODE, Constants.OK);
        response.put(Constants.RESULT, new HashMap<>());
        invokePrivate("populateEmailUserMapFromResponse", new Class[]{Map.class, Map.class}, response, emailUserMap);
        assertTrue(emailUserMap.isEmpty());
    }

    @Test
    void testPopulateEmailUserMapFromResponse_noContentList_noOp() throws Exception {
        Map<String, Object> emailUserMap = new HashMap<>();
        Map<String, Object> response = new HashMap<>();
        Map<String, Object> resultMap = new HashMap<>();
        resultMap.put(Constants.RESPONSE, new HashMap<>());
        response.put(Constants.RESPONSE_CODE, Constants.OK);
        response.put(Constants.RESULT, resultMap);
        invokePrivate("populateEmailUserMapFromResponse", new Class[]{Map.class, Map.class}, response, emailUserMap);
        assertTrue(emailUserMap.isEmpty());
    }

    // ===========================
    // ADD USER INFO ENTRY
    // ===========================

    @Test
    void testAddUserInfoEntry_nullProfileDetails_notAdded() throws Exception {
        Map<String, Object> userRecord = new HashMap<>();
        userRecord.put(Constants.USER_ID, "user1");
        Map<String, Object> emailUserMap = new HashMap<>();

        invokePrivate("addUserInfoEntry", new Class[]{Map.class, Map.class}, userRecord, emailUserMap);

        assertTrue(emailUserMap.isEmpty());
    }

    @Test
    void testAddUserInfoEntry_blankPrimaryEmail_notAdded() throws Exception {
        Map<String, Object> userRecord = new HashMap<>();
        Map<String, Object> personalDetails = new HashMap<>();
        personalDetails.put(Constants.PRIMARY_EMAIL, "   ");
        Map<String, Object> profileDetails = new HashMap<>();
        profileDetails.put(Constants.PERSONAL_DETAILS, personalDetails);
        userRecord.put(Constants.PROFILE_DETAILS, profileDetails);
        Map<String, Object> emailUserMap = new HashMap<>();

        invokePrivate("addUserInfoEntry", new Class[]{Map.class, Map.class}, userRecord, emailUserMap);

        assertTrue(emailUserMap.isEmpty());
    }

    @Test
    void testAddUserInfoEntry_profileDetailsPresentButPersonalDetailsNull_notAdded() throws Exception {
        Map<String, Object> userRecord = new HashMap<>();
        Map<String, Object> profileDetails = new HashMap<>();
        // Constants.PERSONAL_DETAILS intentionally absent -> personalDetails resolves to null.
        userRecord.put(Constants.PROFILE_DETAILS, profileDetails);
        userRecord.put(Constants.USER_ID, "user1");
        Map<String, Object> emailUserMap = new HashMap<>();

        invokePrivate("addUserInfoEntry", new Class[]{Map.class, Map.class}, userRecord, emailUserMap);

        assertTrue(emailUserMap.isEmpty());
    }

    @Test
    void testAddUserInfoEntry_nullPrimaryEmail_notAdded() throws Exception {
        Map<String, Object> userRecord = new HashMap<>();
        Map<String, Object> personalDetails = new HashMap<>();
        // Constants.PRIMARY_EMAIL intentionally absent -> primaryEmail resolves to null.
        Map<String, Object> profileDetails = new HashMap<>();
        profileDetails.put(Constants.PERSONAL_DETAILS, personalDetails);
        userRecord.put(Constants.PROFILE_DETAILS, profileDetails);
        userRecord.put(Constants.USER_ID, "user1");
        Map<String, Object> emailUserMap = new HashMap<>();

        invokePrivate("addUserInfoEntry", new Class[]{Map.class, Map.class}, userRecord, emailUserMap);

        assertTrue(emailUserMap.isEmpty());
    }

    @Test
    void testAddUserInfoEntry_success_addsLowercasedTrimmedEmail() throws Exception {
        Map<String, Object> userRecord = new HashMap<>();
        Map<String, Object> personalDetails = new HashMap<>();
        personalDetails.put(Constants.PRIMARY_EMAIL, "  Mixed@Case.com  ");
        Map<String, Object> profileDetails = new HashMap<>();
        profileDetails.put(Constants.PERSONAL_DETAILS, personalDetails);
        userRecord.put(Constants.PROFILE_DETAILS, profileDetails);
        userRecord.put(Constants.USER_ID, "user1");
        Map<String, Object> emailUserMap = new HashMap<>();

        invokePrivate("addUserInfoEntry", new Class[]{Map.class, Map.class}, userRecord, emailUserMap);

        assertTrue(emailUserMap.containsKey("mixed@case.com"));
    }

    // ===========================
    // IS EVENT ENROLMENT EXIST
    // ===========================

    @Test
    void testIsEventEnrolmentExist_notFound_returnsEmptyMap() throws Exception {
        when(cassandraOperation.getRecordsByProperties(any(), any(), any(), isNull(), isNull()))
                .thenReturn(Collections.emptyList());

        Object result = invokePrivate("isEventEnrolmentExist", new Class[]{String.class, String.class}, "user1", "event1");

        assertTrue(((Map<?, ?>) result).isEmpty());
    }

    @Test
    void testIsEventEnrolmentExist_found_returnsRecord() throws Exception {
        Map<String, Object> enrolmentRecord = Map.of("id", "enroll1");
        when(cassandraOperation.getRecordsByProperties(any(), any(), any(), isNull(), isNull()))
                .thenReturn(List.of(enrolmentRecord));

        Object result = invokePrivate("isEventEnrolmentExist", new Class[]{String.class, String.class}, "user1", "event1");

        assertEquals(enrolmentRecord, result);
    }

    // ===========================
    // ENROLL USER
    // ===========================

    @Test
    void testEnrollUser_success_returnsResponseAndInsertsTwice() throws Exception {
        ApiResponse success = new ApiResponse();
        success.put(Constants.RESPONSE, Constants.SUCCESS);
        when(cassandraOperation.insertRecord(any(), any(), any())).thenReturn(success);

        Map<String, Object> eventDetails = new HashMap<>();
        eventDetails.put(Constants.DURATION, 5);
        eventDetails.put(Constants.START_DATE, Instant.now());
        eventDetails.put(Constants.END_DATE_CAMEL, new Date());

        Object result = invokePrivate("enrollUser", new Class[]{String.class, String.class, String.class, Map.class},
                "user1", "event1", "batch1", eventDetails);

        ApiResponse response = (ApiResponse) result;
        assertEquals(Constants.SUCCESS, response.get(Constants.RESPONSE));
        verify(cassandraOperation, times(2)).insertRecord(any(), any(), any());
    }

    @Test
    void testEnrollUser_exception_returnsFailedResponse() throws Exception {
        when(cassandraOperation.insertRecord(any(), any(), any())).thenThrow(new RuntimeException("db error"));

        Object result = invokePrivate("enrollUser", new Class[]{String.class, String.class, String.class, Map.class},
                "user1", "event1", "batch1", new HashMap<>());

        ApiResponse response = (ApiResponse) result;
        assertEquals(Constants.FAILED, response.get(Constants.RESPONSE));
    }

    // ===========================
    // VALIDATE RECEIVED KAFKA MESSAGE
    // ===========================

    @Test
    void testValidateReceivedKafkaMessage_allPresent_noErrors() throws Exception {
        Map<String, String> input = new HashMap<>();
        input.put(Constants.CONTEXT_ID_KEY, "e1");
        input.put(Constants.BATCH_ID, "b1");
        input.put(Constants.FILE_NAME, "f.csv");

        Object result = invokePrivate("validateReceivedKafkaMessage", new Class[]{Map.class}, input);

        assertTrue(((List<?>) result).isEmpty());
    }

    @Test
    void testValidateReceivedKafkaMessage_allMissing_threeErrors() throws Exception {
        Object result = invokePrivate("validateReceivedKafkaMessage", new Class[]{Map.class}, new HashMap<String, String>());

        assertEquals(3, ((List<?>) result).size());
    }

    // ===========================
    // UPLOAD THE UPDATED CSV FILE
    // ===========================

    @Test
    void testUploadTheUpdatedCSVFile_success() throws Exception {
        File file = File.createTempFile("upload", ".csv");
        try {
            ApiResponse resp = new ApiResponse();
            resp.setResponseCode(HttpStatus.OK);
            when(storageService.uploadFile(any(), any(), any())).thenReturn(resp);

            Object result = invokePrivate("uploadTheUpdatedCSVFile", new Class[]{File.class}, file);

            assertEquals(Constants.SUCCESS, result);
        } finally {
            file.delete();
        }
    }

    @Test
    void testUploadTheUpdatedCSVFile_failure() throws Exception {
        File file = File.createTempFile("upload", ".csv");
        try {
            ApiResponse resp = new ApiResponse();
            resp.setResponseCode(HttpStatus.INTERNAL_SERVER_ERROR);
            resp.getParams().setErrMsg("upload failed");
            when(storageService.uploadFile(any(), any(), any())).thenReturn(resp);

            Object result = invokePrivate("uploadTheUpdatedCSVFile", new Class[]{File.class}, file);

            assertEquals(Constants.FAILED, result);
        } finally {
            file.delete();
        }
    }

    // ===========================
    // WRITE UPDATED CSV
    // ===========================

    @Test
    void testWriteUpdatedCSV_writesRowsToFile() throws Exception {
        File file = File.createTempFile("writeUpdated", ".csv");
        try {
            when(props.getBulkUploadCsvDelimiter()).thenReturn(',');

            List<String> headers = List.of("Email", "Status", "Error Details");
            Map<String, String> row = new LinkedHashMap<>();
            row.put("Email", "a@a.com");
            row.put("Status", "FAILED");
            row.put("Error Details", "Empty email");

            invokePrivate("writeUpdatedCSV", new Class[]{File.class, List.class, List.class}, file, headers, List.of(row));

            String content = new String(Files.readAllBytes(file.toPath()), java.nio.charset.StandardCharsets.UTF_8);
            assertTrue(content.contains("a@a.com"));
            assertTrue(content.contains("FAILED"));
        } finally {
            file.delete();
        }
    }

    // ===========================
    // UPDATE USER BULK UPLOAD STATUS (public)
    // ===========================

    @Test
    void testUpdateUserBulkUploadStatus_allFieldsSet() {
        when(props.getExternalTrainingBulkUploadTable()).thenReturn("table1");
        ArgumentCaptor<Map> captor = ArgumentCaptor.forClass(Map.class);

        consumer.updateUserBulkUploadStatus(new ExternalTrainingBulkUploadConsumer.BulkUploadStatusUpdate(
                "org1", "ctx1", "batch1", "id1", "SUCCESS", 5, 5, 0));

        verify(cassandraOperation).updateRecord(eq(Constants.KEYSPACE_SUNBIRD), eq("table1"), captor.capture(), anyMap());
        Map<String, Object> fields = captor.getValue();
        assertEquals("SUCCESS", fields.get(Constants.STATUS));
        assertEquals(5, fields.get(Constants.TOTAL_RECORDS));
        assertEquals(5, fields.get(Constants.SUCCESSFUL_RECORDS_COUNT));
        assertEquals(0, fields.get(Constants.FAILED_RECORDS_COUNT));
    }

    @Test
    void testUpdateUserBulkUploadStatus_blankStatusAndNegativeCounts_fieldsSkipped() {
        when(props.getExternalTrainingBulkUploadTable()).thenReturn("table1");
        ArgumentCaptor<Map> captor = ArgumentCaptor.forClass(Map.class);

        consumer.updateUserBulkUploadStatus(new ExternalTrainingBulkUploadConsumer.BulkUploadStatusUpdate(
                "org1", "ctx1", "batch1", "id1", "", -1, -1, -1));

        verify(cassandraOperation).updateRecord(eq(Constants.KEYSPACE_SUNBIRD), eq("table1"), captor.capture(), anyMap());
        Map<String, Object> fields = captor.getValue();
        assertFalse(fields.containsKey(Constants.STATUS));
        assertFalse(fields.containsKey(Constants.TOTAL_RECORDS));
        assertFalse(fields.containsKey(Constants.SUCCESSFUL_RECORDS_COUNT));
        assertFalse(fields.containsKey(Constants.FAILED_RECORDS_COUNT));
        assertTrue(fields.containsKey(Constants.UPDATE_ON));
    }

    @Test
    void testUpdateUserBulkUploadStatus_exceptionSwallowed() {
        when(props.getExternalTrainingBulkUploadTable()).thenReturn("table1");
        when(cassandraOperation.updateRecord(any(), any(), any(), any())).thenThrow(new RuntimeException("db down"));

        assertDoesNotThrow(() ->
                consumer.updateUserBulkUploadStatus(new ExternalTrainingBulkUploadConsumer.BulkUploadStatusUpdate(
                        "org1", "ctx1", "batch1", "id1", "FAILED", 1, 0, 1)));
    }

    // ===========================
    // GET EVENT DETAILS
    // ===========================

    @Test
    void testGetEventDetails_noBatchDetailsFound_logsAndReturns() throws Exception {
        when(cassandraOperation.getRecordsByProperties(eq(Constants.KEYSPACE_SUNBIRD_COURSE), eq(Constants.EVENT_BATCH_TABLE_NAME), anyMap(), isNull(), isNull()))
                .thenReturn(Collections.emptyList());

        Map<String, Object> eventDetails = new HashMap<>();
        invokePrivate("getEventDetails", new Class[]{String.class, String.class, Map.class}, "event1", "batch1", eventDetails);

        assertEquals("event1", eventDetails.get(Constants.EVENT_ID));
        assertFalse(eventDetails.containsKey(Constants.EVENT_NAME));
    }

    @Test
    void testGetEventDetails_success_populatesAllFields() throws Exception {
        Map<String, Object> batchRow = new HashMap<>();
        batchRow.put("start_date", new Date(1_000_000L));
        batchRow.put("end_date", new Date(2_000_000L));
        batchRow.put(Constants.BATCH_ATTRIBUTES_COLUMN, "{\"duration\":5}");
        when(cassandraOperation.getRecordsByProperties(eq(Constants.KEYSPACE_SUNBIRD_COURSE), eq(Constants.EVENT_BATCH_TABLE_NAME), anyMap(), isNull(), isNull()))
                .thenReturn(List.of(batchRow));

        Map<String, Object> readResponse = new HashMap<>();
        readResponse.put(Constants.NAME, "Training");
        readResponse.put(Constants.CERT_TEMPLATE, "tmpl");
        readResponse.put(Constants.CERT_TEMPLATE_ID, "tmplId");
        readResponse.put(Constants.SOURCE_NAME, "src");
        when(contentInfoService.readEvent("event1")).thenReturn(readResponse);

        Map<String, Object> eventDetails = new HashMap<>();
        invokePrivate("getEventDetails", new Class[]{String.class, String.class, Map.class}, "event1", "batch1", eventDetails);

        assertEquals("Training", eventDetails.get(Constants.EVENT_NAME));
        assertEquals(300L, eventDetails.get(Constants.DURATION));
        assertNotNull(eventDetails.get(Constants.ISSUED_DATE));
        assertTrue(eventDetails.containsKey("ets"));
    }

    @Test
    void testGetEventDetails_readEventEmpty_wrapsCustomException() {
        Map<String, Object> batchRow = new HashMap<>();
        batchRow.put("start_date", new Date());
        batchRow.put("end_date", new Date());
        batchRow.put(Constants.BATCH_ATTRIBUTES_COLUMN, "{\"duration\":5}");
        when(cassandraOperation.getRecordsByProperties(eq(Constants.KEYSPACE_SUNBIRD_COURSE), eq(Constants.EVENT_BATCH_TABLE_NAME), anyMap(), isNull(), isNull()))
                .thenReturn(List.of(batchRow));
        when(contentInfoService.readEvent("event1")).thenReturn(Collections.emptyMap());

        Map<String, Object> eventDetails = new HashMap<>();
        Exception ex = assertThrows(Exception.class, () ->
                invokePrivate("getEventDetails", new Class[]{String.class, String.class, Map.class}, "event1", "batch1", eventDetails));

        assertTrue(ex.getCause() instanceof CustomException);
        assertTrue(ex.getCause().getMessage().contains("readResponse is empty"));
    }

    @Test
    void testGetEventDetails_fetchThrows_wrapsCustomException() {
        when(cassandraOperation.getRecordsByProperties(eq(Constants.KEYSPACE_SUNBIRD_COURSE), eq(Constants.EVENT_BATCH_TABLE_NAME), anyMap(), isNull(), isNull()))
                .thenThrow(new RuntimeException("db down"));

        Map<String, Object> eventDetails = new HashMap<>();
        Exception ex = assertThrows(Exception.class, () ->
                invokePrivate("getEventDetails", new Class[]{String.class, String.class, Map.class}, "event1", "batch1", eventDetails));

        assertTrue(ex.getCause() instanceof CustomException);
        assertTrue(ex.getCause().getMessage().contains("db down"));
    }

    // ===========================
    // PROCESS RECORD - additional branches
    // ===========================

    @Test
    void testProcessRecord_exceedsExpectedFieldCount_marksFailed() throws Exception {
        CSVRecord csvRecord = mock(CSVRecord.class);
        when(csvRecord.size()).thenReturn(10);
        when(csvRecord.toMap()).thenReturn(new HashMap<>());

        Map<String, String> result = (Map<String, String>) invokePrivate(
                "processRecord",
                new Class[]{CSVRecord.class, int.class, String.class, String.class, Map.class, Map.class, List.class},
                csvRecord, 5, "event", "batch", new HashMap<>(), new HashMap<>(), new ArrayList<>()
        );

        assertEquals("FAILED", result.get("Status"));
        assertTrue(result.get("Error Details").contains("exceeds expected"));
    }

    @Test
    void testProcessRecord_blankEmail_marksFailed() throws Exception {
        CSVRecord csvRecord = mock(CSVRecord.class);
        when(csvRecord.size()).thenReturn(1);
        when(csvRecord.get("Email")).thenReturn("   ");
        when(csvRecord.toMap()).thenReturn(new HashMap<>());

        Map<String, String> result = (Map<String, String>) invokePrivate(
                "processRecord",
                new Class[]{CSVRecord.class, int.class, String.class, String.class, Map.class, Map.class, List.class},
                csvRecord, 5, "event", "batch", new HashMap<>(), new HashMap<>(), new ArrayList<>()
        );

        assertEquals("FAILED", result.get("Status"));
        assertEquals("Empty email", result.get("Error Details"));
    }

    @Test
    void testProcessRecord_userDoesNotExist_marksFailed() throws Exception {
        CSVRecord csvRecord = mock(CSVRecord.class);
        when(csvRecord.size()).thenReturn(1);
        when(csvRecord.get("Email")).thenReturn("test@mail.com");
        when(csvRecord.toMap()).thenReturn(new HashMap<>());

        Map<String, String> result = (Map<String, String>) invokePrivate(
                "processRecord",
                new Class[]{CSVRecord.class, int.class, String.class, String.class, Map.class, Map.class, List.class},
                csvRecord, 5, "event", "batch", new HashMap<>(), new HashMap<>(), new ArrayList<>()
        );

        assertEquals("FAILED", result.get("Status"));
        assertEquals("User does not exist", result.get("Error Details"));
    }

    @Test
    void testProcessRecord_userAlreadyEnrolled_marksFailed() throws Exception {
        CSVRecord csvRecord = mock(CSVRecord.class);
        when(csvRecord.size()).thenReturn(1);
        when(csvRecord.get("Email")).thenReturn("test@mail.com");
        when(csvRecord.toMap()).thenReturn(new HashMap<>());

        Map<String, Object> userInfo = Map.of(Constants.USER_ID, "user1");
        Map<String, Object> emailMap = Map.of("test@mail.com", userInfo);
        when(cassandraOperation.getRecordsByProperties(any(), any(), any(), any(), any()))
                .thenReturn(List.of(Map.of("id", "enroll1")));

        Map<String, String> result = (Map<String, String>) invokePrivate(
                "processRecord",
                new Class[]{CSVRecord.class, int.class, String.class, String.class, Map.class, Map.class, List.class},
                csvRecord, 5, "event", "batch", emailMap, new HashMap<>(), new ArrayList<>()
        );

        assertEquals("FAILED", result.get("Status"));
        assertEquals("User enrolled in the batch", result.get("Error Details"));
    }

    @Test
    void testProcessRecord_enrollFails_marksFailed() throws Exception {
        CSVRecord csvRecord = mock(CSVRecord.class);
        when(csvRecord.size()).thenReturn(1);
        when(csvRecord.get("Email")).thenReturn("test@mail.com");
        when(csvRecord.toMap()).thenReturn(new HashMap<>());

        Map<String, Object> userInfo = Map.of(Constants.USER_ID, "user1");
        Map<String, Object> emailMap = Map.of("test@mail.com", userInfo);
        when(cassandraOperation.getRecordsByProperties(any(), any(), any(), any(), any()))
                .thenReturn(Collections.emptyList());

        ApiResponse failResponse = new ApiResponse();
        failResponse.put(Constants.RESPONSE, "FAILURE");
        when(cassandraOperation.insertRecord(any(), any(), any())).thenReturn(failResponse);

        Map<String, Object> eventDetails = new HashMap<>();
        eventDetails.put(Constants.DURATION, 10);

        Map<String, String> result = (Map<String, String>) invokePrivate(
                "processRecord",
                new Class[]{CSVRecord.class, int.class, String.class, String.class, Map.class, Map.class, List.class},
                csvRecord, 5, "event", "batch", emailMap, eventDetails, new ArrayList<>()
        );

        assertEquals("FAILED", result.get("Status"));
        assertEquals("Failed to enroll", result.get("Error Details"));
    }

    @Test
    void testProcessRecord_userInfoWithNullValue_marksFailedWithValidationMessage() throws Exception {
        CSVRecord csvRecord = mock(CSVRecord.class);
        when(csvRecord.size()).thenReturn(1);
        when(csvRecord.get("Email")).thenReturn("test@mail.com");
        when(csvRecord.toMap()).thenReturn(new HashMap<>());

        Map<String, Object> userInfo = new HashMap<>();
        userInfo.put(Constants.USER_ID, null);
        Map<String, Object> emailMap = new HashMap<>();
        emailMap.put("test@mail.com", userInfo);

        Map<String, String> result = (Map<String, String>) invokePrivate(
                "processRecord",
                new Class[]{CSVRecord.class, int.class, String.class, String.class, Map.class, Map.class, List.class},
                csvRecord, 5, "event", "batch", emailMap, new HashMap<>(), new ArrayList<>()
        );

        assertEquals("FAILED", result.get("Status"));
        assertTrue(result.get("Error Details").contains("null"));
    }

    @Test
    void testProcessRecord_jsonProcessingExceptionFromCertService_marksFailed() throws Exception {
        CSVRecord csvRecord = mock(CSVRecord.class);
        when(csvRecord.size()).thenReturn(1);
        when(csvRecord.get("Email")).thenReturn("test@mail.com");
        when(csvRecord.toMap()).thenReturn(new HashMap<>());

        Map<String, Object> userInfo = Map.of(Constants.USER_ID, "user1");
        Map<String, Object> emailMap = Map.of("test@mail.com", userInfo);
        when(cassandraOperation.getRecordsByProperties(any(), any(), any(), any(), any()))
                .thenReturn(Collections.emptyList());

        ApiResponse success = new ApiResponse();
        success.put(Constants.RESPONSE, Constants.SUCCESS);
        when(cassandraOperation.insertRecord(any(), any(), any())).thenReturn(success);

        doThrow(new JsonProcessingException("boom") {
        }).when(certService).generateCertificateEventAndPushToKafka(any(), any());

        Map<String, Object> eventDetails = new HashMap<>();
        eventDetails.put(Constants.DURATION, 10);
        eventDetails.put(Constants.START_DATE, new Date());
        eventDetails.put(Constants.END_DATE_CAMEL, new Date());

        Map<String, String> result = (Map<String, String>) invokePrivate(
                "processRecord",
                new Class[]{CSVRecord.class, int.class, String.class, String.class, Map.class, Map.class, List.class},
                csvRecord, 5, "event", "batch", emailMap, eventDetails, new ArrayList<>()
        );

        assertEquals("FAILED", result.get("Status"));
        assertEquals("Error processing JSON data", result.get("Error Details"));
    }

    @Test
    void testProcessRecord_unexpectedException_marksFailedWithInternalErrorMessage() throws Exception {
        CSVRecord csvRecord = mock(CSVRecord.class);
        when(csvRecord.size()).thenReturn(1);
        when(csvRecord.get("Email")).thenReturn("test@mail.com");
        when(csvRecord.toMap()).thenReturn(new HashMap<>());

        Map<String, Object> emailMap = new HashMap<>();
        emailMap.put("test@mail.com", "not-a-map");

        Map<String, String> result = (Map<String, String>) invokePrivate(
                "processRecord",
                new Class[]{CSVRecord.class, int.class, String.class, String.class, Map.class, Map.class, List.class},
                csvRecord, 5, "event", "batch", emailMap, new HashMap<>(), new ArrayList<>()
        );

        assertEquals("FAILED", result.get("Status"));
        assertEquals("Internal error while processing record", result.get("Error Details"));
    }

    // ===========================
    // INITIATE EXTERNAL TRAINING BULK UPLOAD PROCESS (public)
    // ===========================

    @Test
    void testInitiate_invalidMessage_skipsProcessing() throws Exception {
        consumer.initiateExternalTrainingBulkUploadProcess("{}");

        verifyNoInteractions(storageService, cassandraOperation);
    }

    @Test
    void testInitiate_validMessage_fileNotPresentAfterDownload_marksFailed() throws Exception {
        String fileName = "missing-" + UUID.randomUUID() + ".csv";
        Map<String, String> input = new HashMap<>();
        input.put(Constants.CONTEXT_ID_KEY, "event1");
        input.put(Constants.BATCH_ID, "batch1");
        input.put(Constants.FILE_NAME, fileName);
        input.put(Constants.ORD_ID, "org1");
        input.put(Constants.IDENTIFIER, "id1");
        String json = new ObjectMapper().writeValueAsString(input);

        when(props.getExternalTrainingBulkUploadContainerName()).thenReturn("container");

        consumer.initiateExternalTrainingBulkUploadProcess(json);

        verify(storageService).downloadFile(fileName, "container");
        verify(cassandraOperation, atLeastOnce()).updateRecord(anyString(), any(), anyMap(), anyMap());
    }

    // ===========================
    // PROCESS EXTERNAL TRAINING BULK UPLOAD MESSAGE (public, kafka listener)
    // ===========================

    @Test
    void testProcessExternalTrainingBulkUploadMessage_blankValue_logsAndSkips() {
        ConsumerRecord<String, String> kafkaRecord = new ConsumerRecord<>("topic", 0, 0L, "key", "");

        assertDoesNotThrow(() -> consumer.processExternalTrainingBulkUploadMessage(kafkaRecord));

        verifyNoInteractions(storageService, cassandraOperation);
    }

    @Test
    void testProcessExternalTrainingBulkUploadMessage_nonBlankValue_submitsAsyncTask() throws Exception {
        ConsumerRecord<String, String> kafkaRecord = new ConsumerRecord<>("topic", 0, 0L, "key", "{}");

        assertDoesNotThrow(() -> consumer.processExternalTrainingBulkUploadMessage(kafkaRecord));

        // the actual processing happens asynchronously via CompletableFuture.runAsync;
        // give it a brief moment to run on the common pool before the test exits.
        Thread.sleep(300);

        verifyNoInteractions(storageService, cassandraOperation);
    }

    @Test
    void testProcessExternalTrainingBulkUploadMessage_asyncTaskThrowsIOException_wrappedAsCustomException() throws Exception {
        ConsumerRecord<String, String> kafkaRecord = new ConsumerRecord<>("topic", 0, 0L, "key", "{}");
        doThrow(new IOException("boom")).when(consumer).initiateExternalTrainingBulkUploadProcess(anyString());

        assertDoesNotThrow(() -> consumer.processExternalTrainingBulkUploadMessage(kafkaRecord));

        // give the CompletableFuture.runAsync task time to run and throw the wrapped CustomException
        // on the common pool; the exception is not propagated back to this thread.
        Thread.sleep(300);

        verify(consumer).initiateExternalTrainingBulkUploadProcess("{}");
    }

    @Test
    @SuppressWarnings("unchecked")
    void testProcessExternalTrainingBulkUploadMessage_valueAccessThrows_hitsOuterCatch() {
        ConsumerRecord<String, String> kafkaRecord = mock(ConsumerRecord.class);
        when(kafkaRecord.value()).thenReturn("non-blank").thenThrow(new RuntimeException("boom"));

        assertDoesNotThrow(() -> consumer.processExternalTrainingBulkUploadMessage(kafkaRecord));

        verifyNoInteractions(storageService, cassandraOperation);
    }

    @Test
    void testProcessExternalTrainingBulkUpload_fileExistsButEmpty_marksFailed() throws Exception {
        String fileName = "empty-" + UUID.randomUUID() + ".csv";
        File file = new File(Constants.LOCAL_BASE_PATH + fileName);
        file.getParentFile().mkdirs();
        assertTrue(file.createNewFile());

        try {
            Map<String, String> inputData = new HashMap<>();
            inputData.put(Constants.CONTEXT_ID_KEY, "event1");
            inputData.put(Constants.BATCH_ID, "batch1");
            inputData.put(Constants.FILE_NAME, fileName);
            inputData.put(Constants.ORD_ID, "org1");
            inputData.put(Constants.IDENTIFIER, "id1");

            when(props.getExternalTrainingBulkUploadTable()).thenReturn("statusTable");

            invokePrivate("processExternalTrainingBulkUpload", new Class[]{Map.class}, inputData);

            ArgumentCaptor<Map> captor = ArgumentCaptor.forClass(Map.class);
            verify(cassandraOperation).updateRecord(eq(Constants.KEYSPACE_SUNBIRD), eq("statusTable"), captor.capture(), anyMap());
            assertEquals(Constants.FAILED_UPPERCASE, captor.getValue().get(Constants.STATUS));
            verifyNoInteractions(notificationService, certService, outboundService);
        } finally {
            file.delete();
        }
    }

    @Test
    void testProcessExternalTrainingBulkUpload_headersAlreadyContainStatusColumns_allRecordsFail_noNotification() throws Exception {
        String fileName = "bulk-" + UUID.randomUUID() + ".csv";
        File file = new File(Constants.LOCAL_BASE_PATH + fileName);
        file.getParentFile().mkdirs();
        try (java.io.FileWriter fw = new java.io.FileWriter(file)) {
            fw.write("Email,Status,Error Details\n");
            fw.write("bad-email,,\n");
        }

        try {
            Map<String, String> inputData = new HashMap<>();
            inputData.put(Constants.CONTEXT_ID_KEY, "event1");
            inputData.put(Constants.BATCH_ID, "batch1");
            inputData.put(Constants.FILE_NAME, fileName);
            inputData.put(Constants.ORD_ID, "org1");
            inputData.put(Constants.IDENTIFIER, "id1");
            inputData.put(Constants.CONTEXT_ID_CAMEL, "event1");

            when(props.getBulkUploadCsvDelimiter()).thenReturn(',');
            when(props.getSbUrl()).thenReturn("http://sb");
            when(props.getUserSearchEndPoint()).thenReturn("/search");
            when(props.getExternalTrainingBulkUploadTable()).thenReturn("statusTable");
            when(props.getExternalTrainingBulkUploadContainerName()).thenReturn("container");
            when(props.getCloudContainerName()).thenReturn("cloud");
            when(outboundService.fetchResultUsingPost(anyString(), anyMap(), anyMap())).thenReturn(null);

            Map<String, Object> batchRow = new HashMap<>();
            batchRow.put("start_date", new Date(1_000_000L));
            batchRow.put("end_date", new Date(2_000_000L));
            batchRow.put(Constants.BATCH_ATTRIBUTES_COLUMN, "{\"duration\":5}");
            when(cassandraOperation.getRecordsByProperties(eq(Constants.KEYSPACE_SUNBIRD_COURSE), eq(Constants.EVENT_BATCH_TABLE_NAME), anyMap(), isNull(), isNull()))
                    .thenReturn(List.of(batchRow));

            Map<String, Object> readResponse = new HashMap<>();
            readResponse.put(Constants.NAME, "Training");
            readResponse.put(Constants.CERT_TEMPLATE, "tmpl");
            readResponse.put(Constants.CERT_TEMPLATE_ID, "tmplId");
            readResponse.put(Constants.SOURCE_NAME, "src");
            when(contentInfoService.readEvent("event1")).thenReturn(readResponse);

            ApiResponse uploadResponse = new ApiResponse();
            uploadResponse.setResponseCode(HttpStatus.OK);
            when(storageService.uploadFile(any(), any(), any())).thenReturn(uploadResponse);

            invokePrivate("processExternalTrainingBulkUpload", new Class[]{Map.class}, inputData);

            // headers already had Status / Error Details columns -> the add() branches are skipped,
            // and the only record fails processing so no notification is ever sent.
            verifyNoInteractions(notificationService, certService);

            String written = new String(Files.readAllBytes(file.toPath()), java.nio.charset.StandardCharsets.UTF_8);
            assertTrue(written.contains("FAILED"));
        } finally {
            file.delete();
        }
    }

    // ===========================
    // PROCESS EXTERNAL TRAINING BULK UPLOAD (private, full file-processing flow)
    // ===========================

    @Test
    void testProcessExternalTrainingBulkUpload_mixedSuccessAndFailure_fullFlow() throws Exception {
        String fileName = "bulk-" + UUID.randomUUID() + ".csv";
        File file = new File(Constants.LOCAL_BASE_PATH + fileName);
        file.getParentFile().mkdirs();
        try (java.io.FileWriter fw = new java.io.FileWriter(file)) {
            fw.write("Email,Name\n");
            fw.write("test@mail.com,John\n");
            fw.write("bad-email,Jane\n");
        }

        try {
            Map<String, String> inputData = new HashMap<>();
            inputData.put(Constants.CONTEXT_ID_KEY, "event1");
            inputData.put(Constants.BATCH_ID, "batch1");
            inputData.put(Constants.FILE_NAME, fileName);
            inputData.put(Constants.ORD_ID, "org1");
            inputData.put(Constants.IDENTIFIER, "id1");
            inputData.put(Constants.CONTEXT_ID_CAMEL, "event1");

            when(props.getBulkUploadCsvDelimiter()).thenReturn(',');
            when(props.getSbUrl()).thenReturn("http://sb");
            when(props.getUserSearchEndPoint()).thenReturn("/search");
            when(props.getExternalTrainingEnrolmentsTableName()).thenReturn("enrolTable");
            when(props.getExternalTrainingEnrolmentBatchLookupTableName()).thenReturn("lookupTable");
            when(props.getExternalTrainingBulkUploadContainerName()).thenReturn("container");
            when(props.getCloudContainerName()).thenReturn("cloud");
            when(props.getExternalTrainingBulkUploadTable()).thenReturn("statusTable");

            Map<String, Object> personalDetails = new HashMap<>();
            personalDetails.put(Constants.PRIMARY_EMAIL, "test@mail.com");
            Map<String, Object> profileDetails = new HashMap<>();
            profileDetails.put(Constants.PERSONAL_DETAILS, personalDetails);
            Map<String, Object> contentEntry = new HashMap<>();
            contentEntry.put(Constants.ROOT_ORG_ID, "org1");
            contentEntry.put(Constants.FIRSTNAME, "John");
            contentEntry.put(Constants.USER_ID, "user1");
            contentEntry.put(Constants.PROFILE_DETAILS, profileDetails);
            Map<String, Object> innerResponse = new HashMap<>();
            innerResponse.put(Constants.CONTENT, List.of(contentEntry));
            Map<String, Object> resultMap = new HashMap<>();
            resultMap.put(Constants.RESPONSE, innerResponse);
            Map<String, Object> outerResponse = new HashMap<>();
            outerResponse.put(Constants.RESPONSE_CODE, Constants.OK);
            outerResponse.put(Constants.RESULT, resultMap);
            when(outboundService.fetchResultUsingPost(anyString(), anyMap(), anyMap())).thenReturn(outerResponse);

            Map<String, Object> batchRow = new HashMap<>();
            batchRow.put("start_date", new Date(1_000_000L));
            batchRow.put("end_date", new Date(2_000_000L));
            batchRow.put(Constants.BATCH_ATTRIBUTES_COLUMN, "{\"duration\":5}");
            when(cassandraOperation.getRecordsByProperties(eq(Constants.KEYSPACE_SUNBIRD_COURSE), eq(Constants.EVENT_BATCH_TABLE_NAME), anyMap(), isNull(), isNull()))
                    .thenReturn(List.of(batchRow));

            Map<String, Object> readResponse = new HashMap<>();
            readResponse.put(Constants.NAME, "Training");
            readResponse.put(Constants.CERT_TEMPLATE, "tmpl");
            readResponse.put(Constants.CERT_TEMPLATE_ID, "tmplId");
            readResponse.put(Constants.SOURCE_NAME, "src");
            when(contentInfoService.readEvent("event1")).thenReturn(readResponse);

            when(cassandraOperation.getRecordsByProperties(eq(Constants.KEYSPACE_SUNBIRD_COURSE), eq("enrolTable"), anyMap(), isNull(), isNull()))
                    .thenReturn(Collections.emptyList());

            ApiResponse insertSuccess = new ApiResponse();
            insertSuccess.put(Constants.RESPONSE, Constants.SUCCESS);
            when(cassandraOperation.insertRecord(any(), any(), any())).thenReturn(insertSuccess);

            doNothing().when(certService).generateCertificateEventAndPushToKafka(any(), any());

            ApiResponse uploadResponse = new ApiResponse();
            uploadResponse.setResponseCode(HttpStatus.OK);
            when(storageService.uploadFile(any(), any(), any())).thenReturn(uploadResponse);

            invokePrivate("processExternalTrainingBulkUpload", new Class[]{Map.class}, inputData);

            verify(notificationService).sendNotificationForExternalTraining("event1", "Training", List.of("user1"), Constants.EXTERNAL_TRAINING);

            ArgumentCaptor<Map> captor = ArgumentCaptor.forClass(Map.class);
            verify(cassandraOperation).updateRecord(eq(Constants.KEYSPACE_SUNBIRD), eq("statusTable"), captor.capture(), anyMap());
            Map<String, Object> fields = captor.getValue();
            assertEquals(Constants.FAILED, fields.get(Constants.STATUS));
            assertEquals(2, fields.get(Constants.TOTAL_RECORDS));
            assertEquals(1, fields.get(Constants.SUCCESSFUL_RECORDS_COUNT));
            assertEquals(1, fields.get(Constants.FAILED_RECORDS_COUNT));

            String written = new String(Files.readAllBytes(file.toPath()), java.nio.charset.StandardCharsets.UTF_8);
            assertTrue(written.contains("Invalid Email Id"));
        } finally {
            file.delete();
        }
    }

    @Test
    void testProcessExternalTrainingBulkUpload_missingEmailColumn_hitsCatchBlock_marksFailed() throws Exception {
        String fileName = "bulk-" + UUID.randomUUID() + ".csv";
        File file = new File(Constants.LOCAL_BASE_PATH + fileName);
        file.getParentFile().mkdirs();
        try (java.io.FileWriter fw = new java.io.FileWriter(file)) {
            fw.write("Name\n");
            fw.write("John\n");
        }

        try {
            Map<String, String> inputData = new HashMap<>();
            inputData.put(Constants.CONTEXT_ID_KEY, "event1");
            inputData.put(Constants.BATCH_ID, "batch1");
            inputData.put(Constants.FILE_NAME, fileName);
            inputData.put(Constants.ORD_ID, "org1");
            inputData.put(Constants.IDENTIFIER, "id1");
            inputData.put(Constants.CONTEXT_ID_CAMEL, "event1");

            when(props.getBulkUploadCsvDelimiter()).thenReturn(',');
            when(props.getExternalTrainingBulkUploadTable()).thenReturn("statusTable");

            invokePrivate("processExternalTrainingBulkUpload", new Class[]{Map.class}, inputData);

            ArgumentCaptor<Map> captor = ArgumentCaptor.forClass(Map.class);
            verify(cassandraOperation, atLeastOnce()).updateRecord(eq(Constants.KEYSPACE_SUNBIRD), eq("statusTable"), captor.capture(), anyMap());
            Map<String, Object> fields = captor.getValue();
            assertEquals(Constants.FAILED_UPPERCASE, fields.get(Constants.STATUS));

            verifyNoInteractions(notificationService, certService);
        } finally {
            file.delete();
        }
    }

    // ===========================
    // ENROLL USER - additional date/instant branch combinations
    // ===========================

    @Test
    void testEnrollUser_dateStart_instantEnd_success() throws Exception {
        ApiResponse success = new ApiResponse();
        success.put(Constants.RESPONSE, Constants.SUCCESS);
        when(cassandraOperation.insertRecord(any(), any(), any())).thenReturn(success);

        Map<String, Object> eventDetails = new HashMap<>();
        eventDetails.put(Constants.DURATION, 5);
        eventDetails.put(Constants.START_DATE, new Date());
        eventDetails.put(Constants.END_DATE_CAMEL, Instant.now());

        Object result = invokePrivate("enrollUser", new Class[]{String.class, String.class, String.class, Map.class},
                "user1", "event1", "batch1", eventDetails);

        ApiResponse response = (ApiResponse) result;
        assertEquals(Constants.SUCCESS, response.get(Constants.RESPONSE));
        verify(cassandraOperation, times(2)).insertRecord(any(), any(), any());
    }

    @Test
    void testEnrollUser_nonDateNonInstantTypes_startEndRemainNull() throws Exception {
        ApiResponse success = new ApiResponse();
        success.put(Constants.RESPONSE, Constants.SUCCESS);
        when(cassandraOperation.insertRecord(any(), any(), any())).thenReturn(success);

        Map<String, Object> eventDetails = new HashMap<>();
        eventDetails.put(Constants.DURATION, 5);
        eventDetails.put(Constants.START_DATE, "not-a-date");
        eventDetails.put(Constants.END_DATE_CAMEL, "not-a-date");

        Object result = invokePrivate("enrollUser", new Class[]{String.class, String.class, String.class, Map.class},
                "user1", "event1", "batch1", eventDetails);

        ApiResponse response = (ApiResponse) result;
        assertEquals(Constants.SUCCESS, response.get(Constants.RESPONSE));
        verify(cassandraOperation, times(2)).insertRecord(any(), any(), any());
    }

    // ===========================
    // GET EVENT DETAILS - Instant-based dates and non-numeric duration branch
    // ===========================

    @Test
    void testGetEventDetails_instantDatesAndNonNumericDuration_success() throws Exception {
        Map<String, Object> batchRow = new HashMap<>();
        batchRow.put("start_date", Instant.now());
        batchRow.put("end_date", Instant.now());
        batchRow.put(Constants.BATCH_ATTRIBUTES_COLUMN, "{\"duration\":\"not-a-number\"}");
        when(cassandraOperation.getRecordsByProperties(eq(Constants.KEYSPACE_SUNBIRD_COURSE), eq(Constants.EVENT_BATCH_TABLE_NAME), anyMap(), isNull(), isNull()))
                .thenReturn(List.of(batchRow));

        Map<String, Object> readResponse = new HashMap<>();
        readResponse.put(Constants.NAME, "Training");
        readResponse.put(Constants.CERT_TEMPLATE, "tmpl");
        readResponse.put(Constants.CERT_TEMPLATE_ID, "tmplId");
        readResponse.put(Constants.SOURCE_NAME, "src");
        when(contentInfoService.readEvent("event1")).thenReturn(readResponse);

        Map<String, Object> eventDetails = new HashMap<>();
        invokePrivate("getEventDetails", new Class[]{String.class, String.class, Map.class}, "event1", "batch1", eventDetails);

        assertEquals(0L, eventDetails.get(Constants.DURATION));
        assertNotNull(eventDetails.get(Constants.ISSUED_DATE));
    }

    @Test
    void testGetEventDetails_startObjNeitherDateNorInstant_startDateNullCausesValidationException() {
        Map<String, Object> batchRow = new HashMap<>();
        batchRow.put("start_date", "not-a-date-or-instant");
        batchRow.put("end_date", new Date());
        batchRow.put(Constants.BATCH_ATTRIBUTES_COLUMN, "{\"duration\":5}");
        when(cassandraOperation.getRecordsByProperties(eq(Constants.KEYSPACE_SUNBIRD_COURSE), eq(Constants.EVENT_BATCH_TABLE_NAME), anyMap(), isNull(), isNull()))
                .thenReturn(List.of(batchRow));

        Map<String, Object> readResponse = new HashMap<>();
        readResponse.put(Constants.NAME, "Training");
        readResponse.put(Constants.CERT_TEMPLATE, "tmpl");
        readResponse.put(Constants.CERT_TEMPLATE_ID, "tmplId");
        readResponse.put(Constants.SOURCE_NAME, "src");
        when(contentInfoService.readEvent("event1")).thenReturn(readResponse);

        Map<String, Object> eventDetails = new HashMap<>();
        Exception ex = assertThrows(Exception.class, () ->
                invokePrivate("getEventDetails", new Class[]{String.class, String.class, Map.class}, "event1", "batch1", eventDetails));

        // startObj is neither a Date nor an Instant, so startDate stays null, which later fails
        // validateNotNullOrEmpty and gets wrapped into a CustomException.
        assertTrue(ex.getCause() instanceof CustomException);
    }

    @Test
    void testGetEventDetails_endObjNeitherDateNorInstant_endDateNullCausesFormattingException() {
        Map<String, Object> batchRow = new HashMap<>();
        batchRow.put("start_date", Instant.now());
        batchRow.put("end_date", "not-a-date-or-instant");
        batchRow.put(Constants.BATCH_ATTRIBUTES_COLUMN, "{\"duration\":5}");
        when(cassandraOperation.getRecordsByProperties(eq(Constants.KEYSPACE_SUNBIRD_COURSE), eq(Constants.EVENT_BATCH_TABLE_NAME), anyMap(), isNull(), isNull()))
                .thenReturn(List.of(batchRow));

        Map<String, Object> readResponse = new HashMap<>();
        readResponse.put(Constants.NAME, "Training");
        readResponse.put(Constants.CERT_TEMPLATE, "tmpl");
        readResponse.put(Constants.CERT_TEMPLATE_ID, "tmplId");
        readResponse.put(Constants.SOURCE_NAME, "src");
        when(contentInfoService.readEvent("event1")).thenReturn(readResponse);

        Map<String, Object> eventDetails = new HashMap<>();
        Exception ex = assertThrows(Exception.class, () ->
                invokePrivate("getEventDetails", new Class[]{String.class, String.class, Map.class}, "event1", "batch1", eventDetails));

        // endObj is neither a Date nor an Instant, so endDate stays null; formatting a null Date
        // throws, which gets wrapped into a CustomException.
        assertTrue(ex.getCause() instanceof CustomException);
    }

    // ===========================
    // VALIDATE NOT NULL OR EMPTY - additional branches
    // ===========================

    @Test
    void testValidateNotNullOrEmpty_emptyMap_throws() {
        Map<String, Object> map = new HashMap<>();

        Exception ex = assertThrows(Exception.class, () ->
                invokePrivate("validateNotNullOrEmpty", new Class[]{Map.class}, map)
        );

        assertTrue(ex.getCause().getMessage().contains("null or empty"));
    }

    @Test
    void testValidateNotNullOrEmpty_blankStringValue_throws() {
        Map<String, Object> map = new HashMap<>();
        map.put("key", "   ");

        Exception ex = assertThrows(Exception.class, () ->
                invokePrivate("validateNotNullOrEmpty", new Class[]{Map.class}, map)
        );

        assertTrue(ex.getCause().getMessage().contains("empty"));
    }

    // ===========================
    // PREPARE LRC PROGRESS DETAILS
    // ===========================

    @Test
    void testPrepareLrcProgressDetails_buildsExpectedJson() throws Exception {
        Map<String, Object> eventDetails = new HashMap<>();
        eventDetails.put(Constants.DURATION, 300L);

        Object result = invokePrivate("prepareLrcProgressDetails", new Class[]{Map.class}, eventDetails);

        assertTrue(result instanceof String);
        String json = (String) result;
        assertTrue(json.contains("max_size"));
        assertTrue(json.contains("mimeType"));
        assertTrue(json.contains("application/html"));
        assertTrue(json.contains("stateMetaData"));
        assertTrue(json.contains("current"));
    }
}