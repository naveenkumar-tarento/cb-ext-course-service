package com.igot.cb.service;

import com.igot.cb.cassandra.CassandraOperation;
import com.igot.cb.model.ApiResponse;
import com.igot.cb.user.UserUtilityService;
import com.igot.cb.util.AccessTokenValidator;
import com.igot.cb.util.CbExtServerProperties;
import com.igot.cb.util.Constants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.time.LocalDate;
import java.util.*;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;

class NotificationServiceImplTest {

    // Use a concrete test implementation instead of mocking to avoid ByteBuddy issues on Java 23
    private static class TestAccessTokenValidator extends AccessTokenValidator {
        private String userIdToReturn = "invokerUser";
        private boolean shouldThrow = false;

        public TestAccessTokenValidator() {
            super(null);
        }

        public void setUserIdToReturn(String userId) {
            this.userIdToReturn = userId;
        }

        public void setShouldThrow(boolean shouldThrow) {
            this.shouldThrow = shouldThrow;
        }

        @Override
        public String fetchUserIdFromAccessToken(String accessToken, ApiResponse response) {
            if (shouldThrow) {
                throw new RuntimeException("accessToken-validation-boom");
            }
            return userIdToReturn;
        }
    }

    // Outbound handler double that always throws, used to exercise catch blocks in sendInAppNotification
    private static class ThrowingOutboundRequestHandlerService extends OutboundRequestHandlerServiceImpl {
        private final RuntimeException toThrow;

        public ThrowingOutboundRequestHandlerService(RuntimeException toThrow) {
            super(null);
            this.toThrow = toThrow;
        }

        @Override
        public Map<String, Object> fetchResultUsingPost(String uri, Object request, Map<String, String> headersValues) {
            throw toThrow;
        }
    }

    // Use a concrete test implementation for OutboundRequestHandlerServiceImpl
    private static class TestOutboundRequestHandlerService extends OutboundRequestHandlerServiceImpl {
        private final java.util.Queue<Map<String, Object>> responses = new java.util.LinkedList<>();

        public TestOutboundRequestHandlerService() {
            super(null); // Pass null for RestTemplate since we're overriding the method
        }

        public void addResponse(Map<String, Object> response) {
            responses.add(response);
        }

        @Override
        public Map<String, Object> fetchResultUsingPost(String uri, Object request, Map<String, String> headersValues) {
            if (responses.isEmpty()) {
                return Collections.emptyMap();
            }
            return responses.poll();
        }
    }

    // Use a concrete test implementation for CbExtServerProperties
    private static class TestCbExtServerProperties extends CbExtServerProperties {
        @Override
        public String getNotificationServiceHost() {
            return "http://notify";
        }

        @Override
        public String getNotificationAsyncPath() {
            return "/async";
        }

        @Override
        public String getNotificationSupportMail() {
            return "support@example.com";
        }

        @Override
        public String getSbUrl() {
            return "http://sb";
        }

        @Override
        public String getUserSearchEndPoint() {
            return "/users/search";
        }

        @Override
        public String getCbWrapperNotificationHost() {
            return "http://wrapper";
        }

        @Override
        public String getCbWrapperNotificationPath() {
            return "/notify";
        }
    }

    private NotificationServiceImpl notificationService;

    private final TestAccessTokenValidator testAccessTokenValidator = new TestAccessTokenValidator();
    private final TestOutboundRequestHandlerService testOutboundRequestHandler = new TestOutboundRequestHandlerService();
    private final TestCbExtServerProperties testProps = new TestCbExtServerProperties();

    // Still need to mock these as they are interfaces and easier to mock
    private CassandraOperation cassandraOperation;
    private UserUtilityService userUtilityService;

    private final String authToken = "validToken";

    @BeforeEach
    void setUp() {
        // Create mocks manually to avoid @Mock annotation
        cassandraOperation = mock(CassandraOperation.class);
        userUtilityService = mock(UserUtilityService.class);

        notificationService = new NotificationServiceImpl(testAccessTokenValidator, cassandraOperation,
                userUtilityService, testOutboundRequestHandler, testProps, new com.fasterxml.jackson.databind.ObjectMapper());
    }

