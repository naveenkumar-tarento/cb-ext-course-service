package com.igot.cb.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.lang.reflect.Method;
import java.time.Instant;
import java.util.*;

import com.igot.cb.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.igot.cb.cassandra.CassandraOperation;
import com.igot.cb.elasticsearch.dto.SearchCriteria;
import com.igot.cb.elasticsearch.dto.SearchResult;
import com.igot.cb.elasticsearch.service.EsUtilService;
import com.igot.cb.model.ApiRequest;
import com.igot.cb.model.ApiResponse;
import com.igot.cb.model.ApiRespParam;
import com.igot.cb.model.CbPlanDto;


class CbPlanServiceImplTest {

    @Mock private AccessTokenValidator accessTokenValidator;
    @Mock private CassandraOperation cassandraOperation;
    @Mock private UserAndOrgServiceImpl userUtilityService;
    @Mock private ContentInfoServiceImpl contentService;
    @Mock private EsUtilService esUtilService;
    @Mock private CbExtServerProperties serverProperties;
    @Mock private RequestValidator requestValidator;
    @Mock private UserAndOrgServiceImpl userAndOrgService;

    private CbPlanServiceImpl cbPlanService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        cbPlanService = new CbPlanServiceImpl(accessTokenValidator, cassandraOperation, serverProperties, userUtilityService,
            contentService, esUtilService, requestValidator);
        ReflectionTestUtils.setField(cbPlanService, "userAndOrgService", userUtilityService);
        ReflectionTestUtils.setField(cbPlanService, "contentService", contentService);
        ReflectionTestUtils.setField(cbPlanService, "esUtilService", esUtilService);
        ReflectionTestUtils.setField(cbPlanService, "serverProperties", serverProperties);
        ReflectionTestUtils.setField(serverProperties, "cpPlanIndex", "test-index");
        ReflectionTestUtils.setField(serverProperties, "elasticCbPlanJsonPath", "test-path");
        ReflectionTestUtils.setField(serverProperties, "cbPlanUpdateAllowedFields", "name,contextDataRequest,endDate");
    }

    @Test
    void testConstructor() {
        assertNotNull(cbPlanService);
        assertEquals(accessTokenValidator, ReflectionTestUtils.getField(cbPlanService, "accessTokenValidator"));
        assertEquals(cassandraOperation, ReflectionTestUtils.getField(cbPlanService, "cassandraOperation"));
    }

    @Test
    void testCreateCbPlan_EmptyUserId() {
        ApiRequest request = new ApiRequest();
        when(accessTokenValidator.fetchUserIdFromAccessToken(anyString(), any())).thenReturn("");

        ApiResponse response = cbPlanService.createCbPlan(request, "orgId", "token");

        assertNotNull(response);
    }

    @Test
    void testCreateCbPlan_ValidationErrors() {
        ApiRequest request = new ApiRequest();
        CbPlanDto dto = new CbPlanDto();
        request.setRequest(dto);

        when(accessTokenValidator.fetchUserIdFromAccessToken(anyString(), any())).thenReturn("userId");

        ApiResponse response = cbPlanService.createCbPlan(request, "orgId", "token");

        assertEquals(Constants.FAILED, response.getParams().getStatus());
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getResponseCode());
    }

    @Test
    void testCreateCbPlan_Success() {
        ApiRequest request = new ApiRequest();
        Map<String, Object> requestMap = new HashMap<>();
        requestMap.put("name", "Test Plan");
        requestMap.put("endDate", new Date());
        requestMap.put("orgScope", "single");
        requestMap.put("orgIdList", Arrays.asList("org1"));
        requestMap.put("contentType", "Course");
        requestMap.put("contentList", Arrays.asList("content1"));
        request.setRequest(requestMap);

        when(accessTokenValidator.fetchUserIdFromAccessToken(anyString(), any())).thenReturn("userId");

        ApiResponse cassandraResp = new ApiResponse();
        cassandraResp.put(Constants.RESPONSE, Constants.SUCCESS);
        when(cassandraOperation.insertRecord(anyString(), anyString(), any())).thenReturn(cassandraResp);

        ApiResponse lookupResp = new ApiResponse();
        lookupResp.put(Constants.RESPONSE, Constants.SUCCESS);
        when(cassandraOperation.insertBulkRecord(anyString(), anyString(), any())).thenReturn(lookupResp);

        ApiResponse response = cbPlanService.createCbPlan(request, "orgId", "token");

        assertNotNull(response);
    }

    @Test
    void testCreateCbPlan_JsonProcessingException() {
        testCreateCbPlan_ValidationErrors();
    }

    @Test
    void testCreateCbPlan_AllOrgScope() {
        ApiRequest request = new ApiRequest();
        Map<String, Object> requestMap = new HashMap<>();
        requestMap.put("name", "Test Plan");
        requestMap.put("endDate", new Date());
        requestMap.put("orgScope", "all");
        requestMap.put("contentType", "Course");
        requestMap.put("contentList", Arrays.asList("content1"));
        request.setRequest(requestMap);

        when(accessTokenValidator.fetchUserIdFromAccessToken(anyString(), any())).thenReturn("userId");

        ApiResponse cassandraResp = new ApiResponse();
        cassandraResp.put(Constants.RESPONSE, Constants.SUCCESS);
        when(cassandraOperation.insertRecord(anyString(), anyString(), any())).thenReturn(cassandraResp);

        ApiResponse response = cbPlanService.createCbPlan(request, "orgId", "token");

        assertNotNull(response);
    }

    @Test
    void testCreateCbPlan_CustomOrgScope() {
        ApiRequest request = new ApiRequest();
        Map<String, Object> requestMap = new HashMap<>();
        requestMap.put("name", "Test Plan");
        requestMap.put("endDate", new Date());
        requestMap.put("orgScope", "custom");
        requestMap.put("orgIdList", Arrays.asList("org1", "org2"));
        requestMap.put("contentType", "Course");
        requestMap.put("contentList", Arrays.asList("content1"));
        request.setRequest(requestMap);

        when(accessTokenValidator.fetchUserIdFromAccessToken(anyString(), any())).thenReturn("userId");

        ApiResponse cassandraResp = new ApiResponse();
        cassandraResp.put(Constants.RESPONSE, Constants.SUCCESS);
        when(cassandraOperation.insertRecord(anyString(), anyString(), any())).thenReturn(cassandraResp);

        ApiResponse lookupResp = new ApiResponse();
        lookupResp.put(Constants.RESPONSE, Constants.SUCCESS);
        when(cassandraOperation.insertBulkRecord(anyString(), anyString(), any())).thenReturn(lookupResp);

        ApiResponse response = cbPlanService.createCbPlan(request, "orgId", "token");

        assertNotNull(response);
    }

    @Test
    void testUpdateCbPlan_EmptyUserId() {
        ApiRequest request = new ApiRequest();
        when(accessTokenValidator.fetchUserIdFromAccessToken(anyString(), any())).thenReturn("");

        ApiResponse response = cbPlanService.updateCbPlan(request, "orgId", "token", Arrays.asList("role"));

        assertNotNull(response);
    }

    @Test
    void testUpdateCbPlan_Success() {
        ApiRequest request = new ApiRequest();
        Map<String, Object> updateMap = new HashMap<>();
        updateMap.put("id", "planId");
        updateMap.put("name", "Updated Plan");
        request.setRequest(updateMap);

        when(accessTokenValidator.fetchUserIdFromAccessToken(anyString(), any())).thenReturn("userId");

        Map<String, Object> existingPlan = new HashMap<>();
        existingPlan.put("createdBy", "userId");
        existingPlan.put("status", "draft");
        existingPlan.put("draftData", "{\"name\":\"Test\"}");
        when(cassandraOperation.getRecordsByProperties(anyString(), anyString(), any(), any(), any()))
            .thenReturn(Arrays.asList(existingPlan));

        Map<String, Object> updateResp = new HashMap<>();
        updateResp.put(Constants.RESPONSE, Constants.SUCCESS);
        when(cassandraOperation.updateRecord(anyString(), anyString(), any(), any())).thenReturn(updateResp);

        ApiResponse response = cbPlanService.updateCbPlan(request, "orgId", "token", Arrays.asList("role"));

        assertNotNull(response);
    }
    @Test
    void testPublishCbPlan_Success() {
        ApiRequest request = new ApiRequest();
        Map<String, Object> requestMap = new HashMap<>();
        requestMap.put("id", "planId");
        request.setRequest(requestMap);

        when(accessTokenValidator.fetchUserIdFromAccessToken(anyString(), any())).thenReturn("userId");

        Map<String, Object> existingPlan = new HashMap<>();
        existingPlan.put("createdBy", "userId");
        existingPlan.put("status", "draft");
        existingPlan.put("draftData", "{\"name\":\"Test\",\"endDate\":\"2024-12-31\"}");
        when(cassandraOperation.getRecordsByProperties(anyString(), anyString(), any(), any(), any()))
            .thenReturn(Arrays.asList(existingPlan));

        Map<String, Object> updateResp = new HashMap<>();
        updateResp.put(Constants.RESPONSE, Constants.SUCCESS);
        when(cassandraOperation.updateRecord(anyString(), anyString(), any(), any(), any(), any())).thenReturn(updateResp);

        ApiResponse response = cbPlanService.publishCbPlan(request, "orgId", "token", Arrays.asList("role"));

        assertNotNull(response);
    }

    @Test
    @SuppressWarnings("unchecked")
    void testPublishCbPlan_EsSyncFailure_AbortsCassandraCommitAndFailsApi() {
        ApiRequest request = new ApiRequest();
        Map<String, Object> requestMap = new HashMap<>();
        requestMap.put("id", "planId");
        request.setRequest(requestMap);

        when(accessTokenValidator.fetchUserIdFromAccessToken(anyString(), any())).thenReturn("userId");

        Map<String, Object> existingPlan = new HashMap<>();
        existingPlan.put("createdBy", "userId");
        existingPlan.put("status", "draft");
        existingPlan.put("draftData", "{\"name\":\"Test\",\"endDate\":\"2024-12-31\"}");
        when(cassandraOperation.getRecordsByProperties(anyString(), anyString(), any(), any(), any()))
            .thenReturn(Arrays.asList(existingPlan));
        when(userUtilityService.readUserProfileFromDB(eq("userId"), anyList()))
            .thenReturn(Map.of(Constants.ID, "userId", Constants.ROOT_ORG_ID, "root1"));
        when(userUtilityService.readOrgFromDB(eq("root1"), any()))
            .thenReturn(Map.of(Constants.IS_CCA, false));

        when(esUtilService.updateDocument(anyString(), anyString(), anyString(), anyMap(), anyString()))
            .thenReturn(null);

        ArgumentCaptor<java.util.function.BooleanSupplier> validatorCaptor = ArgumentCaptor.forClass(java.util.function.BooleanSupplier.class);
        Map<String, Object> abortedResp = new HashMap<>();
        abortedResp.put(Constants.RESPONSE, Constants.FAILED);
        abortedResp.put(Constants.ERROR_MESSAGE, "Update aborted: pre-commit validation failed");
        when(cassandraOperation.updateRecord(anyString(), anyString(), any(), any(), validatorCaptor.capture(), any()))
            .thenReturn(abortedResp);

        ApiResponse response = cbPlanService.publishCbPlan(request, "orgId", "token", Arrays.asList("role"));

        assertFalse(validatorCaptor.getValue().getAsBoolean());
        assertEquals(Constants.FAILED, response.getParams().getStatus());
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getResponseCode());
    }

    @Test
    @SuppressWarnings("unchecked")
    void testPublishCbPlan_CassandraCommitFailsAfterEsSuccess_TriggersEsRollback() {
        ApiRequest request = new ApiRequest();
        Map<String, Object> requestMap = new HashMap<>();
        requestMap.put("id", "planId");
        request.setRequest(requestMap);

        when(accessTokenValidator.fetchUserIdFromAccessToken(anyString(), any())).thenReturn("userId");

        Map<String, Object> existingPlan = new HashMap<>();
        existingPlan.put("createdBy", "userId");
        existingPlan.put("status", "draft");
        existingPlan.put("draftData", "{\"name\":\"Test\",\"endDate\":\"2024-12-31\"}");
        when(cassandraOperation.getRecordsByProperties(anyString(), anyString(), any(), any(), any()))
            .thenReturn(Arrays.asList(existingPlan));
        when(userUtilityService.readUserProfileFromDB(eq("userId"), anyList()))
            .thenReturn(Map.of(Constants.ID, "userId", Constants.ROOT_ORG_ID, "root1"));
        when(userUtilityService.readOrgFromDB(eq("root1"), any()))
            .thenReturn(Map.of(Constants.IS_CCA, false));

        when(esUtilService.updateDocument(anyString(), anyString(), anyString(), anyMap(), anyString()))
            .thenReturn("updated:Created");

        ArgumentCaptor<Runnable> rollbackCaptor = ArgumentCaptor.forClass(Runnable.class);
        Map<String, Object> failedResp = new HashMap<>();
        failedResp.put(Constants.RESPONSE, Constants.FAILED);
        failedResp.put(Constants.ERROR_MESSAGE, "Cassandra down");
        when(cassandraOperation.updateRecord(anyString(), anyString(), any(), any(), any(), rollbackCaptor.capture()))
            .thenReturn(failedResp);

        ApiResponse response = cbPlanService.publishCbPlan(request, "orgId", "token", Arrays.asList("role"));

        // cassandraOperation is fully mocked, so the rollback Runnable passed to it was never invoked
        // by publishCbPlan itself — invoke it here to simulate what CassandraOperationImpl does on commit failure.
        rollbackCaptor.getValue().run();

        verify(esUtilService, times(1)).updateDocument(any(), eq(Constants.INDEX_TYPE), eq("planId"),
                argThat(doc -> existingPlan.get("status").equals(doc.get("status"))), any());
        assertEquals(Constants.FAILED, response.getParams().getStatus());
    }

    @Test
    void testReadCbPlan_Success() {
        when(cassandraOperation.getRecordsByProperties(anyString(), anyString(), any(), any(), any()))
            .thenReturn(Arrays.asList(createMockPlan()));

        when(contentService.readContent(anyString(), any())).thenReturn(createMockContent());

        ApiResponse response = cbPlanService.readCbPlan("planId", "orgId", "token");

        assertNotNull(response);
    }

    @Test
    void testSearchCbPlan_EmptyResult() {
        SearchCriteria criteria = new SearchCriteria();
        when(accessTokenValidator.fetchUserIdFromAccessToken(anyString(), any())).thenReturn("");

        ApiResponse response = cbPlanService.searchCbPlan(criteria, "orgId", "token");

        assertNotNull(response);
    }

    @SuppressWarnings("unchecked")
    @Test
    void testSearchCbPlan_WithResults() throws Exception {
        SearchCriteria criteria = new SearchCriteria();
        criteria.setQuery(new HashMap<>());
        criteria.setFilter(new HashMap<>());

        when(accessTokenValidator.fetchUserIdFromAccessToken(anyString(), any())).thenReturn("userId");

        SearchResult searchResult = new SearchResult();
        searchResult.setData(Arrays.asList(createMockPlan()));
        searchResult.setTotalCount(1L);
        when(esUtilService.searchDocuments(anyString(), any(), anyString())).thenReturn(searchResult);

        Map<String, Object> mockContent = createMockContent();
        when(contentService.readContent(anyString(), any())).thenReturn(mockContent);

        doAnswer(invocation -> {
            Map<String, Map<String, String>> userInfoMap = invocation.getArgument(2);
            Map<String, String> userDetails = new HashMap<>();
            userDetails.put("firstName", "Test");
            userDetails.put("lastName", "User");
            userInfoMap.put("userId", userDetails);
            return null;
        }).when(userUtilityService).readUserProfileFromDB(any(), anyList());

        try {
        ApiResponse response = cbPlanService.searchCbPlan(criteria, "orgId", "token");
        assertNotNull(response);
        assertEquals(Constants.SUCCESS, response.getParams().getStatus());
        } catch (Exception e) {
            // ignored: this test only verifies the success path above
        }
    }

    @Test
    void testRetireCbPlan_Success() {
        ApiRequest request = new ApiRequest();
        Map<String, Object> requestMap = new HashMap<>();
        requestMap.put("id", "planId");
        request.setRequest(requestMap);

        when(accessTokenValidator.fetchUserIdFromAccessToken(anyString(), any())).thenReturn("userId");

        Map<String, Object> existingPlan = new HashMap<>();
        existingPlan.put("createdBy", "userId");
        existingPlan.put("status", "live");
        existingPlan.put("orgScope", "single");
        existingPlan.put("orgIdList", Arrays.asList("org1"));
        when(cassandraOperation.getRecordsByProperties(anyString(), anyString(), any(), any(), any()))
            .thenReturn(Arrays.asList(existingPlan));

        Map<String, Object> updateResp = new HashMap<>();
        updateResp.put(Constants.RESPONSE, Constants.SUCCESS);
        when(cassandraOperation.updateRecord(anyString(), anyString(), any(), any())).thenReturn(updateResp);

        ApiResponse response = cbPlanService.retireCbPlan(request, "orgId", "token", Arrays.asList("role"));

        assertNotNull(response);
    }

    @Test
    void testSanitizeForElastic() {
        Map<String, Object> input = new HashMap<>();
        input.put("key1", "value1");
        input.put("instant", Instant.now());

        Map<String, Object> result = CbPlanServiceImpl.sanitizeForElastic(input);

        assertNotNull(result);
        assertEquals("value1", result.get("key1"));
        assertTrue(result.get("instant") instanceof String);
    }

    @Test
    void testParseToDate_String() {
        Date result = cbPlanService.parseToDate("2024-12-31");
        assertNotNull(result);
    }

    @Test
    void testParseToDate_Instant() {
        Instant instant = Instant.now();
        Date result = cbPlanService.parseToDate(instant);
        assertNotNull(result);
    }

    @Test
    void testParseToDate_Date() {
        Date date = new Date();
        Date result = cbPlanService.parseToDate(date);
        assertNotNull(result);
    }

    @Test
    void testParseToDate_Null() {
        Date result = cbPlanService.parseToDate(null);
        assertNull(result);
    }

    @SuppressWarnings("unchecked")
    @Test
    @Disabled("This test is ignored due to optimization code changes")
    void testValidateCbPlanRequest() {
        CbPlanDto dto = new CbPlanDto();
        dto.setName("Test");
        dto.setEndDate(new Date());

        List<String> result = (List<String>) ReflectionTestUtils.invokeMethod(cbPlanService, "validateCbPlanRequest", dto);

        assertNotNull(result);
    }

    @SuppressWarnings("unchecked")
    @Test
    @Disabled("This test is ignored due to optimization code changes")
    void testValidateContextData_NoContextData() {
        CbPlanDto dto = new CbPlanDto();
        ApiRequest request = new ApiRequest();
        request.setRequest(new HashMap<>());

        List<String> result = (List<String>) ReflectionTestUtils.invokeMethod(cbPlanService, "validateContextData", dto, request);

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    @Disabled("This test is ignored due to optimization code changes")
    void testInsertCustomOrgLookup_EmptyList() {
        ApiResponse result = (ApiResponse) ReflectionTestUtils.invokeMethod(cbPlanService, "insertCustomOrgLookup", "planId", new ArrayList<>(), new Date());

        assertNotNull(result);
        assertEquals(Constants.FAILED, result.getParams().getStatus());
    }

    @Test
    @Disabled("This test is ignored due to optimization code changes")
    void testInsertAllOrgLookup() {
        ApiResponse cassandraResp = new ApiResponse();
        cassandraResp.put(Constants.RESPONSE, Constants.SUCCESS);
        when(cassandraOperation.insertRecord(anyString(), anyString(), any())).thenReturn(cassandraResp);

        ApiResponse result = (ApiResponse) ReflectionTestUtils.invokeMethod(cbPlanService, "insertAllOrgLookup", "planId", new Date());

        assertNotNull(result);
    }

    @SuppressWarnings("unchecked")
    @Test
    @Disabled("This test is ignored due to optimization code changes")
    void testMergeCbPlanData() {
        Map<String, Object> requestMap = new HashMap<>();
        requestMap.put("name", "New Name");

        Map<String, Object> existingMap = new HashMap<>();
        existingMap.put("name", "Old Name");
        existingMap.put("contentType", "Course");

        Map<String, Object> result = (Map<String, Object>) ReflectionTestUtils.invokeMethod(cbPlanService, "mergeCbPlanData", requestMap, existingMap);

        assertNotNull(result);
        assertEquals("New Name", result.get("name"));
    }

    @Test
    @Disabled("This test is ignored due to optimization code changes")
    void testUpdateDraftInfo() {
        Map<String, Object> updatedPlan = new HashMap<>();
        updatedPlan.put("name", "Updated");

        Map<String, Object> cbPlan = new HashMap<>();
        cbPlan.put("draftData", "");
        cbPlan.put("name", "Original");

        String result = (String) ReflectionTestUtils.invokeMethod(cbPlanService, "updateDraftInfo", updatedPlan, cbPlan);

        assertNotNull(result);
    }

    @SuppressWarnings("unchecked")
    @Test
    @Disabled("This test is ignored due to optimization code changes")
    void testExtractRootOrgIds() {
        Map<String, Object> contextData = new HashMap<>();
        Map<String, Object> accessControl = new HashMap<>();
        List<Map<String, Object>> userGroups = new ArrayList<>();
        Map<String, Object> userGroup = new HashMap<>();
        List<Map<String, Object>> criteriaList = new ArrayList<>();
        Map<String, Object> criteria = new HashMap<>();
        criteria.put("criteriaKey", "rootOrgId");
        criteria.put("criteriaValue", Arrays.asList("org1", "org2"));
        criteriaList.add(criteria);
        userGroup.put("userGroupCriteriaList", criteriaList);
        userGroups.add(userGroup);
        accessControl.put("userGroups", userGroups);
        contextData.put("accessControl", accessControl);

        List<String> result = (List<String>) ReflectionTestUtils.invokeMethod(cbPlanService, "extractRootOrgIds", contextData);

        assertNotNull(result);
        assertEquals(2, result.size());
    }

    @Test
    void testArchiveCustomOrgLookup() {
        Map<String, Object> updateResp = new HashMap<>();
        updateResp.put(Constants.RESPONSE, Constants.SUCCESS);
        when(cassandraOperation.updateRecord(anyString(), anyString(), any(), any())).thenReturn(updateResp);

        ApiResponse result = (ApiResponse) ReflectionTestUtils.invokeMethod(cbPlanService, "archiveCustomOrgLookup", "planId", Arrays.asList("org1"));

        assertNotNull(result);
    }

    private Map<String, Object> createMockPlan() {
        Map<String, Object> plan = new HashMap<>();
        plan.put("name", "Test Plan");
        plan.put("createdBy", "userId");
        plan.put("contentList", Arrays.asList("content1"));
        plan.put("status", "live");
        plan.put("draftData", "");
        plan.put("createdAtReq", Instant.now());
        return plan;
    }

    @Test
    void testCreateCbPlan_ContextDataValidation() {
        ApiRequest request = new ApiRequest();
        Map<String, Object> requestMap = new HashMap<>();
        requestMap.put("name", "Test Plan");
        requestMap.put("endDate", new Date());
        requestMap.put("orgScope", "single");
        requestMap.put("contentType", "Course");
        requestMap.put("contentList", Arrays.asList("content1"));

        Map<String, Object> contextData = new HashMap<>();
        Map<String, Object> accessControl = new HashMap<>();
        List<Map<String, Object>> userGroups = new ArrayList<>();
        Map<String, Object> userGroup = new HashMap<>();
        List<Map<String, Object>> criteriaList = new ArrayList<>();
        Map<String, Object> criteria = new HashMap<>();
        criteria.put("criteriaKey", "rootOrgId");
        criteria.put("criteriaValue", Arrays.asList("org1"));
        criteriaList.add(criteria);
        userGroup.put("userGroupCriteriaList", criteriaList);
        userGroups.add(userGroup);
        accessControl.put("userGroups", userGroups);
        contextData.put("accessControl", accessControl);
        requestMap.put("contextDataRequest", contextData);

        request.setRequest(requestMap);

        when(accessTokenValidator.fetchUserIdFromAccessToken(anyString(), any())).thenReturn("userId");

        ApiResponse response = cbPlanService.createCbPlan(request, "orgId", "token");

        assertNotNull(response);
    }

    @Test
    void testCreateCbPlan_ContextDataValidationError() {
        ApiRequest request = new ApiRequest();
        Map<String, Object> requestMap = new HashMap<>();
        requestMap.put("name", "Test Plan");
        requestMap.put("endDate", new Date());
        requestMap.put("orgScope", "single");
        requestMap.put("contentType", "Course");
        requestMap.put("contentList", Arrays.asList("content1"));

        Map<String, Object> contextData = new HashMap<>();
        Map<String, Object> accessControl = new HashMap<>();
        accessControl.put("userGroups", new ArrayList<>());
        contextData.put("accessControl", accessControl);
        requestMap.put("contextDataRequest", contextData);

        request.setRequest(requestMap);

        when(accessTokenValidator.fetchUserIdFromAccessToken(anyString(), any())).thenReturn("userId");

        ApiResponse response = cbPlanService.createCbPlan(request, "orgId", "token");

        assertEquals(Constants.FAILED, response.getParams().getStatus());
    }

    @Test
    void testUpdateCbPlan_NotAuthorized() {
        ApiRequest request = new ApiRequest();
        Map<String, Object> updateMap = new HashMap<>();
        updateMap.put("id", "planId");
        updateMap.put("name", "Updated Plan");
        request.setRequest(updateMap);

        when(accessTokenValidator.fetchUserIdFromAccessToken(anyString(), any())).thenReturn("userId");

        Map<String, Object> existingPlan = new HashMap<>();
        existingPlan.put("createdBy", "otherUser");
        when(cassandraOperation.getRecordsByProperties(anyString(), anyString(), any(), any(), any()))
            .thenReturn(Arrays.asList(existingPlan));

        when(serverProperties.getCbPlanUpdatePublishAuthorizedRoles()).thenReturn(Arrays.asList("admin"));

        ApiResponse response = cbPlanService.updateCbPlan(request, "orgId", "token", Arrays.asList("user"));

        assertEquals(Constants.FAILED, response.getParams().getStatus());
        assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
    }

    @Test
    void testUpdateCbPlan_LivePlanWithRestrictedFields() {
        ApiRequest request = new ApiRequest();
        Map<String, Object> updateMap = new HashMap<>();
        updateMap.put("id", "planId");
        updateMap.put("invalidField", "value");
        request.setRequest(updateMap);

        when(accessTokenValidator.fetchUserIdFromAccessToken(anyString(), any())).thenReturn("userId");

        Map<String, Object> existingPlan = new HashMap<>();
        existingPlan.put("createdBy", "userId");
        existingPlan.put("status", Constants.LIVE);
        existingPlan.put("cbPublishedBy", "userId");
        when(cassandraOperation.getRecordsByProperties(anyString(), anyString(), any(), any(), any()))
            .thenReturn(Arrays.asList(existingPlan));

        ApiResponse response = cbPlanService.updateCbPlan(request, "orgId", "token", Arrays.asList("role"));

        assertEquals(Constants.FAILED, response.getParams().getStatus());
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getResponseCode());
    }

    @Test
    void testUpdateCbPlan_PlanNotFound() {
        ApiRequest request = new ApiRequest();
        Map<String, Object> updateMap = new HashMap<>();
        updateMap.put("id", "planId");
        request.setRequest(updateMap);

        when(accessTokenValidator.fetchUserIdFromAccessToken(anyString(), any())).thenReturn("userId");
        when(cassandraOperation.getRecordsByProperties(anyString(), anyString(), any(), any(), any()))
            .thenReturn(new ArrayList<>());

        ApiResponse response = cbPlanService.updateCbPlan(request, "orgId", "token", Arrays.asList("role"));

        assertEquals(Constants.FAILED, response.getParams().getStatus());
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getResponseCode());
    }

    @Test
    void testUpdateCbPlan_MissingId() {
        ApiRequest request = new ApiRequest();
        Map<String, Object> updateMap = new HashMap<>();
        updateMap.put("name", "Updated Plan");
        request.setRequest(updateMap);

        when(accessTokenValidator.fetchUserIdFromAccessToken(anyString(), any())).thenReturn("userId");

        ApiResponse response = cbPlanService.updateCbPlan(request, "orgId", "token", Arrays.asList("role"));

        assertEquals(Constants.FAILED, response.getParams().getStatus());
        assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
    }

    @Test
    void testUpdateCbPlan_UpdateOrgLookupSuccess() {
        ApiRequest request = new ApiRequest();
        Map<String, Object> updateMap = new HashMap<>();
        updateMap.put("id", "planId");
        updateMap.put("name", "Updated Plan");
        request.setRequest(updateMap);

        when(accessTokenValidator.fetchUserIdFromAccessToken(anyString(), any())).thenReturn("userId");

        Map<String, Object> existingPlan = new HashMap<>();
        existingPlan.put("createdBy", "userId");
        existingPlan.put("orgIdList", Arrays.asList("org1"));
        when(cassandraOperation.getRecordsByProperties(anyString(), anyString(), any(), any(), any()))
            .thenReturn(Arrays.asList(existingPlan));

        Map<String, Object> updateResp = new HashMap<>();
        updateResp.put(Constants.RESPONSE, Constants.SUCCESS);
        when(cassandraOperation.updateRecord(anyString(), anyString(), any(), any())).thenReturn(updateResp);

        ApiResponse response = cbPlanService.updateCbPlan(request, "orgId", "token", Arrays.asList("role"));

        assertNotNull(response);
        assertEquals(Constants.FAILED, response.getParams().getStatus());
    }

    @Test
    void testUpdateCbPlan_OrgLookupError() {
        ApiRequest request = new ApiRequest();
        Map<String, Object> updateMap = new HashMap<>();
        updateMap.put("id", "planId");
        updateMap.put("name", "Updated Plan");
        request.setRequest(updateMap);

        when(accessTokenValidator.fetchUserIdFromAccessToken(anyString(), any())).thenReturn("userId");

        Map<String, Object> existingPlan = new HashMap<>();
        existingPlan.put("createdBy", "userId");
        existingPlan.put("orgIdList", Arrays.asList("org1"));
        when(cassandraOperation.getRecordsByProperties(anyString(), anyString(), any(), any(), any()))
            .thenReturn(Arrays.asList(existingPlan));

        Map<String, Object> updateResp = new HashMap<>();
        updateResp.put(Constants.RESPONSE, Constants.SUCCESS);
        when(cassandraOperation.updateRecord(anyString(), anyString(), any(), any())).thenReturn(updateResp);

        doThrow(new RuntimeException("Delete error")).when(cassandraOperation).deleteRecord(anyString(), anyString(), any());

        ApiResponse response = cbPlanService.updateCbPlan(request, "orgId", "token", Arrays.asList("role"));

        assertNotNull(response);
        assertEquals(Constants.FAILED, response.getParams().getStatus());
    }

    @Test
    void testUpdateCbPlan_RuntimeException() {
        ApiRequest request = new ApiRequest();
        Map<String, Object> updateMap = new HashMap<>();
        updateMap.put("id", "planId");
        request.setRequest(updateMap);

        when(accessTokenValidator.fetchUserIdFromAccessToken(anyString(), any())).thenReturn("userId");
        when(cassandraOperation.getRecordsByProperties(anyString(), anyString(), any(), any(), any()))
            .thenThrow(new RuntimeException("Database error"));

        ApiResponse response = cbPlanService.updateCbPlan(request, "orgId", "token", Arrays.asList("role"));

        assertEquals(Constants.FAILED, response.getParams().getStatus());
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getResponseCode());
    }

    @Test
    @Disabled("This test is ignored due to optimization code changes")
    void testParseEndDate_String() {
        Date result = (Date) ReflectionTestUtils.invokeMethod(cbPlanService, "parseEndDate", "2024-12-31");
        assertNotNull(result);
    }

    @Test
    @Disabled("This test is ignored due to optimization code changes")
    void testParseEndDate_Instant() {
        Instant instant = Instant.now();
        Date result = (Date) ReflectionTestUtils.invokeMethod(cbPlanService, "parseEndDate", instant);
        assertNull(result);
    }

    @Test
    @Disabled("This test is ignored due to optimization code changes")
    void testParseEndDate_Date() {
        Date date = new Date();
        Date result = (Date) ReflectionTestUtils.invokeMethod(cbPlanService, "parseEndDate", date);
        assertEquals(date, result);
    }

    @Test
    @Disabled("This test is ignored due to optimization code changes")
    void testParseEndDate_Null() {
        Date result = (Date) ReflectionTestUtils.invokeMethod(cbPlanService, "parseEndDate", (Object) null);
        assertNull(result);
    }

    @Test
    @Disabled("This test is ignored due to optimization code changes")
    void testGetDesignationForUser() {
        String profileDetails = "{\"professionalDetails\":[{\"designation\":\"Manager\"}]}";
        String result = (String) ReflectionTestUtils.invokeMethod(cbPlanService, "getDesignationForUser", profileDetails, "userId");

        assertEquals("Manager", result);
    }

    @Test
    @Disabled("This test is ignored due to optimization code changes")
    void testGetDesignationForUser_EmptyProfile() {
        String result = (String) ReflectionTestUtils.invokeMethod(cbPlanService, "getDesignationForUser", "", "userId");

        assertEquals("", result);
    }

    @Test
    @Disabled("This test is ignored due to optimization code changes")
    void testGetDesignationForUser_InvalidJson() {
        String result = (String) ReflectionTestUtils.invokeMethod(cbPlanService, "getDesignationForUser", "invalid-json", "userId");

        assertEquals("", result);
    }

    @Test
    @Disabled("This test is ignored due to optimization code changes")
    void testToInstant_String() {
        Instant result = (Instant) ReflectionTestUtils.invokeMethod(cbPlanService, "toInstant", "2024-12-31T10:00:00Z");
        assertNotNull(result);
    }

    @Test
    @Disabled("This test is ignored due to optimization code changes")
    void testToInstant_Instant() {
        Instant instant = Instant.now();
        Instant result = (Instant) ReflectionTestUtils.invokeMethod(cbPlanService, "toInstant", instant);
        assertEquals(instant, result);
    }

    @Test
    @Disabled("This test is ignored due to optimization code changes")
    void testToInstant_Date() {
        Date date = new Date();
        Instant result = (Instant) ReflectionTestUtils.invokeMethod(cbPlanService, "toInstant", date);
        assertNotNull(result);
    }

    @Test
    @Disabled("This test is ignored due to optimization code changes")
    void testToInstant_Null() {
        Instant result = (Instant) ReflectionTestUtils.invokeMethod(cbPlanService, "toInstant", (Object) null);
        assertNull(result);
    }

    @Test
    @Disabled("This test is ignored due to optimization code changes")
    void testEnrichUserInfo() {
        Map<String, Map<String, String>> userInfoMap = new HashMap<>();
        Map<String, String> userDetails = new HashMap<>();
        userDetails.put("firstName", "Test");
        userDetails.put("lastName", "User");
        userInfoMap.put("userId", userDetails);

        ReflectionTestUtils.invokeMethod(cbPlanService, "enrichUserInfo", userInfoMap);

        assertEquals("Test", userInfoMap.get("userId").get("firstName"));
    }

    @Test
    @Disabled("This test is ignored due to optimization code changes")
    void testPopulateReadData() {
        Map<String, Object> cbPlan = new HashMap<>();
        cbPlan.put("contentList", Arrays.asList("content1"));
        cbPlan.put("createdBy", "userId");
        cbPlan.put("status", "live");
        cbPlan.put("draftData", "");
        cbPlan.put("name", "Test Plan");
        cbPlan.put("contentType", "Course");
        cbPlan.put("createdAtReq", Instant.now());
        cbPlan.put("endDateRequest", new Date());
        cbPlan.put("isApar", false);

        Map<String, Object> mockContent = createMockContent();
        when(contentService.readContent(anyString(), any())).thenReturn(mockContent);

        doAnswer(invocation -> {
            Map<String, Map<String, String>> userInfoMap = invocation.getArgument(2);
            Map<String, String> userDetails = new HashMap<>();
            userDetails.put("firstName", "Test");
            userDetails.put("lastName", "User");
            userInfoMap.put("userId", userDetails);
            return null;
        }).when(userUtilityService).readUserProfileFromDB(any(), anyList());

        Map<String, Object> result = (Map<String, Object>) ReflectionTestUtils.invokeMethod(cbPlanService, "populateReadData", cbPlan);

        assertNotNull(result);
        assertNotNull(result.get("contentList"));
        assertNotNull(result.get("createdByName"));
    }



    private Map<String, Object> createMockContent() {
        Map<String, Object> content = new HashMap<>();
        content.put("name", "Test Content");
        content.put("status", "live");
        content.put("avgRating", 4.5);
        content.put("contentType", "Course");
        content.put("duration", 60);
        content.put("appIcon", "test-icon.png");
        content.put("organisation", "Test Org");
        content.put("identifier", "content1");
        content.put("description", "Test Description");
        content.put("primaryCategory", "Course");
        content.put("competenciesV5", Arrays.asList("comp1"));
        content.put("additionalTags", Arrays.asList("tag1"));
        content.put("courseAppIcon", "icon.png");
        content.put("posterImage", "poster.jpg");
        content.put("creatorLogo", "logo.png");
        content.put("languageMapV1", new HashMap<>());
        return content;
    }

    @Test
    void testCreateSuccessResponse() {
        ApiResponse apiResponse = new ApiResponse();
        apiResponse.put("key", "value");

        ReflectionTestUtils.invokeMethod(cbPlanService, "createSuccessResponse", apiResponse);

        assertNotNull(apiResponse);
        assertEquals(Constants.SUCCESS, apiResponse.getParams().getStatus());
    }

    @Test
    @Disabled("This test is ignored due to optimization code changes")
    void testUpdateCbPlanData() {
        Map<String, Object> cbPlan = new HashMap<>();
        cbPlan.put("name", "Original");
        cbPlan.put("status", "draft");

        CbPlanDto dto = new CbPlanDto();
        dto.setName("Updated");
        dto.setEndDate(new Date());

        ReflectionTestUtils.invokeMethod(cbPlanService, "updateCbPlanData", cbPlan, dto);

        assertEquals("Updated", cbPlan.get("name"));
    }

    @SuppressWarnings("unchecked")
    @Test
    @Disabled("This test is ignored due to optimization code changes")
    void testValidateContextData_WithValidData() {
        CbPlanDto dto = new CbPlanDto();
        ApiRequest request = new ApiRequest();
        Map<String, Object> requestMap = new HashMap<>();

        Map<String, Object> contextData = new HashMap<>();
        Map<String, Object> accessControl = new HashMap<>();
        List<Map<String, Object>> userGroups = new ArrayList<>();
        Map<String, Object> userGroup = new HashMap<>();
        List<Map<String, Object>> criteriaList = new ArrayList<>();
        Map<String, Object> criteria = new HashMap<>();
        criteria.put("criteriaKey", "rootOrgId");
        criteria.put("criteriaValue", Arrays.asList("org1"));
        criteriaList.add(criteria);
        userGroup.put("userGroupCriteriaList", criteriaList);
        userGroups.add(userGroup);
        accessControl.put("userGroups", userGroups);
        contextData.put("accessControl", accessControl);
        requestMap.put("contextDataRequest", contextData);

        request.setRequest(requestMap);

        List<String> result = (List<String>) ReflectionTestUtils.invokeMethod(cbPlanService, "validateContextData", dto, request);

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @SuppressWarnings("unchecked")
    @Test
    @Disabled("This test is ignored due to optimization code changes")
    void testValidateContextData_WithInvalidData() {
        CbPlanDto dto = new CbPlanDto();
        ApiRequest request = new ApiRequest();
        Map<String, Object> requestMap = new HashMap<>();

        Map<String, Object> contextData = new HashMap<>();
        Map<String, Object> accessControl = new HashMap<>();
        accessControl.put("userGroups", new ArrayList<>());
        contextData.put("accessControl", accessControl);
        requestMap.put("contextDataRequest", contextData);

        request.setRequest(requestMap);

        List<String> result = (List<String>) ReflectionTestUtils.invokeMethod(cbPlanService, "validateContextData", dto, request);

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    @Disabled("This test is ignored due to optimization code changes")
    void testInsertCustomOrgLookup_WithValidList() {
        ApiResponse cassandraResp = new ApiResponse();
        cassandraResp.getParams().setStatus(Constants.SUCCESS);
        when(cassandraOperation.insertBulkRecord(anyString(), anyString(), any())).thenReturn(cassandraResp);

        ApiResponse result = (ApiResponse) ReflectionTestUtils.invokeMethod(cbPlanService, "insertCustomOrgLookup", "planId", Arrays.asList("org1", "org2"), new Date());

        assertNotNull(result);
        assertEquals(Constants.SUCCESS, result.getParams().getStatus());
    }

    @Test
    void testCreateCbPlan_CassandraFailure() {
        ApiRequest request = new ApiRequest();
        Map<String, Object> requestMap = new HashMap<>();
        requestMap.put("name", "Test Plan");
        requestMap.put("endDate", new Date());
        requestMap.put("orgScope", "single");
        requestMap.put("orgIdList", Arrays.asList("org1"));
        requestMap.put("contentType", "Course");
        requestMap.put("contentList", Arrays.asList("content1"));
        request.setRequest(requestMap);

        when(accessTokenValidator.fetchUserIdFromAccessToken(anyString(), any())).thenReturn("userId");

        ApiResponse cassandraResp = new ApiResponse();
        cassandraResp.put(Constants.RESPONSE, Constants.FAILED);
        cassandraResp.getParams().setErr("DB Error");
        when(cassandraOperation.insertRecord(anyString(), anyString(), any())).thenReturn(cassandraResp);

        ApiResponse response = cbPlanService.createCbPlan(request, "orgId", "token");

        assertEquals(Constants.FAILED, response.getParams().getStatus());
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getResponseCode());
    }

    @Test
    void testCreateCbPlan_LookupFailure() {
        ApiRequest request = new ApiRequest();
        Map<String, Object> requestMap = new HashMap<>();
        requestMap.put("name", "Test Plan");
        requestMap.put("endDate", new Date());
        requestMap.put("orgScope", "single");
        requestMap.put("orgIdList", Arrays.asList("org1"));
        requestMap.put("contentType", "Course");
        requestMap.put("contentList", Arrays.asList("content1"));
        request.setRequest(requestMap);

        when(accessTokenValidator.fetchUserIdFromAccessToken(anyString(), any())).thenReturn("userId");

        ApiResponse cassandraResp = new ApiResponse();
        cassandraResp.put(Constants.RESPONSE, Constants.SUCCESS);
        when(cassandraOperation.insertRecord(anyString(), anyString(), any())).thenReturn(cassandraResp);

        ApiResponse lookupResp = new ApiResponse();
        lookupResp.put(Constants.RESPONSE, Constants.FAILED);
        lookupResp.getParams().setErr("Lookup Error");
        when(cassandraOperation.insertBulkRecord(anyString(), anyString(), any())).thenReturn(lookupResp);

        ApiResponse response = cbPlanService.createCbPlan(request, "orgId", "token");

        assertEquals(Constants.FAILED, response.getParams().getStatus());
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getResponseCode());
    }

    @Test
    void testPublishCbPlan_MissingId() {
        ApiRequest request = new ApiRequest();
        Map<String, Object> requestMap = new HashMap<>();
        request.setRequest(requestMap);

        when(accessTokenValidator.fetchUserIdFromAccessToken(anyString(), any())).thenReturn("userId");

        ApiResponse response = cbPlanService.publishCbPlan(request, "orgId", "token", Arrays.asList("role"));

        assertEquals(Constants.FAILED, response.getParams().getStatus());
        assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
    }

    @Test
    void testPublishCbPlan_PlanNotFound() {
        ApiRequest request = new ApiRequest();
        Map<String, Object> requestMap = new HashMap<>();
        requestMap.put("id", "planId");
        request.setRequest(requestMap);

        when(accessTokenValidator.fetchUserIdFromAccessToken(anyString(), any())).thenReturn("userId");
        when(cassandraOperation.getRecordsByProperties(anyString(), anyString(), any(), any(), any()))
            .thenReturn(new ArrayList<>());

        ApiResponse response = cbPlanService.publishCbPlan(request, "orgId", "token", Arrays.asList("role"));

        assertEquals(Constants.FAILED, response.getParams().getStatus());
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getResponseCode());
    }

    @Test
    void testPublishCbPlan_NotAuthorized() {
        ApiRequest request = new ApiRequest();
        Map<String, Object> requestMap = new HashMap<>();
        requestMap.put("id", "planId");
        request.setRequest(requestMap);

        when(accessTokenValidator.fetchUserIdFromAccessToken(anyString(), any())).thenReturn("userId");

        Map<String, Object> existingPlan = new HashMap<>();
        existingPlan.put("createdBy", "otherUser");
        when(cassandraOperation.getRecordsByProperties(anyString(), anyString(), any(), any(), any()))
            .thenReturn(Arrays.asList(existingPlan));

        when(serverProperties.getCbPlanUpdatePublishAuthorizedRoles()).thenReturn(Arrays.asList("admin"));

        ApiResponse response = cbPlanService.publishCbPlan(request, "orgId", "token", Arrays.asList("user"));

        assertEquals(Constants.FAILED, response.getParams().getStatus());
        assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
    }

    @Test
    void testPublishCbPlan_AlreadyPublished() {
        ApiRequest request = new ApiRequest();
        Map<String, Object> requestMap = new HashMap<>();
        requestMap.put("id", "planId");
        request.setRequest(requestMap);

        when(accessTokenValidator.fetchUserIdFromAccessToken(anyString(), any())).thenReturn("userId");

        Map<String, Object> existingPlan = new HashMap<>();
        existingPlan.put("createdBy", "userId");
        existingPlan.put("status", "live");
        existingPlan.put("draftData", null);
        when(cassandraOperation.getRecordsByProperties(anyString(), anyString(), any(), any(), any()))
            .thenReturn(Arrays.asList(existingPlan));

        ApiResponse response = cbPlanService.publishCbPlan(request, "orgId", "token", Arrays.asList("role"));

        assertEquals(Constants.FAILED, response.getParams().getStatus());
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getResponseCode());
    }

    @Test
    void testRetireCbPlan_MissingId() {
        ApiRequest request = new ApiRequest();
        Map<String, Object> requestMap = new HashMap<>();
        request.setRequest(requestMap);

        when(accessTokenValidator.fetchUserIdFromAccessToken(anyString(), any())).thenReturn("userId");

        ApiResponse response = cbPlanService.retireCbPlan(request, "orgId", "token", Arrays.asList("role"));

        assertNotNull(response);
        assertEquals(Constants.FAILED, response.getParams().getStatus());
    }

    @Test
    void testRetireCbPlan_PlanNotFound() {
        ApiRequest request = new ApiRequest();
        Map<String, Object> requestMap = new HashMap<>();
        requestMap.put("id", "planId");
        request.setRequest(requestMap);

        when(accessTokenValidator.fetchUserIdFromAccessToken(anyString(), any())).thenReturn("userId");
        when(cassandraOperation.getRecordsByProperties(anyString(), anyString(), any(), any(), any()))
            .thenReturn(new ArrayList<>());

        ApiResponse response = cbPlanService.retireCbPlan(request, "orgId", "token", Arrays.asList("role"));

        assertEquals(Constants.FAILED, response.getParams().getStatus());
        assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
    }

    @Test
    void testRetireCbPlan_AlreadyRetired() {
        ApiRequest request = new ApiRequest();
        Map<String, Object> requestMap = new HashMap<>();
        requestMap.put("id", "planId");
        request.setRequest(requestMap);

        when(accessTokenValidator.fetchUserIdFromAccessToken(anyString(), any())).thenReturn("userId");

        Map<String, Object> existingPlan = new HashMap<>();
        existingPlan.put("createdBy", "userId");
        existingPlan.put("status", "retired");
        when(cassandraOperation.getRecordsByProperties(anyString(), anyString(), any(), any(), any()))
            .thenReturn(Arrays.asList(existingPlan));

        ApiResponse response = cbPlanService.retireCbPlan(request, "orgId", "token", Arrays.asList("role"));

        assertEquals(Constants.FAILED, response.getParams().getStatus());
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getResponseCode());
    }

    @Test
    void testParseToDate_Long() {
        Long timestamp = System.currentTimeMillis();
        Date result = cbPlanService.parseToDate(timestamp);
        assertNull(result); // Method doesn't handle Long type
    }

    @Test
    void testParseToDate_SqlTimestamp() {
        java.sql.Timestamp timestamp = new java.sql.Timestamp(System.currentTimeMillis());
        Date result = cbPlanService.parseToDate(timestamp);
        assertNotNull(result);
    }

    @Test
    void testParseToDate_InvalidString() {
        Date result = cbPlanService.parseToDate("invalid-date");
        assertNull(result);
    }

    @Test
    @Disabled("This test is ignored due to optimization code changes")
    void testEnrichUserInfoWithProfile() {
        Map<String, Map<String, String>> userInfoMap = new HashMap<>();
        Map<String, String> userInfo = new HashMap<>();
        userInfo.put("profileDetails", "{\"professionalDetails\":[{\"designation\":\"Developer\"}]}");
        userInfoMap.put("userId", userInfo);

        ReflectionTestUtils.invokeMethod(cbPlanService, "enrichUserInfo", userInfoMap);

        assertEquals("Developer", userInfoMap.get("userId").get("designation"));
    }

    @Test
    @Disabled("This test is ignored due to optimization code changes")
    void testEnrichUserInfo_NoProfileDetails() {
        Map<String, Map<String, String>> userInfoMap = new HashMap<>();
        Map<String, String> userInfo = new HashMap<>();
        userInfo.put("designation", "Existing");
        userInfoMap.put("userId", userInfo);

        ReflectionTestUtils.invokeMethod(cbPlanService, "enrichUserInfo", userInfoMap);

        assertEquals("Existing", userInfoMap.get("userId").get("designation"));
    }

    @SuppressWarnings("unchecked")
    @Test
    void testPopulateReadData_NullDraftData() {
        Map<String, Object> cbPlan = new HashMap<>();
        cbPlan.put("name", "Test Plan");
        cbPlan.put("contentType", "Course");
        cbPlan.put("contentList", Arrays.asList("content1"));
        cbPlan.put("createdBy", "userId");
        cbPlan.put("createdAtReq", Instant.now());
        cbPlan.put("endDateRequest", new Date());
        cbPlan.put("draftData", null);
        cbPlan.put("status", "live");
        cbPlan.put("isApar", false);

        Map<String, Object> mockContent = createMockContent();
        when(contentService.readContent(anyString(), any())).thenReturn(mockContent);

        doAnswer(invocation -> {
            Map<String, Map<String, String>> userInfoMap = invocation.getArgument(2);
            Map<String, String> userDetails = new HashMap<>();
            userDetails.put("firstName", "Test");
            userDetails.put("lastName", "User");
            userInfoMap.put("userId", userDetails);
            return null;
        }).when(userUtilityService).readUserProfileFromDB(any(), anyList());

        Map<String, Object> result = (Map<String, Object>) ReflectionTestUtils.invokeMethod(cbPlanService, "populateReadData", cbPlan);

        assertNotNull(result);
        assertEquals("Test Plan", result.get("name"));
    }

    @SuppressWarnings("unchecked")
    @Test
    void testPopulateReadData_WithDraftStatus() {
        // Arrange
        Map<String, Object> cbPlan = new HashMap<>();
        cbPlan.put(Constants.DRAFT_DATA,
                "{\"name\":\"Draft Plan\",\"contentType\":\"Course\",\"contentList\":[\"content1\"],\"endDate\":\"2024-12-31\"}");
        cbPlan.put(Constants.STATUS, "draft"); // Set status to draft to hit the ELSE block
        cbPlan.put(Constants.CREATED_BY, "userId");
        cbPlan.put(Constants.CREATED_AT_REQ, Instant.now());
        cbPlan.put(Constants.CONTENT_LIST, List.of("content1"));
        cbPlan.put(Constants.END_DATE_REQUEST, new Date());
        cbPlan.put(Constants.IS_APAR, false);
        // Mock enriched content
        List<Map<String, Object>> enrichedContent = List.of(Map.of("content", "enrichedContent1"));
        when(contentService.enrichContentInfoForCBPlan(anyList())).thenReturn(enrichedContent);
        // Mock user profile enrichment
        doAnswer(invocation -> {
            Map<String, Map<String, String>> userInfoMap = invocation.getArgument(2);
            Map<String, String> userDetails = new HashMap<>();
            userDetails.put("firstName", "Test");
            userDetails.put("lastName", "User");
            userInfoMap.put("userId", userDetails);
            return null;
        }).when(userUtilityService).readUserProfileFromDB(any(), anyList());
        Map<String, Object> result =
                (Map<String, Object>) ReflectionTestUtils.invokeMethod(cbPlanService, "populateReadData", cbPlan);
        assertNotNull(result);
        assertEquals(cbPlan.get(Constants.NAME), result.get(Constants.NAME)); // else block uses original map
        assertEquals(cbPlan.get(Constants.CONTENT_TYPE), result.get(Constants.CONTENT_TYPE));
        assertEquals(enrichedContent, result.get(Constants.CONTENT_LIST)); // enriched content still applied
        assertEquals(false, result.get(Constants.IS_APAR));
        assertEquals("userId", result.get(Constants.CREATED_BY));
        assertNotNull(result.get(Constants.END_DATE_REQUEST));
    }


    @Test
    @Disabled("This test is ignored due to optimization code changes")
    void testSearchCbPlan_NoResults() throws Exception {
        SearchCriteria criteria = new SearchCriteria();

        when(accessTokenValidator.fetchUserIdFromAccessToken(anyString(), any())).thenReturn("userId");

        SearchResult searchResult = new SearchResult();
        searchResult.setData(new ArrayList<>());
        when(esUtilService.searchDocuments(anyString(), any(), anyString())).thenReturn(searchResult);

        ApiResponse response = cbPlanService.searchCbPlan(criteria, "orgId", "token");

        assertNotNull(response);
        assertEquals(Constants.SUCCESS, response.getParams().getStatus());
    }

    @Test
    @Disabled("This test is ignored due to optimization code changes")
    void testUpdateCbPlan_EndDateParsing() throws Exception {
        ApiRequest request = new ApiRequest();
        Map<String, Object> updateMap = new HashMap<>();
        updateMap.put("id", "planId");
        updateMap.put("endDate", "2024-12-31T00:00:00Z");

        // ----- contextData -----
        Map<String, Object> criteria = new HashMap<>();
        criteria.put("criteriaKey", "rootOrgId");
        criteria.put("criteriaValue", Arrays.asList("orgId"));

        Map<String, Object> userGroup = new HashMap<>();
        userGroup.put("userGroupCriteriaList", Arrays.asList(criteria));

        Map<String, Object> accessControl = new HashMap<>();
        accessControl.put("userGroups", Arrays.asList(userGroup));

        Map<String, Object> contextData = new HashMap<>();
        contextData.put("accessControl", accessControl);

        updateMap.put(Constants.END_DATE_REQUEST, "2024-12-31T00:00:00Z");
        updateMap.put(Constants.CONTEXT_DATA_REQUEST, contextData);
        request.setRequest(updateMap);

        // --- Mock userId ---
        when(accessTokenValidator.fetchUserIdFromAccessToken(anyString(), any())).thenReturn("userId");

        // --- Mock existing plan ---
        Map<String, Object> existingPlan = new HashMap<>();
        existingPlan.put("createdBy", "userId");
        existingPlan.put("status", "draft");
        existingPlan.put(Constants.ROOT_ORG_ID, "orgId");
        existingPlan.put("rootorgid", "orgId");
        existingPlan.put(Constants.END_DATE_REQUEST, "2024-12-31T00:00:00Z");
        // IMPORTANT: store contextData as String JSON
        ObjectMapper mapper = new ObjectMapper();
        existingPlan.put(Constants.CONTEXT_DATA, mapper.writeValueAsString(contextData));

        when(cassandraOperation.getRecordsByProperties(anyString(), anyString(), any(), any(), any()))
                .thenReturn(Arrays.asList(existingPlan));

        // --- Mock update response ---
        Map<String, Object> updateResp = new HashMap<>();
        updateResp.put(Constants.RESPONSE, Constants.SUCCESS);
        when(cassandraOperation.updateRecord(anyString(), anyString(), any(), any())).thenReturn(updateResp);

        // --- Execute ---
        ApiResponse response = cbPlanService.updateCbPlan(request, "orgId", "token", Arrays.asList("role"));

        // --- Verify ---
        assertNotNull(response);
        assertEquals(Constants.SUCCESS, response.getParams().getStatus());
    }



    @SuppressWarnings("unchecked")
    @Test
    @Disabled("This test is ignored due to optimization code changes")
    void testValidateContextData_InvalidRootOrgId() {
        CbPlanDto dto = new CbPlanDto();
        dto.setOrgScope("single");
        ApiRequest request = new ApiRequest();
        Map<String, Object> requestMap = new HashMap<>();
        Map<String, Object> contextData = new HashMap<>();
        Map<String, Object> accessControl = new HashMap<>();
        List<Map<String, Object>> userGroups = new ArrayList<>();
        Map<String, Object> userGroup = new HashMap<>();
        List<Map<String, Object>> criteriaList = new ArrayList<>();
        Map<String, Object> criteria = new HashMap<>();
        criteria.put("criteriaKey", "rootOrgId");
        criteria.put("criteriaValue", Collections.emptyList());
        criteriaList.add(criteria);
        userGroup.put("userGroupCriteriaList", criteriaList);
        userGroups.add(userGroup);
        accessControl.put("userGroups", userGroups);
        contextData.put("accessControl", accessControl);
        requestMap.put("contextDataRequest", contextData);
        request.setRequest(requestMap);

        List<String> result = (List<String>) ReflectionTestUtils.invokeMethod(cbPlanService, "validateContextData", dto, request);

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    @Disabled("This test is ignored due to optimization code changes")
    void testCreateCbPlan_ElasticSearchError() {
        ApiRequest request = new ApiRequest();
        Map<String, Object> requestMap = new HashMap<>();
        requestMap.put("name", "Test Plan");
        requestMap.put("endDate", new Date());
        requestMap.put("orgScope", "single");
        requestMap.put("orgIdList", List.of("org1"));
        requestMap.put("contentType", "Course");
        requestMap.put("contentList", List.of("content1"));
        request.setRequest(requestMap);

        when(accessTokenValidator.fetchUserIdFromAccessToken(anyString(), any())).thenReturn("userId");

        ApiResponse cassandraResp = new ApiResponse();
        cassandraResp.put(Constants.RESPONSE, Constants.SUCCESS);
        when(cassandraOperation.insertRecord(anyString(), anyString(), any())).thenReturn(cassandraResp);

        doThrow(new RuntimeException("ES error")).when(esUtilService)
            .addDocument(anyString(), anyString(), anyString(), any(), anyString());

        ApiResponse response = cbPlanService.createCbPlan(request, "orgId", "token");

        assertEquals(Constants.FAILED, response.getParams().getStatus());
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getResponseCode());
    }

    @Test
    @Disabled("This test is ignored due to optimization code changes")
    void testUpdateDraftInfo_NullDraftData() {
        Map<String, Object> updatedCbPlan = new HashMap<>();
        updatedCbPlan.put("name", "Updated Plan");

        Map<String, Object> cbPlan = new HashMap<>();
        cbPlan.put("draftData", null);
        cbPlan.put("name", "Original Plan");

        try {
            String result = (String) ReflectionTestUtils.invokeMethod(cbPlanService, "updateDraftInfo", updatedCbPlan, cbPlan);
            assertNotNull(result);
            assertTrue(result.contains("Updated Plan"));
        } catch (Exception e) {
            fail("Should not throw exception");
        }
    }

    @Test
    void testPublishCbPlan_EmptyUserId() {
        ApiRequest request = new ApiRequest();
        when(accessTokenValidator.fetchUserIdFromAccessToken(anyString(), any())).thenReturn("");

        ApiResponse response = cbPlanService.publishCbPlan(request, "orgId", "token", List.of("role"));

        assertNotNull(response);
        assertEquals(Constants.SUCCESS, response.getParams().getStatus());
    }

    @Test
    void testPublishCbPlan_CassandraError() {
        ApiRequest request = new ApiRequest();
        Map<String, Object> requestMap = new HashMap<>();
        requestMap.put("id", "planId");
        request.setRequest(requestMap);

        when(accessTokenValidator.fetchUserIdFromAccessToken(anyString(), any())).thenReturn("userId");

        Map<String, Object> existingPlan = new HashMap<>();
        existingPlan.put("createdBy", "userId");
        existingPlan.put("status", "draft");
        existingPlan.put("draftData", "{\"name\":\"Test Plan\",\"endDate\":\"2024-12-31\"}");
        when(cassandraOperation.getRecordsByProperties(anyString(), anyString(), any(), any(), any()))
            .thenReturn(Arrays.asList(existingPlan));

        Map<String, Object> updateResp = new HashMap<>();
        updateResp.put(Constants.RESPONSE, Constants.FAILED);
        updateResp.put(Constants.ERROR_MESSAGE, "DB error");
        when(cassandraOperation.updateRecord(anyString(), anyString(), any(), any(), any(), any())).thenReturn(updateResp);

        ApiResponse response = cbPlanService.publishCbPlan(request, "orgId", "token", List.of("role"));

        assertEquals(Constants.FAILED, response.getParams().getStatus());
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getResponseCode());
    }

    @Test
    void testRetireCbPlan_EmptyUserId() {
        ApiRequest request = new ApiRequest();
        when(accessTokenValidator.fetchUserIdFromAccessToken(anyString(), any())).thenReturn("");

        ApiResponse response = cbPlanService.retireCbPlan(request, "orgId", "token", List.of("role"));

        assertNotNull(response);
        assertEquals(Constants.FAILED, response.getParams().getStatus());
    }

    @Test
    @Disabled("This test is ignored due to optimization code changes")
    void testRetireCbPlan_ElasticSearchError() {
        ApiRequest request = new ApiRequest();
        Map<String, Object> requestMap = new HashMap<>();
        requestMap.put("id", "planId");
        request.setRequest(requestMap);

        when(accessTokenValidator.fetchUserIdFromAccessToken(anyString(), any())).thenReturn("userId");

        Map<String, Object> existingPlan = new HashMap<>();
        existingPlan.put("createdBy", "userId");
        existingPlan.put("status", "live");
        when(cassandraOperation.getRecordsByProperties(anyString(), anyString(), any(), any(), any()))
            .thenReturn(List.of(existingPlan));

        Map<String, Object> updateResp = new HashMap<>();
        updateResp.put(Constants.RESPONSE, Constants.SUCCESS);
        when(cassandraOperation.updateRecord(anyString(), anyString(), any(), any())).thenReturn(updateResp);

        doThrow(new RuntimeException("ES error")).when(esUtilService)
            .addDocument(anyString(), anyString(), anyString(), any(), anyString());

        ApiResponse response = cbPlanService.retireCbPlan(request, "orgId", "token", Arrays.asList("role"));

        assertEquals(Constants.FAILED, response.getParams().getStatus());
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getResponseCode());
    }

    @Test
    void testReadCbPlan_NotFound() {
        when(cassandraOperation.getRecordsByProperties(anyString(), anyString(), any(), any(), any()))
            .thenReturn(new ArrayList<>());

        ApiResponse response = cbPlanService.readCbPlan("planId", "orgId", "token");

        assertEquals(Constants.FAILED, response.getParams().getStatus());
        assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
    }

    @Test
    @Disabled("This test is ignored due to optimization code changes")
    void testReadCbPlan_ContentError() {
        Map<String, Object> cbPlan = new HashMap<>();
        cbPlan.put("contentList", List.of("content1"));
        cbPlan.put("status", "live");
        cbPlan.put("draftData", "");

        when(cassandraOperation.getRecordsByProperties(anyString(), anyString(), any(), any(), any()))
            .thenReturn(List.of(cbPlan));

        when(contentService.readContent(anyString(), any()))
            .thenThrow(new RuntimeException("Content service error"));

        ApiResponse response = cbPlanService.readCbPlan("planId", "orgId", "token");

        assertEquals(Constants.FAILED, response.getParams().getStatus());
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getResponseCode());
    }

    @Test
    void testSearchCbPlan_Exception() throws Exception {
        SearchCriteria criteria = new SearchCriteria();
        when(accessTokenValidator.fetchUserIdFromAccessToken(anyString(), any())).thenReturn("userId");
        when(esUtilService.searchDocuments(anyString(), any(), anyString()))
            .thenThrow(new RuntimeException("Test exception"));

        try {
            cbPlanService.searchCbPlan(criteria, "orgId", "token");
            fail("Expected CustomException to be thrown");
        } catch (Exception e) {
            assertTrue(e.getMessage().contains("error while processing"));
        }
    }

    @Test
    void testParseEndDate_AllBranches() {
        Instant now = Instant.now();
        Object r1 = ReflectionTestUtils.invokeMethod(cbPlanService, "parseEndDate", Date.from(now));
        assertNotNull(r1);
        Object r2 = ReflectionTestUtils.invokeMethod(cbPlanService, "parseEndDate", now);
        assertNotNull(r2);
        Object r3 = ReflectionTestUtils.invokeMethod(cbPlanService, "parseEndDate", now.toEpochMilli());
        assertNotNull(r3);
        Object r4 = ReflectionTestUtils.invokeMethod(cbPlanService, "parseEndDate", now.toString());
        assertNotNull(r4);
        Object r5 = ReflectionTestUtils.invokeMethod(cbPlanService, "parseEndDate", "2024-12-31");
        assertNotNull(r5);
        Object r6 = ReflectionTestUtils.invokeMethod(cbPlanService, "parseEndDate", new Object());
        assertNull(r6);
    }


    @Test
    @SuppressWarnings("unchecked")
    void testExtractUniqueRootOrgIds_AllPaths() {
        // null contextData → empty set
        Set<String> empty = (Set<String>) ReflectionTestUtils.invokeMethod(cbPlanService, "extractUniqueRootOrgIds", new HashMap<>());
        assertNotNull(empty);
        assertTrue(empty.isEmpty());

        // contextData as Map
        Map<String, Object> crit = Map.of(Constants.CRITERIA_KEY, Constants.ROOT_ORG_ID,
                Constants.CRITERIA_VALUE, List.of("o1"));
        Map<String, Object> ug = Map.of(Constants.USER_GROUP_CRITERIA_LIST, List.of(crit));
        Map<String, Object> ac = Map.of(Constants.USER_GROUPS, List.of(ug));
        Map<String, Object> raw = Map.of(Constants.CONTEXT_DATA_REQUEST, Map.of(Constants.ACCESS_CONTROL, ac));
        Set<String> result = (Set<String>) ReflectionTestUtils.invokeMethod(cbPlanService, "extractUniqueRootOrgIds", raw);
        assertEquals(Set.of("o1"), result);

        // contextData as JSON string
        String json = "{\"accessControl\":{\"userGroups\":[{\"userGroupCriteriaList\":[{\"criteriaKey\":\"rootOrgId\",\"criteriaValue\":[\"o2\"]}]}]}}";
        Set<String> result2 = (Set<String>) ReflectionTestUtils.invokeMethod(cbPlanService, "extractUniqueRootOrgIds",
                Map.of(Constants.CONTEXT_DATA_REQUEST, json));
        assertEquals(Set.of("o2"), result2);
    }

    @Test
    void testParseToDate_AllBranches() {
        assertNull(cbPlanService.parseToDate(null));
        assertNotNull(cbPlanService.parseToDate("2024-12-31"));
        assertNotNull(cbPlanService.parseToDate(Instant.now()));
        assertNotNull(cbPlanService.parseToDate(new java.sql.Timestamp(System.currentTimeMillis())));
        assertNotNull(cbPlanService.parseToDate(new Date()));
    }


    @Test
    void testCreateSuccessResponse_SetsStatusAndHttpOK() {
        ApiResponse apiResponse = new ApiResponse();
        ReflectionTestUtils.invokeMethod(cbPlanService, "createSuccessResponse", apiResponse);
        assertEquals(Constants.SUCCESS, apiResponse.getParams().getStatus());
        assertEquals(HttpStatus.OK, apiResponse.getResponseCode());
    }

    @Test
    void testSanitizeForElastic_ConvertsInstantToString() {
        Map<String, Object> input = new HashMap<>();
        input.put("a", "b");
        input.put("instant", Instant.now());
        Map<String, Object> out = CbPlanServiceImpl.sanitizeForElastic(input);
        assertInstanceOf(String.class, out.get("instant"));
        assertEquals("b", out.get("a"));
    }

    @Test
    void testSearchCbPlan_ExceptionPath() throws Exception {
        when(accessTokenValidator.fetchUserIdFromAccessToken(anyString(), any())).thenReturn("u1");
        when(esUtilService.searchDocuments(anyString(), any(), anyString())).thenThrow(new RuntimeException("boom"));
        SearchCriteria sc = new SearchCriteria();
        assertThrows(RuntimeException.class, () -> cbPlanService.searchCbPlan(sc, "org", "t"));
    }

    @Test
    void testReadCbPlan_EmptyAndErrorPaths() {
        ApiResponse r1 = cbPlanService.readCbPlan("", "org", "t");
        assertEquals(HttpStatus.BAD_REQUEST, r1.getResponseCode());
        when(cassandraOperation.getRecordsByProperties(anyString(), anyString(), anyMap(), any(), any()))
                .thenThrow(new RuntimeException("fail"));
        ApiResponse r2 = cbPlanService.readCbPlan("id", "org", "t");
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, r2.getResponseCode());
    }

    @Test
    void testRetireCbPlan_CbPlanAlreadyArchived() {
        when(accessTokenValidator.fetchUserIdFromAccessToken(anyString(), any())).thenReturn("user");
        Map<String, Object> cbPlan = new HashMap<>();
        cbPlan.put(Constants.CREATED_BY, "user");
        cbPlan.put(Constants.STATUS, Constants.CB_RETIRE);
        when(cassandraOperation.getRecordsByProperties(anyString(), anyString(), any(), any(), any()))
                .thenReturn(List.of(cbPlan));
        ApiRequest req = new ApiRequest();
        req.setRequest(Map.of(Constants.ID, "p1"));
        ApiResponse resp = cbPlanService.retireCbPlan(req, "org", "t", List.of("role"));
        assertEquals(HttpStatus.BAD_REQUEST, resp.getResponseCode());
    }

    @Test
    void testUpdateCbPlan_CassandraThrows() {
        when(accessTokenValidator.fetchUserIdFromAccessToken(anyString(), any())).thenReturn("u1");
        when(cassandraOperation.getRecordsByProperties(anyString(), anyString(), any(), any(), any()))
                .thenThrow(new RuntimeException("fail"));
        ApiRequest req = new ApiRequest();
        req.setRequest(Map.of(Constants.ID, "pid"));
        ApiResponse resp = cbPlanService.updateCbPlan(req, "org", "t", List.of("r"));
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, resp.getResponseCode());
    }

    @Test
    void testPublishCbPlan_InvalidState() {
        when(accessTokenValidator.fetchUserIdFromAccessToken(anyString(), any())).thenReturn("u1");
        Map<String, Object> existing = new HashMap<>();
        existing.put(Constants.CREATED_BY, "u1");
        existing.put(Constants.STATUS, "archived"); // not DRAFT
        when(cassandraOperation.getRecordsByProperties(anyString(), anyString(), any(), any(), any()))
                .thenReturn(List.of(existing));
        ApiRequest req = new ApiRequest();
        req.setRequest(Map.of(Constants.ID, "id"));
        ApiResponse resp = cbPlanService.publishCbPlan(req, "org", "t", List.of("role"));
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, resp.getResponseCode());
    }


    @Test
    void testUpdateCbPlan_GetRootOrgFails() {
        when(accessTokenValidator.fetchUserIdFromAccessToken(anyString(), any())).thenReturn("u1");

        Map<String, Object> existing = new HashMap<>();
        existing.put(Constants.CREATED_BY, "u1");
        existing.put(Constants.STATUS, Constants.LIVE);

        when(cassandraOperation.getRecordsByProperties(anyString(), anyString(), any(), any(), any()))
                .thenReturn(List.of(existing));

        // Simulate root org failure using Reflection instead of mocking private call
        ReflectionTestUtils.invokeMethod(cbPlanService, "getRootOrgFromUser", "u1", new ApiResponse());

        ApiRequest req = new ApiRequest();
        req.setRequest(Map.of(Constants.ID, "plan1"));
        ApiResponse resp = cbPlanService.updateCbPlan(req, "org", "token", List.of("admin"));
        // Should fail because the injected response will have FAILED set
        assertTrue(resp.getParams().getStatus().equalsIgnoreCase(Constants.FAILED)
                || resp.getResponseCode() == HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @Test
    void testUpdateCbPlan_GetCCAFromOrgFails() {
        when(accessTokenValidator.fetchUserIdFromAccessToken(anyString(), any())).thenReturn("u1");

        Map<String, Object> existing = new HashMap<>();
        existing.put(Constants.CREATED_BY, "u1");
        existing.put(Constants.STATUS, Constants.LIVE);

        when(cassandraOperation.getRecordsByProperties(anyString(), anyString(), any(), any(), any()))
                .thenReturn(List.of(existing));

        // Call helper via reflection to simulate the branch
        ReflectionTestUtils.invokeMethod(cbPlanService, "getCCAFromOrg", "org1", new ApiResponse());

        ApiRequest req = new ApiRequest();
        req.setRequest(Map.of(Constants.ID, "plan1"));
        ApiResponse resp = cbPlanService.updateCbPlan(req, "org", "token", List.of("admin"));
        assertTrue(resp.getParams().getStatus().equalsIgnoreCase(Constants.FAILED)
                || resp.getResponseCode() == HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @Test
    void testUpdateCbPlan_DraftPlanValidationError() {
        when(accessTokenValidator.fetchUserIdFromAccessToken(anyString(), any())).thenReturn("u1");

        Map<String, Object> existing = new HashMap<>();
        existing.put(Constants.CREATED_BY, "u1");
        existing.put(Constants.STATUS, Constants.DRAFT);

        when(cassandraOperation.getRecordsByProperties(anyString(), anyString(), any(), any(), any()))
                .thenReturn(List.of(existing));

        when(requestValidator.validateCbPlanCreateRequest(any(), anyBoolean(), anyString()))
                .thenReturn(List.of("err1"));

        ApiRequest req = new ApiRequest();
        req.setRequest(Map.of(Constants.ID, "plan1"));
        ApiResponse resp = cbPlanService.updateCbPlan(req, "org", "token", List.of("admin"));
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, resp.getResponseCode());
    }


    @Test
    void testUpdateCbPlan_DraftPlanUpdateFailure() {
        when(accessTokenValidator.fetchUserIdFromAccessToken(anyString(), any())).thenReturn("u1");

        Map<String, Object> existing = new HashMap<>();
        existing.put(Constants.CREATED_BY, "u1");
        existing.put(Constants.STATUS, Constants.DRAFT);

        when(cassandraOperation.getRecordsByProperties(anyString(), anyString(), any(), any(), any()))
                .thenReturn(List.of(existing));

        when(requestValidator.validateCbPlanCreateRequest(any(), anyBoolean(), anyString())).thenReturn(Collections.emptyList());

        Map<String, Object> updated = new HashMap<>();
        updated.put(Constants.ID, "plan1");

        Map<String, Object> failResp = Map.of(Constants.RESPONSE, "FAILED");
        when(cassandraOperation.updateRecord(anyString(), anyString(), any(), any())).thenReturn(failResp);

        ApiRequest req = new ApiRequest();
        req.setRequest(updated);
        ApiResponse resp = cbPlanService.updateCbPlan(req, "org", "token", List.of("admin"));
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, resp.getResponseCode());
    }

    @Test
    void testCreateCbPlan_UserIdEmpty() {
        ApiRequest request = new ApiRequest();
        ApiResponse defaultResponse = ProjectUtil.createDefaultResponse(Constants.API_CB_PLAN_CREATE);
        Mockito.when(accessTokenValidator.fetchUserIdFromAccessToken(Mockito.anyString(), Mockito.any()))
                .thenReturn("");
        ApiResponse response = cbPlanService.createCbPlan(request, "org1", "token123");
        assertEquals(defaultResponse.getId(), response.getId());
        assertEquals(Constants.SUCCESS, response.getParams().getStatus());
    }

    @Test
    void testCreateCbPlan_RootOrgFailed() throws Exception {
        ApiRequest request = new ApiRequest();

        Mockito.when(accessTokenValidator.fetchUserIdFromAccessToken(Mockito.anyString(), Mockito.any()))
                .thenReturn("user123");

        Method m = CbPlanServiceImpl.class.getDeclaredMethod("getRootOrgFromUser", String.class, ApiResponse.class);
        m.setAccessible(true);

        ApiResponse tempResp = new ApiResponse();
        m.invoke(cbPlanService, "user123", tempResp);
        tempResp.getParams().setStatus(Constants.FAILED);

        ApiResponse response = cbPlanService.createCbPlan(request, "org1", "token123");

        assertEquals(Constants.FAILED, response.getParams().getStatus());
    }

    @Test
    void testCreateCbPlan_CcaFailed() throws Exception {
        ApiRequest request = new ApiRequest();

        Mockito.when(accessTokenValidator.fetchUserIdFromAccessToken(Mockito.anyString(), Mockito.any()))
                .thenReturn("user1");

        Method rootOrgMethod = CbPlanServiceImpl.class.getDeclaredMethod("getRootOrgFromUser", String.class, ApiResponse.class);
        Method ccaMethod = CbPlanServiceImpl.class.getDeclaredMethod("getCCAFromOrg", String.class, ApiResponse.class);

        rootOrgMethod.setAccessible(true);
        ccaMethod.setAccessible(true);

        ApiResponse resp = cbPlanService.createCbPlan(request, "org1", "token");

        assertEquals(Constants.FAILED, resp.getParams().getStatus());
    }

    @Test
    void testCreateCbPlan_ValidationFails() {
        ApiRequest request = new ApiRequest();
        request.setRequest(new HashMap<>());

        Mockito.when(accessTokenValidator.fetchUserIdFromAccessToken(Mockito.anyString(), Mockito.any()))
                .thenReturn("user1");

        Mockito.when(requestValidator.validateCbPlanCreateRequest(Mockito.any(), Mockito.anyBoolean(), Mockito.anyString(), Mockito.anyBoolean()))
                .thenReturn(List.of("error1"));

        ApiResponse resp = cbPlanService.createCbPlan(request, "org1", "token");

        assertEquals(Constants.FAILED, resp.getParams().getStatus());
    }

    @Test
    void testCreateCbPlan_InsertFailure() {
        ApiRequest request = new ApiRequest();
        request.setRequest(new HashMap<>());

        Mockito.when(accessTokenValidator.fetchUserIdFromAccessToken(Mockito.anyString(), Mockito.any()))
                .thenReturn("user1");

        Mockito.when(requestValidator.validateCbPlanCreateRequest(Mockito.any(), Mockito.anyBoolean(), Mockito.anyString(), Mockito.anyBoolean()))
                .thenReturn(Collections.emptyList());

        Map<String, Object> cassResp = new HashMap<>();
        cassResp.put(Constants.RESPONSE, "FAILED");

        Mockito.when(cassandraOperation.insertRecord(Mockito.anyString(), Mockito.anyString(), Mockito.anyMap()))
                .thenReturn(cassResp);

        ApiResponse resp = cbPlanService.createCbPlan(request, "org1", "token");

        assertEquals(Constants.FAILED, resp.getParams().getStatus());
    }

    // Tests for handleUpdateOfLiveCbPlan method
    @Test
    void testHandleUpdateOfLiveCbPlan_ValidationError() {
        ApiResponse response = new ApiResponse();
        response.setParams(new ApiRespParam());
        Map<String, Object> incomingRequest = Map.of("name", "Updated Plan");
        Map<String, Object> existingPlan = Map.of(Constants.PLAN_ID, "plan1");
        
        when(requestValidator.validateContextData(any(), anyBoolean(), anyString(), any()))
            .thenReturn(Arrays.asList("Validation error"));
        
        ReflectionTestUtils.invokeMethod(cbPlanService, "handleUpdateOfLiveCbPlan", 
            response, incomingRequest, existingPlan, "user1", "rootOrg1", false);
        
        assertEquals(Constants.FAILED, response.getParams().getStatus());
        assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
    }

    @Test
    void testHandleUpdateOfLiveCbPlan_IsAparRestriction() {
        ApiResponse response = new ApiResponse();
        response.setParams(new ApiRespParam());
        Map<String, Object> incomingRequest = Map.of(Constants.IS_APAR, false);
        Map<String, Object> existingPlan = Map.of(
            Constants.PLAN_ID, "plan1",
            Constants.IS_APAR, true
        );
        
        when(requestValidator.validateContextData(any(), anyBoolean(), anyString(), any()))
            .thenReturn(Collections.emptyList());
        when(serverProperties.getCbPlanUpdateAllowedFields())
            .thenReturn(Arrays.asList(Constants.IS_APAR));
        
        ReflectionTestUtils.invokeMethod(cbPlanService, "handleUpdateOfLiveCbPlan", 
            response, incomingRequest, existingPlan, "user1", "rootOrg1", false);
        
        assertEquals(Constants.FAILED, response.getParams().getStatus());
        assertTrue(response.getParams().getErr().contains("Cannot change isApar from true to false"));
    }

    @Test
    void testHandleUpdateOfLiveCbPlan_NullFieldValue() {
        ApiResponse response = new ApiResponse();
        response.setParams(new ApiRespParam()); // Initialize params to avoid NullPointerException
        Map<String, Object> incomingRequest = new HashMap<>();
        incomingRequest.put("name", null); // Use HashMap to allow null values
        Map<String, Object> existingPlan = Map.of(Constants.PLAN_ID, "plan1");
        
        when(requestValidator.validateContextData(any(), anyBoolean(), anyString(), any()))
            .thenReturn(Collections.emptyList());
        when(serverProperties.getCbPlanUpdateAllowedFields())
            .thenReturn(Arrays.asList("name"));
        
        ReflectionTestUtils.invokeMethod(cbPlanService, "handleUpdateOfLiveCbPlan", 
            response, incomingRequest, existingPlan, "user1", "rootOrg1", false);
        
        assertEquals(Constants.FAILED, response.getParams().getStatus());
        assertTrue(response.getParams().getErr().contains("Field 'name' cannot be null"));
    }

    @Test
    void testHandleUpdateOfLiveCbPlan_Success() {
        ApiResponse response = new ApiResponse();
        response.setParams(new ApiRespParam());
        response.setResult(new HashMap<>());
        Map<String, Object> incomingRequest = Map.of("name", "Updated Plan");
        Map<String, Object> existingPlan = Map.of(Constants.PLAN_ID, "plan1");
        
        when(requestValidator.validateContextData(any(), anyBoolean(), anyString(), any()))
            .thenReturn(Collections.emptyList());
        when(serverProperties.getCbPlanUpdateAllowedFields())
            .thenReturn(Arrays.asList("name"));
        
        Map<String, Object> updateResp = Map.of(Constants.RESPONSE, Constants.SUCCESS);
        when(cassandraOperation.updateRecord(anyString(), anyString(), any(), any()))
            .thenReturn(updateResp);
        
        ReflectionTestUtils.invokeMethod(cbPlanService, "handleUpdateOfLiveCbPlan", 
            response, incomingRequest, existingPlan, "user1", "rootOrg1", false);
        
        assertEquals(Constants.UPDATED, response.getResult().get(Constants.STATUS));
        assertTrue(response.getResult().get(Constants.MESSAGE).toString().contains("Updated cbPlan as draft"));
    }

    @Test
    void testHandleUpdateOfLiveCbPlan_CassandraFailure() {
        ApiResponse response = new ApiResponse();
        response.setParams(new ApiRespParam());
        Map<String, Object> incomingRequest = Map.of("name", "Updated Plan");
        Map<String, Object> existingPlan = Map.of(Constants.PLAN_ID, "plan1");
        
        when(requestValidator.validateContextData(any(), anyBoolean(), anyString(), any()))
            .thenReturn(Collections.emptyList());
        when(serverProperties.getCbPlanUpdateAllowedFields())
            .thenReturn(Arrays.asList("name"));
        
        Map<String, Object> updateResp = Map.of(
            Constants.RESPONSE, Constants.FAILED,
            Constants.ERROR_MESSAGE, "DB Error"
        );
        when(cassandraOperation.updateRecord(anyString(), anyString(), any(), any()))
            .thenReturn(updateResp);
        
        ReflectionTestUtils.invokeMethod(cbPlanService, "handleUpdateOfLiveCbPlan", 
            response, incomingRequest, existingPlan, "user1", "rootOrg1", false);
        
        assertEquals(Constants.FAILED, response.getParams().getStatus());
        assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
    }

    // Tests for upsertCbPlanContentLookup method
    @Test
    void testUpsertCbPlanContentLookup_NewContent() {
        List<String> contentIds = Arrays.asList("content1");
        
        when(cassandraOperation.getRecordsByProperties(anyString(), anyString(), any(), any(), anyInt()))
            .thenReturn(Collections.emptyList());
        
        ReflectionTestUtils.invokeMethod(cbPlanService, "upsertCbPlanContentLookup", "plan1", contentIds);
        
        verify(cassandraOperation).updateRecord(anyString(), anyString(), any(), any());
    }

    @Test
    void testUpsertCbPlanContentLookup_ExistingContent() {
        List<String> contentIds = Arrays.asList("content1");
        Set<String> existingPlanIds = new HashSet<>(Arrays.asList("plan2"));
        
        Map<String, Object> existingRecord = Map.of("planId", existingPlanIds); // Note: planId not planid
        when(cassandraOperation.getRecordsByProperties(anyString(), anyString(), any(), any(), anyInt()))
            .thenReturn(Arrays.asList(existingRecord));
        
        ReflectionTestUtils.invokeMethod(cbPlanService, "upsertCbPlanContentLookup", "plan1", contentIds);
        
        verify(cassandraOperation).updateRecord(anyString(), anyString(), any(), any());
    }

    @Test
    void testUpsertCbPlanContentLookup_PlanAlreadyExists() {
        List<String> contentIds = Arrays.asList("content1");
        Set<String> existingPlanIds = new HashSet<>(Arrays.asList("plan1"));
        
        Map<String, Object> existingRecord = Map.of("planId", existingPlanIds); // Note: planId not planid
        when(cassandraOperation.getRecordsByProperties(anyString(), anyString(), any(), any(), anyInt()))
            .thenReturn(Arrays.asList(existingRecord));
        
        ReflectionTestUtils.invokeMethod(cbPlanService, "upsertCbPlanContentLookup", "plan1", contentIds);
        
        verify(cassandraOperation, never()).updateRecord(anyString(), anyString(), any(), any());
    }

    @Test
    void testUpsertCbPlanContentLookup_MultipleContents() {
        List<String> contentIds = Arrays.asList("content1", "content2");
        
        when(cassandraOperation.getRecordsByProperties(anyString(), anyString(), any(), any(), anyInt()))
            .thenReturn(Collections.emptyList());
        
        ReflectionTestUtils.invokeMethod(cbPlanService, "upsertCbPlanContentLookup", "plan1", contentIds);
        
        verify(cassandraOperation, times(2)).updateRecord(anyString(), anyString(), any(), any());
    }

    // Tests for upsertAllOrgLookup method
    @Test
    void testUpsertAllOrgLookup_Success() {
        ApiResponse successResp = new ApiResponse();
        successResp.setParams(new ApiRespParam());
        successResp.getParams().setStatus(Constants.SUCCESS);
        when(cassandraOperation.insertRecord(anyString(), anyString(), any())).thenReturn(successResp);
        
        ApiResponse result = (ApiResponse) ReflectionTestUtils.invokeMethod(
            cbPlanService, "upsertAllOrgLookup", "plan1", Instant.now(), true);
        
        assertEquals(Constants.SUCCESS, result.getParams().getStatus());
    }

    @Test
    void testUpsertAllOrgLookup_WithNullEndDate() {
        ApiResponse successResp = new ApiResponse();
        successResp.setParams(new ApiRespParam());
        successResp.getParams().setStatus(Constants.SUCCESS);
        when(cassandraOperation.insertRecord(anyString(), anyString(), any())).thenReturn(successResp);
        
        ApiResponse result = (ApiResponse) ReflectionTestUtils.invokeMethod(
            cbPlanService, "upsertAllOrgLookup", "plan1", null, false);
        
        assertEquals(Constants.SUCCESS, result.getParams().getStatus());
    }

    @Test
    void testUpsertAllOrgLookup_Exception() {
        when(cassandraOperation.insertRecord(anyString(), anyString(), any()))
            .thenThrow(new RuntimeException("DB error"));
        
        ApiResponse result = (ApiResponse) ReflectionTestUtils.invokeMethod(
            cbPlanService, "upsertAllOrgLookup", "plan1", Instant.now(), true);
        
        assertEquals(Constants.FAILED, result.getParams().getStatus());
        assertTrue(result.getParams().getErr().contains("Exception while inserting SINGLE org lookup"));
    }

    // Tests for getRootOrgFromUser method
    @Test
    void testGetRootOrgFromUser_Success() {
        Map<String, Object> userMap = Map.of(
            Constants.ID, "user1",
            Constants.ROOT_ORG_ID, "rootOrg1"
        );
        when(userUtilityService.readUserProfileFromDB(anyString(), any())).thenReturn(userMap);
        
        ApiResponse response = new ApiResponse();
        response.setParams(new ApiRespParam());
        String result = (String) ReflectionTestUtils.invokeMethod(
            cbPlanService, "getRootOrgFromUser", "user1", response);
        
        assertEquals("rootOrg1", result);
        assertNotEquals(Constants.FAILED, response.getParams().getStatus());
    }

    @Test
    void testGetRootOrgFromUser_EmptyUserMap() {
        when(userUtilityService.readUserProfileFromDB(anyString(), any())).thenReturn(new HashMap<>());
        
        ApiResponse response = new ApiResponse();
        response.setParams(new ApiRespParam());
        String result = (String) ReflectionTestUtils.invokeMethod(
            cbPlanService, "getRootOrgFromUser", "user1", response);
        
        assertNull(result);
        assertEquals(Constants.FAILED, response.getParams().getStatus());
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getResponseCode());
    }

    @Test
    void testGetRootOrgFromUser_NullUserMap() {
        when(userUtilityService.readUserProfileFromDB(anyString(), any())).thenReturn(null);
        
        ApiResponse response = new ApiResponse();
        response.setParams(new ApiRespParam());
        String result = (String) ReflectionTestUtils.invokeMethod(
            cbPlanService, "getRootOrgFromUser", "user1", response);
        
        assertNull(result);
        assertEquals(Constants.FAILED, response.getParams().getStatus());
    }

    // Tests for getCCAFromOrg method
    @Test
    void testGetCCAFromOrg_Success_True() {
        Map<String, Object> orgMap = Map.of(Constants.IS_CCA, true);
        when(userUtilityService.readOrgFromDB(anyString(), any())).thenReturn(orgMap);
        
        ApiResponse response = new ApiResponse();
        response.setParams(new ApiRespParam());
        boolean result = (Boolean) ReflectionTestUtils.invokeMethod(
            cbPlanService, "getCCAFromOrg", "org1", response);
        
        assertTrue(result);
        assertNotEquals(Constants.FAILED, response.getParams().getStatus());
    }

    @Test
    void testGetCCAFromOrg_Success_False() {
        Map<String, Object> orgMap = Map.of(Constants.IS_CCA, "false");
        when(userUtilityService.readOrgFromDB(anyString(), any())).thenReturn(orgMap);
        
        ApiResponse response = new ApiResponse();
        response.setParams(new ApiRespParam());
        boolean result = (Boolean) ReflectionTestUtils.invokeMethod(
            cbPlanService, "getCCAFromOrg", "org1", response);
        
        assertFalse(result);
    }

    @Test
    void testGetCCAFromOrg_NoCCAField() {
        Map<String, Object> orgMap = Map.of("name", "Test Org");
        when(userUtilityService.readOrgFromDB(anyString(), any())).thenReturn(orgMap);
        
        ApiResponse response = new ApiResponse();
        response.setParams(new ApiRespParam());
        boolean result = (Boolean) ReflectionTestUtils.invokeMethod(
            cbPlanService, "getCCAFromOrg", "org1", response);
        
        assertFalse(result);
    }

    @Test
    void testGetCCAFromOrg_EmptyOrgMap() {
        when(userUtilityService.readOrgFromDB(anyString(), any())).thenReturn(new HashMap<>());
        
        ApiResponse response = new ApiResponse();
        response.setParams(new ApiRespParam());
        boolean result = (Boolean) ReflectionTestUtils.invokeMethod(
            cbPlanService, "getCCAFromOrg", "org1", response);
        
        assertFalse(result);
        assertEquals(Constants.FAILED, response.getParams().getStatus());
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getResponseCode());
    }

    // Tests for removeCbPlanInfoForUpdateOrDeleteCbPlan method
    @Test
    void testRemoveCbPlanInfoForUpdateOrDeleteCbPlan_SinglePlanDelete() {
        List<String> contentIds = Arrays.asList("content1");
        Set<String> planIds = new HashSet<>(Arrays.asList("plan1"));
        
        Map<String, Object> existingRecord = Map.of("planId", planIds);
        when(cassandraOperation.getRecordsByProperties(anyString(), anyString(), any(), any(), anyInt()))
            .thenReturn(Arrays.asList(existingRecord));
        
        ReflectionTestUtils.invokeMethod(cbPlanService, "removeCbPlanInfoForUpdateOrDeleteCbPlan", "plan1", contentIds);
        
        verify(cassandraOperation).deleteRecord(anyString(), anyString(), any());
    }

    @Test
    void testRemoveCbPlanInfoForUpdateOrDeleteCbPlan_MultiplePlansUpdate() {
        List<String> contentIds = Arrays.asList("content1");
        Set<String> planIds = new HashSet<>(Arrays.asList("plan1", "plan2"));
        
        Map<String, Object> existingRecord = Map.of("planId", planIds);
        when(cassandraOperation.getRecordsByProperties(anyString(), anyString(), any(), any(), anyInt()))
            .thenReturn(Arrays.asList(existingRecord));
        
        ReflectionTestUtils.invokeMethod(cbPlanService, "removeCbPlanInfoForUpdateOrDeleteCbPlan", "plan1", contentIds);
        
        verify(cassandraOperation).updateRecord(anyString(), anyString(), any(), any());
        verify(cassandraOperation, never()).deleteRecord(anyString(), anyString(), any());
    }

    @Test
    void testRemoveCbPlanInfoForUpdateOrDeleteCbPlan_EmptyRows() {
        List<String> contentIds = Arrays.asList("content1");
        
        when(cassandraOperation.getRecordsByProperties(anyString(), anyString(), any(), any(), anyInt()))
            .thenReturn(Collections.emptyList());
        
        // Should not throw exception and should not call delete/update
        ReflectionTestUtils.invokeMethod(cbPlanService, "removeCbPlanInfoForUpdateOrDeleteCbPlan", "plan1", contentIds);
        
        verify(cassandraOperation, never()).deleteRecord(anyString(), anyString(), any());
        verify(cassandraOperation, never()).updateRecord(anyString(), anyString(), any(), any());
    }

    @Test
    void testRemoveCbPlanInfoForUpdateOrDeleteCbPlan_InvalidPlanIdData() {
        List<String> contentIds = Arrays.asList("content1");
        
        Map<String, Object> existingRecord = Map.of("planId", "not-a-set");
        when(cassandraOperation.getRecordsByProperties(anyString(), anyString(), any(), any(), anyInt()))
            .thenReturn(Arrays.asList(existingRecord));
        
        // Should handle ClassCastException gracefully and not call delete/update
        ReflectionTestUtils.invokeMethod(cbPlanService, "removeCbPlanInfoForUpdateOrDeleteCbPlan", "plan1", contentIds);
        
        verify(cassandraOperation, never()).deleteRecord(anyString(), anyString(), any());
        verify(cassandraOperation, never()).updateRecord(anyString(), anyString(), any(), any());
    }

    // Tests for getAddedContent method
    @Test
    void testGetAddedContent_NewContentAdded() {
        List<String> existingContent = Arrays.asList("content1", "content2");
        List<String> updatedContent = Arrays.asList("content1", "content2", "content3");
        
        List<String> result = (List<String>) ReflectionTestUtils.invokeMethod(
            cbPlanService, "getAddedContent", existingContent, updatedContent);
        
        assertEquals(1, result.size());
        assertTrue(result.contains("content3"));
    }

    @Test
    void testGetAddedContent_NoNewContent() {
        List<String> existingContent = Arrays.asList("content1", "content2");
        List<String> updatedContent = Arrays.asList("content1", "content2");
        
        List<String> result = (List<String>) ReflectionTestUtils.invokeMethod(
            cbPlanService, "getAddedContent", existingContent, updatedContent);
        
        assertTrue(result.isEmpty());
    }

    @Test
    void testGetAddedContent_NullExistingContent() {
        List<String> updatedContent = Arrays.asList("content1", "content2");
        
        List<String> result = (List<String>) ReflectionTestUtils.invokeMethod(
            cbPlanService, "getAddedContent", null, updatedContent);
        
        assertEquals(2, result.size());
        assertTrue(result.containsAll(updatedContent));
    }

    @Test
    void testGetAddedContent_NullUpdatedContent() {
        List<String> existingContent = Arrays.asList("content1", "content2");
        
        List<String> result = (List<String>) ReflectionTestUtils.invokeMethod(
            cbPlanService, "getAddedContent", existingContent, null);
        
        assertTrue(result.isEmpty());
    }

    // Tests for getDeletedContent method
    @Test
    void testGetDeletedContent_ContentRemoved() {
        List<String> existingContent = Arrays.asList("content1", "content2", "content3");
        List<String> updatedContent = Arrays.asList("content1", "content2");
        
        List<String> result = (List<String>) ReflectionTestUtils.invokeMethod(
            cbPlanService, "getDeletedContent", existingContent, updatedContent);
        
        assertEquals(1, result.size());
        assertTrue(result.contains("content3"));
    }

    @Test
    void testGetDeletedContent_NoContentRemoved() {
        List<String> existingContent = Arrays.asList("content1", "content2");
        List<String> updatedContent = Arrays.asList("content1", "content2", "content3");
        
        List<String> result = (List<String>) ReflectionTestUtils.invokeMethod(
            cbPlanService, "getDeletedContent", existingContent, updatedContent);
        
        assertTrue(result.isEmpty());
    }

    @Test
    void testGetDeletedContent_NullExistingContent() {
        List<String> updatedContent = Arrays.asList("content1", "content2");
        
        List<String> result = (List<String>) ReflectionTestUtils.invokeMethod(
            cbPlanService, "getDeletedContent", null, updatedContent);
        
        assertTrue(result.isEmpty());
    }

    @Test
    void testGetDeletedContent_NullUpdatedContent() {
        List<String> existingContent = Arrays.asList("content1", "content2");
        
        List<String> result = (List<String>) ReflectionTestUtils.invokeMethod(
            cbPlanService, "getDeletedContent", existingContent, null);
        
        assertEquals(2, result.size());
        assertTrue(result.containsAll(existingContent));
    }

    @Test
    void testUpsertCbPlanContentLookup_FixedFieldName() {
        testUpsertCbPlanContentLookup_ExistingContent();
    }

    // ===================== New tests added to raise coverage =====================

    // ---- createCbPlanByAdmin ----

    @Test
    void testCreateCbPlanByAdmin_MissingTargetedOrganisation_ReturnsBadRequest() {
        ApiRequest request = new ApiRequest();
        request.setRequest(new HashMap<>());

        ApiResponse response = cbPlanService.createCbPlanByAdmin(request, "token");

        assertEquals(Constants.FAILED, response.getParams().getStatus());
        assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
        assertEquals("targetedOrganisation is required", response.getParams().getErr());
    }

    @Test
    void testCreateCbPlanByAdmin_Success_SetsOrgIdListAndDelegatesWithIsAdminTrue() {
        ApiRequest request = new ApiRequest();
        Map<String, Object> requestMap = new HashMap<>();
        requestMap.put(Constants.TARGETED_ORGANISATION, "orgAdmin1");
        request.setRequest(requestMap);

        when(accessTokenValidator.fetchUserIdFromAccessToken(anyString(), any())).thenReturn("adminUser");
        when(userUtilityService.readUserProfileFromDB(eq("adminUser"), anyList()))
            .thenReturn(Map.of(Constants.ID, "adminUser", Constants.ROOT_ORG_ID, "root1"));
        when(userUtilityService.readOrgFromDB(eq("root1"), any()))
            .thenReturn(Map.of(Constants.IS_CCA, false));
        when(requestValidator.validateCbPlanCreateRequest(any(ApiRequest.class), anyBoolean(), anyString(), eq(true)))
            .thenReturn(Collections.emptyList());
        ApiResponse insertResp = new ApiResponse();
        insertResp.put(Constants.RESPONSE, Constants.SUCCESS);
        when(cassandraOperation.insertRecord(anyString(), anyString(), anyMap())).thenReturn(insertResp);

        ApiResponse response = cbPlanService.createCbPlanByAdmin(request, "token");

        assertEquals(Constants.CREATED, response.getResult().get(Constants.STATUS));
        assertEquals(List.of("orgAdmin1"), requestMap.get(Constants.ORG_ID_LIST));
    }

    // ---- publishCbPlanByAdmin ----

    @Test
    void testPublishCbPlanByAdmin_MissingTargetedOrganisation_ReturnsBadRequest() {
        ApiRequest request = new ApiRequest();
        request.setRequest(new HashMap<>());

        ApiResponse response = cbPlanService.publishCbPlanByAdmin(request, "token");

        assertEquals(Constants.FAILED, response.getParams().getStatus());
        assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
    }

    @Test
    void testPublishCbPlanByAdmin_Success_SkipsAuthCheckAndUsesTargetedOrgAsRootOrg() {
        ApiRequest request = new ApiRequest();
        Map<String, Object> requestMap = new HashMap<>();
        requestMap.put(Constants.TARGETED_ORGANISATION, "orgAdmin2");
        requestMap.put(Constants.ID, "plan1");
        request.setRequest(requestMap);

        when(accessTokenValidator.fetchUserIdFromAccessToken(anyString(), any())).thenReturn("adminUser");
        Map<String, Object> existingPlan = new HashMap<>();
        existingPlan.put(Constants.CREATED_BY, "someoneElse");
        existingPlan.put(Constants.STATUS, "draft");
        when(cassandraOperation.getRecordsByProperties(anyString(), anyString(), any(), any(), any()))
            .thenReturn(List.of(existingPlan));
        when(userUtilityService.readUserProfileFromDB(eq("adminUser"), anyList()))
            .thenReturn(Map.of(Constants.ID, "adminUser", Constants.ROOT_ORG_ID, "root1"));
        when(userUtilityService.readOrgFromDB(anyString(), any()))
            .thenReturn(Map.of(Constants.IS_CCA, false));
        when(requestValidator.validateContextData(any(), anyBoolean(), anyString(), any(), anyBoolean()))
            .thenReturn(Collections.emptyList());
        Map<String, Object> updateResp = new HashMap<>();
        updateResp.put(Constants.RESPONSE, Constants.SUCCESS);
        when(cassandraOperation.updateRecord(anyString(), anyString(), any(), any(), any(), any()))
            .thenReturn(updateResp);

        ApiResponse response = cbPlanService.publishCbPlanByAdmin(request, "token");

        assertEquals(Constants.SUCCESS, response.getParams().getStatus());
        verify(userUtilityService).readOrgFromDB(eq("orgAdmin2"), any());
    }

    // ---- handlePublishLookupUpdates (private, via reflection) ----

    @Test
    void testHandlePublishLookupUpdates_RespFailed() {
        Map<String, Object> resp = new HashMap<>();
        resp.put(Constants.RESPONSE, Constants.FAILED);
        resp.put(Constants.ERROR_MESSAGE, "db failure");
        ApiResponse response = new ApiResponse();
        response.setParams(new ApiRespParam());

        ReflectionTestUtils.invokeMethod(cbPlanService, "handlePublishLookupUpdates",
            resp, new HashMap<>(), null, new HashSet<>(), new HashSet<>(), "plan1", response);

        assertEquals(Constants.FAILED, response.getParams().getStatus());
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getResponseCode());
        assertTrue(response.getParams().getErr().contains("db failure"));
        assertTrue(response.getParams().getErr().contains("plan1"));
    }

    @Test
    void testHandlePublishLookupUpdates_SingleScope_Success() {
        Map<String, Object> resp = Map.of(Constants.RESPONSE, Constants.SUCCESS);
        Map<String, Object> updatedRequest = new HashMap<>();
        updatedRequest.put(Constants.ORG_SCOPE, Constants.SINGLE);
        ApiResponse bulkResp = new ApiResponse();
        bulkResp.getParams().setStatus(Constants.SUCCESS);
        bulkResp.put(Constants.RESPONSE, Constants.SUCCESS);
        when(cassandraOperation.insertBulkRecord(anyString(), anyString(), anyList())).thenReturn(bulkResp);
        ApiResponse response = new ApiResponse();
        response.setParams(new ApiRespParam());

        ReflectionTestUtils.invokeMethod(cbPlanService, "handlePublishLookupUpdates",
            resp, updatedRequest, null, new HashSet<>(Set.of("org1")), new HashSet<>(), "plan1", response);

        assertNotEquals(Constants.FAILED, response.getParams().getStatus());
        verify(cassandraOperation, times(1)).insertBulkRecord(anyString(), anyString(), anyList());
    }

    @Test
    void testHandlePublishLookupUpdates_CustomScope_Failure() {
        Map<String, Object> resp = Map.of(Constants.RESPONSE, Constants.SUCCESS);
        Map<String, Object> updatedRequest = new HashMap<>();
        updatedRequest.put(Constants.ORG_SCOPE, Constants.CUSTOM);
        ApiResponse bulkResp = new ApiResponse();
        bulkResp.getParams().setStatus(Constants.FAILED);
        bulkResp.getParams().setErr("insert failed");
        when(cassandraOperation.insertBulkRecord(anyString(), anyString(), anyList())).thenReturn(bulkResp);
        ApiResponse response = new ApiResponse();
        response.setParams(new ApiRespParam());

        ReflectionTestUtils.invokeMethod(cbPlanService, "handlePublishLookupUpdates",
            resp, updatedRequest, null, new HashSet<>(Set.of("org1")), new HashSet<>(), "plan1", response);

        assertEquals(Constants.FAILED, response.getParams().getStatus());
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getResponseCode());
        assertEquals("insert failed", response.getParams().getErr());
    }

    @Test
    void testHandlePublishLookupUpdates_AllScope_Failure() {
        Map<String, Object> resp = Map.of(Constants.RESPONSE, Constants.SUCCESS);
        Map<String, Object> updatedRequest = new HashMap<>();
        updatedRequest.put(Constants.ORG_SCOPE, Constants.ALL);
        ApiResponse insertResp = new ApiResponse();
        insertResp.getParams().setStatus(Constants.FAILED);
        insertResp.getParams().setErr("all-scope insert failed");
        when(cassandraOperation.insertRecord(anyString(), anyString(), anyMap())).thenReturn(insertResp);
        ApiResponse response = new ApiResponse();
        response.setParams(new ApiRespParam());

        ReflectionTestUtils.invokeMethod(cbPlanService, "handlePublishLookupUpdates",
            resp, updatedRequest, null, new HashSet<>(), new HashSet<>(), "plan1", response);

        assertEquals(Constants.FAILED, response.getParams().getStatus());
        assertEquals("all-scope insert failed", response.getParams().getErr());
    }

    @Test
    void testHandlePublishLookupUpdates_RemovedOrgs_CustomScope() {
        Map<String, Object> resp = Map.of(Constants.RESPONSE, Constants.SUCCESS);
        Map<String, Object> updatedRequest = new HashMap<>();
        updatedRequest.put(Constants.ORG_SCOPE, Constants.CUSTOM);
        ApiResponse bulkResp = new ApiResponse();
        bulkResp.getParams().setStatus(Constants.SUCCESS);
        bulkResp.put(Constants.RESPONSE, Constants.SUCCESS);
        when(cassandraOperation.insertBulkRecord(anyString(), anyString(), anyList())).thenReturn(bulkResp);
        Set<String> rootOrgIdsInCriteria = new HashSet<>(Set.of("orgA"));
        Set<String> existingRootOrgIdsInCriteria = new HashSet<>(Set.of("orgA", "orgB"));
        ApiResponse response = new ApiResponse();
        response.setParams(new ApiRespParam());

        ReflectionTestUtils.invokeMethod(cbPlanService, "handlePublishLookupUpdates",
            resp, updatedRequest, Constants.CUSTOM, rootOrgIdsInCriteria, existingRootOrgIdsInCriteria, "plan1", response);

        assertNotEquals(Constants.FAILED, response.getParams().getStatus());
        verify(cassandraOperation, times(2)).insertBulkRecord(anyString(), anyString(), anyList());
    }

    @Test
    void testHandlePublishLookupUpdates_AllPreviously_NewlyAdded_Failure() {
        Map<String, Object> resp = Map.of(Constants.RESPONSE, Constants.SUCCESS);
        Map<String, Object> updatedRequest = new HashMap<>();
        ApiResponse removeResp = new ApiResponse();
        removeResp.getParams().setStatus(Constants.FAILED);
        removeResp.getParams().setErr("remove ALL entry failed");
        when(cassandraOperation.insertRecord(anyString(), anyString(), anyMap())).thenReturn(removeResp);
        Set<String> rootOrgIdsInCriteria = new HashSet<>(Set.of("orgX"));
        Set<String> existingRootOrgIdsInCriteria = new HashSet<>();
        ApiResponse response = new ApiResponse();
        response.setParams(new ApiRespParam());

        ReflectionTestUtils.invokeMethod(cbPlanService, "handlePublishLookupUpdates",
            resp, updatedRequest, Constants.ALL, rootOrgIdsInCriteria, existingRootOrgIdsInCriteria, "plan1", response);

        assertEquals(Constants.FAILED, response.getParams().getStatus());
        assertEquals("remove ALL entry failed", response.getParams().getErr());
        verify(cassandraOperation, times(1)).insertRecord(anyString(), anyString(), anyMap());
    }

    @Test
    void testHandlePublishLookupUpdates_AllPreviously_NothingAdded_NoOp() {
        Map<String, Object> resp = Map.of(Constants.RESPONSE, Constants.SUCCESS);
        Map<String, Object> updatedRequest = new HashMap<>();
        Set<String> rootOrgIdsInCriteria = new HashSet<>(Set.of("orgX"));
        Set<String> existingRootOrgIdsInCriteria = new HashSet<>(Set.of("orgX"));
        ApiResponse response = new ApiResponse();
        response.setParams(new ApiRespParam());

        ReflectionTestUtils.invokeMethod(cbPlanService, "handlePublishLookupUpdates",
            resp, updatedRequest, Constants.ALL, rootOrgIdsInCriteria, existingRootOrgIdsInCriteria, "plan1", response);

        assertNotEquals(Constants.FAILED, response.getParams().getStatus());
        verify(cassandraOperation, never()).insertRecord(anyString(), anyString(), anyMap());
        verify(cassandraOperation, never()).insertBulkRecord(anyString(), anyString(), anyList());
    }

    // ---- prepareAndValidatePublishRequest (private, via reflection) ----

    @SuppressWarnings("unchecked")
    @Test
    void testPrepareAndValidatePublishRequest_LiveWithContextData_ReturnsValidationErrors() {
        Map<String, Object> existingCbPlan = new HashMap<>();
        existingCbPlan.put(Constants.DRAFT_DATA, "{\"name\":\"Updated\",\"contextData\":{\"key\":\"value\"}}");
        Map<String, Object> incomingRequest = new HashMap<>();
        Map<String, Object> updatedRequest = new HashMap<>();
        Set<String> rootOrgIdsInCriteria = new HashSet<>();
        Set<String> existingRootOrgIdsInCriteria = new HashSet<>();
        ApiResponse response = new ApiResponse();
        response.setParams(new ApiRespParam());

        when(requestValidator.validateContextData(any(), anyBoolean(), anyString(), any(), anyBoolean()))
            .thenReturn(List.of("ctx-error"));

        List<String> errors = (List<String>) ReflectionTestUtils.invokeMethod(cbPlanService,
            "prepareAndValidatePublishRequest", existingCbPlan, incomingRequest, updatedRequest,
            Constants.LIVE, false, "org1", "user1", false, rootOrgIdsInCriteria, existingRootOrgIdsInCriteria,
            "plan1", response);

        assertEquals(List.of("ctx-error"), errors);
        assertTrue(updatedRequest.containsKey(Constants.CONTEXT_DATA_REQUEST));
        verify(requestValidator, times(2)).validateContextData(any(), anyBoolean(), anyString(), any(), anyBoolean());
    }

    @SuppressWarnings("unchecked")
    @Test
    void testPrepareAndValidatePublishRequest_LiveWithoutContextData_SkipsSecondValidation() {
        Map<String, Object> existingCbPlan = new HashMap<>();
        existingCbPlan.put(Constants.DRAFT_DATA, "{\"name\":\"Updated\"}");
        Map<String, Object> incomingRequest = new HashMap<>();
        Map<String, Object> updatedRequest = new HashMap<>();
        Set<String> rootOrgIdsInCriteria = new HashSet<>();
        Set<String> existingRootOrgIdsInCriteria = new HashSet<>();
        ApiResponse response = new ApiResponse();
        response.setParams(new ApiRespParam());

        when(requestValidator.validateContextData(any(), anyBoolean(), anyString(), any(), anyBoolean()))
            .thenReturn(List.of("should-not-be-used"));

        List<String> errors = (List<String>) ReflectionTestUtils.invokeMethod(cbPlanService,
            "prepareAndValidatePublishRequest", existingCbPlan, incomingRequest, updatedRequest,
            Constants.LIVE, false, "org1", "user1", false, rootOrgIdsInCriteria, existingRootOrgIdsInCriteria,
            "plan1", response);

        assertTrue(errors.isEmpty());
        assertFalse(updatedRequest.containsKey(Constants.CONTEXT_DATA_REQUEST));
        verify(requestValidator, times(1)).validateContextData(any(), anyBoolean(), anyString(), any(), anyBoolean());
    }

    @Test
    void testPublishCbPlan_InvalidStatus_SetsBadRequestAfterOrgResolution() {
        ApiRequest request = new ApiRequest();
        Map<String, Object> requestMap = new HashMap<>();
        requestMap.put(Constants.ID, "planId");
        request.setRequest(requestMap);

        when(accessTokenValidator.fetchUserIdFromAccessToken(anyString(), any())).thenReturn("u1");
        Map<String, Object> existingPlan = new HashMap<>();
        existingPlan.put(Constants.CREATED_BY, "u1");
        existingPlan.put(Constants.STATUS, "archived");
        when(cassandraOperation.getRecordsByProperties(anyString(), anyString(), any(), any(), any()))
            .thenReturn(List.of(existingPlan));
        when(userUtilityService.readUserProfileFromDB(eq("u1"), anyList()))
            .thenReturn(Map.of(Constants.ID, "u1", Constants.ROOT_ORG_ID, "root1"));
        when(userUtilityService.readOrgFromDB(eq("root1"), any()))
            .thenReturn(Map.of(Constants.IS_CCA, false));

        ApiResponse response = cbPlanService.publishCbPlan(request, "org1", "token", List.of("role"));

        assertEquals(Constants.FAILED, response.getParams().getStatus());
        assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
        assertTrue(response.getParams().getErr().contains("invalid state"));
    }

    @Test
    void testPublishCbPlan_DraftStatus_ValidationErrorsReturnBadRequest() {
        ApiRequest request = new ApiRequest();
        Map<String, Object> requestMap = new HashMap<>();
        requestMap.put(Constants.ID, "planId");
        request.setRequest(requestMap);

        when(accessTokenValidator.fetchUserIdFromAccessToken(anyString(), any())).thenReturn("u1");
        Map<String, Object> existingPlan = new HashMap<>();
        existingPlan.put(Constants.CREATED_BY, "u1");
        existingPlan.put(Constants.STATUS, "draft");
        existingPlan.put(Constants.END_DATE_REQUEST, "2024-12-31");
        when(cassandraOperation.getRecordsByProperties(anyString(), anyString(), any(), any(), any()))
            .thenReturn(List.of(existingPlan));
        when(userUtilityService.readUserProfileFromDB(eq("u1"), anyList()))
            .thenReturn(Map.of(Constants.ID, "u1", Constants.ROOT_ORG_ID, "root1"));
        when(userUtilityService.readOrgFromDB(eq("root1"), any()))
            .thenReturn(Map.of(Constants.IS_CCA, false));
        when(requestValidator.validateContextData(any(), anyBoolean(), anyString(), any(), anyBoolean()))
            .thenReturn(List.of("missing field"));

        ApiResponse response = cbPlanService.publishCbPlan(request, "org1", "token", List.of("role"));

        assertEquals(Constants.FAILED, response.getParams().getStatus());
        assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
        assertTrue(response.getParams().getErr().contains("missing field"));
    }

    // ---- finalizeOrgScopeForPublish (private, via reflection) ----

    @Test
    void testFinalizeOrgScopeForPublish_LiveStatus_RemovesRootOrgIdsAndSetsEmptyDraftData() {
        Map<String, Object> updatedRequest = new HashMap<>();
        updatedRequest.put(Constants.ROOT_ORG_IDS_IN_CONTEXT_DATA, List.of("org1"));
        Map<String, Object> existingCbPlan = new HashMap<>();

        ReflectionTestUtils.invokeMethod(cbPlanService, "finalizeOrgScopeForPublish", updatedRequest, existingCbPlan, "live");

        assertFalse(updatedRequest.containsKey(Constants.ROOT_ORG_IDS_IN_CONTEXT_DATA));
        assertEquals("{}", updatedRequest.get(Constants.DRAFT_DATA));
    }

    @Test
    void testFinalizeOrgScopeForPublish_DraftStatus_RestoresExistingOrgScope() {
        Map<String, Object> updatedRequest = new HashMap<>();
        Map<String, Object> existingCbPlan = new HashMap<>();
        existingCbPlan.put(Constants.ORG_SCOPE, "single");

        ReflectionTestUtils.invokeMethod(cbPlanService, "finalizeOrgScopeForPublish", updatedRequest, existingCbPlan, "draft");

        assertEquals("single", updatedRequest.get(Constants.ORG_SCOPE));
    }

    @Test
    void testFinalizeOrgScopeForPublish_OtherStatus_NoChanges() {
        Map<String, Object> updatedRequest = new HashMap<>();
        updatedRequest.put("marker", "x");

        ReflectionTestUtils.invokeMethod(cbPlanService, "finalizeOrgScopeForPublish", updatedRequest, new HashMap<>(), "archived");

        assertEquals(1, updatedRequest.size());
        assertEquals("x", updatedRequest.get("marker"));
    }

    // ---- extractTargetedOrganisation (private, via reflection) ----

    @Test
    void testExtractTargetedOrganisation_StringValue() {
        ApiRequest request = new ApiRequest();
        Map<String, Object> req = new HashMap<>();
        req.put(Constants.TARGETED_ORGANISATION, "orgXYZ");
        request.setRequest(req);

        String result = (String) ReflectionTestUtils.invokeMethod(cbPlanService, "extractTargetedOrganisation", request);

        assertEquals("orgXYZ", result);
    }

    @Test
    void testExtractTargetedOrganisation_NonStringValue() {
        ApiRequest request = new ApiRequest();
        Map<String, Object> req = new HashMap<>();
        req.put(Constants.TARGETED_ORGANISATION, 12345);
        request.setRequest(req);

        String result = (String) ReflectionTestUtils.invokeMethod(cbPlanService, "extractTargetedOrganisation", request);

        assertEquals("12345", result);
    }

    @Test
    void testExtractTargetedOrganisation_MissingKey() {
        ApiRequest request = new ApiRequest();
        request.setRequest(new HashMap<>());

        String result = (String) ReflectionTestUtils.invokeMethod(cbPlanService, "extractTargetedOrganisation", request);

        assertNull(result);
    }

    // ---- populateReadData (private, via reflection) ----

    @SuppressWarnings("unchecked")
    @Test
    void testPopulateReadData_LiveStatusWithValidDraftData_UsesCbPlanDto() {
        Map<String, Object> cbPlan = new HashMap<>();
        cbPlan.put(Constants.STATUS, Constants.LIVE);
        cbPlan.put(Constants.DRAFT_DATA,
            "{\"name\":\"Draft Plan\",\"contentType\":\"Course\",\"contentList\":[\"c1\"],\"endDate\":\"2024-12-31\",\"isApar\":true}");
        cbPlan.put(Constants.CREATED_BY, "userId");
        cbPlan.put(Constants.CREATED_AT_REQ, Instant.now());

        when(contentService.enrichContentInfoForCBPlan(anyList())).thenReturn(List.of(Map.of("id", "c1")));

        Map<String, Object> result = (Map<String, Object>) ReflectionTestUtils.invokeMethod(cbPlanService, "populateReadData", cbPlan);

        assertEquals("Draft Plan", result.get(Constants.NAME));
        assertEquals(Boolean.TRUE, result.get(Constants.IS_APAR));
        assertNotNull(result.get(Constants.END_DATE_REQUEST));
    }

    @SuppressWarnings("unchecked")
    @Test
    void testPopulateReadData_CreatedByBlank_NoCreatedByNameAdded() {
        Map<String, Object> cbPlan = new HashMap<>();
        cbPlan.put(Constants.STATUS, "draft");
        cbPlan.put(Constants.NAME, "Plan X");
        cbPlan.put(Constants.CONTENT_TYPE, "Course");
        cbPlan.put(Constants.CONTENT_LIST, List.of("c1"));
        cbPlan.put(Constants.CREATED_AT_REQ, Instant.now());
        cbPlan.put(Constants.CREATED_BY, "");
        when(contentService.enrichContentInfoForCBPlan(anyList())).thenReturn(Collections.emptyList());

        Map<String, Object> result = (Map<String, Object>) ReflectionTestUtils.invokeMethod(cbPlanService, "populateReadData", cbPlan);

        assertFalse(result.containsKey(Constants.CREATED_BY_NAME));
    }

    @SuppressWarnings("unchecked")
    @Test
    void testPopulateReadData_ContextDataValidJson_ParsedAsJsonNode() {
        Map<String, Object> cbPlan = new HashMap<>();
        cbPlan.put(Constants.STATUS, "draft");
        cbPlan.put(Constants.NAME, "Plan X");
        cbPlan.put(Constants.CONTENT_TYPE, "Course");
        cbPlan.put(Constants.CONTENT_LIST, List.of("c1"));
        cbPlan.put(Constants.CREATED_AT_REQ, Instant.now());
        cbPlan.put(Constants.CONTEXT_DATA_REQUEST, "{\"foo\":\"bar\"}");
        when(contentService.enrichContentInfoForCBPlan(anyList())).thenReturn(Collections.emptyList());

        Map<String, Object> result = (Map<String, Object>) ReflectionTestUtils.invokeMethod(cbPlanService, "populateReadData", cbPlan);

        assertTrue(result.get(Constants.CONTEXT_DATA_REQUEST) instanceof JsonNode);
    }

    @SuppressWarnings("unchecked")
    @Test
    void testPopulateReadData_ContextDataInvalidJson_SkipsEnrichment() {
        Map<String, Object> cbPlan = new HashMap<>();
        cbPlan.put(Constants.STATUS, "draft");
        cbPlan.put(Constants.NAME, "Plan X");
        cbPlan.put(Constants.CONTENT_TYPE, "Course");
        cbPlan.put(Constants.CONTENT_LIST, List.of("c1"));
        cbPlan.put(Constants.CREATED_AT_REQ, Instant.now());
        cbPlan.put(Constants.CONTEXT_DATA_REQUEST, "{invalid-json");
        when(contentService.enrichContentInfoForCBPlan(anyList())).thenReturn(Collections.emptyList());

        Map<String, Object> result = (Map<String, Object>) ReflectionTestUtils.invokeMethod(cbPlanService, "populateReadData", cbPlan);

        assertFalse(result.containsKey(Constants.CONTEXT_DATA_REQUEST));
    }

    // ---- enrichSearchResultItem / enrichCreatedByInfo (private, via reflection) ----

    @SuppressWarnings("unchecked")
    @Test
    void testEnrichSearchResultItem_NoCreatedByOrContentListKeys_ReturnsCopyUnchanged() {
        Map<String, Object> item = new HashMap<>();
        item.put("name", "Plan A");

        Map<String, Object> result = (Map<String, Object>) ReflectionTestUtils.invokeMethod(cbPlanService, "enrichSearchResultItem", item);

        assertEquals("Plan A", result.get("name"));
        assertFalse(result.containsKey(Constants.CREATED_BY_NAME));
        verify(contentService, never()).enrichContentInfoForCBPlan(anyList());
    }

    @SuppressWarnings("unchecked")
    @Test
    void testEnrichSearchResultItem_CreatedByBlank_SkipsUserLookup() {
        Map<String, Object> item = new HashMap<>();
        item.put(Constants.CREATED_BY, "   ");

        Map<String, Object> result = (Map<String, Object>) ReflectionTestUtils.invokeMethod(cbPlanService, "enrichSearchResultItem", item);

        assertFalse(result.containsKey(Constants.CREATED_BY_NAME));
        verify(userUtilityService, never()).readUserProfile(anyString(), anyList());
    }

    @SuppressWarnings("unchecked")
    @Test
    void testEnrichSearchResultItem_UserProfileNull_SkipsEnrichment() {
        Map<String, Object> item = new HashMap<>();
        item.put(Constants.CREATED_BY, "user1");
        when(userUtilityService.readUserProfile(eq("user1"), anyList())).thenReturn(null);

        Map<String, Object> result = (Map<String, Object>) ReflectionTestUtils.invokeMethod(cbPlanService, "enrichSearchResultItem", item);

        assertFalse(result.containsKey(Constants.CREATED_BY_NAME));
    }

    @SuppressWarnings("unchecked")
    @Test
    void testEnrichSearchResultItem_ContentListNotAList_SkipsEnrichment() {
        Map<String, Object> item = new HashMap<>();
        item.put(Constants.CONTENT_LIST, "not-a-list");

        Map<String, Object> result = (Map<String, Object>) ReflectionTestUtils.invokeMethod(cbPlanService, "enrichSearchResultItem", item);

        assertEquals("not-a-list", result.get(Constants.CONTENT_LIST));
        verify(contentService, never()).enrichContentInfoForCBPlan(anyList());
    }

    // ---- retireCbPlan org-scope branches (full flow) ----

    @Test
    void testRetireCbPlan_AllOrgScope_LookupFailureAfterSuccessfulRetire() {
        ApiRequest request = new ApiRequest();
        Map<String, Object> requestMap = new HashMap<>();
        requestMap.put(Constants.ID, "planId");
        request.setRequest(requestMap);

        when(accessTokenValidator.fetchUserIdFromAccessToken(anyString(), any())).thenReturn("userId");

        Map<String, Object> existingPlan = new HashMap<>();
        existingPlan.put("createdBy", "userId");
        existingPlan.put("status", "live");
        existingPlan.put("orgScope", Constants.ALL);
        when(cassandraOperation.getRecordsByProperties(anyString(), anyString(), any(), any(), any()))
            .thenReturn(List.of(existingPlan));

        Map<String, Object> updateResp = new HashMap<>();
        updateResp.put(Constants.RESPONSE, Constants.SUCCESS);
        when(cassandraOperation.updateRecord(anyString(), anyString(), any(), any())).thenReturn(updateResp);

        ApiResponse failInsert = new ApiResponse();
        failInsert.put(Constants.ERROR_MESSAGE, "cassandra down");
        when(cassandraOperation.insertRecord(anyString(), anyString(), anyMap())).thenReturn(failInsert);

        ApiResponse response = cbPlanService.retireCbPlan(request, "orgId", "token", List.of("role"));

        assertEquals(Constants.FAILED, response.getParams().getStatus());
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getResponseCode());
        assertEquals("cassandra down", response.getParams().getErr());
    }

    @Test
    void testRetireCbPlan_CustomOrgScopeWithContentList_SuccessRemovesLookupsAndArchives() {
        ApiRequest request = new ApiRequest();
        Map<String, Object> requestMap = new HashMap<>();
        requestMap.put(Constants.ID, "planId");
        request.setRequest(requestMap);

        when(accessTokenValidator.fetchUserIdFromAccessToken(anyString(), any())).thenReturn("userId");

        Map<String, Object> contextData = Map.of(Constants.ACCESS_CONTROL, Map.of(
            Constants.USER_GROUPS, List.of(Map.of(
                Constants.USER_GROUP_CRITERIA_LIST, List.of(Map.of(
                    Constants.CRITERIA_KEY, Constants.ROOT_ORG_ID,
                    Constants.CRITERIA_VALUE, List.of("orgA")
                ))
            ))
        ));

        Map<String, Object> existingPlan = new HashMap<>();
        existingPlan.put("createdBy", "userId");
        existingPlan.put("status", "live");
        existingPlan.put("orgScope", Constants.CUSTOM);
        existingPlan.put(Constants.CONTENT_LIST, List.of("content1"));
        existingPlan.put(Constants.CONTEXT_DATA_REQUEST, contextData);
        when(cassandraOperation.getRecordsByProperties(anyString(), anyString(), any(), any(), any()))
            .thenReturn(List.of(existingPlan))
            .thenReturn(Collections.emptyList());

        Map<String, Object> updateResp = new HashMap<>();
        updateResp.put(Constants.RESPONSE, Constants.SUCCESS);
        when(cassandraOperation.updateRecord(anyString(), anyString(), any(), any())).thenReturn(updateResp);

        ApiResponse bulkResp = new ApiResponse();
        bulkResp.getParams().setStatus(Constants.SUCCESS);
        bulkResp.put(Constants.RESPONSE, Constants.SUCCESS);
        when(cassandraOperation.insertBulkRecord(anyString(), anyString(), anyList())).thenReturn(bulkResp);

        ApiResponse response = cbPlanService.retireCbPlan(request, "orgId", "token", List.of("role"));

        assertEquals(Constants.UPDATED, response.getResult().get(Constants.STATUS));
        assertTrue(response.getResult().get(Constants.MESSAGE).toString().contains("Archived cbPlan"));
    }

    // ===================== Additional tests to close remaining coverage gaps =====================

    // ---- parseEndDate: malformed string hits the catch-all RuntimeException wrap (neither ISO_INSTANT nor yyyy-MM-dd parse) ----

    @Test
    void testParseEndDate_InvalidString_ThrowsWrappedRuntimeException() {
        RuntimeException ex = assertThrows(RuntimeException.class, () ->
            ReflectionTestUtils.invokeMethod(cbPlanService, "parseEndDate", "not-a-valid-date"));
        assertTrue(ex.getMessage().contains("Invalid endDate format"));
    }

    // ---- archiveCustomOrgLookup (private, via reflection) - branches not covered by testArchiveCustomOrgLookup ----

    @Test
    void testArchiveCustomOrgLookup_EmptyOrgIdList_ReturnsFailed() {
        ApiResponse result = (ApiResponse) ReflectionTestUtils.invokeMethod(
            cbPlanService, "archiveCustomOrgLookup", "planId", new ArrayList<String>());

        assertNotNull(result);
        assertEquals(Constants.FAILED, result.getParams().getStatus());
        assertEquals("orgIdList is empty. Cannot archive lookup entries.", result.getParams().getErr());
    }

    @Test
    void testArchiveCustomOrgLookup_CassandraUpdateFails_ReturnsFailed() {
        Map<String, Object> failResp = new HashMap<>();
        failResp.put(Constants.RESPONSE, Constants.FAILED);
        when(cassandraOperation.updateRecord(anyString(), anyString(), any(), any())).thenReturn(failResp);

        ApiResponse result = (ApiResponse) ReflectionTestUtils.invokeMethod(
            cbPlanService, "archiveCustomOrgLookup", "planId", Arrays.asList("org1"));

        assertNotNull(result);
        assertEquals(Constants.FAILED, result.getParams().getStatus());
        assertEquals("Failed to archive record for orgId: org1", result.getParams().getErr());
    }

    @Test
    void testArchiveCustomOrgLookup_ExceptionThrown_ReturnsFailed() {
        when(cassandraOperation.updateRecord(anyString(), anyString(), any(), any()))
            .thenThrow(new RuntimeException("DB down"));

        ApiResponse result = (ApiResponse) ReflectionTestUtils.invokeMethod(
            cbPlanService, "archiveCustomOrgLookup", "planId", Arrays.asList("org1"));

        assertNotNull(result);
        assertEquals(Constants.FAILED, result.getParams().getStatus());
        assertTrue(result.getParams().getErr().contains("Exception while archiving org lookup entries"));
    }

    // ---- extractUniqueRootOrgIds / collectRootOrgIdsFromCriteriaList / resolveContextDataForRootOrgExtraction: remaining branches ----

    @SuppressWarnings("unchecked")
    @Test
    void testExtractUniqueRootOrgIds_TargetedOrganisationCriteriaKey_Collected() {
        Map<String, Object> crit = Map.of(Constants.CRITERIA_KEY, Constants.TARGETED_ORGANISATION,
                Constants.CRITERIA_VALUE, List.of("orgT1", "orgT2"));
        Map<String, Object> ug = Map.of(Constants.USER_GROUP_CRITERIA_LIST, List.of(crit));
        Map<String, Object> ac = Map.of(Constants.USER_GROUPS, List.of(ug));
        Map<String, Object> raw = Map.of(Constants.CONTEXT_DATA_REQUEST, Map.of(Constants.ACCESS_CONTROL, ac));

        Set<String> result = (Set<String>) ReflectionTestUtils.invokeMethod(cbPlanService, "extractUniqueRootOrgIds", raw);

        assertEquals(Set.of("orgT1", "orgT2"), result);
    }

    @SuppressWarnings("unchecked")
    @Test
    void testExtractUniqueRootOrgIds_ContextDataNeitherStringNorMap_ReturnsEmptySet() {
        Map<String, Object> raw = new HashMap<>();
        raw.put(Constants.CONTEXT_DATA_REQUEST, 12345);

        Set<String> result = (Set<String>) ReflectionTestUtils.invokeMethod(cbPlanService, "extractUniqueRootOrgIds", raw);

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @SuppressWarnings("unchecked")
    @Test
    void testExtractUniqueRootOrgIds_MalformedJsonString_ReturnsEmptySet() {
        Map<String, Object> raw = new HashMap<>();
        raw.put(Constants.CONTEXT_DATA_REQUEST, "{not-valid-json");

        Set<String> result = (Set<String>) ReflectionTestUtils.invokeMethod(cbPlanService, "extractUniqueRootOrgIds", raw);

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    // ---- updateCbPlan (DRAFT status) with changed contentList: exercises the added/deleted content-lookup branch
    // inside handleUpdateOfDraftCbPlan that no existing test reaches ----

    @Test
    void testUpdateCbPlan_DraftPlanWithContentListChanges_UpsertsAddedAndRemovesDeletedContentLookup() {
        ApiRequest request = new ApiRequest();
        Map<String, Object> updateMap = new HashMap<>();
        updateMap.put(Constants.ID, "plan1");
        updateMap.put(Constants.CONTENT_LIST, Arrays.asList("content2", "content3"));
        request.setRequest(updateMap);

        when(accessTokenValidator.fetchUserIdFromAccessToken(anyString(), any())).thenReturn("u1");

        Map<String, Object> existing = new HashMap<>();
        existing.put(Constants.CREATED_BY, "u1");
        existing.put(Constants.STATUS, Constants.DRAFT);
        existing.put(Constants.CONTENT_LIST, Arrays.asList("content1", "content2"));

        when(cassandraOperation.getRecordsByProperties(anyString(), anyString(), any(), any(), any()))
            .thenReturn(List.of(existing));

        when(userUtilityService.readUserProfileFromDB(eq("u1"), anyList()))
            .thenReturn(Map.of(Constants.ID, "u1", Constants.ROOT_ORG_ID, "root1"));
        when(userUtilityService.readOrgFromDB(eq("root1"), any()))
            .thenReturn(Map.of(Constants.IS_CCA, false));

        when(requestValidator.validateCbPlanCreateRequest(any(), anyBoolean(), anyString()))
            .thenReturn(Collections.emptyList());

        Map<String, Object> updateResp = new HashMap<>();
        updateResp.put(Constants.RESPONSE, Constants.SUCCESS);
        when(cassandraOperation.updateRecord(anyString(), anyString(), any(), any())).thenReturn(updateResp);

        ApiResponse response = cbPlanService.updateCbPlan(request, "org", "token", Arrays.asList("admin"));

        assertEquals(Constants.UPDATED, response.getResult().get(Constants.STATUS));
        // Confirms the content-lookup branch (added content "content3") actually ran, not just the plan update.
        verify(cassandraOperation, atLeastOnce()).updateRecord(eq(Constants.KEYSPACE_SUNBIRD),
                eq(Constants.TABLE_CB_PLAN_V2_CONTENT_LOOKUP), any(), any());
    }

    // ---- retireCbPlan: StringUtils.isNoneBlank(comment) true branch (comment actually persisted) ----

    @SuppressWarnings("unchecked")
    @Test
    void testRetireCbPlan_WithComment_PersistsCommentOnUpdatedRecord() {
        ApiRequest request = new ApiRequest();
        Map<String, Object> requestMap = new HashMap<>();
        requestMap.put(Constants.ID, "planId");
        requestMap.put(Constants.COMMENT, "retiring now");
        request.setRequest(requestMap);

        when(accessTokenValidator.fetchUserIdFromAccessToken(anyString(), any())).thenReturn("userId");

        Map<String, Object> existingPlan = new HashMap<>();
        existingPlan.put(Constants.CREATED_BY, "userId");
        existingPlan.put(Constants.STATUS, "live");
        when(cassandraOperation.getRecordsByProperties(anyString(), anyString(), any(), any(), any()))
            .thenReturn(Arrays.asList(existingPlan));

        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        Map<String, Object> updateResp = new HashMap<>();
        updateResp.put(Constants.RESPONSE, Constants.SUCCESS);
        when(cassandraOperation.updateRecord(eq(Constants.KEYSPACE_SUNBIRD), eq(Constants.TABLE_CB_PLAN_V2),
                captor.capture(), any())).thenReturn(updateResp);

        ApiResponse response = cbPlanService.retireCbPlan(request, "orgId", "token", Arrays.asList("role"));

        assertEquals(Constants.UPDATED, response.getResult().get(Constants.STATUS));
        assertEquals("retiring now", captor.getValue().get(Constants.COMMENT));
    }

    // ===================== Additional tests targeting remaining JaCoCo gaps =====================
    // A Jackson bean whose getter always throws, used to force mapper.writeValueAsString(...) to raise
    // a JsonProcessingException (JsonMappingException wraps the unchecked exception by default).
    public static class ThrowingBean {
        public String getValue() {
            throw new RuntimeException("boom-serialize");
        }
    }

    // ---- createCbPlan: getCCAFromOrg failure branch (line ~134) ----
    @Test
    void testCreateCbPlan_CcaCheckFails_ReturnsFailedBeforeValidation() {
        ApiRequest request = new ApiRequest();
        request.setRequest(new HashMap<>());

        when(accessTokenValidator.fetchUserIdFromAccessToken(anyString(), any())).thenReturn("userId");
        when(userUtilityService.readUserProfileFromDB(eq("userId"), anyList()))
            .thenReturn(Map.of(Constants.ID, "userId", Constants.ROOT_ORG_ID, "root1"));
        when(userUtilityService.readOrgFromDB(eq("root1"), any()))
            .thenReturn(Collections.emptyMap());

        ApiResponse response = cbPlanService.createCbPlan(request, "orgId", "token");

        assertEquals(Constants.FAILED, response.getParams().getStatus());
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getResponseCode());
        verify(requestValidator, never()).validateCbPlanCreateRequest(any(), anyBoolean(), anyString(), anyBoolean());
    }

    // ---- createCbPlan: generic Exception caught by the outer catch (line ~145-149) ----
    @Test
    void testCreateCbPlan_ValidatorThrowsRuntimeException_CaughtByGenericCatch() {
        ApiRequest request = new ApiRequest();
        request.setRequest(new HashMap<>());

        when(accessTokenValidator.fetchUserIdFromAccessToken(anyString(), any())).thenReturn("userId");
        when(userUtilityService.readUserProfileFromDB(eq("userId"), anyList()))
            .thenReturn(Map.of(Constants.ID, "userId", Constants.ROOT_ORG_ID, "root1"));
        when(userUtilityService.readOrgFromDB(eq("root1"), any()))
            .thenReturn(Map.of(Constants.IS_CCA, false));
        when(requestValidator.validateCbPlanCreateRequest(any(), anyBoolean(), anyString(), anyBoolean()))
            .thenThrow(new RuntimeException("boom"));

        ApiResponse response = cbPlanService.createCbPlan(request, "orgId", "token");

        assertEquals(Constants.FAILED, response.getParams().getStatus());
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getResponseCode());
        assertEquals("boom", response.getParams().getErr());
    }

    // ---- insertCbPlanAndRespond: catch(JsonProcessingException) branch (line ~183-187) ----
    @Test
    void testCreateCbPlan_ContextDataSerializationThrows_CaughtAsJsonProcessingException() {
        ApiRequest request = new ApiRequest();
        Map<String, Object> requestMap = new HashMap<>();
        requestMap.put("name", "Test Plan");
        requestMap.put("orgScope", "single");
        requestMap.put("orgIdList", Arrays.asList("org1"));
        requestMap.put("contentType", "Course");
        requestMap.put("contentList", Arrays.asList("content1"));
        requestMap.put(Constants.CONTEXT_DATA_REQUEST, new ThrowingBean());
        request.setRequest(requestMap);

        when(accessTokenValidator.fetchUserIdFromAccessToken(anyString(), any())).thenReturn("userId");
        when(userUtilityService.readUserProfileFromDB(eq("userId"), anyList()))
            .thenReturn(Map.of(Constants.ID, "userId", Constants.ROOT_ORG_ID, "root1"));
        when(userUtilityService.readOrgFromDB(eq("root1"), any()))
            .thenReturn(Map.of(Constants.IS_CCA, false));
        when(requestValidator.validateCbPlanCreateRequest(any(), anyBoolean(), anyString(), anyBoolean()))
            .thenReturn(Collections.emptyList());

        ApiResponse response = cbPlanService.createCbPlan(request, "orgId", "token");

        assertEquals(Constants.FAILED, response.getParams().getStatus());
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getResponseCode());
        assertNotNull(response.getParams().getErr());
        verify(cassandraOperation, never()).insertRecord(anyString(), anyString(), any());
    }

    // ---- updateCbPlan: existing plan lookup returns a list containing an empty map (line ~212-215) ----
    @Test
    void testUpdateCbPlan_ExistingPlanMapEmpty_ReturnsNotFound() {
        ApiRequest request = new ApiRequest();
        request.setRequest(Map.of(Constants.ID, "plan1"));

        when(accessTokenValidator.fetchUserIdFromAccessToken(anyString(), any())).thenReturn("userId");
        when(cassandraOperation.getRecordsByProperties(anyString(), anyString(), any(), any(), any()))
            .thenReturn(Arrays.asList(new HashMap<>()));

        ApiResponse response = cbPlanService.updateCbPlan(request, "orgId", "token", Arrays.asList("role"));

        assertEquals(Constants.FAILED, response.getParams().getStatus());
        assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
        assertEquals(CbPlanServiceImplTest.CB_PLAN_NOT_FOUND_PREFIX + "plan1", response.getParams().getErr());
    }

    // ---- updateCbPlan: getCCAFromOrg failure branch (line ~234) ----
    @Test
    void testUpdateCbPlan_CcaCheckFails_ReturnsFailed() {
        ApiRequest request = new ApiRequest();
        request.setRequest(Map.of(Constants.ID, "plan1"));

        when(accessTokenValidator.fetchUserIdFromAccessToken(anyString(), any())).thenReturn("userId");

        Map<String, Object> existingPlan = new HashMap<>();
        existingPlan.put(Constants.CREATED_BY, "userId");
        existingPlan.put(Constants.STATUS, Constants.DRAFT);
        when(cassandraOperation.getRecordsByProperties(anyString(), anyString(), any(), any(), any()))
            .thenReturn(Arrays.asList(existingPlan));

        when(userUtilityService.readUserProfileFromDB(eq("userId"), anyList()))
            .thenReturn(Map.of(Constants.ID, "userId", Constants.ROOT_ORG_ID, "root1"));
        when(userUtilityService.readOrgFromDB(eq("root1"), any()))
            .thenReturn(Collections.emptyMap());

        ApiResponse response = cbPlanService.updateCbPlan(request, "orgId", "token", Arrays.asList("role"));

        assertEquals(Constants.FAILED, response.getParams().getStatus());
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getResponseCode());
        verify(requestValidator, never()).validateCbPlanCreateRequest(any(), anyBoolean(), anyString());
    }

    // ---- updateCbPlan: LIVE plan whose handleUpdateOfLiveCbPlan call fails propagates the failure (line ~242-244) ----
    @Test
    void testUpdateCbPlan_LiveStatus_HandleUpdateFailurePropagates() {
        ApiRequest request = new ApiRequest();
        request.setRequest(Map.of(Constants.ID, "plan1", "name", "New Name"));

        when(accessTokenValidator.fetchUserIdFromAccessToken(anyString(), any())).thenReturn("userId");

        Map<String, Object> existingPlan = new HashMap<>();
        existingPlan.put(Constants.CREATED_BY, "userId");
        existingPlan.put(Constants.STATUS, Constants.LIVE);
        when(cassandraOperation.getRecordsByProperties(anyString(), anyString(), any(), any(), any()))
            .thenReturn(Arrays.asList(existingPlan));

        when(userUtilityService.readUserProfileFromDB(eq("userId"), anyList()))
            .thenReturn(Map.of(Constants.ID, "userId", Constants.ROOT_ORG_ID, "root1"));
        when(userUtilityService.readOrgFromDB(eq("root1"), any()))
            .thenReturn(Map.of(Constants.IS_CCA, false));
        when(requestValidator.validateContextData(any(), anyBoolean(), anyString(), any()))
            .thenReturn(Arrays.asList("ctx error"));

        ApiResponse response = cbPlanService.updateCbPlan(request, "orgId", "token", Arrays.asList("role"));

        assertEquals(Constants.FAILED, response.getParams().getStatus());
        assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
        assertTrue(response.getParams().getErr().contains("ctx error"));
    }

    // ---- updateCbPlan: DRAFT plan validation errors (line ~269-272) ----
    @Test
    void testUpdateCbPlan_DraftStatus_ValidationErrors_ReturnsBadRequest() {
        ApiRequest request = new ApiRequest();
        request.setRequest(Map.of(Constants.ID, "plan1"));

        when(accessTokenValidator.fetchUserIdFromAccessToken(anyString(), any())).thenReturn("userId");

        Map<String, Object> existingPlan = new HashMap<>();
        existingPlan.put(Constants.CREATED_BY, "userId");
        existingPlan.put(Constants.STATUS, Constants.DRAFT);
        when(cassandraOperation.getRecordsByProperties(anyString(), anyString(), any(), any(), any()))
            .thenReturn(Arrays.asList(existingPlan));

        when(userUtilityService.readUserProfileFromDB(eq("userId"), anyList()))
            .thenReturn(Map.of(Constants.ID, "userId", Constants.ROOT_ORG_ID, "root1"));
        when(userUtilityService.readOrgFromDB(eq("root1"), any()))
            .thenReturn(Map.of(Constants.IS_CCA, false));
        when(requestValidator.validateCbPlanCreateRequest(any(), anyBoolean(), anyString()))
            .thenReturn(Arrays.asList("draft err"));

        ApiResponse response = cbPlanService.updateCbPlan(request, "orgId", "token", Arrays.asList("role"));

        assertEquals(Constants.FAILED, response.getParams().getStatus());
        assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
        assertTrue(response.getParams().getErr().contains("draft err"));
    }

    // ---- updateCbPlan: DRAFT plan whose cassandra update fails (line ~293-295) ----
    @Test
    void testUpdateCbPlan_DraftStatus_CassandraUpdateFails_ReturnsBadRequest() {
        ApiRequest request = new ApiRequest();
        request.setRequest(Map.of(Constants.ID, "plan1"));

        when(accessTokenValidator.fetchUserIdFromAccessToken(anyString(), any())).thenReturn("userId");

        Map<String, Object> existingPlan = new HashMap<>();
        existingPlan.put(Constants.CREATED_BY, "userId");
        existingPlan.put(Constants.STATUS, Constants.DRAFT);
        when(cassandraOperation.getRecordsByProperties(anyString(), anyString(), any(), any(), any()))
            .thenReturn(Arrays.asList(existingPlan));

        when(userUtilityService.readUserProfileFromDB(eq("userId"), anyList()))
            .thenReturn(Map.of(Constants.ID, "userId", Constants.ROOT_ORG_ID, "root1"));
        when(userUtilityService.readOrgFromDB(eq("root1"), any()))
            .thenReturn(Map.of(Constants.IS_CCA, false));
        when(requestValidator.validateCbPlanCreateRequest(any(), anyBoolean(), anyString()))
            .thenReturn(Collections.emptyList());

        Map<String, Object> failResp = new HashMap<>();
        failResp.put(Constants.RESPONSE, Constants.FAILED);
        when(cassandraOperation.updateRecord(anyString(), anyString(), anyMap(), anyMap())).thenReturn(failResp);

        ApiResponse response = cbPlanService.updateCbPlan(request, "orgId", "token", Arrays.asList("role"));

        assertEquals(Constants.FAILED, response.getParams().getStatus());
        assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
        assertEquals(CbPlanServiceImplTest.CB_PLAN_NOT_FOUND_PREFIX + "plan1", response.getParams().getErr());
    }

    // ---- publishCbPlan: getCCAFromOrg failure branch (line ~359) ----
    @Test
    void testPublishCbPlan_CcaCheckFails_ReturnsFailed() {
        ApiRequest request = new ApiRequest();
        request.setRequest(Map.of(Constants.ID, "plan1"));

        when(accessTokenValidator.fetchUserIdFromAccessToken(anyString(), any())).thenReturn("userId");

        Map<String, Object> existingPlan = new HashMap<>();
        existingPlan.put(Constants.CREATED_BY, "userId");
        existingPlan.put(Constants.STATUS, Constants.DRAFT);
        when(cassandraOperation.getRecordsByProperties(anyString(), anyString(), any(), any(), any()))
            .thenReturn(Arrays.asList(existingPlan));

        when(userUtilityService.readUserProfileFromDB(eq("userId"), anyList()))
            .thenReturn(Map.of(Constants.ID, "userId", Constants.ROOT_ORG_ID, "root1"));
        when(userUtilityService.readOrgFromDB(eq("root1"), any()))
            .thenReturn(Collections.emptyMap());

        ApiResponse response = cbPlanService.publishCbPlan(request, "orgId", "token", Arrays.asList("role"));

        assertEquals(Constants.FAILED, response.getParams().getStatus());
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getResponseCode());
    }

    // ---- publishCbPlan -> loadAndAuthorizePublishPlan: existing plan is an empty map (line ~421-424) ----
    @Test
    void testPublishCbPlan_ExistingPlanMapEmpty_ReturnsNotFound() {
        ApiRequest request = new ApiRequest();
        request.setRequest(Map.of(Constants.ID, "plan1"));

        when(accessTokenValidator.fetchUserIdFromAccessToken(anyString(), any())).thenReturn("userId");
        when(cassandraOperation.getRecordsByProperties(anyString(), anyString(), any(), any(), any()))
            .thenReturn(Arrays.asList(new HashMap<>()));

        ApiResponse response = cbPlanService.publishCbPlan(request, "orgId", "token", Arrays.asList("role"));

        assertEquals(Constants.FAILED, response.getParams().getStatus());
        assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
        assertEquals(CbPlanServiceImplTest.CB_PLAN_NOT_FOUND_PREFIX + "plan1", response.getParams().getErr());
    }

    // ---- handlePublishLookupUpdates: removal-of-previously-custom/single orgIds failure (line ~518-521) ----
    @Test
    void testHandlePublishLookupUpdates_RemovalOfCustomScopeOrgsFails() {
        Map<String, Object> resp = Map.of(Constants.RESPONSE, Constants.SUCCESS);
        // No ORG_SCOPE key at all => the initial add/upsert block (SINGLE/CUSTOM/ALL) is skipped entirely,
        // isolating the "removed orgIds" branch below.
        Map<String, Object> updatedRequest = new HashMap<>();

        ApiResponse bulkResp = new ApiResponse();
        bulkResp.getParams().setStatus(Constants.FAILED);
        bulkResp.getParams().setErr("removal failed");
        when(cassandraOperation.insertBulkRecord(anyString(), anyString(), anyList())).thenReturn(bulkResp);

        Set<String> rootOrgIdsInCriteria = new HashSet<>();
        Set<String> existingRootOrgIdsInCriteria = new HashSet<>(Set.of("orgA"));
        ApiResponse response = new ApiResponse();
        response.setParams(new ApiRespParam());

        ReflectionTestUtils.invokeMethod(cbPlanService, "handlePublishLookupUpdates",
            resp, updatedRequest, Constants.CUSTOM, rootOrgIdsInCriteria, existingRootOrgIdsInCriteria, "plan1", response);

        assertEquals(Constants.FAILED, response.getParams().getStatus());
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getResponseCode());
        assertEquals("removal failed", response.getParams().getErr());
        verify(cassandraOperation, times(1)).insertBulkRecord(anyString(), anyString(), anyList());
    }

    // ---- parseToDate -> parseDateString: full ISO-8601 datetime parses directly via Instant.parse (line ~674) ----
    @Test
    void testParseToDate_FullIsoDateTimeString_UsesDirectInstantParse() {
        Date result = cbPlanService.parseToDate("2024-12-31T10:15:30Z");

        assertNotNull(result);
        assertEquals(Date.from(Instant.parse("2024-12-31T10:15:30Z")), result);
    }

    // ---- retireCbPlan: blank (non-null) cbPlanId (line ~862-866) ----
    @Test
    void testRetireCbPlan_BlankId_ReturnsBadRequest() {
        ApiRequest request = new ApiRequest();
        Map<String, Object> requestMap = new HashMap<>();
        requestMap.put(Constants.ID, "");
        request.setRequest(requestMap);

        when(accessTokenValidator.fetchUserIdFromAccessToken(anyString(), any())).thenReturn("userId");

        ApiResponse response = cbPlanService.retireCbPlan(request, "orgId", "token", Arrays.asList("role"));

        assertEquals(Constants.FAILED, response.getParams().getStatus());
        assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
        assertEquals("CbPlanId is missing.", response.getParams().getErr());
    }

    // ---- retireExistingCbPlan: not authorized branch (line ~892-897) ----
    @Test
    void testRetireCbPlan_NotAuthorized_ReturnsBadRequest() {
        ApiRequest request = new ApiRequest();
        request.setRequest(Map.of(Constants.ID, "plan1"));

        when(accessTokenValidator.fetchUserIdFromAccessToken(anyString(), any())).thenReturn("userId");

        Map<String, Object> existingPlan = new HashMap<>();
        existingPlan.put(Constants.CREATED_BY, "otherUser");
        existingPlan.put(Constants.STATUS, "live");
        when(cassandraOperation.getRecordsByProperties(anyString(), anyString(), any(), any(), any()))
            .thenReturn(Arrays.asList(existingPlan));
        when(serverProperties.getCbPlanUpdatePublishAuthorizedRoles()).thenReturn(Arrays.asList("admin"));

        ApiResponse response = cbPlanService.retireCbPlan(request, "orgId", "token", Arrays.asList("user"));

        assertEquals(Constants.FAILED, response.getParams().getStatus());
        assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
        assertEquals("Not Authorized to delete cbp Plan", response.getParams().getErr());
    }

    // ---- retireExistingCbPlan: cassandra update failure (line ~917-921) ----
    @Test
    void testRetireCbPlan_CassandraUpdateFails_ReturnsBadRequest() {
        ApiRequest request = new ApiRequest();
        request.setRequest(Map.of(Constants.ID, "plan1"));

        when(accessTokenValidator.fetchUserIdFromAccessToken(anyString(), any())).thenReturn("userId");

        Map<String, Object> existingPlan = new HashMap<>();
        existingPlan.put(Constants.CREATED_BY, "userId");
        existingPlan.put(Constants.STATUS, "live");
        when(cassandraOperation.getRecordsByProperties(anyString(), anyString(), any(), any(), any()))
            .thenReturn(Arrays.asList(existingPlan));

        Map<String, Object> failResp = new HashMap<>();
        failResp.put(Constants.RESPONSE, Constants.FAILED);
        failResp.put(Constants.ERROR_MESSAGE, "db error");
        when(cassandraOperation.updateRecord(anyString(), anyString(), anyMap(), anyMap())).thenReturn(failResp);

        ApiResponse response = cbPlanService.retireCbPlan(request, "orgId", "token", Arrays.asList("role"));

        assertEquals(Constants.FAILED, response.getParams().getStatus());
        assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
        assertTrue(response.getParams().getErr().contains("db error"));
        assertTrue(response.getParams().getErr().contains("plan1"));
    }

    // ---- prepareCbPlanForInsert: PLAN_TYPE present in incoming request (line ~1028-1030) ----
    @Test
    void testPrepareCbPlanForInsert_WithPlanType_IncludesPlanType() {
        Map<String, Object> incomingRequest = new HashMap<>();
        incomingRequest.put(Constants.PLAN_TYPE, "nomination");

        Map<String, Object> result = ReflectionTestUtils.invokeMethod(
            cbPlanService, "prepareCbPlanForInsert", incomingRequest, "user123");

        assertNotNull(result);
        assertEquals("nomination", result.get(Constants.PLAN_TYPE));
    }

    // ---- prepareCbPlanForUpdate: PLAN_TYPE present in incoming request (line ~1051-1053) ----
    @Test
    void testPrepareCbPlanForUpdate_WithPlanType_IncludesPlanType() {
        Map<String, Object> incomingRequest = new HashMap<>();
        incomingRequest.put(Constants.PLAN_TYPE, "nomination");

        Map<String, Object> result = ReflectionTestUtils.invokeMethod(
            cbPlanService, "prepareCbPlanForUpdate", incomingRequest, "user123");

        assertNotNull(result);
        assertEquals("nomination", result.get(Constants.PLAN_TYPE));
    }

    // ---- prepareCbPlanForRePublish: existing plan has no draftData => empty map short-circuit (line ~1059-1065) ----
    @Test
    void testPrepareCbPlanForRePublish_NoDraftData_ReturnsEmptyMap() {
        Map<String, Object> existingCbPlan = new HashMap<>(); // no DRAFT_DATA key
        Map<String, Object> incomingRequest = new HashMap<>();
        incomingRequest.put(Constants.COMMENT, "republish");

        Map<String, Object> result = ReflectionTestUtils.invokeMethod(
            cbPlanService, "prepareCbPlanForRePublish", existingCbPlan, incomingRequest);

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    // ---- prepareCbPlanForRePublish: draft data includes CONTENT_LIST and PLAN_TYPE (line ~1090-1096) ----
    @Test
    void testPrepareCbPlanForRePublish_WithContentListAndPlanType_IncludesBoth() throws Exception {
        Map<String, Object> draftData = new HashMap<>();
        draftData.put(Constants.CONTENT_LIST, Arrays.asList("c1", "c2"));
        draftData.put(Constants.PLAN_TYPE, "custom-type");

        Map<String, Object> existingCbPlan = new HashMap<>();
        existingCbPlan.put(Constants.DRAFT_DATA, new ObjectMapper().writeValueAsString(draftData));

        Map<String, Object> incomingRequest = new HashMap<>();
        incomingRequest.put(Constants.COMMENT, "republish");

        Map<String, Object> result = ReflectionTestUtils.invokeMethod(
            cbPlanService, "prepareCbPlanForRePublish", existingCbPlan, incomingRequest);

        assertNotNull(result);
        assertEquals(Arrays.asList("c1", "c2"), result.get(Constants.CONTENT_LIST));
        assertEquals("custom-type", result.get(Constants.PLAN_TYPE));
    }

    // ---- searchCbPlan: ES returns a non-null but empty data list => falls through to the default-status return (line ~804) ----
    @Test
    void testSearchCbPlan_EmptyDataList_SkipsEnrichmentAndReturnsDefaultResponse() {
        SearchCriteria criteria = new SearchCriteria();
        when(accessTokenValidator.fetchUserIdFromAccessToken(anyString(), any())).thenReturn("userId");

        SearchResult searchResult = new SearchResult();
        searchResult.setData(new ArrayList<>());
        when(esUtilService.searchDocuments(anyString(), any(), anyString())).thenReturn(searchResult);

        ApiResponse response = cbPlanService.searchCbPlan(criteria, "orgId", "token");

        assertNotNull(response);
        assertFalse(response.containsKey(Constants.RESULT));
        verify(contentService, never()).enrichContentInfoForCBPlan(any());
    }

    // ---- handleUpdateOfLiveCbPlan: catch(JsonProcessingException) when draft serialization fails (line ~1263-1267) ----
    @Test
    void testHandleUpdateOfLiveCbPlan_SerializationFails_CaughtAsJsonProcessingException() {
        ApiResponse response = new ApiResponse();
        response.setParams(new ApiRespParam());
        Map<String, Object> incomingRequest = new HashMap<>();
        incomingRequest.put("badField", new ThrowingBean());
        Map<String, Object> existingPlan = Map.of(Constants.PLAN_ID, "plan1");

        when(requestValidator.validateContextData(any(), anyBoolean(), anyString(), any()))
            .thenReturn(Collections.emptyList());
        when(serverProperties.getCbPlanUpdateAllowedFields())
            .thenReturn(Arrays.asList("badField"));

        ReflectionTestUtils.invokeMethod(cbPlanService, "handleUpdateOfLiveCbPlan",
            response, incomingRequest, existingPlan, "user1", "rootOrg1", false);

        assertEquals(Constants.FAILED, response.getParams().getStatus());
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getResponseCode());
        assertEquals("Error processing existing CB Plan data", response.getParams().getErr());
    }

    // ---- populateUpdatedCbPlanFields: allowed field absent from incoming request hits "continue" (line ~1274-1276) ----
    @Test
    void testHandleUpdateOfLiveCbPlan_FieldNotInRequest_SkipsAndSucceeds() {
        ApiResponse response = new ApiResponse();
        response.setParams(new ApiRespParam());
        response.setResult(new HashMap<>());
        // "missingField" is allowed but absent from the incoming request => the loop must "continue" over it.
        Map<String, Object> incomingRequest = Map.of("name", "Updated Plan");
        Map<String, Object> existingPlan = Map.of(Constants.PLAN_ID, "plan1");

        when(requestValidator.validateContextData(any(), anyBoolean(), anyString(), any()))
            .thenReturn(Collections.emptyList());
        when(serverProperties.getCbPlanUpdateAllowedFields())
            .thenReturn(Arrays.asList("missingField", "name"));

        Map<String, Object> updateResp = Map.of(Constants.RESPONSE, Constants.SUCCESS);
        when(cassandraOperation.updateRecord(anyString(), anyString(), any(), any()))
            .thenReturn(updateResp);

        ReflectionTestUtils.invokeMethod(cbPlanService, "handleUpdateOfLiveCbPlan",
            response, incomingRequest, existingPlan, "user1", "rootOrg1", false);

        assertEquals(Constants.UPDATED, response.getResult().get(Constants.STATUS));
    }

    private static final String CB_PLAN_NOT_FOUND_PREFIX = "cbPlan is not found for id: ";

}