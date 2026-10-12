package com.igot.cb.storage.service;

import com.igot.cb.model.ApiResponse;
import com.igot.cb.util.CbExtServerProperties;
import com.igot.cb.util.Constants;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.multipart.MultipartFile;
import org.sunbird.cloud.storage.BaseStorageService;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StorageServiceImplTest {

    @Mock
    private CbExtServerProperties serverProperties;

    @Mock
    private BaseStorageService baseStorageService;

    @Mock
    private MultipartFile multipartFile;

    private StorageServiceImpl storageServiceImpl;

    private final List<File> filesToCleanup = new ArrayList<>();

    @BeforeEach
    void setup() throws Exception {
        storageServiceImpl = new StorageServiceImpl(serverProperties);
        Field field = StorageServiceImpl.class.getDeclaredField("storageService");
        field.setAccessible(true);
        field.set(storageServiceImpl, baseStorageService);
    }

    @AfterEach
    void cleanup() {
        for (File file : filesToCleanup) {
            if (file.exists()) {
                file.delete();
            }
        }
    }

    @Test
    void testUploadFile_MultipartFile_Success() throws Exception {
        when(multipartFile.getOriginalFilename()).thenReturn("test.txt");
        when(multipartFile.getBytes()).thenReturn("content".getBytes());
        when(baseStorageService.upload(anyString(), anyString(), anyString(), any(), any(), any(), any()))
                .thenReturn("http://cloud/test.txt");

        ApiResponse response = storageServiceImpl.uploadFile(multipartFile, "folder", "container");

        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getResponseCode());
        assertEquals("http://cloud/test.txt", response.getResult().get(Constants.URL));
    }

    @Test
    void testUploadFile_MultipartFile_TwoArgOverload_UsesConfiguredContainer() throws Exception {
        when(serverProperties.getCloudContainerName()).thenReturn("default-container");
        when(multipartFile.getOriginalFilename()).thenReturn("test2.txt");
        when(multipartFile.getBytes()).thenReturn("content".getBytes());
        when(baseStorageService.upload(eq("default-container"), anyString(), anyString(), any(), any(), any(), any()))
                .thenReturn("http://cloud/test2.txt");

        ApiResponse response = storageServiceImpl.uploadFile(multipartFile, "folder");

        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getResponseCode());
    }

    @Test
    void testUploadFile_MultipartFile_ExceptionDuringRead_ReturnsFailure() throws Exception {
        when(multipartFile.getOriginalFilename()).thenReturn("bad.txt");
        when(multipartFile.getBytes()).thenThrow(new IOException("disk error"));

        ApiResponse response = storageServiceImpl.uploadFile(multipartFile, "folder", "container");

        assertNotNull(response);
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getResponseCode());
        assertEquals(Constants.FAILED, response.getParams().getStatus());
        assertTrue(response.getParams().getErrMsg().contains("disk error"));
    }

    @Test
    void testUploadFile_File_Success() {
        File file = new File("storage-test-" + System.nanoTime() + ".txt");
        filesToCleanup.add(file);
        when(baseStorageService.upload(eq("container"), anyString(), anyString(), any(), any(), any(), any()))
                .thenReturn("http://cloud/file.txt");

        ApiResponse response = storageServiceImpl.uploadFile(file, "folder", "container");

        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getResponseCode());
        assertEquals("http://cloud/file.txt", response.getResult().get(Constants.URL));
    }

    @Test
    void testUploadFile_File_ExceptionDuringUpload_ReturnsFailure() {
        File file = new File("storage-test-" + System.nanoTime() + ".txt");
        filesToCleanup.add(file);
        when(baseStorageService.upload(anyString(), anyString(), anyString(), any(), any(), any(), any()))
                .thenThrow(new RuntimeException("upload failed"));

        ApiResponse response = storageServiceImpl.uploadFile(file, "folder", "container");

        assertNotNull(response);
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getResponseCode());
        assertEquals(Constants.FAILED, response.getParams().getStatus());
        assertTrue(response.getParams().getErrMsg().contains("upload failed"));
    }

    @Test
    void testDownloadFile_Success() {
        when(serverProperties.getCloudContainerName()).thenReturn("container");
        when(serverProperties.getLocalBasePath()).thenReturn("/tmp");

        ApiResponse response = storageServiceImpl.downloadFile("file.txt", "container");

        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getResponseCode());
        verify(baseStorageService).download(eq("container"), eq("container/file.txt"), eq("/tmp"), any());
    }

    @Test
    void testDownloadFile_ExceptionDuringDownload_ReturnsFailure() {
        when(serverProperties.getCloudContainerName()).thenReturn("container");
        when(serverProperties.getLocalBasePath()).thenReturn("/tmp");
        doThrow(new RuntimeException("download failed"))
                .when(baseStorageService).download(anyString(), anyString(), anyString(), any());

        ApiResponse response = storageServiceImpl.downloadFile("file.txt", "container");

        assertNotNull(response);
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getResponseCode());
        assertEquals(Constants.FAILED, response.getParams().getStatus());
        assertTrue(response.getParams().getErrMsg().contains("download failed"));
    }
}