    private Map<String, Object> buildUserSearchResponse(String email, String firstName) {
        Map<String, Object> personal = new HashMap<>();
        personal.put(Constants.PRIMARY_EMAIL, email);
        personal.put(Constants.FIRST_NAME, firstName);

        Map<String, Object> profileDetails = Map.of(Constants.PERSONAL_DETAILS, personal);
        Map<String, Object> contentEntry = Map.of(Constants.PROFILE_DETAILS, profileDetails);

        List<Object> contentList = List.of(contentEntry);
        Map<String, Object> top = new HashMap<>();
        top.put(Constants.RESPONSE_CODE, "OK");
        top.put(Constants.RESULT, Map.of(Constants.RESPONSE, Map.of(Constants.CONTENT, contentList)));
        return top;
    }

    @Test
    void testNotifyAssignmentUploaded_success() {
        // arrange
        Map<String, Object> request = Map.of(
                Constants.COURSE_ID, "course1",
                Constants.BATCH_ID, "batch1",
                Constants.ASSIGNMENT_TITLE, "Assignment A"
        );

        // enrollments: one active user
        Map<String, Object> enrollment = new HashMap<>();
        enrollment.put(Constants.USER_ID, "learner1");
        enrollment.put(Constants.ACTIVE, true);
        List<Map<String, Object>> enrollments = List.of(enrollment);

        when(cassandraOperation.getRecordsByProperties(
                eq(Constants.KEYSPACE_SUNBIRD_COURSE),
                eq(Constants.ENROLLMENT_BATCH_LOOKUP),
                anyMap(), anyList(), isNull()))
                .thenReturn(enrollments);

        // email template fetch
        when(cassandraOperation.getRecordsByProperties(
                eq(Constants.KEYSPACE_SUNBIRD),
                eq(Constants.TABLE_EMAIL_TEMPLATE),
                anyMap(), anyList(), isNull()))
                .thenReturn(List.of(Map.of(Constants.TEMPLATE, "<html>Hi $assignmentTitle</html>")));

        // outbound: user search responses
        testOutboundRequestHandler.addResponse(buildUserSearchResponse("learner@example.com", "Learner"));
        testOutboundRequestHandler.addResponse(Collections.emptyMap());

        // act
        var response = notificationService.notifyAssignmentUploaded(request, authToken);

        // assert
        assertEquals(HttpStatus.OK, response.getResponseCode());
    }

    @Test
    void testNotifyAssignmentUploaded_noEnrollments() {
        // arrange
        Map<String, Object> request = Map.of(
                Constants.COURSE_ID, "course1",
                Constants.BATCH_ID, "batch-no",
                Constants.ASSIGNMENT_TITLE, "Assignment A"
        );

        when(cassandraOperation.getRecordsByProperties(
                eq(Constants.KEYSPACE_SUNBIRD_COURSE),
                eq(Constants.ENROLLMENT_BATCH_LOOKUP),
                anyMap(), anyList(), isNull()))
                .thenReturn(Collections.emptyList());

        // act
        var response = notificationService.notifyAssignmentUploaded(request, authToken);

        // assert
        assertEquals(HttpStatus.OK, response.getResponseCode());
        assertEquals("No enrolled users found for given courseId/batchId", response.getResult().get(Constants.MESSAGE));
    }

    @Test
    void testNotifyAssignmentEvaluate_missingLearner() {
        // arrange: missing learnerId
        Map<String, Object> request = Map.of(
                Constants.COURSE_ID, "course1",
                Constants.BATCH_ID, "batch1",
                Constants.ASSIGNMENT_TITLE, "Assignment A"
        );

        // act
        var response = notificationService.notifyAssignmentEvaluate(request, authToken);

        // assert -> bad request due to missing learnerId
        assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
        assertEquals(Constants.FAILED, response.getParams().getStatus());
    }

