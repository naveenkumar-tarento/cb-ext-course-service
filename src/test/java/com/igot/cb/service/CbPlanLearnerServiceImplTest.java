package com.igot.cb.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.igot.cb.cache.CbPlanCacheMgr;
import com.igot.cb.cache.RedisCacheMgr;
import com.igot.cb.cassandra.CassandraOperation;
import com.igot.cb.model.ApiResponse;
import com.igot.cb.util.AccessTokenValidator;
import com.igot.cb.util.Constants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Method;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CbPlanLearnerServiceImplTest {

    @Mock(strictness = Mock.Strictness.LENIENT)
    private AccessTokenValidator accessTokenValidator;

    @Mock(strictness = Mock.Strictness.LENIENT)
    private CassandraOperation cassandraOperation;

    @Mock(strictness = Mock.Strictness.LENIENT)
    private ContentInfoServiceImpl contentService;

    @Mock(strictness = Mock.Strictness.LENIENT)
    private CbPlanCacheMgr cbPlanCacheMgr;

    @Mock(strictness = Mock.Strictness.LENIENT)
    private RedisCacheMgr redisCacheMgr;


    private CbPlanLearnerServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new CbPlanLearnerServiceImpl(accessTokenValidator, cassandraOperation, cbPlanCacheMgr,
                contentService, redisCacheMgr);
    }


    @Test
    void testGetCBPlanListForUser_Success() {
        when(accessTokenValidator.fetchUserIdFromAccessToken(anyString(), any())).thenReturn("user123");

        Map<String, Object> userData = createUserData();
        when(cassandraOperation.getRecordsByProperties(eq(Constants.KEYSPACE_SUNBIRD), eq(Constants.USER), any(), any(), any()))
                .thenReturn(Arrays.asList(userData));

        when(cassandraOperation.getRecordsByProperties(eq(Constants.KEYSPACE_SUNBIRD), eq(Constants.TABLE_CB_PLAN_V2_LOOKUP_BY_ALL_ORG), any(), any(), any()))
                .thenReturn(new ArrayList<>());

        when(cassandraOperation.getRecordsByProperties(eq(Constants.KEYSPACE_SUNBIRD), eq(Constants.TABLE_CB_PLAN_V2_LOOKUP_BY_ORG), any(), any(), any()))
                .thenReturn(new ArrayList<>());

        ApiResponse response = service.getCBPlanListForUser("org123", "token123", false);

        assertEquals(Constants.SUCCESS, response.getParams().getStatus());
    }

    @Test
    void testGetCBPlanListForUser_UserNotFound() {
        when(accessTokenValidator.fetchUserIdFromAccessToken(anyString(), any())).thenReturn("user123");
        when(cassandraOperation.getRecordsByProperties(eq(Constants.KEYSPACE_SUNBIRD), eq(Constants.USER), any(), any(), any()))
                .thenReturn(new ArrayList<>());

        ApiResponse response = service.getCBPlanListForUser("org123", "token123", false);

        assertEquals(Constants.FAILED, response.getParams().getStatus());
        assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
    }

    @Test
    void testGetCBPlanListForUser_WithActivePlans() {
        when(accessTokenValidator.fetchUserIdFromAccessToken(anyString(), any())).thenReturn("user123");

        Map<String, Object> userData = createUserData();
        when(cassandraOperation.getRecordsByProperties(eq(Constants.KEYSPACE_SUNBIRD), eq(Constants.USER), any(), any(), any()))
                .thenReturn(Arrays.asList(userData));

        // Mock Redis cache to return nothing (so CBPlan fetches from cacheMgr)
        when(redisCacheMgr.getFromCache(anyString())).thenReturn("");

        // Prepare an active plan
        Map<String, Object> activePlan = new HashMap<>();
        activePlan.put(Constants.PLAN_ID, "plan1");
        activePlan.put(Constants.STATUS, Constants.LIVE);
        activePlan.put(Constants.CONTENT_LIST, Arrays.asList("course1"));
        activePlan.put(Constants.END_DATE_REQUEST, Instant.now());

        // Use argument matchers for AtomicBoolean
        when(cbPlanCacheMgr.getCbPlanForAllAndOrgId(eq("org123"), any(AtomicBoolean.class)))
                .thenReturn(Arrays.asList(activePlan));

        // Mock content details
        Map<String, Object> contentDetails = new HashMap<>();
        contentDetails.put(Constants.IDENTIFIER, "course1");
        when(contentService.readContent("course1", null)).thenReturn(contentDetails);

        // Execute
        ApiResponse response = service.getCBPlanListForUser("org123", "token123", false);

        // Verify
        assertEquals(Constants.SUCCESS, response.getParams().getStatus());
        assertEquals(1, response.getResult().get(Constants.COUNT));
    }



    @Test
    void testGetCBPlanListForUser_PrivateMode() {
        Map<String, Object> userData = createUserData();
        when(cassandraOperation.getRecordsByProperties(eq(Constants.KEYSPACE_SUNBIRD), eq(Constants.USER), any(), any(), any()))
                .thenReturn(Arrays.asList(userData));

        when(cassandraOperation.getRecordsByProperties(eq(Constants.KEYSPACE_SUNBIRD), eq(Constants.TABLE_CB_PLAN_V2_LOOKUP_BY_ALL_ORG), any(), any(), any()))
                .thenReturn(new ArrayList<>());
        
        when(cassandraOperation.getRecordsByProperties(eq(Constants.KEYSPACE_SUNBIRD), eq(Constants.TABLE_CB_PLAN_V2_LOOKUP_BY_ORG), any(), any(), any()))
                .thenReturn(new ArrayList<>());

        ApiResponse response = service.getCBPlanListForUser("org123", "user123", true);

        assertEquals(Constants.SUCCESS, response.getParams().getStatus());
        verify(accessTokenValidator, never()).fetchUserIdFromAccessToken(anyString(), any());
    }

    @Test
    void testGetCBPlanListForUser_BlankUserId() {
        when(accessTokenValidator.fetchUserIdFromAccessToken(anyString(), any())).thenReturn("");

        ApiResponse response = service.getCBPlanListForUser("org123", "token123", false);

        assertNotNull(response);
    }

    @Test
    void testGetCBPlanListForUser_Exception() {
        when(accessTokenValidator.fetchUserIdFromAccessToken(anyString(), any())).thenThrow(new RuntimeException("Test exception"));

        ApiResponse response = service.getCBPlanListForUser("org123", "token123", false);

        assertEquals(Constants.FAILED, response.getParams().getStatus());
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getResponseCode());
    }

    @Test
    void testRemoveDuplicateCourses_WithLanguageMap() {
        List<Map<String, Object>> courseList = new ArrayList<>();
        
        Map<String, Object> course1 = new HashMap<>();
        course1.put(Constants.IDENTIFIER, "course1");
        Map<String, Object> langMap1 = new HashMap<>();
        Map<String, Object> langDetails = new HashMap<>();
        langDetails.put(Constants.ID, "lang1");
        langMap1.put("en", langDetails);
        course1.put(Constants.LANGUAGE_MAP_V1, langMap1);
        
        Map<String, Object> course2 = new HashMap<>();
        course2.put(Constants.IDENTIFIER, "course2");
        course2.put(Constants.LANGUAGE_MAP_V1, new HashMap<>());
        
        courseList.add(course1);
        courseList.add(course2);

        List<Map<String, Object>> result = service.removeDuplicateCourses(courseList);

        assertEquals(2, result.size());
    }

    @Test
    void testRemoveDuplicateCourses_EmptyList() {
        List<Map<String, Object>> courseList = new ArrayList<>();
        List<Map<String, Object>> result = service.removeDuplicateCourses(courseList);
        assertTrue(result.isEmpty());
    }

    @Test
    void testParseContextData_ValidJson() throws Exception {
        String jsonData = "{\"accessControl\":{\"userGroups\":[]}}";
        
        Method parseMethod = CbPlanLearnerServiceImpl.class.getDeclaredMethod("parseContextData", Object.class);
        parseMethod.setAccessible(true);
        
        Map<String, Object> result = (Map<String, Object>) parseMethod.invoke(service, jsonData);
        
        assertNotNull(result);
        assertTrue(result.containsKey("accessControl"));
    }

    @Test
    void testParseContextData_InvalidJson() throws Exception {
        String invalidJson = "invalid json";
        
        Method parseMethod = CbPlanLearnerServiceImpl.class.getDeclaredMethod("parseContextData", Object.class);
        parseMethod.setAccessible(true);
        
        Map<String, Object> result = (Map<String, Object>) parseMethod.invoke(service, invalidJson);
        
        assertTrue(result.isEmpty());
    }

    @Test
    void testParseContextData_NonStringInput() throws Exception {
        Integer nonStringInput = 123;
        
        Method parseMethod = CbPlanLearnerServiceImpl.class.getDeclaredMethod("parseContextData", Object.class);
        parseMethod.setAccessible(true);
        
        Map<String, Object> result = (Map<String, Object>) parseMethod.invoke(service, nonStringInput);
        
        assertTrue(result.isEmpty());
    }

    @Test
    @Disabled("This test is ignored due to optimization code changes")
    void testSetUserProfile_ValidData() throws Exception {
        Map<String, String> userProfile = new HashMap<>();
        Map<String, Object> userBasicProfile = createUserData();
        
        Method setUserProfileMethod = CbPlanLearnerServiceImpl.class.getDeclaredMethod("setUserProfile", Map.class, Map.class);
        setUserProfileMethod.setAccessible(true);
        
        setUserProfileMethod.invoke(service, userProfile, userBasicProfile);
        
        assertEquals("user123", userProfile.get(Constants.USER));
        assertEquals("org123", userProfile.get(Constants.ROOT_ORG_ID));
    }

    @Test
    void testSetUserProfile_EmptyProfile() throws Exception {
        Map<String, String> userProfile = new HashMap<>();
        Map<String, Object> emptyProfile = new HashMap<>();
        
        Method setUserProfileMethod = CbPlanLearnerServiceImpl.class.getDeclaredMethod("setUserProfile", Map.class, Map.class);
        setUserProfileMethod.setAccessible(true);
        
        setUserProfileMethod.invoke(service, userProfile, emptyProfile);
        
        assertTrue(userProfile.isEmpty());
    }

    @Test
    void testEvaluateContextAccessRule_ValidAccess() throws Exception {
        Map<String, Object> accessSettingMap = createAccessSettingMap();
        Map<String, String> userProfile = createUserProfile();
        
        Method evaluateMethod = CbPlanLearnerServiceImpl.class.getDeclaredMethod("evaluateContextAccessRule", Map.class, Map.class);
        evaluateMethod.setAccessible(true);
        
        boolean result = (boolean) evaluateMethod.invoke(service, accessSettingMap, userProfile);
        
        assertTrue(result);
    }

    @Test
    void testEvaluateContextAccessRule_NoAccess() throws Exception {
        Map<String, Object> accessSettingMap = createAccessSettingMapNoAccess();
        Map<String, String> userProfile = createUserProfile();
        
        Method evaluateMethod = CbPlanLearnerServiceImpl.class.getDeclaredMethod("evaluateContextAccessRule", Map.class, Map.class);
        evaluateMethod.setAccessible(true);
        
        boolean result = (boolean) evaluateMethod.invoke(service, accessSettingMap, userProfile);
        
        assertFalse(result);
    }

    @Test
    void testEvaluateContextAccessRule_EmptyMaps() throws Exception {
        Method evaluateMethod = CbPlanLearnerServiceImpl.class.getDeclaredMethod("evaluateContextAccessRule", Map.class, Map.class);
        evaluateMethod.setAccessible(true);
        
        boolean result = (boolean) evaluateMethod.invoke(service, new HashMap<>(), new HashMap<>());
        
        assertFalse(result);
    }

    private Map<String, Object> createUserData() {
        Map<String, Object> userData = new HashMap<>();
        userData.put(Constants.ID, "user123");
        userData.put("rootorgid", "org123");
        userData.put("profiledetails", "{\"professionalDetails\":[{\"designation\":\"Test\",\"group\":\"TestGroup\"}],\"profileStatus\":\"VERIFIED\",\"cadreDetails\":{\"cadreName\":\"TestCadre\",\"civilServiceName\":\"TestService\",\"cadreBatch\":2020}}");
        return userData;
    }

    private Map<String, String> createUserProfile() {
        Map<String, String> userProfile = new HashMap<>();
        userProfile.put("designation", "Test");
        userProfile.put("group", "TestGroup");
        return userProfile;
    }

    private Map<String, Object> createAccessSettingMap() {
        Map<String, Object> accessControl = new HashMap<>();
        List<Map<String, Object>> userGroups = new ArrayList<>();
        
        Map<String, Object> userGroup = new HashMap<>();
        userGroup.put(Constants.USER_GROUP_NAME, "TestGroup");
        
        List<Map<String, Object>> criteriaList = new ArrayList<>();
        Map<String, Object> criteria = new HashMap<>();
        criteria.put(Constants.CRITERIA_KEY, "designation");
        criteria.put(Constants.CRITERIA_VALUE, Arrays.asList("Test"));
        criteriaList.add(criteria);
        
        userGroup.put(Constants.USER_GROUP_CRITERIA_LIST, criteriaList);
        userGroups.add(userGroup);
        
        accessControl.put(Constants.USER_GROUPS, userGroups);
        
        Map<String, Object> accessSettingMap = new HashMap<>();
        accessSettingMap.put(Constants.ACCESS_CONTROL, accessControl);
        
        return accessSettingMap;
    }

    private Map<String, Object> createAccessSettingMapNoAccess() {
        Map<String, Object> accessControl = new HashMap<>();
        List<Map<String, Object>> userGroups = new ArrayList<>();
        
        Map<String, Object> userGroup = new HashMap<>();
        userGroup.put(Constants.USER_GROUP_NAME, "TestGroup");
        
        List<Map<String, Object>> criteriaList = new ArrayList<>();
        Map<String, Object> criteria = new HashMap<>();
        criteria.put(Constants.CRITERIA_KEY, "designation");
        criteria.put(Constants.CRITERIA_VALUE, Arrays.asList("NoMatch"));
        criteriaList.add(criteria);
        
        userGroup.put(Constants.USER_GROUP_CRITERIA_LIST, criteriaList);
        userGroups.add(userGroup);
        
        accessControl.put(Constants.USER_GROUPS, userGroups);
        
        Map<String, Object> accessSettingMap = new HashMap<>();
        accessSettingMap.put(Constants.ACCESS_CONTROL, accessControl);
        
        return accessSettingMap;
    }
    @Test
    void testGetExistingContextData_WithCustomFields() throws Exception {
        Map<String, String> userProfile = new HashMap<>();

        // Prepare custom field TEXT type
        Map<String, Object> customFieldText = new HashMap<>();
        customFieldText.put(Constants.TYPE, Constants.TEXT);
        customFieldText.put(Constants.ATTRIBUTE_NAME, "customText");
        customFieldText.put(Constants.VALUE, "CustomValue");

        // Prepare custom field MASTER_LIST type
        Map<String, Object> masterListValue = new HashMap<>();
        masterListValue.put(Constants.ATTRIBUTE_NAME, "skill");
        masterListValue.put(Constants.VALUE, "Java");

        Map<String, Object> customFieldMasterList = new HashMap<>();
        customFieldMasterList.put(Constants.TYPE, Constants.MASTER_LIST);
        customFieldMasterList.put(Constants.VALUES, List.of(masterListValue));

        Map<String, Object> orgAdditionalProperty = new HashMap<>();
        orgAdditionalProperty.put(Constants.ORGANISATION_ID, "org123");
        orgAdditionalProperty.put(Constants.CUSTOM_FIELD_VALUES, List.of(customFieldText, customFieldMasterList));

        String json = new ObjectMapper().writeValueAsString(List.of(orgAdditionalProperty));
        Map<String, Object> row = Map.of(Constants.CONTEXT_DATA_KEY, json);

        when(cassandraOperation.getRecordsByProperties(eq(Constants.KEYSPACE_SUNBIRD), eq(Constants.TABLE_USER_EXTENDED_PROFILE), any(), any(), any()))
                .thenReturn(List.of(row));

        // Call private method via reflection
        Method method = CbPlanLearnerServiceImpl.class.getDeclaredMethod("getExistingContextData", String.class, String.class, Map.class);
        method.setAccessible(true);
        method.invoke(service, "user123", "org123", userProfile);

        assertEquals("CustomValue", userProfile.get("customtext"));
        assertEquals("Java", userProfile.get("skill"));
    }


    @Test
    void testGetCBPlanCourseListForUser_CacheHitValidJson() throws Exception {
        String validJson = new ObjectMapper().writeValueAsString(Map.of("course1", "plan1"));
        when(redisCacheMgr.getFromCache(anyString())).thenReturn(validJson);

        ApiResponse response = service.getCBPlanCourseListForUser("user123", "org123");
        assertEquals(HttpStatus.OK, response.getResponseCode());
        assertTrue(((Map<?, ?>) response.getResult().get("contents")).containsKey("course1"));
    }

    @Test
    void testGetCBPlanCourseListForUser_InvalidJson() {
        when(redisCacheMgr.getFromCache(anyString())).thenReturn("{invalid}");
        ApiResponse response = service.getCBPlanCourseListForUser("user123", "org123");
        assertEquals(HttpStatus.OK, response.getResponseCode());
        assertEquals(Constants.SUCCESS, response.getParams().getStatus());
    }

    @Test
    void testGetCBPlanCourseListForUser_CacheMiss() {
        when(redisCacheMgr.getFromCache(anyString())).thenReturn(null);
        when(accessTokenValidator.fetchUserIdFromAccessToken(anyString(), any())).thenReturn("user123");
        ApiResponse response = service.getCBPlanCourseListForUser("user123", "org123");
        assertEquals(HttpStatus.OK, response.getResponseCode());
    }

    @Test
    void testProcessCoursesForCbPlan_WithSecureSettingsAndRC() throws Exception {
        Map<String, Object> contentDetails = new HashMap<>();
        contentDetails.put(Constants.SECURE_SETTINGS, Map.of(Constants.ORGANISATION, List.of("org123")));
        when(contentService.readContent(eq("course_rc"), any())).thenReturn(contentDetails);

        Map<String, String> userProfile = new HashMap<>();
        userProfile.put(Constants.PROFILE_STATUS_KEY, Constants.VERIFIED);

        Method method = CbPlanLearnerServiceImpl.class.getDeclaredMethod("processCoursesForCbPlan",
                List.class, String.class, Map.class, Map.class, String.class, Map.class);
        method.setAccessible(true);

        List<Map<String, Object>> result = (List<Map<String, Object>>) method.invoke(service,
                List.of("course_rc"), "org123", userProfile, new HashMap<>(), "2025-01-01", new HashMap<>());
        assertNotNull(result);
    }

    @Test
    void testEvaluateContextAccessRule_BooleanCriteria() throws Exception {
        Map<String, Object> criteria = Map.of(Constants.CRITERIA_KEY, Constants.CENTRAL_DEPUTATION,
                Constants.CRITERIA_VALUE, true);
        Map<String, Object> userGroup = Map.of(Constants.USER_GROUP_CRITERIA_LIST, List.of(criteria),
                Constants.USER_GROUP_NAME, "CentralGroup");
        Map<String, Object> accessControl = Map.of(Constants.USER_GROUPS, List.of(userGroup));
        Map<String, Object> accessSetting = Map.of(Constants.ACCESS_CONTROL, accessControl);

        Map<String, String> userProfile = Map.of(Constants.CENTRAL_DEPUTATION_LOWER_KEY, "true");

        Method method = CbPlanLearnerServiceImpl.class.getDeclaredMethod("evaluateContextAccessRule", Map.class, Map.class);
        method.setAccessible(true);
        boolean result = (boolean) method.invoke(service, accessSetting, userProfile);
        assertTrue(result);
    }


    @Test
    void testProcessActiveCbPlans_WithCacheEnabled() throws Exception {
        ReflectionTestUtils.setField(service, "mapper", new ObjectMapper());
        ReflectionTestUtils.setField(service, "redisCacheMgr", redisCacheMgr);

        List<Map<String, Object>> activePlans = List.of(Map.of(
                Constants.PLAN_ID, "plan123",
                Constants.CONTENT_LIST, List.of("course1"),
                Constants.END_DATE_REQUEST, Instant.now()
        ));

        Method m = CbPlanLearnerServiceImpl.class.getDeclaredMethod("processActiveCbPlans",
                List.class, String.class, String.class, Map.class, AtomicBoolean.class, List.class);
        m.setAccessible(true);

        AtomicBoolean isCacheEnabled = new AtomicBoolean(true);
        m.invoke(service, activePlans, "org123", "user123", new HashMap<>(), isCacheEnabled, new ArrayList<>());
        verify(redisCacheMgr, atLeastOnce()).putInCache(anyString(), anyString());
    }

    @Test
    void testGetExistingContextData_InvalidJson() throws Exception {
        when(cassandraOperation.getRecordsByProperties(any(), any(), any(), any(), any()))
                .thenReturn(List.of(Map.of(Constants.CONTEXT_DATA_KEY, "{invalid")));
        Method m = CbPlanLearnerServiceImpl.class.getDeclaredMethod("getExistingContextData",
                String.class, String.class, Map.class);
        m.setAccessible(true);
        Map<String, String> userProfile = new HashMap<>();
        m.invoke(service, "user123", "org123", userProfile);

        assertTrue(userProfile.isEmpty());
    }

    @Test
    void testRemoveDuplicateCourses_DuplicateIdentifiers() {
        Map<String, Object> langMap = Map.of("en", Map.of(Constants.ID, "dup1"));
        Map<String, Object> c1 = Map.of(Constants.IDENTIFIER, "dup1", Constants.LANGUAGE_MAP_V1, langMap);
        Map<String, Object> c2 = Map.of(Constants.IDENTIFIER, "dup1", Constants.LANGUAGE_MAP_V1, langMap);
        List<Map<String, Object>> result = service.removeDuplicateCourses(List.of(c1, c2));
        assertEquals(1, result.size());
    }

    @Test
    void testProcessActiveCbPlans_WithInvalidContextData() throws Exception {
        Map<String, Object> invalidPlan = new HashMap<>();
        invalidPlan.put(Constants.PLAN_ID, "plan123");
        invalidPlan.put(Constants.CONTENT_LIST, List.of("course1"));
        invalidPlan.put(Constants.CONTEXT_DATA_REQUEST, "{invalid-json}");
        invalidPlan.put(Constants.END_DATE_REQUEST, Instant.now());

        List<Map<String, Object>> activePlans = List.of(invalidPlan);
        AtomicBoolean isCacheEnabled = new AtomicBoolean(false);

        Method method = CbPlanLearnerServiceImpl.class.getDeclaredMethod("processActiveCbPlans",
                List.class, String.class, String.class, Map.class, AtomicBoolean.class, List.class);
        method.setAccessible(true);

        List<Map<String, Object>> resultMap = new ArrayList<>();
        method.invoke(service, activePlans, "org123", "user123", new HashMap<>(), isCacheEnabled, resultMap);

        // Invalid/unparseable contextData is treated as "no access restriction" (fail-open), so the plan
        // is still included rather than skipped.
        assertEquals(1, resultMap.size());
        assertEquals("plan123", resultMap.get(0).get(Constants.ID));
    }

    @Test
    void testSetUserProfile_InvalidRawValueType() throws Exception {
        Map<String, String> userProfile = new HashMap<>();
        Map<String, Object> userBasicProfile = Map.of(
                Constants.ID, "user123",
                Constants.ROOT_ORG_ID, "org123",
                Constants.PROFILE_DETAILS.toLowerCase(), 12345 // invalid type
        );

        Method method = CbPlanLearnerServiceImpl.class.getDeclaredMethod("setUserProfile", Map.class, Map.class);
        method.setAccessible(true);
        method.invoke(service, userProfile, userBasicProfile);

        assertTrue(userProfile.isEmpty() || userProfile.containsKey(Constants.USER));
    }

    @Test
    void testEvaluateContextAccessRule_NoUserGroups() throws Exception {
        Map<String, Object> accessSettingMap = Map.of(Constants.ACCESS_CONTROL, Map.of());
        Map<String, String> userProfile = Map.of("designation", "Test");

        Method method = CbPlanLearnerServiceImpl.class.getDeclaredMethod("evaluateContextAccessRule", Map.class, Map.class);
        method.setAccessible(true);
        boolean result = (boolean) method.invoke(service, accessSettingMap, userProfile);
        assertFalse(result);
    }

    @Test
    void testGetCBPlanCourseListForUser_BlankUserId() {
        ApiResponse response = service.getCBPlanCourseListForUser("", "org123");
        assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
        assertEquals(Constants.FAILED, response.getParams().getStatus());
    }

    @Test
    void testGetCBPlanListForUser_EmptyRedisString() {
        when(accessTokenValidator.fetchUserIdFromAccessToken(anyString(), any())).thenReturn("user123");
        Map<String, Object> userData = createUserData();
        when(cassandraOperation.getRecordsByProperties(eq(Constants.KEYSPACE_SUNBIRD), eq(Constants.USER), any(), any(), any()))
                .thenReturn(List.of(userData));
        when(redisCacheMgr.getFromCache(anyString())).thenReturn("\"\"");
        ApiResponse response = service.getCBPlanListForUser("org123", "token123", false);
        assertEquals(0, response.getResult().get(Constants.COUNT));
    }

    @Test
    void testGetCBPlanListForUser_WithCachedPlans() throws Exception {
        when(accessTokenValidator.fetchUserIdFromAccessToken(anyString(), any())).thenReturn("user123");
        Map<String, Object> userData = createUserData();
        when(cassandraOperation.getRecordsByProperties(eq(Constants.KEYSPACE_SUNBIRD), eq(Constants.USER), any(), any(), any()))
                .thenReturn(List.of(userData));

        String cachedPlansJson = new ObjectMapper().writeValueAsString(List.of("plan1"));
        when(redisCacheMgr.getFromCache(anyString())).thenReturn(cachedPlansJson);
        when(cbPlanCacheMgr.getCbPlansByPlanIdsInBatch(anyList()))
                .thenReturn(List.of(Map.of(Constants.PLAN_ID, "plan1", Constants.CONTENT_LIST, List.of("c1"))));

        Map<String, Object> contentDetails = Map.of(Constants.IDENTIFIER, "c1");
        when(contentService.readContent("c1", null)).thenReturn(new HashMap<>(contentDetails));

        ApiResponse response = service.getCBPlanListForUser("org123", "token123", false);
        assertEquals(HttpStatus.OK, response.getResponseCode());
    }

    // ---- Additional coverage tests ----

    @Test
    void testRemoveDuplicateCoursesAcrossPlans_AparDuplicateAcrossPlans_removesSecondOccurrence() {
        Map<String, Object> course1a = new HashMap<>();
        course1a.put(Constants.IDENTIFIER, "c1");
        List<Map<String, Object>> content1 = new ArrayList<>(List.of(course1a));
        Map<String, Object> plan1 = new HashMap<>();
        plan1.put(Constants.IS_APAR, true);
        plan1.put(Constants.CONTENT_LIST, content1);

        Map<String, Object> course1b = new HashMap<>();
        course1b.put(Constants.IDENTIFIER, "c1");
        List<Map<String, Object>> content2 = new ArrayList<>(List.of(course1b));
        Map<String, Object> plan2 = new HashMap<>();
        plan2.put(Constants.IS_APAR, true);
        plan2.put(Constants.CONTENT_LIST, content2);

        List<Map<String, Object>> resultMap = new ArrayList<>(List.of(plan1, plan2));
        service.removeDuplicateCoursesAcrossPlans(resultMap);

        assertEquals(1, content1.size());
        assertEquals(0, content2.size());
    }

    @Test
    void testRemoveDuplicateCoursesAcrossPlans_AparSeenThenNonAparSameId_removesFromNonApar() {
        Map<String, Object> course2a = new HashMap<>();
        course2a.put(Constants.IDENTIFIER, "c2");
        List<Map<String, Object>> content1 = new ArrayList<>(List.of(course2a));
        Map<String, Object> plan1 = new HashMap<>();
        plan1.put(Constants.IS_APAR, true);
        plan1.put(Constants.CONTENT_LIST, content1);

        Map<String, Object> course2b = new HashMap<>();
        course2b.put(Constants.IDENTIFIER, "c2");
        List<Map<String, Object>> content2 = new ArrayList<>(List.of(course2b));
        Map<String, Object> plan2 = new HashMap<>();
        plan2.put(Constants.IS_APAR, false);
        plan2.put(Constants.CONTENT_LIST, content2);

        List<Map<String, Object>> resultMap = new ArrayList<>(List.of(plan1, plan2));
        service.removeDuplicateCoursesAcrossPlans(resultMap);

        assertEquals(1, content1.size());
        assertEquals(0, content2.size());
    }

    @Test
    void testRemoveDuplicateCoursesAcrossPlans_NonAparDuplicateWithinNonApar_removesSecondOccurrence() {
        Map<String, Object> course3a = new HashMap<>();
        course3a.put(Constants.IDENTIFIER, "c3");
        List<Map<String, Object>> content1 = new ArrayList<>(List.of(course3a));
        Map<String, Object> plan1 = new HashMap<>();
        plan1.put(Constants.IS_APAR, false);
        plan1.put(Constants.CONTENT_LIST, content1);

        Map<String, Object> course3b = new HashMap<>();
        course3b.put(Constants.IDENTIFIER, "c3");
        List<Map<String, Object>> content2 = new ArrayList<>(List.of(course3b));
        Map<String, Object> plan2 = new HashMap<>();
        plan2.put(Constants.IS_APAR, false);
        plan2.put(Constants.CONTENT_LIST, content2);

        List<Map<String, Object>> resultMap = new ArrayList<>(List.of(plan1, plan2));
        service.removeDuplicateCoursesAcrossPlans(resultMap);

        assertEquals(1, content1.size());
        assertEquals(0, content2.size());
    }

    @Test
    void testRemoveDuplicateCoursesAcrossPlans_BlankIdentifier_courseRetained() {
        Map<String, Object> courseNoId = new HashMap<>();
        List<Map<String, Object>> content = new ArrayList<>(List.of(courseNoId));
        Map<String, Object> plan = new HashMap<>();
        plan.put(Constants.IS_APAR, false);
        plan.put(Constants.CONTENT_LIST, content);

        service.removeDuplicateCoursesAcrossPlans(new ArrayList<>(List.of(plan)));

        assertEquals(1, content.size());
    }

    @Test
    void testRemoveDuplicateCoursesAcrossPlans_MissingContentList_doesNotThrow() {
        Map<String, Object> plan = new HashMap<>();
        plan.put(Constants.IS_APAR, false);

        assertDoesNotThrow(() -> service.removeDuplicateCoursesAcrossPlans(new ArrayList<>(List.of(plan))));
    }

    @Test
    void testHandleRestrictedCourse_notVerified_clearsContentDetails() throws Exception {
        Map<String, Object> contentDetails = new HashMap<>();
        contentDetails.put(Constants.SECURE_SETTINGS, Map.of(Constants.ORGANISATION, List.of("org123")));
        Map<String, String> userProfile = new HashMap<>();
        userProfile.put(Constants.PROFILE_STATUS_LOWER_KEY, "UNVERIFIED");
        Map<String, Object> courseDetailsMap = new HashMap<>();

        Method method = CbPlanLearnerServiceImpl.class.getDeclaredMethod("handleRestrictedCourse",
                String.class, Map.class, String.class, Map.class, Map.class);
        method.setAccessible(true);
        method.invoke(service, "course_rc", contentDetails, "org123", userProfile, courseDetailsMap);

        assertFalse(courseDetailsMap.containsKey("course_rc"));
        assertTrue(contentDetails.isEmpty());
    }

    @Test
    void testHandleRestrictedCourse_verifiedButOrgNotMatching_clearsContentDetails() throws Exception {
        Map<String, Object> contentDetails = new HashMap<>();
        contentDetails.put(Constants.SECURE_SETTINGS, Map.of(Constants.ORGANISATION, List.of("otherOrg")));
        Map<String, String> userProfile = new HashMap<>();
        userProfile.put(Constants.PROFILE_STATUS_LOWER_KEY, Constants.VERIFIED);
        Map<String, Object> courseDetailsMap = new HashMap<>();

        Method method = CbPlanLearnerServiceImpl.class.getDeclaredMethod("handleRestrictedCourse",
                String.class, Map.class, String.class, Map.class, Map.class);
        method.setAccessible(true);
        method.invoke(service, "course_rc2", contentDetails, "org123", userProfile, courseDetailsMap);

        assertFalse(courseDetailsMap.containsKey("course_rc2"));
        assertTrue(contentDetails.isEmpty());
    }

    @Test
    void testFilterValidCourses_blankOrMissingIdentifier_isSkipped() throws Exception {
        Map<String, Object> validCourse = Map.of(Constants.IDENTIFIER, "c1");
        Map<String, Object> blankCourse = new HashMap<>();
        blankCourse.put(Constants.IDENTIFIER, "   ");
        Map<String, Object> missingCourse = new HashMap<>();

        List<Map<String, Object>> courseList = new ArrayList<>(List.of(validCourse, blankCourse, missingCourse));
        Map<String, Object> cbPlan = Map.of(Constants.PLAN_ID, "planX");

        Method method = CbPlanLearnerServiceImpl.class.getDeclaredMethod("filterValidCourses", List.class, Map.class);
        method.setAccessible(true);

        List<Map<String, Object>> result = (List<Map<String, Object>>) method.invoke(service, courseList, cbPlan);

        assertEquals(1, result.size());
        assertEquals("c1", result.get(0).get(Constants.IDENTIFIER));
    }

    @Test
    void testCacheProcessedPlans_emptyMappingsAndPlans_putsEmptyStrings() throws Exception {
        ReflectionTestUtils.setField(service, "mapper", new ObjectMapper());

        Method method = CbPlanLearnerServiceImpl.class.getDeclaredMethod("processActiveCbPlans",
                List.class, String.class, String.class, Map.class, AtomicBoolean.class, List.class);
        method.setAccessible(true);

        AtomicBoolean isCacheEnabled = new AtomicBoolean(true);
        method.invoke(service, new ArrayList<Map<String, Object>>(), "org123", "user123", new HashMap<>(),
                isCacheEnabled, new ArrayList<>());

        verify(redisCacheMgr).putInCache(
                Constants.CB_PLAN_REDIS_KEY_PREFIX + "user123" + Constants.BY_COURSE_SUFFIX, "");
        verify(redisCacheMgr).putInCache(
                Constants.CB_PLAN_REDIS_KEY_PREFIX + "user123" + Constants.BY_PLANS_SUFFIX, "");
    }

    @Test
    void testSetUserProfile_MissingProfileDetailsKey_returnsEarlyWithoutExtendedLookup() throws Exception {
        Map<String, String> userProfile = new HashMap<>();
        Map<String, Object> userBasicProfile = new HashMap<>();
        userBasicProfile.put(Constants.ID, "user1");
        userBasicProfile.put(Constants.ROOT_ORG_ID, "org1");

        Method method = CbPlanLearnerServiceImpl.class.getDeclaredMethod("setUserProfile", Map.class, Map.class);
        method.setAccessible(true);
        method.invoke(service, userProfile, userBasicProfile);

        assertEquals("user1", userProfile.get(Constants.USER));
        assertEquals("org1", userProfile.get(Constants.USER_ROOT_ORG_ID));
        assertFalse(userProfile.containsKey(Constants.DESIGNATION));
        verify(cassandraOperation, never()).getRecordsByProperties(
                eq(Constants.KEYSPACE_SUNBIRD), eq(Constants.TABLE_USER_EXTENDED_PROFILE), any(), any(), any());
    }

    @Test
    void testSetUserProfile_BlankProfileDetailsString_stillChecksExtendedContextData() throws Exception {
        Map<String, String> userProfile = new HashMap<>();
        Map<String, Object> userBasicProfile = new HashMap<>();
        userBasicProfile.put(Constants.ID, "user1");
        userBasicProfile.put(Constants.ROOT_ORG_ID, "org1");
        userBasicProfile.put(Constants.PROFILE_DETAILS.toLowerCase(), "");

        Method method = CbPlanLearnerServiceImpl.class.getDeclaredMethod("setUserProfile", Map.class, Map.class);
        method.setAccessible(true);
        method.invoke(service, userProfile, userBasicProfile);

        assertEquals("user1", userProfile.get(Constants.USER));
        assertFalse(userProfile.containsKey(Constants.DESIGNATION));
        verify(cassandraOperation, atLeastOnce()).getRecordsByProperties(
                eq(Constants.KEYSPACE_SUNBIRD), eq(Constants.TABLE_USER_EXTENDED_PROFILE), any(), any(), any());
    }

    @Test
    void testSetUserProfile_ProfileDetailsAlreadyAMap_usedDirectly() throws Exception {
        Map<String, String> userProfile = new HashMap<>();
        Map<String, Object> profileDetailsMap = new HashMap<>();
        profileDetailsMap.put(Constants.PROFILE_STATUS_KEY, Constants.VERIFIED);

        Map<String, Object> userBasicProfile = new HashMap<>();
        userBasicProfile.put(Constants.ID, "user1");
        userBasicProfile.put(Constants.ROOT_ORG_ID, "org1");
        userBasicProfile.put(Constants.PROFILE_DETAILS.toLowerCase(), profileDetailsMap);

        Method method = CbPlanLearnerServiceImpl.class.getDeclaredMethod("setUserProfile", Map.class, Map.class);
        method.setAccessible(true);
        method.invoke(service, userProfile, userBasicProfile);

        assertEquals(Constants.VERIFIED, userProfile.get(Constants.PROFILE_STATUS_LOWER_KEY));
    }

    @Test
    void testApplyProfileDetails_noProfessionalOrCadreDetails_setsDefaultsOnly() throws Exception {
        Map<String, Object> profileDetails = Map.of(Constants.PROFILE_STATUS_KEY, "VERIFIED");
        Map<String, String> userProfile = new HashMap<>();

        Method method = CbPlanLearnerServiceImpl.class.getDeclaredMethod("applyProfileDetails", Map.class, Map.class);
        method.setAccessible(true);
        method.invoke(service, userProfile, profileDetails);

        assertFalse(userProfile.containsKey(Constants.DESIGNATION));
        assertFalse(userProfile.containsKey(Constants.GROUP));
        assertEquals("VERIFIED", userProfile.get(Constants.PROFILE_STATUS_LOWER_KEY));
        assertFalse(userProfile.containsKey(Constants.CADRE));
        assertEquals("false", userProfile.get(Constants.CENTRAL_DEPUTATION_LOWER_KEY));
    }

    @Test
    void testApplyProfileDetails_cadreDetailsWithCentralDeputationTrue() throws Exception {
        Map<String, Object> cadreDetails = new HashMap<>();
        cadreDetails.put(Constants.CADRE_NAME, "CadreX");
        cadreDetails.put(Constants.CIVIL_SERVICE_NAME, "ServiceX");
        cadreDetails.put(Constants.CADRE_BATCH, 2021);
        cadreDetails.put(Constants.CENTRAL_DEPUTATION, true);

        Map<String, Object> profileDetails = new HashMap<>();
        profileDetails.put(Constants.CADRE_DETAILS, cadreDetails);

        Map<String, String> userProfile = new HashMap<>();

        Method method = CbPlanLearnerServiceImpl.class.getDeclaredMethod("applyProfileDetails", Map.class, Map.class);
        method.setAccessible(true);
        method.invoke(service, userProfile, profileDetails);

        assertEquals("CadreX", userProfile.get(Constants.CADRE));
        assertEquals("ServiceX", userProfile.get(Constants.SERVICE));
        assertEquals("2021", userProfile.get(Constants.BATCH));
        assertEquals("true", userProfile.get(Constants.CENTRAL_DEPUTATION_LOWER_KEY));
    }

    @Test
    void testEvaluateContextAccessRule_booleanCriteriaMismatch_returnsFalse() throws Exception {
        Map<String, Object> criteria = Map.of(Constants.CRITERIA_KEY, Constants.CENTRAL_DEPUTATION,
                Constants.CRITERIA_VALUE, true);
        Map<String, Object> userGroup = Map.of(Constants.USER_GROUP_CRITERIA_LIST, List.of(criteria),
                Constants.USER_GROUP_NAME, "CentralGroup");
        Map<String, Object> accessControl = Map.of(Constants.USER_GROUPS, List.of(userGroup));
        Map<String, Object> accessSetting = Map.of(Constants.ACCESS_CONTROL, accessControl);

        Map<String, String> userProfile = Map.of(Constants.CENTRAL_DEPUTATION_LOWER_KEY, "false");

        Method method = CbPlanLearnerServiceImpl.class.getDeclaredMethod("evaluateContextAccessRule", Map.class, Map.class);
        method.setAccessible(true);
        boolean result = (boolean) method.invoke(service, accessSetting, userProfile);
        assertFalse(result);
    }

    @Test
    void testEvaluateContextAccessRule_scalarCriteriaValueMatches() throws Exception {
        Map<String, Object> criteria = Map.of(Constants.CRITERIA_KEY, "group", Constants.CRITERIA_VALUE, "TestGroup");
        Map<String, Object> userGroup = Map.of(Constants.USER_GROUP_CRITERIA_LIST, List.of(criteria),
                Constants.USER_GROUP_NAME, "G1");
        Map<String, Object> accessControl = Map.of(Constants.USER_GROUPS, List.of(userGroup));
        Map<String, Object> accessSetting = Map.of(Constants.ACCESS_CONTROL, accessControl);

        Map<String, String> userProfile = Map.of("group", "TestGroup");

        Method method = CbPlanLearnerServiceImpl.class.getDeclaredMethod("evaluateContextAccessRule", Map.class, Map.class);
        method.setAccessible(true);
        boolean result = (boolean) method.invoke(service, accessSetting, userProfile);
        assertTrue(result);
    }

    @Test
    void testEvaluateContextAccessRule_emptyUserCriteriaValue_returnsFalse() throws Exception {
        Map<String, Object> criteria = Map.of(Constants.CRITERIA_KEY, "group",
                Constants.CRITERIA_VALUE, List.of("TestGroup"));
        Map<String, Object> userGroup = Map.of(Constants.USER_GROUP_CRITERIA_LIST, List.of(criteria),
                Constants.USER_GROUP_NAME, "G1");
        Map<String, Object> accessControl = Map.of(Constants.USER_GROUPS, List.of(userGroup));
        Map<String, Object> accessSetting = Map.of(Constants.ACCESS_CONTROL, accessControl);

        Map<String, String> userProfile = new HashMap<>();
        userProfile.put("group", "");

        Method method = CbPlanLearnerServiceImpl.class.getDeclaredMethod("evaluateContextAccessRule", Map.class, Map.class);
        method.setAccessible(true);
        boolean result = (boolean) method.invoke(service, accessSetting, userProfile);
        assertFalse(result);
    }

    @Test
    void testGetCBPlanListForUser_CachedEmptyArray_fallsThroughToOrgLookup() {
        when(accessTokenValidator.fetchUserIdFromAccessToken(anyString(), any())).thenReturn("user123");
        Map<String, Object> userData = createUserData();
        when(cassandraOperation.getRecordsByProperties(eq(Constants.KEYSPACE_SUNBIRD), eq(Constants.USER), any(), any(), any()))
                .thenReturn(List.of(userData));
        when(redisCacheMgr.getFromCache(anyString())).thenReturn("[]");
        when(cbPlanCacheMgr.getCbPlanForAllAndOrgId(eq("org123"), any(AtomicBoolean.class)))
                .thenReturn(new ArrayList<>());

        ApiResponse response = service.getCBPlanListForUser("org123", "token123", false);

        assertEquals(0, response.getResult().get(Constants.COUNT));
        verify(cbPlanCacheMgr).getCbPlanForAllAndOrgId(eq("org123"), any(AtomicBoolean.class));
    }

    // ---- Additional gap-closing coverage tests ----

    @Test
    void testProcessActiveCbPlans_AccessDenied_skipsPlan() throws Exception {
        String contextDataJson = new ObjectMapper().writeValueAsString(
                Map.of(Constants.ACCESS_CONTROL, Map.of(Constants.USER_GROUPS, List.of(
                        Map.of(Constants.USER_GROUP_NAME, "G1",
                                Constants.USER_GROUP_CRITERIA_LIST, List.of(
                                        Map.of(Constants.CRITERIA_KEY, "designation", Constants.CRITERIA_VALUE, "NoMatch")))))));

        Map<String, Object> plan = new HashMap<>();
        plan.put(Constants.PLAN_ID, "planDenied");
        plan.put(Constants.CONTENT_LIST, new ArrayList<String>());
        plan.put(Constants.CONTEXT_DATA_REQUEST, contextDataJson);
        plan.put(Constants.END_DATE_REQUEST, Instant.now());

        Map<String, String> userProfile = new HashMap<>();
        userProfile.put("designation", "Actual");

        Method method = CbPlanLearnerServiceImpl.class.getDeclaredMethod("processActiveCbPlans",
                List.class, String.class, String.class, Map.class, AtomicBoolean.class, List.class);
        method.setAccessible(true);

        List<Map<String, Object>> resultMap = new ArrayList<>();
        method.invoke(service, List.of(plan), "org123", "user123", userProfile, new AtomicBoolean(false), resultMap);

        assertTrue(resultMap.isEmpty());
    }

    @Test
    void testProcessActiveCbPlans_MalformedAccessControl_treatsAsNoAccess() throws Exception {
        String contextDataJson = "{\"accessControl\":\"notAMap\"}";

        Map<String, Object> plan = new HashMap<>();
        plan.put(Constants.PLAN_ID, "planMalformed");
        plan.put(Constants.CONTENT_LIST, new ArrayList<String>());
        plan.put(Constants.CONTEXT_DATA_REQUEST, contextDataJson);
        plan.put(Constants.END_DATE_REQUEST, Instant.now());

        Method method = CbPlanLearnerServiceImpl.class.getDeclaredMethod("processActiveCbPlans",
                List.class, String.class, String.class, Map.class, AtomicBoolean.class, List.class);
        method.setAccessible(true);

        List<Map<String, Object>> resultMap = new ArrayList<>();
        method.invoke(service, List.of(plan), "org123", "user123", new HashMap<>(), new AtomicBoolean(false), resultMap);

        // Casting a non-map accessControl value throws internally; hasAccessToCbPlan's catch
        // block treats this as "no access", so the plan is skipped rather than included.
        assertTrue(resultMap.isEmpty());
    }

    @Test
    void testProcessActiveCbPlans_CacheEnabledWithCourses_cachesNonEmptyMappings() throws Exception {
        ReflectionTestUtils.setField(service, "mapper", new ObjectMapper());

        Map<String, Object> plan = new HashMap<>();
        plan.put(Constants.PLAN_ID, "planWithCourse");
        plan.put(Constants.CONTENT_LIST, List.of("courseA"));
        plan.put(Constants.END_DATE_REQUEST, Instant.now());

        Map<String, Object> contentDetails = new HashMap<>();
        contentDetails.put(Constants.IDENTIFIER, "courseA");
        when(contentService.readContent("courseA", null)).thenReturn(contentDetails);

        Method method = CbPlanLearnerServiceImpl.class.getDeclaredMethod("processActiveCbPlans",
                List.class, String.class, String.class, Map.class, AtomicBoolean.class, List.class);
        method.setAccessible(true);

        AtomicBoolean isCacheEnabled = new AtomicBoolean(true);
        method.invoke(service, List.of(plan), "org123", "user123", new HashMap<>(), isCacheEnabled, new ArrayList<>());

        verify(redisCacheMgr).putInCache(
                eq(Constants.CB_PLAN_REDIS_KEY_PREFIX + "user123" + Constants.BY_COURSE_SUFFIX),
                argThat(json -> json != null && json.contains("courseA")));
    }

    @Test
    void testEvaluateContextAccessRule_SkipsGroupWithEmptyCriteria_thenMatchesNext() throws Exception {
        Map<String, Object> emptyCriteriaGroup = new HashMap<>();
        emptyCriteriaGroup.put(Constants.USER_GROUP_NAME, "EmptyGroup");
        emptyCriteriaGroup.put(Constants.USER_GROUP_CRITERIA_LIST, new ArrayList<>());

        Map<String, Object> matchingCriteria = Map.of(Constants.CRITERIA_KEY, "designation", Constants.CRITERIA_VALUE, "Test");
        Map<String, Object> matchingGroup = Map.of(Constants.USER_GROUP_NAME, "MatchGroup",
                Constants.USER_GROUP_CRITERIA_LIST, List.of(matchingCriteria));

        Map<String, Object> accessControl = Map.of(Constants.USER_GROUPS, List.of(emptyCriteriaGroup, matchingGroup));
        Map<String, Object> accessSetting = Map.of(Constants.ACCESS_CONTROL, accessControl);

        Map<String, String> userProfile = Map.of("designation", "Test");

        Method method = CbPlanLearnerServiceImpl.class.getDeclaredMethod("evaluateContextAccessRule", Map.class, Map.class);
        method.setAccessible(true);
        boolean result = (boolean) method.invoke(service, accessSetting, userProfile);
        assertTrue(result);
    }

    @Test
    void testHandleRestrictedCourse_verifiedAndOrgMatches_retainsContentDetails() throws Exception {
        Map<String, Object> contentDetails = new HashMap<>();
        contentDetails.put(Constants.SECURE_SETTINGS, Map.of(Constants.ORGANISATION, List.of("org123")));
        Map<String, String> userProfile = new HashMap<>();
        userProfile.put(Constants.PROFILE_STATUS_LOWER_KEY, Constants.VERIFIED);
        Map<String, Object> courseDetailsMap = new HashMap<>();

        Method method = CbPlanLearnerServiceImpl.class.getDeclaredMethod("handleRestrictedCourse",
                String.class, Map.class, String.class, Map.class, Map.class);
        method.setAccessible(true);
        method.invoke(service, "course_rc3", contentDetails, "org123", userProfile, courseDetailsMap);

        assertTrue(courseDetailsMap.containsKey("course_rc3"));
        assertFalse(contentDetails.isEmpty());
    }

    @Test
    void testGetExistingContextData_OrgIdMismatch_propertySkipped() throws Exception {
        Map<String, Object> orgAdditionalProperty = new HashMap<>();
        orgAdditionalProperty.put(Constants.ORGANISATION_ID, "otherOrg");
        Map<String, Object> customFieldText = new HashMap<>();
        customFieldText.put(Constants.TYPE, Constants.TEXT);
        customFieldText.put(Constants.ATTRIBUTE_NAME, "customText");
        customFieldText.put(Constants.VALUE, "ShouldNotAppear");
        orgAdditionalProperty.put(Constants.CUSTOM_FIELD_VALUES, List.of(customFieldText));

        String json = new ObjectMapper().writeValueAsString(List.of(orgAdditionalProperty));
        when(cassandraOperation.getRecordsByProperties(eq(Constants.KEYSPACE_SUNBIRD), eq(Constants.TABLE_USER_EXTENDED_PROFILE), any(), any(), any()))
                .thenReturn(List.of(Map.of(Constants.CONTEXT_DATA_KEY, json)));

        Map<String, String> userProfile = new HashMap<>();
        Method method = CbPlanLearnerServiceImpl.class.getDeclaredMethod("getExistingContextData", String.class, String.class, Map.class);
        method.setAccessible(true);
        method.invoke(service, "user123", "org123", userProfile);

        assertFalse(userProfile.containsKey("customtext"));
    }

    @Test
    void testGetCBPlanCourseListForUser_Exception_returnsFailedResponse() {
        when(redisCacheMgr.getFromCache(anyString())).thenThrow(new RuntimeException("redis down"));
        ApiResponse response = service.getCBPlanCourseListForUser("user123", "org123");
        assertEquals(Constants.FAILED, response.getParams().getStatus());
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getResponseCode());
    }

    @Test
    void testSetUserProfile_ProfileDetailsConvertibleObject_convertsSuccessfully() throws Exception {
        Map<String, String> userProfile = new HashMap<>();
        com.fasterxml.jackson.databind.node.ObjectNode node = new ObjectMapper().createObjectNode();
        node.put(Constants.PROFILE_STATUS_KEY, Constants.VERIFIED);

        Map<String, Object> userBasicProfile = new HashMap<>();
        userBasicProfile.put(Constants.ID, "user1");
        userBasicProfile.put(Constants.ROOT_ORG_ID, "org1");
        userBasicProfile.put(Constants.PROFILE_DETAILS.toLowerCase(), node);

        Method method = CbPlanLearnerServiceImpl.class.getDeclaredMethod("setUserProfile", Map.class, Map.class);
        method.setAccessible(true);
        method.invoke(service, userProfile, userBasicProfile);

        assertEquals(Constants.VERIFIED, userProfile.get(Constants.PROFILE_STATUS_LOWER_KEY));
    }

    @Test
    void testApplyProfileDetails_cadreDetailsWithoutBatchOrDeputationKeys() throws Exception {
        Map<String, Object> cadreDetails = new HashMap<>();
        cadreDetails.put(Constants.CADRE_NAME, "CadreY");
        cadreDetails.put(Constants.CIVIL_SERVICE_NAME, "ServiceY");

        Map<String, Object> profileDetails = new HashMap<>();
        profileDetails.put(Constants.CADRE_DETAILS, cadreDetails);

        Map<String, String> userProfile = new HashMap<>();

        Method method = CbPlanLearnerServiceImpl.class.getDeclaredMethod("applyProfileDetails", Map.class, Map.class);
        method.setAccessible(true);
        method.invoke(service, userProfile, profileDetails);

        assertEquals("CadreY", userProfile.get(Constants.CADRE));
        assertEquals("ServiceY", userProfile.get(Constants.SERVICE));
        assertFalse(userProfile.containsKey(Constants.BATCH));
        assertEquals("false", userProfile.get(Constants.CENTRAL_DEPUTATION_LOWER_KEY));
    }

}