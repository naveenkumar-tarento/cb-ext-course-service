package com.igot.cb.controller;

import com.igot.cb.model.ApiResponse;
import com.igot.cb.service.ExternalTrainingService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExternalTrainingControllerTest {

    @Mock
    private ExternalTrainingService externalTrainingService;

    @InjectMocks
    private ExternalTrainingController externalTrainingController;

    @Test
    void testExternalTrainingUserBulkUpload_success() {
        MultipartFile file = new MockMultipartFile("file", "test.csv", "text/csv", "Email\na@a.com\n".getBytes());
        ApiResponse mockResponse = new ApiResponse();
        mockResponse.setResponseCode(HttpStatus.OK);

        when(externalTrainingService.externalTrainingUserBulkUpload(file, "event1", "batch1", "token"))
                .thenReturn(mockResponse);

        ResponseEntity<ApiResponse> result = externalTrainingController.externalTrainingUserBulkUpload(file, "event1", "batch1", "token");

        assertEquals(HttpStatus.OK, result.getStatusCode());
        assertSame(mockResponse, result.getBody());
        verify(externalTrainingService).externalTrainingUserBulkUpload(file, "event1", "batch1", "token");
    }

    @Test
    void testExternalTrainingUserBulkUpload_failure() {
        MultipartFile file = new MockMultipartFile("file", "test.txt", "text/plain", "data".getBytes());
        ApiResponse mockResponse = new ApiResponse();
        mockResponse.setResponseCode(HttpStatus.INTERNAL_SERVER_ERROR);

        when(externalTrainingService.externalTrainingUserBulkUpload(file, "event1", "batch1", "token"))
                .thenReturn(mockResponse);

        ResponseEntity<ApiResponse> result = externalTrainingController.externalTrainingUserBulkUpload(file, "event1", "batch1", "token");

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, result.getStatusCode());
        assertSame(mockResponse, result.getBody());
    }

    @Test
    void testExternalTrainingUserBulkUploadStatus_success() {
        ApiResponse mockResponse = new ApiResponse();
        mockResponse.setResponseCode(HttpStatus.OK);

        when(externalTrainingService.externalTrainingUserBulkUploadStatus("event1", "batch1", "token"))
                .thenReturn(mockResponse);

        ResponseEntity<ApiResponse> result = externalTrainingController.externalTrainingUserBulkUploadStatus("event1", "batch1", "token");

        assertEquals(HttpStatus.OK, result.getStatusCode());
        assertSame(mockResponse, result.getBody());
        verify(externalTrainingService).externalTrainingUserBulkUploadStatus("event1", "batch1", "token");
    }

    @Test
    void testExternalTrainingUserBulkUploadStatus_failure() {
        ApiResponse mockResponse = new ApiResponse();
        mockResponse.setResponseCode(HttpStatus.INTERNAL_SERVER_ERROR);

        when(externalTrainingService.externalTrainingUserBulkUploadStatus("event1", "batch1", "token"))
                .thenReturn(mockResponse);

        ResponseEntity<ApiResponse> result = externalTrainingController.externalTrainingUserBulkUploadStatus("event1", "batch1", "token");

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, result.getStatusCode());
    }

    @Test
    void testDownloadFile() {
        ResponseEntity<Object> mockResponse = ResponseEntity.ok().body(new ByteArrayResource("data".getBytes()));

        when(externalTrainingService.downloadFile("sample.csv", "token")).thenReturn(mockResponse);

        ResponseEntity<Object> result = externalTrainingController.downloadFile("sample.csv", "token");

        assertSame(mockResponse, result);
        verify(externalTrainingService).downloadFile("sample.csv", "token");
    }

    @Test
    void testDownloadFile_unauthorized() {
        ApiResponse errorBody = new ApiResponse();
        errorBody.setResponseCode(HttpStatus.UNAUTHORIZED);
        ResponseEntity<Object> mockResponse = ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(errorBody);

        when(externalTrainingService.downloadFile("sample.csv", "badtoken")).thenReturn(mockResponse);

        ResponseEntity<Object> result = externalTrainingController.downloadFile("sample.csv", "badtoken");

        assertEquals(HttpStatus.UNAUTHORIZED, result.getStatusCode());
    }

    @Test
    void testDownloadBulkUploadSampleFile() {
        ResponseEntity<Resource> mockResponse = ResponseEntity.ok().body(new ByteArrayResource("sample".getBytes()));

        when(externalTrainingService.downloadBulkUploadSampleFile()).thenReturn(mockResponse);

        ResponseEntity<Resource> result = externalTrainingController.downloadBulkUploadSampleFile();

        assertSame(mockResponse, result);
        verify(externalTrainingService).downloadBulkUploadSampleFile();
    }

    @Test
    void testDownloadBulkUploadSampleFile_serverError() {
        ResponseEntity<Resource> mockResponse = ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();

        when(externalTrainingService.downloadBulkUploadSampleFile()).thenReturn(mockResponse);

        ResponseEntity<Resource> result = externalTrainingController.downloadBulkUploadSampleFile();

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, result.getStatusCode());
    }
}