    @Test
    void testNotifyAssignmentEvaluate_success() {
        // arrange
        Map<String, Object> request = Map.of(
                Constants.COURSE_ID, "course1",
                Constants.BATCH_ID, "batch1",
                Constants.ASSIGNMENT_TITLE, "Assignment A",
                Constants.LEARNER_ID, "learner1"
        );

        // email template fetch
        when(cassandraOperation.getRecordsByProperties(
                eq(Constants.KEYSPACE_SUNBIRD),
                eq(Constants.TABLE_EMAIL_TEMPLATE),
                anyMap(), anyList(), isNull()))
                .thenReturn(List.of(Map.of(Constants.TEMPLATE, "<html>Hi $firstName, Assignment: $assignment</html>")));

        // outbound: learner user search, notification send
        testOutboundRequestHandler.addResponse(buildUserSearchResponse("learner@example.com", "Learner"));
        testOutboundRequestHandler.addResponse(Collections.emptyMap()); // For in-app notification
        testOutboundRequestHandler.addResponse(Collections.emptyMap()); // For email notification

        // act
        var response = notificationService.notifyAssignmentEvaluate(request, authToken);

        // assert
        assertEquals(HttpStatus.OK, response.getResponseCode());
    }

    @Test
    void testNotifyAssignmentSubmit_success() {
        Map<String, Object> request = Map.of(
                Constants.COURSE_ID, "course1",
                Constants.BATCH_ID, "batch1",
                Constants.ASSIGNMENT_TITLE, "Assignment A",
                Constants.INSTRUCTOR_ID, "instructor1"
        );

        when(cassandraOperation.getRecordsByProperties(
                eq(Constants.KEYSPACE_SUNBIRD),
                eq(Constants.TABLE_EMAIL_TEMPLATE),
                anyMap(), anyList(), isNull()))
                .thenReturn(List.of(Map.of(Constants.TEMPLATE, "<html>Hi $learnerName</html>")));

        // outbound: instructor user search, learner name lookup, notification send
        testOutboundRequestHandler.addResponse(buildUserSearchResponse("instructor@example.com", "Instructor"));
        testOutboundRequestHandler.addResponse(buildUserSearchResponse("learner@example.com", "Learner"));
        testOutboundRequestHandler.addResponse(Collections.emptyMap()); // For in-app notification
        testOutboundRequestHandler.addResponse(Collections.emptyMap()); // For email notification

        // act
        var response = notificationService.notifyAssignmentSubmit(request, authToken);

        // assert
        assertEquals(HttpStatus.OK, response.getResponseCode());
    }

    @Test
    void sendNotificationForContentRetirement_emptyUsers_shouldReturn() {

        assertDoesNotThrow(() -> notificationService.sendNotificationForContentRetirement(
                "do_1",
                "Course A",
                LocalDate.now(),
                Collections.emptyList(),
                Constants.CONTENT_RETIREMENT_APPROVED_NOTIFICATION
        ));
    }

    @Test
    void sendNotificationForContentRetirement_approved_shouldSendApprovedTemplate() {

        assertDoesNotThrow(() -> notificationService.sendNotificationForContentRetirement(
                "do_2",
                "Course Approved",
                LocalDate.of(2026, 1, 10),
                List.of("user1"),
                Constants.CONTENT_RETIREMENT_APPROVED_NOTIFICATION
        ));
    }

    @Test
    void sendNotificationForContentRetirement_sevenDayReminder_shouldSendReminder() {

        assertDoesNotThrow(() -> notificationService.sendNotificationForContentRetirement(
                "do_3",
                "Course Reminder",
                LocalDate.of(2026, 1, 10),
                List.of("user1"),
                Constants.REMINDER_NOTIFICATION_SEVEN_DAY
        ));
    }

    @Test
    void sendNotificationForContentRetirement_oneDayReminder_shouldSendReminder() {

        assertDoesNotThrow(() -> notificationService.sendNotificationForContentRetirement(
                "do_4",
                "Course Reminder",
                LocalDate.of(2026, 1, 10),
                List.of("user1"),
                Constants.REMINDER_NOTIFICATION_ONE_DAY
        ));
    }

    @Test
    void sendNotificationForContentRetirement_finalRetired_shouldSendFinalNotification() {

        assertDoesNotThrow(() -> notificationService.sendNotificationForContentRetirement(
                "do_5",
                "Course Retired",
                LocalDate.of(2026, 1, 10),
                List.of("user1"),
                "UNKNOWN_TYPE"
        ));
    }

