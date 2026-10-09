package com.igot.cb.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.igot.cb.util.CbExtServerProperties;
import com.igot.cb.util.Constants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ExternalTrainingCertificateServiceImplTest {

    private ExternalTrainingCertificateServiceImpl service;

    @Mock
    private CbExtServerProperties serverProperties;

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    @BeforeEach
    void setUp() {
        service = new ExternalTrainingCertificateServiceImpl(serverProperties, kafkaTemplate, new ObjectMapper());
    }

    private Map<String, Object> validUserDetails() {
        Map<String, Object> userDetails = new HashMap<>();
        userDetails.put(Constants.USER_ID, "user1");
        userDetails.put(Constants.FIRSTNAME, "John");
        userDetails.put(Constants.ROOT_ORG_ID, "org1");
        return userDetails;
    }

    private Map<String, Object> validEventDetails() {
        Map<String, Object> eventDetails = new HashMap<>();
        eventDetails.put(Constants.BATCH_ID, "batch1");
        eventDetails.put(Constants.EVENT_ID, "event1");
        eventDetails.put(Constants.ISSUED_DATE, "2024-01-01");
        eventDetails.put(Constants.CERT_TEMPLATE, "template-svg");
        eventDetails.put(Constants.CERT_TEMPLATE_ID, "templateId1");
        eventDetails.put(Constants.SOURCE_NAME, "providerX");
        eventDetails.put(Constants.EVENT_NAME, "Training Event");
        eventDetails.put("ets", 123456789L);
        return eventDetails;
    }

    // ===========================
    // generateCertificateEvent
    // ===========================

    @Test
    void testGenerateCertificateEvent_success() throws Exception {
        when(serverProperties.getDomainHost()).thenReturn("https://host/");
        when(serverProperties.getExternalTrainingDefaultPosterImage()).thenReturn("poster.png");

        String json = service.generateCertificateEvent(validUserDetails(), validEventDetails());

        assertNotNull(json);
        assertTrue(json.contains("\"userId\":\"user1\""));
        assertTrue(json.contains("\"recipientName\":\"John\""));
        assertTrue(json.contains("\"batchId\":\"batch1\""));
        assertTrue(json.contains("BE_JOB_REQUEST"));
    }

    @Test
    void testGenerateCertificateEvent_nullUserDetailsMap() {
        Map<String, Object> eventDetails = validEventDetails();
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.generateCertificateEvent(null, eventDetails));
        assertTrue(ex.getMessage().contains("Input map is null or empty"));
    }

    @Test
    void testGenerateCertificateEvent_emptyUserDetailsMap() {
        Map<String, Object> eventDetails = validEventDetails();
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.generateCertificateEvent(new HashMap<>(), eventDetails));
        assertTrue(ex.getMessage().contains("Input map is null or empty"));
    }

    @Test
    void testGenerateCertificateEvent_missingUserId() {
        Map<String, Object> userDetails = validUserDetails();
        userDetails.remove(Constants.USER_ID);
        Map<String, Object> eventDetails = validEventDetails();

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.generateCertificateEvent(userDetails, eventDetails));
        assertTrue(ex.getMessage().contains("Missing required field: " + Constants.USER_ID));
    }

    @Test
    void testGenerateCertificateEvent_blankFirstName() {
        Map<String, Object> userDetails = validUserDetails();
        userDetails.put(Constants.FIRSTNAME, "   ");
        Map<String, Object> eventDetails = validEventDetails();

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.generateCertificateEvent(userDetails, eventDetails));
        assertTrue(ex.getMessage().contains("Empty value for field: " + Constants.FIRSTNAME));
    }

    @Test
    void testGenerateCertificateEvent_missingEventDetailsField() {
        Map<String, Object> eventDetails = validEventDetails();
        eventDetails.remove(Constants.BATCH_ID);
        Map<String, Object> userDetails = validUserDetails();

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.generateCertificateEvent(userDetails, eventDetails));
        assertTrue(ex.getMessage().contains("Missing required field: " + Constants.BATCH_ID));
    }

    // ===========================
    // generateCertificateEventAndPushToKafka
    // ===========================

    @Test
    void testGenerateCertificateEventAndPushToKafka_success() throws Exception {
        when(serverProperties.getDomainHost()).thenReturn("https://host/");
        when(serverProperties.getExternalTrainingDefaultPosterImage()).thenReturn("poster.png");
        when(serverProperties.getUserIssueCertificateForEventTopic()).thenReturn("cert-topic");

        service.generateCertificateEventAndPushToKafka(validUserDetails(), validEventDetails());

        verify(kafkaTemplate).send(eq("cert-topic"), eq("user1"), anyString());
    }

    @Test
    void testGenerateCertificateEventAndPushToKafka_invalidDataPropagatesException() {
        Map<String, Object> userDetails = validUserDetails();
        userDetails.remove(Constants.USER_ID);
        Map<String, Object> eventDetails = validEventDetails();

        assertThrows(IllegalArgumentException.class,
                () -> service.generateCertificateEventAndPushToKafka(userDetails, eventDetails));

        verifyNoInteractions(kafkaTemplate);
    }
}
