package com.igot.cb.controller;

import com.igot.cb.model.ApiResponse;
import com.igot.cb.service.ExternalTrainingService;
import com.igot.cb.util.Constants;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/externaltraining/v1")
public class ExternalTrainingController {

    private final ExternalTrainingService externalTrainingService;

    public ExternalTrainingController(ExternalTrainingService externalTrainingService) {
        this.externalTrainingService = externalTrainingService;
    }

    @PostMapping("/bulkupload/{eventId}/{batchId}")
    public ResponseEntity<ApiResponse> externalTrainingUserBulkUpload(@RequestParam("file") MultipartFile multipartFile, @PathVariable(value = "eventId") String eventId, @PathVariable("batchId") String batchId, @RequestHeader(Constants.X_AUTH_TOKEN) String authToken) {
        ApiResponse uploadResponse = externalTrainingService.externalTrainingUserBulkUpload(multipartFile, eventId, batchId, authToken);
        return new ResponseEntity<>(uploadResponse, uploadResponse.getResponseCode());

    }
    @GetMapping("/bulkupload/status")
    public ResponseEntity<ApiResponse> externalTrainingUserBulkUploadStatus(@RequestParam("eventId") String eventId, @RequestParam("batchId") String batchId, @RequestHeader(Constants.X_AUTH_TOKEN) String authToken) {
        ApiResponse response = externalTrainingService.externalTrainingUserBulkUploadStatus(eventId, batchId, authToken);
        return new ResponseEntity<>(response, response.getResponseCode());
    }

    @GetMapping("/bulkupload/download/{fileName}")
    public ResponseEntity<Object> downloadFile(@PathVariable("fileName") String fileName, @RequestHeader(Constants.X_AUTH_TOKEN) String authToken) {
        return externalTrainingService.downloadFile(fileName, authToken);
    }

    @GetMapping("/bulkupload/sample")
    public ResponseEntity<Resource> downloadBulkUploadSampleFile() {
        return externalTrainingService.downloadBulkUploadSampleFile();
    }

}