    @Test
    void sendNotificationForContentRetirement_exceptionThrown_shouldBeCaught() {
        NotificationServiceImpl spyService = spy(notificationService);

        doThrow(new RuntimeException("Boom"))
                .when(spyService)
                .sendInAppNotification(
                        anyString(),
                        anyString(),
                        anyList(),
                        anyMap()
                );

        assertDoesNotThrow(() ->
                spyService.sendNotificationForContentRetirement(
                        "do_999",
                        "Crash Course",
                        LocalDate.now(),
                        List.of("user1"),
                        Constants.CONTENT_RETIREMENT_APPROVED_NOTIFICATION
                )
        );
    }

    @Test
    void sendNotificationForContentRetirementSpv_validInput_shouldSendNotification() {

        NotificationServiceImpl spyService = spy(notificationService);

        ArrayList<String> users = new ArrayList<>(List.of("user1", "user2"));
        LocalDate date = LocalDate.now();

        // Act
        List<String> emails = List.of(
                "rkspvpublisher@yopmail.com",
                "tarento.spv.publisher@yopmail.com"
        );
        String requestedBy = "91c9351f-803b-44d0-92a1-31f033bf3cc5";
        spyService.sendNotificationForContentRetirementSpv(
                "do_123",
                "Sample Course",
                new ArrayList<>(users),
                Constants.CONTENT_RETIREMENT_SCHEDULED_NOTIFICATION,
                LocalDate.now(),
                emails,
                requestedBy
        );

        // Assert
        verify(spyService).sendInAppNotification(
                eq(Constants.CONTENT_RETIREMENT_SCHEDULED_NOTIFICATION),
                eq(Constants.ALERT),
                eq(users),
                argThat(message -> {
                    Map<String, String> placeholders =
                            (Map<String, String>) message.get(Constants.PLACE_HOLDERS);
                    Map<String, Object> data =
                            (Map<String, Object>) message.get(Constants.DATA);

                    return "Sample Course".equals(placeholders.get(Constants.TITLE))
                            && date.toString().equals(placeholders.get(Constants.DATE_KEY))
                            && "do_123".equals(data.get(Constants.ID));
                })
        );
    }

    @Test
    void sendNotificationForContentRetirementSpv_emptyUsers_shouldReturnEarly() {

        NotificationServiceImpl spyService = spy(notificationService);

        // Act
        List<String> emails = List.of(
                "rkspvpublisher@yopmail.com",
                "tarento.spv.publisher@yopmail.com"
        );
        String requestedBy = "91c9351f-803b-44d0-92a1-31f033bf3cc5";
        spyService.sendNotificationForContentRetirementSpv(
                "do_124",
                "Course X",
                new ArrayList<>(),
                Constants.CONTENT_RETIREMENT_SCHEDULED_NOTIFICATION,
                LocalDate.now(),
                emails, requestedBy
        );

        // Assert
        verify(spyService, never()).sendInAppNotification(any(), any(), any(), any());
    }

    @Test
    void sendNotificationForContentRetirementSpv_exceptionThrown_shouldBeCaught() {

        NotificationServiceImpl spyService = spy(notificationService);

        doThrow(new RuntimeException("Boom"))
                .when(spyService)
                .sendInAppNotification(any(), any(), any(), any());

        ArrayList<String> users = new ArrayList<>(List.of("user1"));
        List<String> emails = List.of(
                "rkspvpublisher@yopmail.com",
                "tarento.spv.publisher@yopmail.com"
        );
        String requestedBy = "91c9351f-803b-44d0-92a1-31f033bf3cc5";
        assertDoesNotThrow(() ->
                spyService.sendNotificationForContentRetirementSpv(
                        "do_500",
                        "Crash Course",
                        users,
                        Constants.CONTENT_RETIREMENT_SCHEDULED_NOTIFICATION,
                        LocalDate.now(),
                        emails, requestedBy
                )
        );
    }

    @Test
    void sendNotificationForExternalTraining_emptyUsers_shouldReturn() {
        NotificationServiceImpl spyService = spy(notificationService);

        spyService.sendNotificationForExternalTraining(
                "train1",
                "Training A",
                Collections.emptyList(),
                "TYPE1"
        );

        verify(spyService, never()).sendInAppNotification(any(), any(), any(), any());
    }

