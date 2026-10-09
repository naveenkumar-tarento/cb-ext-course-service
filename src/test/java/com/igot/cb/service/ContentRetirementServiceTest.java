package com.igot.cb.service;

import com.igot.cb.cassandra.CassandraOperation;
import com.igot.cb.model.ApiResponse;
import com.igot.cb.util.CbExtServerProperties;
import com.igot.cb.util.Constants;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ContentRetirementServiceTest {

    @Mock
    private CassandraOperation cassandraOperation;

    @Mock
    private ContentInfoServiceImpl contentService;


    @Mock
    private NotificationService notificationService;

    @Mock
    private OutboundRequestHandlerServiceImpl outboundRequestHandlerService;

    @Mock
    private CbExtServerProperties props;

    @InjectMocks
    private ContentRetirementService contentRetirementService;

    @Test
    void processDueRetirements_NoRecords_ShouldReturnEmptyList() {
        when(cassandraOperation.getRecordsByProperties(any(), any(), any(), any(), any()))
                .thenReturn(Collections.emptyList());

        ApiResponse response = contentRetirementService.processDueRetirements();

        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getResponseCode());
        assertTrue(response.getResult().containsKey(Constants.CONTENT));
        assertTrue(((List<?>) response.getResult().get(Constants.CONTENT)).isEmpty());
        verifyNoInteractions(contentService);
    }

    @Test
    void processDueRetirements_WithDueContent_ShouldRetireContent() {
        Map<String, Object> recordData = createRetirementRecord("content123", "request123", LocalDate.now().minusDays(1));
        when(cassandraOperation.getRecordsByProperties(any(), any(), any(), any(), any()))
                .thenReturn(Arrays.asList(recordData));
        when(contentService.retireContent("content123"))
                .thenReturn(Map.of("status", "success"));

        ApiResponse response = contentRetirementService.processDueRetirements();

        assertNotNull(response);
        List<Map<String, Object>> contentList = (List<Map<String, Object>>) response.getResult().get(Constants.CONTENT);
        assertEquals(1, contentList.size());
        assertEquals("content123", contentList.get(0).get(Constants.CONTENT_ID));
        assertEquals(true, contentList.get(0).get(Constants.RETIRED));
        
        verify(contentService).retireContent("content123");
        verify(cassandraOperation).updateRecord(eq(Constants.KEYSPACE_SUNBIRD_COURSE), 
                eq(Constants.CONTENT_RETIREMENT_REQUEST_TABLE), any(Map.class), any(Map.class));
    }

    @Test
    void processDueRetirements_WithFutureRetirementDate_ShouldNotRetire() {
        Map<String, Object> recordData = createRetirementRecord("content123", "request123", LocalDate.now().plusDays(1));
        when(cassandraOperation.getRecordsByProperties(any(), any(), any(), any(), any()))
                .thenReturn(Arrays.asList(recordData));

        ApiResponse response = contentRetirementService.processDueRetirements();

        List<Map<String, Object>> contentList = (List<Map<String, Object>>) response.getResult().get(Constants.CONTENT);
        assertTrue(contentList.isEmpty());
        verifyNoInteractions(contentService);
        verify(cassandraOperation, never()).updateRecord(any(), any(), any(), any());
    }

    @Test
    void processDueRetirements_WithNullRetirementDate_ShouldNotRetire() {
        Map<String, Object> recordData = createRetirementRecord("content123", "request123", null);
        when(cassandraOperation.getRecordsByProperties(any(), any(), any(), any(), any()))
                .thenReturn(Arrays.asList(recordData));

        ApiResponse response = contentRetirementService.processDueRetirements();

        List<Map<String, Object>> contentList = (List<Map<String, Object>>) response.getResult().get(Constants.CONTENT);
        assertTrue(contentList.isEmpty());
        verifyNoInteractions(contentService);
    }

    @Test
    void processDueRetirements_RetireContentReturnsEmpty_ShouldNotUpdateRecord() {
        Map<String, Object> recordData = createRetirementRecord("content123", "request123", LocalDate.now());
        when(cassandraOperation.getRecordsByProperties(any(), any(), any(), any(), any()))
                .thenReturn(Arrays.asList(recordData));
        when(contentService.retireContent("content123"))
                .thenReturn(Collections.emptyMap());

        ApiResponse response = contentRetirementService.processDueRetirements();

        List<Map<String, Object>> contentList = (List<Map<String, Object>>) response.getResult().get(Constants.CONTENT);
        assertEquals(1, contentList.size());
        assertEquals(false, contentList.get(0).get(Constants.RETIRED));
        assertEquals("Retirement API returned empty response", contentList.get(0).get(Constants.MESSAGE));
        
        verify(contentService).retireContent("content123");
        verify(cassandraOperation, never()).updateRecord(any(), any(), any(), any());
    }

    @Test
    void processDueRetirements_ExceptionDuringRetirement_ShouldHandleGracefully() {
        Map<String, Object> recordData = createRetirementRecord("content123", "request123", LocalDate.now());
        when(cassandraOperation.getRecordsByProperties(any(), any(), any(), any(), any()))
                .thenReturn(Arrays.asList(recordData));
        when(contentService.retireContent("content123"))
                .thenThrow(new RuntimeException("Service error"));

        ApiResponse response = contentRetirementService.processDueRetirements();

        List<Map<String, Object>> contentList = (List<Map<String, Object>>) response.getResult().get(Constants.CONTENT);
        assertEquals(1, contentList.size());
        assertEquals(false, contentList.get(0).get(Constants.RETIRED));
        assertEquals("Service error", contentList.get(0).get(Constants.MESSAGE));
        
        verify(cassandraOperation, never()).updateRecord(any(), any(), any(), any());
    }

    @Test
    void processDueRetirements_MultipleRecords_ShouldProcessAll() {
        Map<String, Object> record1 = createRetirementRecord("content1", "request1", LocalDate.now().minusDays(1));
        Map<String, Object> record2 = createRetirementRecord("content2", "request2", LocalDate.now());
        Map<String, Object> record3 = createRetirementRecord("content3", "request3", LocalDate.now().plusDays(1));
        
        when(cassandraOperation.getRecordsByProperties(any(), any(), any(), any(), any()))
                .thenReturn(Arrays.asList(record1, record2, record3));
        when(contentService.retireContent(anyString()))
                .thenReturn(Map.of("status", "success"));

        ApiResponse response = contentRetirementService.processDueRetirements();

        List<Map<String, Object>> contentList = (List<Map<String, Object>>) response.getResult().get(Constants.CONTENT);
        assertEquals(2, contentList.size());
        
        verify(contentService).retireContent("content1");
        verify(contentService).retireContent("content2");
        verify(contentService, never()).retireContent("content3");
    }

    @Test
    void processDueRetirements_ShouldCallCassandraWithCorrectParameters() {
        when(cassandraOperation.getRecordsByProperties(any(), any(), any(), any(), any()))
                .thenReturn(Collections.emptyList());

        contentRetirementService.processDueRetirements();

        verify(cassandraOperation).getRecordsByProperties(
                eq(Constants.KEYSPACE_SUNBIRD_COURSE),
                eq(Constants.CONTENT_RETIREMENT_BY_RETIREMENT_DATE_TABLE),
                argThat(map -> map.containsKey(Constants.RETIREMENT_DATE_KEY)),
                argThat(fields ->
                        fields.contains(Constants.CONTENT_ID_KEY) &&
                                fields.contains(Constants.REQUEST_ID_KEY) &&
                                fields.contains(Constants.RETIREMENT_DATE_KEY) &&
                                fields.contains(Constants.STATUS)
                ),
                isNull()
        );
    }

    private Map<String, Object> createRetirementRecord(String contentId, String requestId, LocalDate retirementDate) {
        Map<String, Object> recordData = new HashMap<>();
        recordData.put(Constants.CONTENT_ID, contentId);
        recordData.put(Constants.REQUEST_ID, requestId);
        recordData.put(Constants.RETIREMENT_DATE, retirementDate);
        recordData.put(Constants.STATUS, Constants.APPROVED);
        return recordData;
    }

    @Test
    void sendContentRetirementNotifications_NoRetirementRequests_ShouldDoNothing() {
        when(cassandraOperation.getRecordsByProperties(any(), any(), any(), any(), any()))
                .thenReturn(Collections.emptyList());

        contentRetirementService.sendContentRetirementNotifications();

        verifyNoInteractions(contentService);
        verifyNoInteractions(notificationService);
    }

    @Test
    void sendContentRetirementNotifications_ApprovedToday_ShouldSendApprovedNotification() {
        LocalDate today = LocalDate.now();

        Map<String, Object> recordData = new HashMap<>();
        recordData.put(Constants.CONTENT_ID, "content1");
        recordData.put(Constants.STATUS, Constants.APPROVED);
        recordData.put(Constants.APPROVED_DATE, today);
        recordData.put(Constants.RETIREMENT_DATE, today.plusDays(10));

        // Approved-date lookup
        when(cassandraOperation.getRecordsByProperties(
                eq(Constants.KEYSPACE_SUNBIRD_COURSE),
                eq(Constants.CONTENT_RETIREMENT_BY_APPROVED_DATE_TABLE),
                any(), any(), any()
        )).thenReturn(List.of(recordData));

        // Retirement-date lookup (not used here, but called)
        when(cassandraOperation.getRecordsByProperties(
                eq(Constants.KEYSPACE_SUNBIRD_COURSE),
                eq(Constants.CONTENT_RETIREMENT_BY_RETIREMENT_DATE_TABLE),
                any(), any(), any()
        )).thenReturn(Collections.emptyList());

        // REQUIRED: content with batches
        when(contentService.readContent(eq("content1"), any()))
                .thenReturn(Map.of(
                        Constants.NAME, "Test Course",
                        "batches", List.of(Map.of(Constants.BATCH_ID, "batch1"))
                ));

        //REQUIRED: batch users
        when(cassandraOperation.getRecordsByProperties(
                eq(Constants.KEYSPACE_SUNBIRD_COURSE),
                eq(Constants.ENROLLMENT_BATCH_LOOKUP),
                any(), any(), any()
        )).thenReturn(List.of(Map.of(Constants.USER_ID, "user1")));

        //REQUIRED: eligible enrolment
        when(cassandraOperation.getRecordsByProperties(
                eq(Constants.KEYSPACE_SUNBIRD_COURSE),
                eq(Constants.USER_ENROLMENTS_V2_TABLE),
                any(), any(), any()
        )).thenReturn(List.of(Map.of(
                Constants.STATUS, 1,
                Constants.ACTIVE, true,
                Constants.ISSUED_CERTIFICATES, Collections.emptyList()
        )));

        contentRetirementService.sendContentRetirementNotifications();
        verify(notificationService).sendNotificationForContentRetirement(
                "content1",
                "Test Course",
                today.plusDays(10),
                List.of("user1"),
                Constants.CONTENT_RETIREMENT_APPROVED_NOTIFICATION
        );
    }

    @Test
    void sendContentRetirementNotifications_SevenDaysBefore_ShouldSendSevenDayReminder() {
        LocalDate today = LocalDate.now();

        Map<String, Object> recordData = new HashMap<>();
        recordData.put(Constants.CONTENT_ID, "content2");
        recordData.put(Constants.STATUS, Constants.APPROVED);
        recordData.put(Constants.RETIREMENT_DATE, today.plusDays(7));

        when(cassandraOperation.getRecordsByProperties(
                eq(Constants.KEYSPACE_SUNBIRD_COURSE),
                eq(Constants.CONTENT_RETIREMENT_BY_RETIREMENT_DATE_TABLE),
                any(), any(), any()
        )).thenReturn(List.of(recordData));

        when(cassandraOperation.getRecordsByProperties(
                eq(Constants.KEYSPACE_SUNBIRD_COURSE),
                eq(Constants.CONTENT_RETIREMENT_BY_APPROVED_DATE_TABLE),
                any(), any(), any()
        )).thenReturn(Collections.emptyList());

        when(contentService.readContent(eq("content2"), any()))
                .thenReturn(Map.of(
                        Constants.NAME, "Test Course",
                        "batches", List.of(Map.of(Constants.BATCH_ID, "batch1"))
                ));

        when(cassandraOperation.getRecordsByProperties(
                eq(Constants.KEYSPACE_SUNBIRD_COURSE),
                eq(Constants.ENROLLMENT_BATCH_LOOKUP),
                any(), any(), any()
        )).thenReturn(List.of(Map.of(Constants.USER_ID, "user1")));

        when(cassandraOperation.getRecordsByProperties(
                eq(Constants.KEYSPACE_SUNBIRD_COURSE),
                eq(Constants.USER_ENROLMENTS_V2_TABLE),
                any(), any(), any()
        )).thenReturn(List.of(Map.of(
                Constants.STATUS, 1,
                Constants.ACTIVE, true,
                Constants.ISSUED_CERTIFICATES, Collections.emptyList()
        )));

        contentRetirementService.sendContentRetirementNotifications();
        verify(notificationService).sendNotificationForContentRetirement(
                "content2",
                "Test Course",
                today.plusDays(7),
                List.of("user1"),
                Constants.REMINDER_NOTIFICATION_SEVEN_DAY
        );
    }

    @Test
    void sendContentRetirementNotifications_OneDayBefore_ShouldSendOneDayReminder() {
        LocalDate today = LocalDate.now();

        Map<String, Object> recordData = new HashMap<>();
        recordData.put(Constants.CONTENT_ID, "content3");
        recordData.put(Constants.STATUS, Constants.APPROVED);
        recordData.put(Constants.RETIREMENT_DATE, today.plusDays(1));

        when(cassandraOperation.getRecordsByProperties(
                eq(Constants.KEYSPACE_SUNBIRD_COURSE),
                eq(Constants.CONTENT_RETIREMENT_BY_RETIREMENT_DATE_TABLE),
                any(), any(), any()
        )).thenReturn(List.of(recordData));

        when(cassandraOperation.getRecordsByProperties(
                eq(Constants.KEYSPACE_SUNBIRD_COURSE),
                eq(Constants.CONTENT_RETIREMENT_BY_APPROVED_DATE_TABLE),
                any(), any(), any()
        )).thenReturn(Collections.emptyList());

        when(contentService.readContent(eq("content3"), any()))
                .thenReturn(Map.of(
                        Constants.NAME, "Test Course",
                        "batches", List.of(Map.of(Constants.BATCH_ID, "batch1"))
                ));

        when(cassandraOperation.getRecordsByProperties(
                eq(Constants.KEYSPACE_SUNBIRD_COURSE),
                eq(Constants.ENROLLMENT_BATCH_LOOKUP),
                any(), any(), any()
        )).thenReturn(List.of(Map.of(Constants.USER_ID, "user1")));

        when(cassandraOperation.getRecordsByProperties(
                eq(Constants.KEYSPACE_SUNBIRD_COURSE),
                eq(Constants.USER_ENROLMENTS_V2_TABLE),
                any(), any(), any()
        )).thenReturn(List.of(Map.of(
                Constants.STATUS, 1,
                Constants.ACTIVE, true,
                Constants.ISSUED_CERTIFICATES, Collections.emptyList()
        )));

        contentRetirementService.sendContentRetirementNotifications();
        verify(notificationService).sendNotificationForContentRetirement(
                "content3",
                "Test Course",
                today.plusDays(1),
                List.of("user1"),
                Constants.REMINDER_NOTIFICATION_ONE_DAY
        );
    }

    @Test
    void sendContentRetirementNotifications_NoEligibleEnrolment_ShouldNotNotify() {
        LocalDate today = LocalDate.now();

        Map<String, Object> retirementRecord = new HashMap<>();
        retirementRecord.put(Constants.CONTENT_ID, "content4");
        retirementRecord.put(Constants.STATUS, Constants.APPROVED);
        retirementRecord.put(Constants.RETIREMENT_DATE, today.plusDays(7));

        // Retirement-date table
        when(cassandraOperation.getRecordsByProperties(
                eq(Constants.KEYSPACE_SUNBIRD_COURSE),
                eq(Constants.CONTENT_RETIREMENT_BY_RETIREMENT_DATE_TABLE),
                any(), any(), any()
        )).thenReturn(List.of(retirementRecord));

        // Approved-date table
        when(cassandraOperation.getRecordsByProperties(
                eq(Constants.KEYSPACE_SUNBIRD_COURSE),
                eq(Constants.CONTENT_RETIREMENT_BY_APPROVED_DATE_TABLE),
                any(), any(), any()
        )).thenReturn(Collections.emptyList());

        // Content has batches
        when(contentService.readContent(any(), any()))
                .thenReturn(Map.of(
                        Constants.NAME, "Test Course",
                        "batches", List.of(Map.of(Constants.BATCH_ID, "batch1"))
                ));

        when(cassandraOperation.getRecordsByProperties(
                eq(Constants.KEYSPACE_SUNBIRD_COURSE),
                eq(Constants.ENROLLMENT_BATCH_LOOKUP),
                any(), any(), any()))
                .thenReturn(List.of(Map.of(Constants.USER_ID, "user1")));

        when(cassandraOperation.getRecordsByProperties(
                eq(Constants.KEYSPACE_SUNBIRD_COURSE),
                eq(Constants.USER_ENROLMENTS_V2_TABLE),
                any(), any(), any()))
                .thenReturn(List.of(Map.of(
                        Constants.STATUS, 2,              // completed
                        Constants.ACTIVE, true,
                        Constants.ISSUED_CERTIFICATES, List.of("cert")
                )));

        contentRetirementService.sendContentRetirementNotifications();
        verifyNoInteractions(notificationService);
    }

    @Test
    void sendContentRetirementNotificationsToSpv_NoRequests_ShouldReturn() {
        when(cassandraOperation.getRecordsByProperties(any(), any(), any(), any(), any()))
                .thenReturn(Collections.emptyList());

        contentRetirementService.sendContentRetirementNotificationsToSpv();
        verifyNoInteractions(contentService);
        verifyNoInteractions(notificationService);
    }

    @Test
    void sendContentRetirementNotificationsToSpv_CreatedDateNotToday_ShouldSkip() {
        Map<String, Object> recordData = Map.of(
                Constants.CONTENT_ID, "do_123",
                Constants.CREATED_AT_FIELD, Instant.now().minus(1, ChronoUnit.DAYS),
                Constants.USER_ID_RAISED_FIELD, "user-1",
                Constants.RETIREMENT_DATE, LocalDate.now().plusDays(10)
        );
        when(cassandraOperation.getRecordsByProperties(
                any(), any(), any(), any(), any()))
                .thenReturn(List.of(recordData));

        when(props.getSbUrl()).thenReturn("http://localhost");
        when(props.getUserSearchEndPoint()).thenReturn("/user/search");

        Map<String, Object> user1 = Map.of(Constants.USER_ID, "spv-1");
        Map<String, Object> user2 = Map.of(Constants.USER_ID, "spv-2");

        Map<String, Object> spvResponse =
                Map.of(
                        Constants.RESPONSE_CODE, "OK",
                        Constants.RESULT, Map.of(
                                Constants.RESPONSE, Map.of(
                                        Constants.CONTENT, List.of(user1, user2)
                                )
                        )
                );
        when(outboundRequestHandlerService.fetchResultUsingPost(
                anyString(), any(), any()))
                .thenReturn(spvResponse);

        contentRetirementService.sendContentRetirementNotificationsToSpv();

        verify(notificationService, never())
                .sendNotificationForContentRetirementSpv(
                        any(), any(), any(), any(), any(), any(), any());

        verify(contentService, never())
                .readContent(anyString(), anyList());
    }

    @Test
    void sendContentRetirementNotificationsToSpv_ValidRequest_ShouldNotify() {
        LocalDate today = LocalDate.now();

        Map<String, Object> recordData = new HashMap<>();
        recordData.put(Constants.CONTENT_ID, "do_123");
        recordData.put(Constants.CREATED_DATE, today);
        recordData.put(Constants.USER_ID_RAISED_FIELD, "requester-1");
        recordData.put(Constants.RETIREMENT_DATE, today.plusDays(5));

        when(cassandraOperation.getRecordsByProperties(any(), any(), any(), any(), any()))
                .thenReturn(List.of(recordData));
        when(contentService.readContent(eq("do_123"), any()))
                .thenReturn(Map.of("name", "Sample Course"));
        mockSpvUsers(List.of("spv-1", "spv-2"));
        contentRetirementService.sendContentRetirementNotificationsToSpv();
        verify(notificationService).sendNotificationForContentRetirementSpv(
                eq("do_123"),
                eq("Sample Course"),
                argThat(list -> list.contains("spv-1") && list.contains("spv-2")),
                eq(Constants.CONTENT_RETIREMENT_SCHEDULED_NOTIFICATION),
                eq(today.plusDays(5)),
                argThat(emails -> emails.contains("spv-1@test.com")),
                eq("requester-1")
        );
    }

    @Test
    void sendContentRetirementNotificationsToSpv_NoRequester_ShouldNotifyOnlySpv() {
        LocalDate today = LocalDate.now();

        Map<String, Object> recordData = new HashMap<>();
        recordData.put(Constants.CONTENT_ID, "do_124");
        recordData.put(Constants.CREATED_DATE, today);
        recordData.put(Constants.RETIREMENT_DATE, today.plusDays(7));
        // NOTE: no USER_ID_RAISED_FIELD on purpose

        when(cassandraOperation.getRecordsByProperties(any(), any(), any(), any(), any()))
                .thenReturn(List.of(recordData));
        when(contentService.readContent(eq("do_124"), any()))
                .thenReturn(Map.of("name", "Course X"));
        mockSpvUsers(List.of("spv-1"));
        contentRetirementService.sendContentRetirementNotificationsToSpv();
        verify(notificationService).sendNotificationForContentRetirementSpv(
                eq("do_124"),
                eq("Course X"),
                argThat(list -> list.contains("spv-1")),
                eq(Constants.CONTENT_RETIREMENT_SCHEDULED_NOTIFICATION),
                eq(today.plusDays(7)),
                argThat(emails -> emails.contains("spv-1@test.com")),
                isNull()   
        );
    }

    @Test
    void sendContentRetirementNotificationsToSpv_NoSpvUsers_ShouldNotNotifyAnyone() {
        LocalDate today = LocalDate.now();
        Map<String, Object> recordData = Map.of(
                Constants.CONTENT_ID, "do_125",
                Constants.CREATED_AT_FIELD, today,
                Constants.USER_ID_RAISED_FIELD, "requester-2",
                Constants.RETIREMENT_DATE, today.plusDays(3)
        );
        when(cassandraOperation.getRecordsByProperties(any(), any(), any(), any(), any()))
                .thenReturn(List.of(recordData));
        mockSpvUsers(Collections.emptyList());
        contentRetirementService.sendContentRetirementNotificationsToSpv();
        verifyNoInteractions(notificationService);
    }


    @Test
    void sendContentRetirementNotificationsToSpv_RetirementDateInstant_ShouldConvert() {
        Map<String, Object> recordData = new HashMap<>();
        recordData.put(Constants.CONTENT_ID, "do_126");
        recordData.put(Constants.CREATED_DATE, Instant.now());
        recordData.put(Constants.USER_ID_RAISED_FIELD, "user-x");
        recordData.put(Constants.RETIREMENT_DATE, LocalDate.now().plusDays(10));

        when(cassandraOperation.getRecordsByProperties(any(), any(), any(), any(), any()))
                .thenReturn(List.of(recordData));
        when(contentService.readContent(any(), any()))
                .thenReturn(Map.of("name", "Course Z"));
        mockSpvUsers(List.of("spv"));

        contentRetirementService.sendContentRetirementNotificationsToSpv();
        verify(notificationService).sendNotificationForContentRetirementSpv(
                eq("do_126"),
                eq("Course Z"),
                argThat(list -> list.contains("spv")),
                eq(Constants.CONTENT_RETIREMENT_SCHEDULED_NOTIFICATION),
                any(LocalDate.class),        
                argThat(emails -> emails.contains("spv@test.com")),
                eq("user-x")
        );
    }


    private void mockSpvUsers(List<String> spvUserIds) {
        when(props.getSbUrl()).thenReturn("http://test");
        when(props.getUserSearchEndPoint()).thenReturn("/search");
        List<Map<String, Object>> contents = spvUserIds.stream()
                .map(id -> {
                    Map<String, Object> personalDetails = new HashMap<>();
                    personalDetails.put(Constants.PRIMARY_EMAIL, id + "@test.com");

                    Map<String, Object> profileDetails = new HashMap<>();
                    profileDetails.put(Constants.PERSONAL_DETAILS, personalDetails);

                    Map<String, Object> user = new HashMap<>();
                    user.put(Constants.USER_ID, id);
                    user.put(Constants.PROFILE_DETAILS, profileDetails);

                    return user;
                }).toList();
        Map<String, Object> response = new HashMap<>();
        response.put(Constants.RESPONSE_CODE, "OK");
        response.put(Constants.RESULT, Map.of(
                Constants.RESPONSE, Map.of(
                        Constants.CONTENT, contents
                )
        ));
        when(outboundRequestHandlerService.fetchResultUsingPost(
                eq("http://test/search"),
                any(),
                any()
        )).thenReturn(response);
    }

    // ==================== Additional coverage tests ====================

    @Test
    void processDueRetirements_NullStatus_ShouldSkipRecord() {
        Map<String, Object> recordData = new HashMap<>();
        recordData.put(Constants.CONTENT_ID, "content-null-status");
        recordData.put(Constants.REQUEST_ID, "request-null-status");
        recordData.put(Constants.RETIREMENT_DATE, LocalDate.now());
        recordData.put(Constants.STATUS, null);

        when(cassandraOperation.getRecordsByProperties(any(), any(), any(), any(), any()))
                .thenReturn(Arrays.asList(recordData));

        ApiResponse response = contentRetirementService.processDueRetirements();

        List<Map<String, Object>> contentList = (List<Map<String, Object>>) response.getResult().get(Constants.CONTENT);
        assertTrue(contentList.isEmpty());
        verifyNoInteractions(contentService);
    }

    @Test
    void processDueRetirements_NotificationLookupThrows_ShouldHandleGracefully() {
        Map<String, Object> recordData = createRetirementRecord("content-notify-fail", "request-notify-fail", LocalDate.now());
        when(cassandraOperation.getRecordsByProperties(any(), any(), any(), any(), any()))
                .thenReturn(Arrays.asList(recordData));
        when(contentService.retireContent("content-notify-fail"))
                .thenReturn(Map.of("status", "success"));
        when(contentService.readContent(eq("content-notify-fail"), any()))
                .thenThrow(new RuntimeException("content lookup failed"));

        ApiResponse response = contentRetirementService.processDueRetirements();

        List<Map<String, Object>> contentList = (List<Map<String, Object>>) response.getResult().get(Constants.CONTENT);
        assertEquals(1, contentList.size());
        assertEquals(true, contentList.get(0).get(Constants.RETIRED));
        verify(cassandraOperation).updateRecord(eq(Constants.KEYSPACE_SUNBIRD_COURSE),
                eq(Constants.CONTENT_RETIREMENT_REQUEST_TABLE), any(Map.class), any(Map.class));
        verifyNoInteractions(notificationService);
    }

    @Test
    void sendContentRetirementNotifications_NullStatus_ShouldSkipNotification() {
        LocalDate today = LocalDate.now();
        Map<String, Object> recordData = new HashMap<>();
        recordData.put(Constants.CONTENT_ID, "content-null-status");
        recordData.put(Constants.RETIREMENT_DATE, today.plusDays(1));
        recordData.put(Constants.STATUS, null);

        when(cassandraOperation.getRecordsByProperties(
                eq(Constants.KEYSPACE_SUNBIRD_COURSE),
                eq(Constants.CONTENT_RETIREMENT_BY_RETIREMENT_DATE_TABLE),
                any(), any(), any()
        )).thenReturn(List.of(recordData));

        when(cassandraOperation.getRecordsByProperties(
                eq(Constants.KEYSPACE_SUNBIRD_COURSE),
                eq(Constants.CONTENT_RETIREMENT_BY_APPROVED_DATE_TABLE),
                any(), any(), any()
        )).thenReturn(Collections.emptyList());

        contentRetirementService.sendContentRetirementNotifications();

        verifyNoInteractions(notificationService);
        verifyNoInteractions(contentService);
    }

    @Test
    void sendContentRetirementNotifications_RetirementDateNullOrNotMatching_ShouldNotTriggerNotification() {
        LocalDate today = LocalDate.now();

        Map<String, Object> nullDateRecord = new HashMap<>();
        nullDateRecord.put(Constants.CONTENT_ID, "content-null-date");
        nullDateRecord.put(Constants.STATUS, Constants.APPROVED);
        nullDateRecord.put(Constants.RETIREMENT_DATE, null);

        Map<String, Object> nonMatchingDateRecord = new HashMap<>();
        nonMatchingDateRecord.put(Constants.CONTENT_ID, "content-far-date");
        nonMatchingDateRecord.put(Constants.STATUS, Constants.APPROVED);
        nonMatchingDateRecord.put(Constants.RETIREMENT_DATE, today.plusDays(15));

        when(cassandraOperation.getRecordsByProperties(
                eq(Constants.KEYSPACE_SUNBIRD_COURSE),
                eq(Constants.CONTENT_RETIREMENT_BY_RETIREMENT_DATE_TABLE),
                any(), any(), any()
        )).thenReturn(List.of(nullDateRecord, nonMatchingDateRecord));

        when(cassandraOperation.getRecordsByProperties(
                eq(Constants.KEYSPACE_SUNBIRD_COURSE),
                eq(Constants.CONTENT_RETIREMENT_BY_APPROVED_DATE_TABLE),
                any(), any(), any()
        )).thenReturn(Collections.emptyList());

        contentRetirementService.sendContentRetirementNotifications();

        verifyNoInteractions(notificationService);
        verifyNoInteractions(contentService);
    }

    @Test
    void sendContentRetirementNotifications_EmptyBatchUsers_ShouldSkipBatch() {
        LocalDate today = LocalDate.now();
        Map<String, Object> recordData = new HashMap<>();
        recordData.put(Constants.CONTENT_ID, "content-empty-batchusers");
        recordData.put(Constants.STATUS, Constants.APPROVED);
        recordData.put(Constants.RETIREMENT_DATE, today.plusDays(1));

        when(cassandraOperation.getRecordsByProperties(
                eq(Constants.KEYSPACE_SUNBIRD_COURSE),
                eq(Constants.CONTENT_RETIREMENT_BY_RETIREMENT_DATE_TABLE),
                any(), any(), any()
        )).thenReturn(List.of(recordData));

        when(cassandraOperation.getRecordsByProperties(
                eq(Constants.KEYSPACE_SUNBIRD_COURSE),
                eq(Constants.CONTENT_RETIREMENT_BY_APPROVED_DATE_TABLE),
                any(), any(), any()
        )).thenReturn(Collections.emptyList());

        when(contentService.readContent(eq("content-empty-batchusers"), any()))
                .thenReturn(Map.of(
                        Constants.NAME, "Course With Empty Batch Users",
                        "batches", List.of(Map.of(Constants.BATCH_ID, "batchEmpty"))
                ));

        when(cassandraOperation.getRecordsByProperties(
                eq(Constants.KEYSPACE_SUNBIRD_COURSE),
                eq(Constants.ENROLLMENT_BATCH_LOOKUP),
                any(), any(), any()
        )).thenReturn(Collections.emptyList());

        contentRetirementService.sendContentRetirementNotifications();

        verifyNoInteractions(notificationService);
    }

    @Test
    void sendContentRetirementNotifications_EmptyEnrolment_ShouldNotNotify() {
        LocalDate today = LocalDate.now();
        Map<String, Object> recordData = new HashMap<>();
        recordData.put(Constants.CONTENT_ID, "content-empty-enrolment");
        recordData.put(Constants.STATUS, Constants.APPROVED);
        recordData.put(Constants.RETIREMENT_DATE, today.plusDays(1));

        when(cassandraOperation.getRecordsByProperties(
                eq(Constants.KEYSPACE_SUNBIRD_COURSE),
                eq(Constants.CONTENT_RETIREMENT_BY_RETIREMENT_DATE_TABLE),
                any(), any(), any()
        )).thenReturn(List.of(recordData));

        when(cassandraOperation.getRecordsByProperties(
                eq(Constants.KEYSPACE_SUNBIRD_COURSE),
                eq(Constants.CONTENT_RETIREMENT_BY_APPROVED_DATE_TABLE),
                any(), any(), any()
        )).thenReturn(Collections.emptyList());

        when(contentService.readContent(eq("content-empty-enrolment"), any()))
                .thenReturn(Map.of(
                        Constants.NAME, "Course With Empty Enrolment",
                        "batches", List.of(Map.of(Constants.BATCH_ID, "batch1"))
                ));

        when(cassandraOperation.getRecordsByProperties(
                eq(Constants.KEYSPACE_SUNBIRD_COURSE),
                eq(Constants.ENROLLMENT_BATCH_LOOKUP),
                any(), any(), any()
        )).thenReturn(List.of(Map.of(Constants.USER_ID, "user1")));

        when(cassandraOperation.getRecordsByProperties(
                eq(Constants.KEYSPACE_SUNBIRD_COURSE),
                eq(Constants.USER_ENROLMENTS_V2_TABLE),
                any(), any(), any()
        )).thenReturn(Collections.emptyList());

        contentRetirementService.sendContentRetirementNotifications();

        verifyNoInteractions(notificationService);
    }

    @Test
    void sendContentRetirementNotifications_EligibilityFilterEdgeCases_ShouldEvaluateAllBranches() {
        LocalDate today = LocalDate.now();
        Map<String, Object> recordData = new HashMap<>();
        recordData.put(Constants.CONTENT_ID, "content-filter-edge");
        recordData.put(Constants.STATUS, Constants.APPROVED);
        recordData.put(Constants.RETIREMENT_DATE, today.plusDays(1));

        when(cassandraOperation.getRecordsByProperties(
                eq(Constants.KEYSPACE_SUNBIRD_COURSE),
                eq(Constants.CONTENT_RETIREMENT_BY_RETIREMENT_DATE_TABLE),
                any(), any(), any()
        )).thenReturn(List.of(recordData));

        when(cassandraOperation.getRecordsByProperties(
                eq(Constants.KEYSPACE_SUNBIRD_COURSE),
                eq(Constants.CONTENT_RETIREMENT_BY_APPROVED_DATE_TABLE),
                any(), any(), any()
        )).thenReturn(Collections.emptyList());

        when(contentService.readContent(eq("content-filter-edge"), any()))
                .thenReturn(Map.of(
                        Constants.NAME, "Course Filter Edge",
                        "batches", List.of(Map.of(Constants.BATCH_ID, "batch1"))
                ));

        when(cassandraOperation.getRecordsByProperties(
                eq(Constants.KEYSPACE_SUNBIRD_COURSE),
                eq(Constants.ENROLLMENT_BATCH_LOOKUP),
                any(), any(), any()
        )).thenReturn(List.of(Map.of(Constants.USER_ID, "user1")));

        Map<String, Object> badStatus = Map.of(
                Constants.STATUS, "not-an-int",
                Constants.ACTIVE, true,
                Constants.ISSUED_CERTIFICATES, Collections.emptyList()
        );
        Map<String, Object> badActive = Map.of(
                Constants.STATUS, 1,
                Constants.ACTIVE, "not-a-boolean",
                Constants.ISSUED_CERTIFICATES, Collections.emptyList()
        );
        Map<String, Object> certsNullActiveTrue = new HashMap<>();
        certsNullActiveTrue.put(Constants.STATUS, 1);
        certsNullActiveTrue.put(Constants.ACTIVE, true);
        Map<String, Object> activeFalse = Map.of(
                Constants.STATUS, 1,
                Constants.ACTIVE, false,
                Constants.ISSUED_CERTIFICATES, Collections.emptyList()
        );

        when(cassandraOperation.getRecordsByProperties(
                eq(Constants.KEYSPACE_SUNBIRD_COURSE),
                eq(Constants.USER_ENROLMENTS_V2_TABLE),
                any(), any(), any()
        )).thenReturn(List.of(badStatus, badActive, certsNullActiveTrue, activeFalse));

        contentRetirementService.sendContentRetirementNotifications();

        verify(notificationService, times(1)).sendNotificationForContentRetirement(
                "content-filter-edge",
                "Course Filter Edge",
                today.plusDays(1),
                List.of("user1"),
                Constants.REMINDER_NOTIFICATION_ONE_DAY
        );
    }

    @Test
    void sendContentRetirementNotifications_ApprovedDateVariants_ShouldHandleAllBranches() {
        LocalDate today = LocalDate.now();

        Map<String, Object> nullStatusRecord = new HashMap<>();
        nullStatusRecord.put(Constants.CONTENT_ID, "content-approved-null-status");
        nullStatusRecord.put(Constants.STATUS, null);
        nullStatusRecord.put(Constants.APPROVED_DATE, today);
        nullStatusRecord.put(Constants.RETIREMENT_DATE, today.plusDays(10));

        Map<String, Object> nullApprovedDateRecord = new HashMap<>();
        nullApprovedDateRecord.put(Constants.CONTENT_ID, "content-approved-null-date");
        nullApprovedDateRecord.put(Constants.STATUS, Constants.APPROVED);
        nullApprovedDateRecord.put(Constants.APPROVED_DATE, null);
        nullApprovedDateRecord.put(Constants.RETIREMENT_DATE, today.plusDays(10));

        Map<String, Object> pastApprovedDateRecord = new HashMap<>();
        pastApprovedDateRecord.put(Constants.CONTENT_ID, "content-approved-past-date");
        pastApprovedDateRecord.put(Constants.STATUS, Constants.APPROVED);
        pastApprovedDateRecord.put(Constants.APPROVED_DATE, today.minusDays(3));
        pastApprovedDateRecord.put(Constants.RETIREMENT_DATE, today.plusDays(10));

        when(cassandraOperation.getRecordsByProperties(
                eq(Constants.KEYSPACE_SUNBIRD_COURSE),
                eq(Constants.CONTENT_RETIREMENT_BY_APPROVED_DATE_TABLE),
                any(), any(), any()
        )).thenReturn(List.of(nullStatusRecord, nullApprovedDateRecord, pastApprovedDateRecord));

        when(cassandraOperation.getRecordsByProperties(
                eq(Constants.KEYSPACE_SUNBIRD_COURSE),
                eq(Constants.CONTENT_RETIREMENT_BY_RETIREMENT_DATE_TABLE),
                any(), any(), any()
        )).thenReturn(Collections.emptyList());

        contentRetirementService.sendContentRetirementNotifications();

        verifyNoInteractions(notificationService);
        verifyNoInteractions(contentService);
    }

    @Test
    void sendContentRetirementNotificationsToSpv_NullOutboundResponse_ShouldReturnEmptyPublishers() {
        LocalDate today = LocalDate.now();
        Map<String, Object> recordData = new HashMap<>();
        recordData.put(Constants.CONTENT_ID, "do_null_resp");
        recordData.put(Constants.CREATED_DATE, today);
        recordData.put(Constants.USER_ID_RAISED_FIELD, "requester-null");
        recordData.put(Constants.RETIREMENT_DATE, today.plusDays(5));

        when(cassandraOperation.getRecordsByProperties(any(), any(), any(), any(), any()))
                .thenReturn(List.of(recordData));
        when(props.getSbUrl()).thenReturn("http://test");
        when(props.getUserSearchEndPoint()).thenReturn("/search");
        when(outboundRequestHandlerService.fetchResultUsingPost(anyString(), any(), any()))
                .thenReturn(null);

        contentRetirementService.sendContentRetirementNotificationsToSpv();

        verifyNoInteractions(notificationService);
        verify(contentService, never()).readContent(anyString(), anyList());
    }

    @Test
    void sendContentRetirementNotificationsToSpv_NonOkResponseCode_ShouldReturnEmptyPublishers() {
        LocalDate today = LocalDate.now();
        Map<String, Object> recordData = new HashMap<>();
        recordData.put(Constants.CONTENT_ID, "do_bad_code");
        recordData.put(Constants.CREATED_DATE, today);
        recordData.put(Constants.USER_ID_RAISED_FIELD, "requester-bad");
        recordData.put(Constants.RETIREMENT_DATE, today.plusDays(5));

        when(cassandraOperation.getRecordsByProperties(any(), any(), any(), any(), any()))
                .thenReturn(List.of(recordData));
        when(props.getSbUrl()).thenReturn("http://test");
        when(props.getUserSearchEndPoint()).thenReturn("/search");
        when(outboundRequestHandlerService.fetchResultUsingPost(anyString(), any(), any()))
                .thenReturn(Map.of(Constants.RESPONSE_CODE, "FAILED"));

        contentRetirementService.sendContentRetirementNotificationsToSpv();

        verifyNoInteractions(notificationService);
    }

    @Test
    void sendContentRetirementNotificationsToSpv_MissingContentInResponse_ShouldReturnEmptyPublishers() {
        LocalDate today = LocalDate.now();
        Map<String, Object> recordData = new HashMap<>();
        recordData.put(Constants.CONTENT_ID, "do_no_content");
        recordData.put(Constants.CREATED_DATE, today);
        recordData.put(Constants.USER_ID_RAISED_FIELD, "requester-noc");
        recordData.put(Constants.RETIREMENT_DATE, today.plusDays(5));

        when(cassandraOperation.getRecordsByProperties(any(), any(), any(), any(), any()))
                .thenReturn(List.of(recordData));
        when(props.getSbUrl()).thenReturn("http://test");
        when(props.getUserSearchEndPoint()).thenReturn("/search");
        when(outboundRequestHandlerService.fetchResultUsingPost(anyString(), any(), any()))
                .thenReturn(Map.of(
                        Constants.RESPONSE_CODE, "OK",
                        Constants.RESULT, Map.of(Constants.RESPONSE, Map.of())
                ));

        contentRetirementService.sendContentRetirementNotificationsToSpv();

        verifyNoInteractions(notificationService);
    }

    @Test
    void sendContentRetirementNotificationsToSpv_EmptyFinalRecipients_CorrectCreatedDate_ShouldSkip() {
        LocalDate today = LocalDate.now();
        Map<String, Object> recordData = new HashMap<>();
        recordData.put(Constants.CONTENT_ID, "do_empty_recipients");
        recordData.put(Constants.CREATED_DATE, today);
        recordData.put(Constants.USER_ID_RAISED_FIELD, "requester-empty");
        recordData.put(Constants.RETIREMENT_DATE, today.plusDays(5));

        when(cassandraOperation.getRecordsByProperties(any(), any(), any(), any(), any()))
                .thenReturn(List.of(recordData));
        mockSpvUsers(Collections.emptyList());

        contentRetirementService.sendContentRetirementNotificationsToSpv();

        verifyNoInteractions(notificationService);
        verify(contentService, never()).readContent(anyString(), anyList());
    }

    @Test
    void sendContentRetirementNotificationsToSpv_CreatedDateNotTodayCorrectKey_ShouldSkip() {
        Map<String, Object> recordData = new HashMap<>();
        recordData.put(Constants.CONTENT_ID, "do_yesterday");
        recordData.put(Constants.CREATED_DATE, LocalDate.now().minusDays(1));
        recordData.put(Constants.USER_ID_RAISED_FIELD, "requester-yesterday");
        recordData.put(Constants.RETIREMENT_DATE, LocalDate.now().plusDays(5));

        when(cassandraOperation.getRecordsByProperties(any(), any(), any(), any(), any()))
                .thenReturn(List.of(recordData));

        contentRetirementService.sendContentRetirementNotificationsToSpv();

        verifyNoInteractions(contentService);
        verifyNoInteractions(notificationService);
    }

    @Test
    void sendContentRetirementNotificationsToSpv_MalformedPublisherEntries_ShouldFilterInvalidAndKeepValid() {
        LocalDate today = LocalDate.now();
        Map<String, Object> recordData = new HashMap<>();
        recordData.put(Constants.CONTENT_ID, "do_malformed");
        recordData.put(Constants.CREATED_DATE, today);
        recordData.put(Constants.USER_ID_RAISED_FIELD, "requester-malformed");
        recordData.put(Constants.RETIREMENT_DATE, today.plusDays(5));

        when(cassandraOperation.getRecordsByProperties(any(), any(), any(), any(), any()))
                .thenReturn(List.of(recordData));
        when(contentService.readContent(eq("do_malformed"), any()))
                .thenReturn(Map.of("name", "Malformed Course"));
        when(props.getSbUrl()).thenReturn("http://test");
        when(props.getUserSearchEndPoint()).thenReturn("/search");

        List<Object> contents = new ArrayList<>();
        contents.add("not-a-map");
        contents.add(Map.of());
        contents.add(Map.of(Constants.USER_ID, ""));
        contents.add(Map.of(Constants.USER_ID, "uid4"));
        contents.add(Map.of(Constants.USER_ID, "uid5", Constants.PROFILE_DETAILS, Map.of()));
        contents.add(Map.of(Constants.USER_ID, "uid6", Constants.PROFILE_DETAILS,
                Map.of(Constants.PERSONAL_DETAILS, Map.of())));
        contents.add(Map.of(Constants.USER_ID, "uid7", Constants.PROFILE_DETAILS,
                Map.of(Constants.PERSONAL_DETAILS, Map.of(Constants.PRIMARY_EMAIL, ""))));
        contents.add(Map.of(Constants.USER_ID, "uid8", Constants.PROFILE_DETAILS,
                Map.of(Constants.PERSONAL_DETAILS, Map.of(Constants.PRIMARY_EMAIL, "uid8@test.com"))));

        Map<String, Object> spvResponse = Map.of(
                Constants.RESPONSE_CODE, "OK",
                Constants.RESULT, Map.of(
                        Constants.RESPONSE, Map.of(Constants.CONTENT, contents)
                )
        );
        when(outboundRequestHandlerService.fetchResultUsingPost(anyString(), any(), any()))
                .thenReturn(spvResponse);

        contentRetirementService.sendContentRetirementNotificationsToSpv();

        verify(notificationService).sendNotificationForContentRetirementSpv(
                eq("do_malformed"),
                eq("Malformed Course"),
                argThat(list -> list.size() == 1 && list.contains("uid8")),
                eq(Constants.CONTENT_RETIREMENT_SCHEDULED_NOTIFICATION),
                eq(today.plusDays(5)),
                argThat(emails -> emails.size() == 1 && emails.contains("uid8@test.com")),
                eq("requester-malformed")
        );
    }

    @Test
    void collectSpvPublisherIdentifiers_BlankFields_ShouldBeExcluded() {
        List<Map<String, String>> publishers = new ArrayList<>();
        Map<String, String> blankEntry = new HashMap<>();
        blankEntry.put(Constants.USER_ID, "");
        blankEntry.put(Constants.EMAIL, "");
        publishers.add(blankEntry);

        Map<String, String> validEntry = new HashMap<>();
        validEntry.put(Constants.USER_ID, "valid-user");
        validEntry.put(Constants.EMAIL, "valid@test.com");
        publishers.add(validEntry);

        List<String> userIds = new ArrayList<>();
        List<String> emails = new ArrayList<>();

        ReflectionTestUtils.invokeMethod(contentRetirementService, "collectSpvPublisherIdentifiers",
                publishers, userIds, emails);

        assertEquals(List.of("valid-user"), userIds);
        assertEquals(List.of("valid@test.com"), emails);
    }

}
