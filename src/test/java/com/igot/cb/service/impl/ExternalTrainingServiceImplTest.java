package com.igot.cb.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.igot.cb.cassandra.CassandraOperation;
import com.igot.cb.model.ApiResponse;
import com.igot.cb.service.UserAndOrgServiceImpl;
import com.igot.cb.storage.service.StorageService;
import com.igot.cb.user.UserUtilityService;
import com.igot.cb.util.AccessTokenValidator;
import com.igot.cb.util.CbExtServerProperties;
import com.igot.cb.util.Constants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ExternalTrainingServiceImplTest {

    private ExternalTrainingServiceImpl service;

    @Mock
    private StorageService storageService;

    @Mock
    private CbExtServerProperties serverConfig;

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    @Mock
    private CassandraOperation cassandraOperation;

    @Mock
    private AccessTokenValidator accessTokenValidator;

    @Mock
    private UserAndOrgServiceImpl userAndOrgService;

    @Mock
    private UserUtilityService userUtilityService;

    @BeforeEach
    void setUp() {
        service = new ExternalTrainingServiceImpl(storageService, serverConfig, kafkaTemplate, cassandraOperation,
                accessTokenValidator, new ObjectMapper().registerModule(new JavaTimeModule()), userAndOrgService);
    }

    // ===========================
    // validateCsvFile
    // ===========================

    @Test
    void testValidateCsvFile_nullFile() {
        assertEquals("File is empty or not provided.", service.validateCsvFile(null));
    }

    @Test
    void testValidateCsvFile_emptyFile() {
        MockMultipartFile file = new MockMultipartFile("file", "test.csv", "text/csv", new byte[0]);
        assertEquals("File is empty or not provided.", service.validateCsvFile(file));
    }

    @Test
    void testValidateCsvFile_blankFileName() {
        MockMultipartFile file = new MockMultipartFile("file", "", "text/csv", "data".getBytes(StandardCharsets.UTF_8));
        assertEquals("File name is invalid.", service.validateCsvFile(file));
    }

    @Test
    void testValidateCsvFile_invalidExtension() {
        MockMultipartFile file = new MockMultipartFile("file", "test.txt", "text/plain", "data".getBytes(StandardCharsets.UTF_8));
        assertEquals("Invalid file type. Only CSV files are allowed.", service.validateCsvFile(file));
    }

    @Test
    void testValidateCsvFile_invalidHeader() {
        MockMultipartFile file = new MockMultipartFile("file", "test.csv", "text/csv",
                "Name\na@a.com\n".getBytes(StandardCharsets.UTF_8));
        assertEquals("Invalid CSV header. Expected header: Email", service.validateCsvFile(file));
    }

    @Test
    void testValidateCsvFile_exceedsBatchSize() {
        when(serverConfig.getExternalTrainingBatchSize()).thenReturn(1);
        MockMultipartFile file = new MockMultipartFile("file", "test.csv", "text/csv",
                "Email\na@a.com\nb@b.com\nc@c.com\n".getBytes(StandardCharsets.UTF_8));
        String result = service.validateCsvFile(file);
        assertEquals("CSV file should not contain more than 1 rows.", result);
    }

    @Test
    void testValidateCsvFile_noDataRows() {
        when(serverConfig.getExternalTrainingBatchSize()).thenReturn(10);
        MockMultipartFile file = new MockMultipartFile("file", "test.csv", "text/csv",
                "Email\n".getBytes(StandardCharsets.UTF_8));
        assertEquals("CSV file contains no data rows.", service.validateCsvFile(file));
    }

    @Test
    void testValidateCsvFile_success() {
        when(serverConfig.getExternalTrainingBatchSize()).thenReturn(10);
        MockMultipartFile file = new MockMultipartFile("file", "test.csv", "text/csv",
                "Email\na@a.com\n".getBytes(StandardCharsets.UTF_8));
        assertEquals("", service.validateCsvFile(file));
    }

    @Test
    void testValidateCsvFile_exceptionWhileReading() throws IOException {
        MultipartFile file = mock(MultipartFile.class);
        when(file.isEmpty()).thenReturn(false);
        when(file.getOriginalFilename()).thenReturn("test.csv");
        when(file.getInputStream()).thenThrow(new IOException("boom"));
        assertEquals("Error while reading CSV file.", service.validateCsvFile(file));
    }

    @Test
    void testValidateCsvFile_headerMissing_emptyStream() throws IOException {
        // Non-empty file (isEmpty() = false) but the input stream yields no lines at all,
        // so reader.readLine() returns null on the very first call.
        MultipartFile file = mock(MultipartFile.class);
        when(file.isEmpty()).thenReturn(false);
        when(file.getOriginalFilename()).thenReturn("test.csv");
        when(file.getInputStream()).thenReturn(new java.io.ByteArrayInputStream(new byte[0]));
        assertEquals("CSV header is missing. Expected header: Email", service.validateCsvFile(file));
    }

    // ===========================
    // externalTrainingUserBulkUpload
    // ===========================

    @Test
    void testBulkUpload_blankUserId() {
        when(accessTokenValidator.fetchUserIdFromAccessToken(anyString(), any(ApiResponse.class))).thenReturn("");
        ApiResponse result = service.externalTrainingUserBulkUpload(mock(MultipartFile.class), "event1", "batch1", "token");
        assertNotNull(result);
        verifyNoInteractions(cassandraOperation, storageService, kafkaTemplate);
    }

    @Test
    void testBulkUpload_userProfileEmpty() {
        when(accessTokenValidator.fetchUserIdFromAccessToken(anyString(), any(ApiResponse.class))).thenReturn("user1");
        when(userAndOrgService.readUserProfileFromDB(eq("user1"), anyList())).thenReturn(Collections.emptyMap());

        ApiResponse result = service.externalTrainingUserBulkUpload(mock(MultipartFile.class), "event1", "batch1", "token");

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, result.getResponseCode());
        assertEquals(Constants.FAILED, result.getParams().getStatus());
        assertTrue(result.getParams().getErrMsg().contains("Failed to read user details from DB"));
    }

    @Test
    void testBulkUpload_eventBatchDetailsNotFound() {
        when(accessTokenValidator.fetchUserIdFromAccessToken(anyString(), any(ApiResponse.class))).thenReturn("user1");
        Map<String, Object> userMap = new HashMap<>();
        userMap.put(Constants.ROOT_ORG_ID, "org1");
        when(userAndOrgService.readUserProfileFromDB(eq("user1"), anyList())).thenReturn(userMap);
        when(cassandraOperation.getRecordsByProperties(eq(Constants.KEYSPACE_SUNBIRD_COURSE), eq(Constants.EVENT_BATCH_TABLE), anyMap(), isNull(), isNull()))
                .thenReturn(Collections.emptyList());

        ApiResponse result = service.externalTrainingUserBulkUpload(mock(MultipartFile.class), "event1", "batch1", "token");

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, result.getResponseCode());
        assertEquals(Constants.FAILED, result.getParams().getStatus());
        assertTrue(result.getParams().getErrMsg().contains("No event batch details found"));
    }

    @Test
    void testBulkUpload_eventBatchDetailsFetchThrowsException() {
        when(accessTokenValidator.fetchUserIdFromAccessToken(anyString(), any(ApiResponse.class))).thenReturn("user1");
        Map<String, Object> userMap = new HashMap<>();
        userMap.put(Constants.ROOT_ORG_ID, "org1");
        when(userAndOrgService.readUserProfileFromDB(eq("user1"), anyList())).thenReturn(userMap);
        when(cassandraOperation.getRecordsByProperties(eq(Constants.KEYSPACE_SUNBIRD_COURSE), eq(Constants.EVENT_BATCH_TABLE), anyMap(), isNull(), isNull()))
                .thenThrow(new RuntimeException("db down"));

        ApiResponse result = service.externalTrainingUserBulkUpload(mock(MultipartFile.class), "event1", "batch1", "token");

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, result.getResponseCode());
        assertEquals(Constants.FAILED, result.getParams().getStatus());
        assertTrue(result.getParams().getErrMsg().contains("Error while fetching event batch details"));
    }

    @Test
    void testBulkUpload_invalidCsvFile() {
        when(accessTokenValidator.fetchUserIdFromAccessToken(anyString(), any(ApiResponse.class))).thenReturn("user1");
        Map<String, Object> userMap = new HashMap<>();
        userMap.put(Constants.ROOT_ORG_ID, "org1");
        when(userAndOrgService.readUserProfileFromDB(eq("user1"), anyList())).thenReturn(userMap);
        when(cassandraOperation.getRecordsByProperties(eq(Constants.KEYSPACE_SUNBIRD_COURSE), eq(Constants.EVENT_BATCH_TABLE), anyMap(), isNull(), isNull()))
                .thenReturn(List.of(Map.of("id", "batchRow")));

        MockMultipartFile file = new MockMultipartFile("file", "test.txt", "text/plain", "data".getBytes(StandardCharsets.UTF_8));
        ApiResponse result = service.externalTrainingUserBulkUpload(file, "event1", "batch1", "token");

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, result.getResponseCode());
        assertEquals(Constants.FAILED, result.getParams().getStatus());
        assertTrue(result.getParams().getErrMsg().contains("Invalid file type"));
    }

    @Test
    void testBulkUpload_fileAlreadyProcessing() {
        when(accessTokenValidator.fetchUserIdFromAccessToken(anyString(), any(ApiResponse.class))).thenReturn("user1");
        Map<String, Object> userMap = new HashMap<>();
        userMap.put(Constants.ROOT_ORG_ID, "org1");
        when(userAndOrgService.readUserProfileFromDB(eq("user1"), anyList())).thenReturn(userMap);
        when(cassandraOperation.getRecordsByProperties(eq(Constants.KEYSPACE_SUNBIRD_COURSE), eq(Constants.EVENT_BATCH_TABLE), anyMap(), isNull(), isNull()))
                .thenReturn(List.of(Map.of("id", "batchRow")));
        when(serverConfig.getExternalTrainingBatchSize()).thenReturn(10);
        when(serverConfig.getExternalTrainingBulkUploadTable()).thenReturn("bulkUploadTable");

        Map<String, Object> inProgressRow = new HashMap<>();
        inProgressRow.put(Constants.STATUS, Constants.STATUS_IN_PROGRESS_UPPERCASE);
        when(cassandraOperation.getRecordsByProperties(eq(Constants.KEYSPACE_SUNBIRD), eq("bulkUploadTable"), anyMap(), anyList(), isNull()))
                .thenReturn(List.of(inProgressRow));

        MockMultipartFile file = new MockMultipartFile("file", "test.csv", "text/csv", "Email\na@a.com\n".getBytes(StandardCharsets.UTF_8));
        ApiResponse result = service.externalTrainingUserBulkUpload(file, "event1", "batch1", "token");

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, result.getResponseCode());
        assertTrue(result.getParams().getErrMsg().contains("previous request is in processing state"));
    }

    @Test
    void testBulkUpload_uploadFileFails() throws IOException {
        when(accessTokenValidator.fetchUserIdFromAccessToken(anyString(), any(ApiResponse.class))).thenReturn("user1");
        Map<String, Object> userMap = new HashMap<>();
        userMap.put(Constants.ROOT_ORG_ID, "org1");
        when(userAndOrgService.readUserProfileFromDB(eq("user1"), anyList())).thenReturn(userMap);
        when(cassandraOperation.getRecordsByProperties(eq(Constants.KEYSPACE_SUNBIRD_COURSE), eq(Constants.EVENT_BATCH_TABLE), anyMap(), isNull(), isNull()))
                .thenReturn(List.of(Map.of("id", "batchRow")));
        when(serverConfig.getExternalTrainingBatchSize()).thenReturn(10);
        when(serverConfig.getExternalTrainingBulkUploadTable()).thenReturn("bulkUploadTable");
        when(cassandraOperation.getRecordsByProperties(eq(Constants.KEYSPACE_SUNBIRD), eq("bulkUploadTable"), anyMap(), anyList(), isNull()))
                .thenReturn(Collections.emptyList());
        when(serverConfig.getExternalTrainingBulkUploadContainerName()).thenReturn("container1");

        ApiResponse uploadResponse = new ApiResponse();
        uploadResponse.setResponseCode(HttpStatus.INTERNAL_SERVER_ERROR);
        uploadResponse.getParams().setErrMsg("storage down");
        when(storageService.uploadFile(any(MultipartFile.class), anyString())).thenReturn(uploadResponse);

        MockMultipartFile file = new MockMultipartFile("file", "test.csv", "text/csv", "Email\na@a.com\n".getBytes(StandardCharsets.UTF_8));
        ApiResponse result = service.externalTrainingUserBulkUpload(file, "event1", "batch1", "token");

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, result.getResponseCode());
        assertTrue(result.getParams().getErrMsg().contains("Failed to upload file"));
    }

    @Test
    void testBulkUpload_insertRecordFails() throws IOException {
        when(accessTokenValidator.fetchUserIdFromAccessToken(anyString(), any(ApiResponse.class))).thenReturn("user1");
        Map<String, Object> userMap = new HashMap<>();
        userMap.put(Constants.ROOT_ORG_ID, "org1");
        when(userAndOrgService.readUserProfileFromDB(eq("user1"), anyList())).thenReturn(userMap);
        when(cassandraOperation.getRecordsByProperties(eq(Constants.KEYSPACE_SUNBIRD_COURSE), eq(Constants.EVENT_BATCH_TABLE), anyMap(), isNull(), isNull()))
                .thenReturn(List.of(Map.of("id", "batchRow")));
        when(serverConfig.getExternalTrainingBatchSize()).thenReturn(10);
        when(serverConfig.getExternalTrainingBulkUploadTable()).thenReturn("bulkUploadTable");
        when(cassandraOperation.getRecordsByProperties(eq(Constants.KEYSPACE_SUNBIRD), eq("bulkUploadTable"), anyMap(), anyList(), isNull()))
                .thenReturn(Collections.emptyList());
        when(serverConfig.getExternalTrainingBulkUploadContainerName()).thenReturn("container1");

        ApiResponse uploadResponse = new ApiResponse();
        uploadResponse.setResponseCode(HttpStatus.OK);
        uploadResponse.getResult().put(Constants.NAME, "file.csv");
        uploadResponse.getResult().put(Constants.URL, "http://file");
        when(storageService.uploadFile(any(MultipartFile.class), anyString())).thenReturn(uploadResponse);

        ApiResponse insertResponse = new ApiResponse();
        insertResponse.put(Constants.RESPONSE, "FAILURE");
        when(cassandraOperation.insertRecord(eq(Constants.KEYSPACE_SUNBIRD), eq("bulkUploadTable"), anyMap()))
                .thenReturn(insertResponse);

        MockMultipartFile file = new MockMultipartFile("file", "test.csv", "text/csv", "Email\na@a.com\n".getBytes(StandardCharsets.UTF_8));
        ApiResponse result = service.externalTrainingUserBulkUpload(file, "event1", "batch1", "token");

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, result.getResponseCode());
        assertTrue(result.getParams().getErrMsg().contains("Failed to update database"));
    }

    @Test
    void testBulkUpload_success() throws IOException {
        when(accessTokenValidator.fetchUserIdFromAccessToken(anyString(), any(ApiResponse.class))).thenReturn("user1");
        Map<String, Object> userMap = new HashMap<>();
        userMap.put(Constants.ROOT_ORG_ID, "org1");
        when(userAndOrgService.readUserProfileFromDB(eq("user1"), anyList())).thenReturn(userMap);
        when(cassandraOperation.getRecordsByProperties(eq(Constants.KEYSPACE_SUNBIRD_COURSE), eq(Constants.EVENT_BATCH_TABLE), anyMap(), isNull(), isNull()))
                .thenReturn(List.of(Map.of("id", "batchRow")));
        when(serverConfig.getExternalTrainingBatchSize()).thenReturn(10);
        when(serverConfig.getExternalTrainingBulkUploadTable()).thenReturn("bulkUploadTable");
        when(cassandraOperation.getRecordsByProperties(eq(Constants.KEYSPACE_SUNBIRD), eq("bulkUploadTable"), anyMap(), anyList(), isNull()))
                .thenReturn(Collections.emptyList());
        when(serverConfig.getExternalTrainingBulkUploadContainerName()).thenReturn("container1");

        ApiResponse uploadResponse = new ApiResponse();
        uploadResponse.setResponseCode(HttpStatus.OK);
        uploadResponse.getResult().put(Constants.NAME, "file.csv");
        uploadResponse.getResult().put(Constants.URL, "http://file");
        when(storageService.uploadFile(any(MultipartFile.class), anyString())).thenReturn(uploadResponse);

        ApiResponse insertResponse = new ApiResponse();
        insertResponse.put(Constants.RESPONSE, Constants.SUCCESS);
        when(cassandraOperation.insertRecord(eq(Constants.KEYSPACE_SUNBIRD), eq("bulkUploadTable"), anyMap()))
                .thenReturn(insertResponse);
        when(serverConfig.getExternalTrainingBulkUploadTopic()).thenReturn("topic1");

        MockMultipartFile file = new MockMultipartFile("file", "test.csv", "text/csv", "Email\na@a.com\n".getBytes(StandardCharsets.UTF_8));
        ApiResponse result = service.externalTrainingUserBulkUpload(file, "event1", "batch1", "token");

        assertEquals(HttpStatus.OK, result.getResponseCode());
        assertEquals(Constants.SUCCESS, result.getParams().getStatus());
        assertEquals("org1", result.getResult().get(Constants.ORD_ID));
        verify(kafkaTemplate).send(eq("topic1"), anyString());
    }

    @Test
    void testBulkUpload_unexpectedException() {
        when(accessTokenValidator.fetchUserIdFromAccessToken(anyString(), any(ApiResponse.class)))
                .thenThrow(new RuntimeException("boom"));

        ApiResponse result = service.externalTrainingUserBulkUpload(mock(MultipartFile.class), "event1", "batch1", "token");

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, result.getResponseCode());
        assertEquals(Constants.FAILED, result.getParams().getStatus());
    }

    // ===========================
    // externalTrainingUserBulkUploadStatus
    // ===========================

    @Test
    void testBulkUploadStatus_blankUserId() {
        when(accessTokenValidator.fetchUserIdFromAccessToken(anyString(), any(ApiResponse.class))).thenReturn("");
        ApiResponse result = service.externalTrainingUserBulkUploadStatus("event1", "batch1", "token");
        assertNotNull(result);
        verifyNoInteractions(cassandraOperation);
    }

    @Test
    void testBulkUploadStatus_successWithEventId() {
        when(accessTokenValidator.fetchUserIdFromAccessToken(anyString(), any(ApiResponse.class))).thenReturn("user1");
        Map<String, Object> userMap = new HashMap<>();
        userMap.put(Constants.ROOT_ORG_ID, "org1");
        when(userAndOrgService.readUserProfileFromDB(eq("user1"), anyList())).thenReturn(userMap);
        when(serverConfig.getExternalTrainingBulkUploadTable()).thenReturn("bulkUploadTable");
        when(cassandraOperation.getRecordsByProperties(eq(Constants.KEYSPACE_SUNBIRD), eq("bulkUploadTable"), anyMap(), isNull(), isNull()))
                .thenReturn(List.of(Map.of("id", "row1")));

        ApiResponse result = service.externalTrainingUserBulkUploadStatus("event1", "batch1", "token");

        assertEquals(HttpStatus.OK, result.getResponseCode());
        assertEquals(1, result.getResult().get(Constants.COUNT));
    }

    @Test
    void testBulkUploadStatus_blankEventId_emptyPropertyMap() {
        when(accessTokenValidator.fetchUserIdFromAccessToken(anyString(), any(ApiResponse.class))).thenReturn("user1");
        Map<String, Object> userMap = new HashMap<>();
        userMap.put(Constants.ROOT_ORG_ID, "org1");
        when(userAndOrgService.readUserProfileFromDB(eq("user1"), anyList())).thenReturn(userMap);
        when(serverConfig.getExternalTrainingBulkUploadTable()).thenReturn("bulkUploadTable");
        when(cassandraOperation.getRecordsByProperties(eq(Constants.KEYSPACE_SUNBIRD), eq("bulkUploadTable"), eq(new HashMap<>()), isNull(), isNull()))
                .thenReturn(null);

        ApiResponse result = service.externalTrainingUserBulkUploadStatus(null, "batch1", "token");

        assertEquals(HttpStatus.OK, result.getResponseCode());
        assertEquals(0, result.getResult().get(Constants.COUNT));
    }

    @Test
    void testBulkUploadStatus_userProfileEmpty() {
        when(accessTokenValidator.fetchUserIdFromAccessToken(anyString(), any(ApiResponse.class))).thenReturn("user1");
        when(userAndOrgService.readUserProfileFromDB(eq("user1"), anyList())).thenReturn(Collections.emptyMap());

        ApiResponse result = service.externalTrainingUserBulkUploadStatus("event1", "batch1", "token");

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, result.getResponseCode());
        assertEquals(Constants.FAILED, result.getParams().getStatus());
        assertTrue(result.getParams().getErrMsg().contains("Failed to read user details from DB"));
        verifyNoInteractions(cassandraOperation);
    }

    @Test
    void testBulkUploadStatus_unexpectedException() {
        when(accessTokenValidator.fetchUserIdFromAccessToken(anyString(), any(ApiResponse.class)))
                .thenThrow(new RuntimeException("boom"));

        ApiResponse result = service.externalTrainingUserBulkUploadStatus("event1", "batch1", "token");

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, result.getResponseCode());
        assertEquals(Constants.FAILED, result.getParams().getStatus());
    }

    // ===========================
    // downloadFile
    // ===========================

    @Test
    void testDownloadFile_blankUserId() {
        when(accessTokenValidator.fetchUserIdFromAccessToken(anyString(), any(ApiResponse.class))).thenReturn("");
        ResponseEntity<Object> result = service.downloadFile("sample.csv", "token");
        assertEquals(HttpStatus.UNAUTHORIZED, result.getStatusCode());
    }

    @Test
    void testDownloadFile_ioExceptionReturnsServerError() {
        when(accessTokenValidator.fetchUserIdFromAccessToken(anyString(), any(ApiResponse.class))).thenReturn("user1");

        String missingFile = "does-not-exist-" + UUID.randomUUID() + ".csv";
        ResponseEntity<Object> result = service.downloadFile(missingFile, "token");

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, result.getStatusCode());
    }

    @Test
    void testDownloadFile_success() throws IOException {
        when(accessTokenValidator.fetchUserIdFromAccessToken(anyString(), any(ApiResponse.class))).thenReturn("user1");

        String fileName = "present-" + UUID.randomUUID() + ".csv";
        java.nio.file.Path tmpPath = java.nio.file.Paths.get(Constants.LOCAL_BASE_PATH + fileName);
        java.nio.file.Files.createDirectories(tmpPath.getParent());
        java.nio.file.Files.write(tmpPath, "Email\na@a.com\n".getBytes(StandardCharsets.UTF_8));

        try {
            ResponseEntity<Object> result = service.downloadFile(fileName, "token");

            assertEquals(HttpStatus.OK, result.getStatusCode());
            assertTrue(result.getHeaders().getFirst(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION).contains(fileName));
            verify(storageService).downloadFile(eq(fileName), any());
        } finally {
            java.nio.file.Files.deleteIfExists(tmpPath);
        }
    }

    // ===========================
    // downloadBulkUploadSampleFile
    // ===========================

    @Test
    void testDownloadBulkUploadSampleFile_ioExceptionReturnsServerError() {
        String missingFile = "missing-sample-" + UUID.randomUUID() + ".csv";
        when(serverConfig.getExternalTrainingUserBulkUploadSampleFileName()).thenReturn(missingFile);

        ResponseEntity<org.springframework.core.io.Resource> result = service.downloadBulkUploadSampleFile();

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, result.getStatusCode());
    }

    @Test
    void testDownloadBulkUploadSampleFile_success() throws IOException {
        String fileName = "sample-" + UUID.randomUUID() + ".csv";
        when(serverConfig.getExternalTrainingUserBulkUploadSampleFileName()).thenReturn(fileName);

        java.nio.file.Path filePath = java.nio.file.Paths.get(Constants.LOCAL_BASE_PATH, fileName);
        java.nio.file.Files.createDirectories(filePath.getParent());
        java.nio.file.Files.write(filePath, "Email\na@a.com\n".getBytes(StandardCharsets.UTF_8));

        try {
            ResponseEntity<org.springframework.core.io.Resource> result = service.downloadBulkUploadSampleFile();

            assertEquals(HttpStatus.OK, result.getStatusCode());
            assertTrue(result.getHeaders().getFirst(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION).contains(fileName));
            verify(storageService).downloadFile(eq(fileName), any());
            // service deletes the temp file itself in the finally block
            assertFalse(java.nio.file.Files.exists(filePath));
        } finally {
            java.nio.file.Files.deleteIfExists(filePath);
        }
    }
}