    @Test
    void sendNotificationForExternalTraining_emptyType_shouldReturn() {
        NotificationServiceImpl spyService = spy(notificationService);

        spyService.sendNotificationForExternalTraining(
                "train1",
                "Training A",
                List.of("user1"),
                ""
        );

        verify(spyService, never()).sendInAppNotification(any(), any(), any(), any());
    }

    @Test
    void sendNotificationForExternalTraining_validInput_shouldSendNotification() {
        NotificationServiceImpl spyService = spy(notificationService);

        List<String> users = List.of("user1", "user2");

        spyService.sendNotificationForExternalTraining(
                "train123",
                "Spring Boot Training",
                users,
                "EXTERNAL_TRAINING"
        );

        verify(spyService).sendInAppNotification(
                eq("EXTERNAL_TRAINING"),
                eq(Constants.ALERT),
                eq(users),
                argThat(message -> {
                    Map<String, String> placeholders =
                            (Map<String, String>) message.get(Constants.PLACE_HOLDERS);
                    Map<String, Object> data =
                            (Map<String, Object>) message.get(Constants.DATA);

                    return "Spring Boot Training".equals(placeholders.get(Constants.COURSE_NAME))
                            && "train123".equals(data.get(Constants.ID));
                })
        );
    }

    @Test
    void sendNotificationForExternalTraining_exceptionThrown_shouldBeCaught() {
        NotificationServiceImpl spyService = spy(notificationService);

        doThrow(new RuntimeException("Boom"))
                .when(spyService)
                .sendInAppNotification(any(), any(), any(), any());

        assertDoesNotThrow(() ->
                spyService.sendNotificationForExternalTraining(
                        "train999",
                        "Crash Training",
                        List.of("user1"),
                        "TYPE1"
                )
        );
    }

    // ---- Additional coverage tests ----

    @Test
    void testNotifyAssignmentUploaded_emptyUserId_returnsDefaultResponseUnchanged() {
        testAccessTokenValidator.setUserIdToReturn("");

        var response = notificationService.notifyAssignmentUploaded(
                Map.of(Constants.COURSE_ID, "course1"), authToken);

        assertEquals(HttpStatus.OK, response.getResponseCode());
        assertEquals(Constants.SUCCESS, response.getParams().getStatus());
    }

    @Test
    void testNotifyAssignmentUploaded_emptyRequestMap_returnsRequestEmptyError() {
        var response = notificationService.notifyAssignmentUploaded(Collections.emptyMap(), authToken);

        assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
        assertEquals(Constants.FAILED, response.getParams().getStatus());
        assertEquals("Request object is empty.", response.getParams().getErrMsg());
    }

    @Test
    void testNotifyAssignmentUploaded_missingRequiredFields_returnsValidationError() {
        Map<String, Object> request = new HashMap<>();
        request.put("someOtherField", "value");

        var response = notificationService.notifyAssignmentUploaded(request, authToken);

        assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
        assertEquals(Constants.FAILED, response.getParams().getStatus());
        assertEquals("Failed Due To Missing Params - [courseId, batchId, assignmentTitle].",
                response.getParams().getErrMsg());
    }

    @Test
    void testNotifyAssignmentUploaded_inactiveEnrollments_resultsInNoEmailsMessage() {
        Map<String, Object> request = Map.of(
                Constants.COURSE_ID, "course1",
                Constants.BATCH_ID, "batch1",
                Constants.ASSIGNMENT_TITLE, "Assignment A"
        );

        Map<String, Object> enrollment = new HashMap<>();
        enrollment.put(Constants.USER_ID, "learner1");
        enrollment.put(Constants.ACTIVE, false);

        when(cassandraOperation.getRecordsByProperties(
                eq(Constants.KEYSPACE_SUNBIRD_COURSE),
                eq(Constants.ENROLLMENT_BATCH_LOOKUP),
                anyMap(), anyList(), isNull()))
                .thenReturn(List.of(enrollment));

        var response = notificationService.notifyAssignmentUploaded(request, authToken);

        assertEquals(HttpStatus.OK, response.getResponseCode());
        assertEquals("No enrolled users found for given batchId", response.getResult().get(Constants.MESSAGE));
    }

    @Test
    void testNotifyAssignmentUploaded_exceptionFromCassandra_returnsInternalServerError() {
        Map<String, Object> request = Map.of(
                Constants.COURSE_ID, "course1",
                Constants.BATCH_ID, "batch1",
                Constants.ASSIGNMENT_TITLE, "Assignment A"
        );

        when(cassandraOperation.getRecordsByProperties(
                eq(Constants.KEYSPACE_SUNBIRD_COURSE),
                eq(Constants.ENROLLMENT_BATCH_LOOKUP),
                anyMap(), anyList(), isNull()))
                .thenThrow(new RuntimeException("boom"));

        var response = notificationService.notifyAssignmentUploaded(request, authToken);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getResponseCode());
        assertEquals(Constants.FAILED, response.getParams().getStatus());
        assertEquals("boom", response.getParams().getErrMsg());
    }

    @Test
    void testNotifyAssignmentUploaded_manyActiveEnrollments_coversMultiUserLoop() {
        Map<String, Object> request = Map.of(
                Constants.COURSE_ID, "course1",
                Constants.BATCH_ID, "batch1",
                Constants.ASSIGNMENT_TITLE, "Assignment A"
        );

        Map<String, Object> enrollment1 = new HashMap<>();
        enrollment1.put(Constants.USER_ID, "learner1");
        enrollment1.put(Constants.ACTIVE, true);
        Map<String, Object> enrollment2 = new HashMap<>();
        enrollment2.put(Constants.USER_ID, "learner2");
        enrollment2.put(Constants.ACTIVE, true);

        when(cassandraOperation.getRecordsByProperties(
                eq(Constants.KEYSPACE_SUNBIRD_COURSE),
                eq(Constants.ENROLLMENT_BATCH_LOOKUP),
                anyMap(), anyList(), isNull()))
                .thenReturn(List.of(enrollment1, enrollment2));

        when(cassandraOperation.getRecordsByProperties(
                eq(Constants.KEYSPACE_SUNBIRD),
                eq(Constants.TABLE_EMAIL_TEMPLATE),
                anyMap(), anyList(), isNull()))
                .thenReturn(List.of(Map.of(Constants.TEMPLATE, "<html>Hi $assignmentTitle</html>")));

        testOutboundRequestHandler.addResponse(buildUserSearchResponse("learner1@example.com", "Learner1"));
        testOutboundRequestHandler.addResponse(Collections.emptyMap());

        var response = notificationService.notifyAssignmentUploaded(request, authToken);

        assertEquals(HttpStatus.OK, response.getResponseCode());
    }

    @Test
    void testNotifyAssignmentUploaded_templateFetchThrows_isCaughtInsideConstructEmailTemplate() {
        Map<String, Object> request = Map.of(
                Constants.COURSE_ID, "course1",
                Constants.BATCH_ID, "batch1",
                Constants.ASSIGNMENT_TITLE, "Assignment A"
        );

        Map<String, Object> enrollment = new HashMap<>();
        enrollment.put(Constants.USER_ID, "learner1");
        enrollment.put(Constants.ACTIVE, true);

        when(cassandraOperation.getRecordsByProperties(
                eq(Constants.KEYSPACE_SUNBIRD_COURSE),
                eq(Constants.ENROLLMENT_BATCH_LOOKUP),
                anyMap(), anyList(), isNull()))
                .thenReturn(List.of(enrollment));

        when(cassandraOperation.getRecordsByProperties(
                eq(Constants.KEYSPACE_SUNBIRD),
                eq(Constants.TABLE_EMAIL_TEMPLATE),
                anyMap(), anyList(), isNull()))
                .thenThrow(new RuntimeException("template-boom"));

        testOutboundRequestHandler.addResponse(buildUserSearchResponse("learner@example.com", "Learner"));
        testOutboundRequestHandler.addResponse(Collections.emptyMap());

        var response = notificationService.notifyAssignmentUploaded(request, authToken);

        // constructEmailTemplate swallows the exception internally, overall flow still succeeds
        assertEquals(HttpStatus.OK, response.getResponseCode());
    }

    @Test
    void testNotifyAssignmentEvaluate_emptyUserId_returnsUntouchedResponse() {
        testAccessTokenValidator.setUserIdToReturn("");

        var response = notificationService.notifyAssignmentEvaluate(Map.of(), authToken);

        assertEquals(null, response.getResponseCode());
        assertEquals(null, response.getParams().getStatus());
    }

    @Test
    void testNotifyAssignmentEvaluate_exceptionFromAccessTokenValidator_isCaught() {
        testAccessTokenValidator.setShouldThrow(true);

        var response = notificationService.notifyAssignmentEvaluate(
                Map.of(Constants.LEARNER_ID, "learner1"), authToken);

        assertEquals(Constants.FAILED, response.getParams().getStatus());
        assertEquals("accessToken-validation-boom", response.getParams().getErrMsg());
    }

    @Test
    void testNotifyAssignmentSubmit_missingInstructor_returnsBadRequest() {
        Map<String, Object> request = Map.of(
                Constants.COURSE_ID, "course1",
                Constants.BATCH_ID, "batch1",
                Constants.ASSIGNMENT_TITLE, "Assignment A"
        );

        var response = notificationService.notifyAssignmentSubmit(request, authToken);

        assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
        assertEquals(Constants.FAILED, response.getParams().getStatus());
    }

    @Test
    void testNotifyAssignmentSubmit_exceptionFromAccessTokenValidator_isCaught() {
        testAccessTokenValidator.setShouldThrow(true);

        var response = notificationService.notifyAssignmentSubmit(
                Map.of(Constants.INSTRUCTOR_ID, "instructor1"), authToken);

        assertEquals(Constants.FAILED, response.getParams().getStatus());
        assertEquals("accessToken-validation-boom", response.getParams().getErrMsg());
    }

    @Test
    void testSendInAppNotification_blankAndEmptyInputs_logWarningsButStillAttemptsSend() {
        assertDoesNotThrow(() -> notificationService.sendInAppNotification(
                "", "", Collections.emptyList(), Collections.emptyMap()));
    }

    @Test
    void testSendInAppNotification_illegalArgumentExceptionIsCaught() {
        ThrowingOutboundRequestHandlerService throwingHandler =
                new ThrowingOutboundRequestHandlerService(new IllegalArgumentException("bad arg"));
        NotificationServiceImpl service = new NotificationServiceImpl(testAccessTokenValidator, cassandraOperation,
                userUtilityService, throwingHandler, testProps, new com.fasterxml.jackson.databind.ObjectMapper());

        assertDoesNotThrow(() -> service.sendInAppNotification(
                Constants.ALERT, Constants.ALERT, List.of("user1"), Map.of("k", "v")));
    }

    @Test
    void testSendInAppNotification_genericExceptionIsCaught() {
        ThrowingOutboundRequestHandlerService throwingHandler =
                new ThrowingOutboundRequestHandlerService(new IllegalStateException("generic boom"));
        NotificationServiceImpl service = new NotificationServiceImpl(testAccessTokenValidator, cassandraOperation,
                userUtilityService, throwingHandler, testProps, new com.fasterxml.jackson.databind.ObjectMapper());

        assertDoesNotThrow(() -> service.sendInAppNotification(
                Constants.ALERT, Constants.ALERT, List.of("user1"), Map.of("k", "v")));
    }

    @Test
    void testFetchUserEmails_nonOkResponseCode_returnsEmptyResult() {
        Map<String, Object> request = Map.of(
                Constants.COURSE_ID, "course1",
                Constants.BATCH_ID, "batch1",
                Constants.ASSIGNMENT_TITLE, "Assignment A",
                Constants.LEARNER_ID, "learner1"
        );

        when(cassandraOperation.getRecordsByProperties(
                eq(Constants.KEYSPACE_SUNBIRD),
                eq(Constants.TABLE_EMAIL_TEMPLATE),
                anyMap(), anyList(), isNull()))
                .thenReturn(List.of(Map.of(Constants.TEMPLATE, "<html>Hi</html>")));

        Map<String, Object> failResp = new HashMap<>();
        failResp.put(Constants.RESPONSE_CODE, "FAIL");
        testOutboundRequestHandler.addResponse(failResp);
        testOutboundRequestHandler.addResponse(Collections.emptyMap());
        testOutboundRequestHandler.addResponse(Collections.emptyMap());

        var response = notificationService.notifyAssignmentEvaluate(request, authToken);

        assertEquals(HttpStatus.OK, response.getResponseCode());
    }

    @Test
    void testFetchUserEmails_contentsNotAList_returnsEmptyResult() {
        Map<String, Object> request = Map.of(
                Constants.COURSE_ID, "course1",
                Constants.BATCH_ID, "batch1",
                Constants.ASSIGNMENT_TITLE, "Assignment A",
                Constants.LEARNER_ID, "learner1"
        );

        when(cassandraOperation.getRecordsByProperties(
                eq(Constants.KEYSPACE_SUNBIRD),
                eq(Constants.TABLE_EMAIL_TEMPLATE),
                anyMap(), anyList(), isNull()))
                .thenReturn(List.of(Map.of(Constants.TEMPLATE, "<html>Hi</html>")));

        Map<String, Object> resp = new HashMap<>();
        resp.put(Constants.RESPONSE_CODE, "OK");
        resp.put(Constants.RESULT, Map.of(Constants.RESPONSE, Map.of(Constants.CONTENT, "not-a-list")));
        testOutboundRequestHandler.addResponse(resp);
        testOutboundRequestHandler.addResponse(Collections.emptyMap());
        testOutboundRequestHandler.addResponse(Collections.emptyMap());

        var response = notificationService.notifyAssignmentEvaluate(request, authToken);

        assertEquals(HttpStatus.OK, response.getResponseCode());
    }

    @Test
    void testFetchUserEmails_contentItemNotAMap_isSkipped() {
        Map<String, Object> request = Map.of(
                Constants.COURSE_ID, "course1",
                Constants.BATCH_ID, "batch1",
                Constants.ASSIGNMENT_TITLE, "Assignment A",
                Constants.LEARNER_ID, "learner1"
        );

        when(cassandraOperation.getRecordsByProperties(
                eq(Constants.KEYSPACE_SUNBIRD),
                eq(Constants.TABLE_EMAIL_TEMPLATE),
                anyMap(), anyList(), isNull()))
                .thenReturn(List.of(Map.of(Constants.TEMPLATE, "<html>Hi</html>")));

        Map<String, Object> resp = new HashMap<>();
        resp.put(Constants.RESPONSE_CODE, "OK");
        resp.put(Constants.RESULT, Map.of(Constants.RESPONSE, Map.of(Constants.CONTENT, List.of("plainString"))));
        testOutboundRequestHandler.addResponse(resp);
        testOutboundRequestHandler.addResponse(Collections.emptyMap());
        testOutboundRequestHandler.addResponse(Collections.emptyMap());

        var response = notificationService.notifyAssignmentEvaluate(request, authToken);

        assertEquals(HttpStatus.OK, response.getResponseCode());
    }

    @Test
    void testFetchUserEmails_missingPersonalDetails_emailNotCollected() {
        Map<String, Object> request = Map.of(
                Constants.COURSE_ID, "course1",
                Constants.BATCH_ID, "batch1",
                Constants.ASSIGNMENT_TITLE, "Assignment A",
                Constants.LEARNER_ID, "learner1"
        );

        when(cassandraOperation.getRecordsByProperties(
                eq(Constants.KEYSPACE_SUNBIRD),
                eq(Constants.TABLE_EMAIL_TEMPLATE),
                anyMap(), anyList(), isNull()))
                .thenReturn(List.of(Map.of(Constants.TEMPLATE, "<html>Hi</html>")));

        Map<String, Object> contentEntry = Map.of(Constants.PROFILE_DETAILS, Map.of());
        Map<String, Object> resp = new HashMap<>();
        resp.put(Constants.RESPONSE_CODE, "OK");
        resp.put(Constants.RESULT, Map.of(Constants.RESPONSE, Map.of(Constants.CONTENT, List.of(contentEntry))));
        testOutboundRequestHandler.addResponse(resp);
        testOutboundRequestHandler.addResponse(Collections.emptyMap());
        testOutboundRequestHandler.addResponse(Collections.emptyMap());

        var response = notificationService.notifyAssignmentEvaluate(request, authToken);

        assertEquals(HttpStatus.OK, response.getResponseCode());
    }

}
