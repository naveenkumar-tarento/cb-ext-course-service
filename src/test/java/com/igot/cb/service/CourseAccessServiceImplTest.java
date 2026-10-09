package com.igot.cb.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.*;
import java.lang.reflect.Field;
import java.time.LocalDate;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.igot.cb.cache.IdMapCacheMgr;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import com.igot.cb.cache.AccessSettingRuleCacheMgr;
import com.igot.cb.cache.RedisCacheMgr;
import com.igot.cb.model.ApiResponse;
import com.igot.cb.model.CachedAccessSettingRule;
import com.igot.cb.util.AccessTokenValidator;
import com.igot.cb.util.Constants;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class CourseAccessServiceImplTest {

    private CourseAccessServiceImpl courseAccessService;
    
    @Mock
    private AccessTokenValidator mockAccessTokenValidator;
    
    @Mock
    private UserAndOrgServiceImpl mockUserProfileService;
    
    @Mock
    private AccessSettingRuleCacheMgr mockAccessSettingRuleCacheMgr;

    @Mock
    private ContentInfoServiceImpl contentInfoService;

    @Mock
    private RedisCacheMgr redisCacheMgr;

    @Mock
    private CbPlanLearnerServiceImpl cbPlanLearnerServiceImpl;

    @Mock
    private OutboundRequestHandlerServiceImpl outboundRequestHandlerService;
    private final String authToken = "validToken";

    @Mock
    private IdMapCacheMgr idMapCacheMgr;

    @BeforeEach
    void setUp() throws Exception {
        courseAccessService = new CourseAccessServiceImpl(
            mockAccessTokenValidator,
            mockUserProfileService,
            mockAccessSettingRuleCacheMgr, contentInfoService, outboundRequestHandlerService, cbPlanLearnerServiceImpl, redisCacheMgr
        );

        // Inject contentReadFields using reflection
        Field contentReadFieldsField = CourseAccessServiceImpl.class.getDeclaredField("contentReadFields");
        contentReadFieldsField.setAccessible(true);
        contentReadFieldsField.set(courseAccessService, "identifier,name,description");

        // Default TTL so redisCacheMgr.putInCache(..., int) doesn't NPE on unboxing a null Integer
        Field accessCacheTtlSecodsField = CourseAccessServiceImpl.class.getDeclaredField("accessCacheTtlSecods");
        accessCacheTtlSecodsField.setAccessible(true);
        accessCacheTtlSecodsField.set(courseAccessService, 600);
    }

    @Test
    void testGetCoursesForUser_InvalidToken() {
        when(mockAccessTokenValidator.fetchUserIdFromAccessToken(eq("invalid"), any(ApiResponse.class))).thenReturn("");
        
        ApiResponse result = courseAccessService.getCoursesForUser(Map.of("key", "value"), "invalid");
        
        assertEquals(HttpStatus.UNAUTHORIZED, result.getResponseCode());
    }

    @Test
    void testGetCoursesForUser_EmptyRequest() {
        when(mockAccessTokenValidator.fetchUserIdFromAccessToken(eq("token"), any(ApiResponse.class))).thenReturn("user123");
        
        ApiResponse result = courseAccessService.getCoursesForUser(null, "token");
        
        assertEquals(HttpStatus.BAD_REQUEST, result.getResponseCode());
    }

    @Test
    void testGetCoursesForUser_NoRules() {
        when(mockAccessTokenValidator.fetchUserIdFromAccessToken(eq("token"), any(ApiResponse.class))).thenReturn("user123");
        when(redisCacheMgr.getFromCache(Constants.ACCESS_KEY + "user123")).thenReturn(null);
        when(mockUserProfileService.getUserProfile("user123")).thenReturn(Map.of("cadre", 1));
        when(mockAccessSettingRuleCacheMgr.getAccessSettingRules()).thenReturn(Collections.emptyList());
        
        ApiResponse result = courseAccessService.getCoursesForUser(Map.of("key", "value"), "token");
        
        assertEquals(HttpStatus.OK, result.getResponseCode());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> content = (List<Map<String, Object>>) result.getResult().get(Constants.CONTENT);
        assertEquals(0, content.size());
    }

    @Test
    void testEvaluateAccessSettingRule_EmptyMaps() {
        when(mockAccessTokenValidator.fetchUserIdFromAccessToken(eq("token"), any(ApiResponse.class))).thenReturn("user123");
        when(redisCacheMgr.getFromCache(Constants.ACCESS_KEY + "user123")).thenReturn(null);
        when(mockUserProfileService.getUserProfile("user123")).thenReturn(Map.of());
        
        CachedAccessSettingRule rule = new CachedAccessSettingRule("course123", "Course", "{\"accessControlId\":{}}", false);
        when(mockAccessSettingRuleCacheMgr.getAccessSettingRules()).thenReturn(List.of(rule));
        
        ApiResponse result = courseAccessService.getCoursesForUser(Map.of("key", "value"), "token");
        
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> content = (List<Map<String, Object>>) result.getResult().get(Constants.CONTENT);
        assertEquals(0, content.size());
    }

    @Test
    void testEvaluateAccessSettingRule_NoUserGroups() {
        when(mockAccessTokenValidator.fetchUserIdFromAccessToken(eq("token"), any(ApiResponse.class))).thenReturn("user123");
        when(redisCacheMgr.getFromCache(Constants.ACCESS_KEY + "user123")).thenReturn(null);
        when(mockUserProfileService.getUserProfile("user123")).thenReturn(Map.of("cadre", 1));
        
        CachedAccessSettingRule rule = new CachedAccessSettingRule("course123", "Course", "{\"accessControlId\":{\"userGroups\":[]}}", false);
        when(mockAccessSettingRuleCacheMgr.getAccessSettingRules()).thenReturn(List.of(rule));
        
        ApiResponse result = courseAccessService.getCoursesForUser(Map.of("key", "value"), "token");
        
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> content = (List<Map<String, Object>>) result.getResult().get(Constants.CONTENT);
        assertEquals(0, content.size());
    }

    @Test
    void testEvaluateAccessSettingRule_NoCriteria() {
        when(mockAccessTokenValidator.fetchUserIdFromAccessToken(eq("token"), any(ApiResponse.class))).thenReturn("user123");
        when(redisCacheMgr.getFromCache(Constants.ACCESS_KEY + "user123")).thenReturn(null);
        when(mockUserProfileService.getUserProfile("user123")).thenReturn(Map.of("cadre", 1));
        
        CachedAccessSettingRule rule = new CachedAccessSettingRule("course123", "Course", "{\"accessControlId\":{\"userGroups\":[{\"userGroupId\":\"group1\",\"userGroupCriteriaList\":[]}]}}", false);
        when(mockAccessSettingRuleCacheMgr.getAccessSettingRules()).thenReturn(List.of(rule));
        
        ApiResponse result = courseAccessService.getCoursesForUser(Map.of("key", "value"), "token");
        
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> content = (List<Map<String, Object>>) result.getResult().get(Constants.CONTENT);
        assertEquals(0, content.size());
    }

    @Test
    void testEvaluateAccessSettingRule_UserCriteriaMissing() {
        when(mockAccessTokenValidator.fetchUserIdFromAccessToken(eq("token"), any(ApiResponse.class))).thenReturn("user123");
        when(redisCacheMgr.getFromCache(Constants.ACCESS_KEY + "user123")).thenReturn(null);
        when(mockUserProfileService.getUserProfile("user123")).thenReturn(Map.of());
        
        // Create a BitSet for criteria value
        BitSet criteriaValue = new BitSet();
        criteriaValue.set(1); // Set bit 1 to true
        
        // Create the rule with proper BitSet in contextData
        Map<String, Object> contextData = new HashMap<>();
        Map<String, Object> accessControlId = new HashMap<>();
        List<Map<String, Object>> userGroups = new ArrayList<>();
        Map<String, Object> userGroup = new HashMap<>();
        userGroup.put("userGroupId", "group1");
        List<Map<String, Object>> criteriaList = new ArrayList<>();
        Map<String, Object> criteria = new HashMap<>();
        criteria.put("criteriaKey", "cadre");
        criteria.put("criteriaValue", criteriaValue);
        criteriaList.add(criteria);
        userGroup.put("userGroupCriteriaList", criteriaList);
        userGroups.add(userGroup);
        accessControlId.put("userGroups", userGroups);
        contextData.put("accessControlId", accessControlId);
        
        CachedAccessSettingRule rule = mock(CachedAccessSettingRule.class);
        when(rule.getContextData()).thenReturn(contextData);
        
        when(mockAccessSettingRuleCacheMgr.getAccessSettingRules()).thenReturn(List.of(rule));
        
        ApiResponse result = courseAccessService.getCoursesForUser(Map.of("key", "value"), "token");
        
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> content = (List<Map<String, Object>>) result.getResult().get(Constants.CONTENT);
        assertEquals(0, content.size());
    }

    @Test
    void testEvaluateAccessSettingRule_UserCriteriaMatches() {
        when(mockAccessTokenValidator.fetchUserIdFromAccessToken(eq("token"), any(ApiResponse.class))).thenReturn("user123");
        when(redisCacheMgr.getFromCache(Constants.ACCESS_KEY + "user123")).thenReturn(null);
        when(mockUserProfileService.getUserProfile("user123")).thenReturn(Map.of("cadre", 1));
        
        // Create a BitSet for criteria value
        BitSet criteriaValue = new BitSet();
        criteriaValue.set(1); // Set bit 1 to true to match user's cadre value
        
        // Create the rule with proper BitSet in contextData
        Map<String, Object> contextData = new HashMap<>();
        Map<String, Object> accessControlId = new HashMap<>();
        List<Map<String, Object>> userGroups = new ArrayList<>();
        Map<String, Object> userGroup = new HashMap<>();
        userGroup.put("userGroupId", "group1");
        List<Map<String, Object>> criteriaList = new ArrayList<>();
        Map<String, Object> criteria = new HashMap<>();
        criteria.put("criteriaKey", "cadre");
        criteria.put("criteriaValue", criteriaValue);
        criteriaList.add(criteria);
        userGroup.put("userGroupCriteriaList", criteriaList);
        userGroups.add(userGroup);
        accessControlId.put("userGroups", userGroups);
        contextData.put("accessControlId", accessControlId);
        
        CachedAccessSettingRule rule = mock(CachedAccessSettingRule.class);
        when(rule.getContextId()).thenReturn("course123");
        when(rule.getContextData()).thenReturn(contextData);
        
        when(mockAccessSettingRuleCacheMgr.getAccessSettingRules()).thenReturn(List.of(rule));
        
        // Mock content service to return course details
        Map<String, Object> courseDetails = Map.of(
            "identifier", "course123",
            "name", "Test Course",
            "description", "Test Description"
        );
        when(contentInfoService.readContent(eq("course123"), anyList()))
                .thenReturn(courseDetails);

        ApiResponse result = courseAccessService.getCoursesForUser(Map.of("key", "value"), "token");
        
        assertEquals(HttpStatus.OK, result.getResponseCode());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> content = (List<Map<String, Object>>) result.getResult().get(Constants.CONTENT);
        assertEquals(1, content.size());
        assertEquals("course123", content.get(0).get("identifier"));
    }


    @Test
    void testGetCoursesForUser_1() {
        // Arrange
        Map<String, Object> request = Map.of("key", "value");

        when(mockAccessTokenValidator.fetchUserIdFromAccessToken(eq(authToken), any(ApiResponse.class)))
                .thenReturn("user123");
        when(redisCacheMgr.getFromCache(Constants.ACCESS_KEY + "user123")).thenReturn("No records found for this user");

        ObjectMapper mapperSpy = Mockito.spy(new ObjectMapper());
        ReflectionTestUtils.setField(courseAccessService, "mapper", mapperSpy);

        // Act & Assert
        assertDoesNotThrow(() ->
                courseAccessService.getCoursesForUser(request, authToken));
    }

    @Test
    void testGetCoursesForUser_shouldHandleExceptionFromRetrieveUserCourses() {
        // Arrange
        Map<String, Object> request = Map.of("key", "value");

        when(mockAccessTokenValidator.fetchUserIdFromAccessToken(eq(authToken), any(ApiResponse.class)))
                .thenReturn("user123");
        when(redisCacheMgr.getFromCache(Constants.ACCESS_KEY + "user123")).thenReturn(null);
        when(mockUserProfileService.getUserProfile("user123")).thenReturn(Map.of("k", 1));

        when(mockAccessSettingRuleCacheMgr.getAccessSettingRules())
                .thenThrow(new RuntimeException("Cache error"));

        // Act
        ApiResponse response = courseAccessService.getCoursesForUser(request, authToken);

        // Assert
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getResponseCode());
    }

    @SuppressWarnings("unchecked")
    @Test
    void testGetCoursesForUser_CacheHasValidJson() {
        when(mockAccessTokenValidator.fetchUserIdFromAccessToken(eq(authToken), any(ApiResponse.class)))
                .thenReturn("user1");

        String json = "[{\"identifier\":\"c1\"}]";
        when(redisCacheMgr.getFromCache(Constants.ACCESS_KEY + "user1")).thenReturn(json);

        ObjectMapper mapperSpy = spy(new ObjectMapper());
        ReflectionTestUtils.setField(courseAccessService, "mapper", mapperSpy);

        ApiResponse response = courseAccessService.getCoursesForUser(Map.of("x", "y"), authToken);

        List<Map<String, Object>> content =
                (List<Map<String, Object>>) response.getResult().get(Constants.CONTENT);

        assertEquals(1, content.size());
        assertEquals("c1", content.get(0).get("identifier"));
    }

    @SuppressWarnings("unchecked")
    @Test
    void testGetCoursesForUser_CacheNoRecordsFound() {
        when(mockAccessTokenValidator.fetchUserIdFromAccessToken(eq(authToken), any(ApiResponse.class)))
                .thenReturn("user1");

        when(redisCacheMgr.getFromCache(Constants.ACCESS_KEY + "user1"))
                .thenReturn(Constants.NO_RECORDS_FOUND);

        ApiResponse response = courseAccessService.getCoursesForUser(Map.of("x", "y"), authToken);

        List<Map<String, Object>> content =
                (List<Map<String, Object>>) response.getResult().get(Constants.CONTENT);

        assertTrue(content.isEmpty());
    }

    @Test
    void testGetCoursesForUser_CacheInvalidJson_ThrowsException() throws Exception {
        when(mockAccessTokenValidator.fetchUserIdFromAccessToken(eq(authToken), any(ApiResponse.class)))
                .thenReturn("u1");

        when(redisCacheMgr.getFromCache(Constants.ACCESS_KEY + "u1")).thenReturn("invalid JSON");

        ObjectMapper mapperSpy = spy(new ObjectMapper());
        doThrow(new JsonProcessingException("error") {})
                .when(mapperSpy)
                .readValue(anyString(), (TypeReference<?>) any());
        ReflectionTestUtils.setField(courseAccessService, "mapper", mapperSpy);

        Map<String, Object> request = Map.of("x", "y");
        assertThrows(RuntimeException.class, () ->
                courseAccessService.getCoursesForUser(request, authToken));
    }

    @SuppressWarnings("unchecked")
    @Test
    void testGetCoursesForUser_RetrieveUserCoursesReturnsFalse() {
        when(mockAccessTokenValidator.fetchUserIdFromAccessToken(eq(authToken), any(ApiResponse.class)))
                .thenReturn("u1");

        when(redisCacheMgr.getFromCache(Constants.ACCESS_KEY + "u1")).thenReturn(null);
        when(mockUserProfileService.getUserProfile("u1")).thenReturn(Map.of("k", 1));
        when(mockAccessSettingRuleCacheMgr.getAccessSettingRules()).thenReturn(Collections.emptyList());

        ApiResponse response = courseAccessService.getCoursesForUser(Map.of("x", "y"), authToken);

        List<Map<String, Object>> content =
                (List<Map<String, Object>>) response.getResult().get(Constants.CONTENT);

        assertTrue(content.isEmpty());
    }

    @Test
    void testGetCoursesForUser_ExceptionInRedisPut() {
        when(mockAccessTokenValidator.fetchUserIdFromAccessToken(eq(authToken), any(ApiResponse.class)))
                .thenReturn("u1");
        when(redisCacheMgr.getFromCache(Constants.ACCESS_KEY + "u1")).thenReturn(null);
        when(mockUserProfileService.getUserProfile("u1")).thenReturn(Map.of("cadre", 1));
        BitSet bit = new BitSet();
        bit.set(1);
        Map<String, Object> contextData = new HashMap<>();
        Map<String, Object> ac = new HashMap<>();
        Map<String, Object> ug = new HashMap<>();
        ug.put("userGroupCriteriaList", List.of(Map.of("criteriaKey", "cadre", "criteriaValue", bit)));
        ac.put("userGroups", List.of(ug));
        contextData.put("accessControlId", ac);
        CachedAccessSettingRule rule = mock(CachedAccessSettingRule.class);
        when(rule.getContextId()).thenReturn("c1");
        when(rule.getContextData()).thenReturn(contextData);
        when(contentInfoService.readContent(eq("c1"), anyList()))
                .thenReturn(Map.of("identifier", "c1"));
        when(mockAccessSettingRuleCacheMgr.getAccessSettingRules()).thenReturn(List.of(rule));
        ObjectMapper mapperSpy = spy(new ObjectMapper());
        ReflectionTestUtils.setField(courseAccessService, "mapper", mapperSpy);

        doThrow(new RuntimeException("cache fail"))
                .when(redisCacheMgr)
                .putInCache(anyString(), anyString());
        ApiResponse response = courseAccessService.getCoursesForUser(Map.of("x", "y"), authToken);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getResponseCode());
    }

    @Test
    void testFetchFromRedisCache_InvalidJson() throws Exception {
        String key = "k1";
        when(redisCacheMgr.getFromCache(key)).thenReturn("invalid");

        ObjectMapper mapperSpy = spy(new ObjectMapper());
        ReflectionTestUtils.setField(courseAccessService, "mapper", mapperSpy);
        doThrow(new JsonProcessingException("error") {})
                .when(mapperSpy)
                .readValue(anyString(), (TypeReference<?>) any());
        List<Map<String, Object>> result =
                ReflectionTestUtils.invokeMethod(courseAccessService, "fetchFromRedisCache", key);

        assertTrue(result.isEmpty());
    }

    @Test
    void testGetAssignedCoursesForUser_EmptyRequest() {
        ApiResponse response = courseAccessService.getAssignedCoursesForUser(null, authToken);
        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getResponseCode());
        assertInstanceOf(Map.class, response.getResult());
        Map<String, Object> result = (Map<String, Object>) response.getResult();
        assertTrue(result.isEmpty() || result.containsKey("courses"));
    }



    @Test
    void testGetAssignedCoursesForUser_MissingCourseCategory() {
        when(mockAccessTokenValidator.fetchUserIdFromAccessToken(eq(authToken), any(ApiResponse.class)))
                .thenReturn("u1");

        ApiResponse response = courseAccessService.getAssignedCoursesForUser(Map.of(), authToken);

        assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
    }

    @Test
    void testGetCoursesFromCacheOrService_NoIdentifiers() {
        Map<String, Object> result = Map.of(
                Constants.RESULT, Map.of(Constants.CONTENT, List.of())
        );

        when(outboundRequestHandlerService.fetchResultUsingPost(anyString(), anyMap(), isNull()))
                .thenReturn(result);

        List<String> list = ReflectionTestUtils.invokeMethod(
                courseAccessService, "getCoursesFromCacheOrService", "category1");

        assertNotNull(list);
        assertTrue(list.isEmpty());
    }

    @Test
    void testGetCoursesForUser_InvalidToken_ShouldReturnBadRequest() {
        when(mockAccessTokenValidator.fetchUserIdFromAccessToken(eq(authToken), any(ApiResponse.class)))
                .thenReturn(null);
        ApiResponse response = courseAccessService.getCoursesForUser(Map.of(), authToken);
        assertEquals(HttpStatus.UNAUTHORIZED, response.getResponseCode());
    }

    @Test
    void testGetCoursesForUser_NoUserProfile() {
        when(mockAccessTokenValidator.fetchUserIdFromAccessToken(eq(authToken), any(ApiResponse.class)))
                .thenReturn("u1");
        when(redisCacheMgr.getFromCache(Constants.ACCESS_KEY + "u1")).thenReturn(null);
        when(mockUserProfileService.getUserProfile("u1")).thenReturn(Map.of());
        ApiResponse response = courseAccessService.getCoursesForUser(Map.of("dummy", "value"), authToken);
        assertEquals(HttpStatus.OK, response.getResponseCode());
        assertTrue(((List<?>) response.getResult().get(Constants.CONTENT)).isEmpty());
    }

    @Test
    void testGetCoursesForUser_NoAccessRules() {
        when(mockAccessTokenValidator.fetchUserIdFromAccessToken(eq(authToken), any(ApiResponse.class)))
                .thenReturn("u1");
        when(redisCacheMgr.getFromCache(anyString())).thenReturn(null);
        when(mockUserProfileService.getUserProfile("u1"))
                .thenReturn(Map.of("cadre", 1));
        when(mockAccessSettingRuleCacheMgr.getAccessSettingRules())
                .thenReturn(List.of());
        ApiResponse response = courseAccessService.getCoursesForUser(
                Map.of("dummy", "value"),  // must be NON-EMPTY
                authToken
        );
        assertEquals(HttpStatus.OK, response.getResponseCode());
    }

    @Test
    void testGetAssignedCoursesForUser_ValidFlow() {
        String userId = "u1";
        when(mockAccessTokenValidator.fetchUserIdFromAccessToken(eq(authToken), any(ApiResponse.class)))
                .thenReturn(userId);
        Map<String, Object> request = Map.of(Constants.COURSE_CATEGORY, "cat1");
        // Simulate Redis cache miss for the user-course assignment
        when(redisCacheMgr.getFromCache(startsWith(Constants.ACCESS_KEY + "_cat1_" + userId))).thenReturn(null);
        // Simulate Redis cache miss for course category list (fix key)
        when(redisCacheMgr.getFromCache("access_settings_enabled_cat1")).thenReturn(null);
        when(outboundRequestHandlerService.fetchResultUsingPost(anyString(), anyMap(), isNull()))
                .thenReturn(Map.of(Constants.RESULT,
                        Map.of(Constants.CONTENT,
                                List.of(Map.of(Constants.IDENTIFIER, "C1")))));
        ReflectionTestUtils.setField(courseAccessService, "cacheTtlMs", 99999999L);
        ReflectionTestUtils.setField(courseAccessService, "accessCacheTtlSecods", 600); // Set TTL to avoid NPE
        BitSet bit = new BitSet();
        bit.set(1);
        Map<String,Object> accessControl = Map.of(
                Constants.USER_GROUPS,
                List.of(
                        Map.of(
                                Constants.USER_GROUP_ID, "G1",
                                Constants.USER_GROUP_CRITERIA_LIST,
                                List.of(
                                        Map.of(Constants.CRITERIA_KEY, "cadre",
                                                Constants.CRITERIA_VALUE, bit)
                                )
                        )
                )
        );
        CachedAccessSettingRule rule = mock(CachedAccessSettingRule.class);
        when(rule.getContextId()).thenReturn("C1");
        when(rule.getContextData()).thenReturn(
                Map.of(Constants.ACCESS_CONTROL_ID, accessControl)
        );
        when(mockAccessSettingRuleCacheMgr.getOrLoadAccessSettingRule(anyString(), anyString()))
                .thenReturn(rule);
        when(mockUserProfileService.getUserProfile(userId))
                .thenReturn(Map.of("cadre", 1));
        when(contentInfoService.readContent(eq("C1"), anyList()))
                .thenReturn(Map.of("identifier", "C1"));
        ApiResponse response = courseAccessService.getAssignedCoursesForUser(request, authToken);
        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getResponseCode());
        assertEquals(1,
                ((List<?>)response.getResult().get(Constants.CONTENT)).size()
        );
    }


    @Test
    void testGetAssignedCoursesForUser_InvalidToken() {
        when(mockAccessTokenValidator.fetchUserIdFromAccessToken(eq(authToken), any(ApiResponse.class)))
                .thenReturn(null);
        ApiResponse response = courseAccessService.getAssignedCoursesForUser(
                Map.of(Constants.COURSE_CATEGORY, "c1"), authToken);
        assertEquals(HttpStatus.OK, response.getResponseCode()); // default response
    }

    @Test
    void testGetAssignedCoursesForUser_CacheHit() {
        String userId = "u1";
        when(mockAccessTokenValidator.fetchUserIdFromAccessToken(eq(authToken), any(ApiResponse.class)))
                .thenReturn(userId);
        ObjectMapper spyMapper = spy(new ObjectMapper());
        ReflectionTestUtils.setField(courseAccessService, "mapper", spyMapper);
        when(redisCacheMgr.getFromCache(anyString()))
                .thenReturn("[{\"id\":\"C1\"}]");
        ApiResponse response = courseAccessService.getAssignedCoursesForUser(
                Map.of(Constants.COURSE_CATEGORY,"CAT"), authToken);
        assertEquals("C1", ((Map<?,?>)((List<?>)response.getResult().get(Constants.CONTENT)).get(0)).get("id"));
    }

    @Test
    void testFetchAccessSettingsEnabledCoursesForCategory() {
        Map<String,Object> mockResponse = Map.of("RES","OK");
        when(outboundRequestHandlerService.fetchResultUsingPost(anyString(), anyMap(), isNull()))
                .thenReturn(mockResponse);
        Map<String,Object> result = courseAccessService.fetchAccessSettingsEnabledCoursesForCategory("cat");
        assertEquals("OK", result.get("RES"));
    }

    @Test
    void testGetCoursesFromCacheOrService_CacheHit() {
        // Simulate Redis cache hit for the course category
        when(redisCacheMgr.getFromCache("access_settings_enabled_cat"))
            .thenReturn("[\"C1\",\"C2\"]");
        List<String> result = ReflectionTestUtils.invokeMethod(courseAccessService,
                "getCoursesFromCacheOrService", "cat");
        assertNotNull(result);
        assertEquals(2, result.size());
        assertTrue(result.contains("C1"));
        assertTrue(result.contains("C2"));
    }
    @Test
    void testGetCoursesFromCacheOrService_Exception() {
        ReflectionTestUtils.setField(courseAccessService, "courseCategoryCache",
                new HashMap<>());
        when(outboundRequestHandlerService.fetchResultUsingPost(anyString(), anyMap(), isNull()))
                .thenThrow(new RuntimeException("ERR"));
        List<String> result = ReflectionTestUtils.invokeMethod(courseAccessService,
                "getCoursesFromCacheOrService", "cat");
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void testEvaluateAccessSettingRule_FullMatchTrue() {
        BitSet bs = new BitSet();
        bs.set(1);
        Map<String,Object> group = Map.of(
                Constants.USER_GROUP_ID, "g1",
                Constants.USER_GROUP_CRITERIA_LIST,
                List.of(Map.of(Constants.CRITERIA_KEY,"cadre", Constants.CRITERIA_VALUE,bs))
        );
        Map<String,Object> access = Map.of(Constants.USER_GROUPS, List.of(group));
        boolean result = Boolean.TRUE.equals(ReflectionTestUtils.invokeMethod(
                courseAccessService,
                "evaluateAccessSettingRule",
                access,
                Map.of("cadre", 1)
        ));
        assertTrue(result);
    }
    @Test
    void testEvaluateAccessSettingRule_False() {
        BitSet bs = new BitSet();
        bs.set(1);
        Map<String,Object> group = Map.of(
                Constants.USER_GROUP_ID, "g1",
                Constants.USER_GROUP_CRITERIA_LIST,
                List.of(Map.of(Constants.CRITERIA_KEY,"grade", Constants.CRITERIA_VALUE,bs))
        );
        Map<String,Object> access = Map.of(Constants.USER_GROUPS, List.of(group));
        // does NOT match
        boolean result = Boolean.TRUE.equals(ReflectionTestUtils.invokeMethod(
                courseAccessService,
                "evaluateAccessSettingRule",
                access,
                Map.of("cadre", 1) // does NOT match
        ));
        assertFalse(result);
    }

    @Test
    void testRetrieveUserCourses_RuleMatches() {
        BitSet bs = new BitSet();
        bs.set(1);
        Map<String,Object> ruleData = Map.of(Constants.ACCESS_CONTROL_ID,
                Map.of(Constants.USER_GROUPS,
                        List.of(Map.of(Constants.USER_GROUP_ID,"G1",
                                Constants.USER_GROUP_CRITERIA_LIST,
                                List.of(Map.of(Constants.CRITERIA_KEY,"cadre", Constants.CRITERIA_VALUE,bs)))
                        )));
        CachedAccessSettingRule rule = mock(CachedAccessSettingRule.class);
        when(rule.getContextId()).thenReturn("C1");
        when(rule.getContextData()).thenReturn(ruleData);
        when(mockAccessSettingRuleCacheMgr.getAccessSettingRules())
                .thenReturn(List.of(rule));
        when(contentInfoService.readContent(eq("C1"), anyList()))
                .thenReturn(Map.of("id","C1"));
        List<Map<String,Object>> list = new ArrayList<>();
        boolean val = Boolean.TRUE.equals(ReflectionTestUtils.invokeMethod(courseAccessService,
                "retrieveUserCourses",
                Map.of("cadre", 1),
                list));
        assertTrue(val);
        assertEquals(1, list.size());
    }

    @Test
    void testGetAssignedExternalCoursesForUser_ContentDirectMap() {
        String userId = "u_ext";
        when(mockAccessTokenValidator.fetchUserIdFromAccessToken(eq(authToken), any(ApiResponse.class)))
                .thenReturn(userId);
        Map<String, Object> request = Map.of(Constants.PARTNER_ID, "partner1");
        when(redisCacheMgr.getFromCache(anyString())).thenReturn(null);
        ReflectionTestUtils.setField(courseAccessService, "courseCategoryCache",
                new HashMap<>(Map.of("access_settings_enabled_partner1", List.of("C1"))));
        ReflectionTestUtils.setField(courseAccessService, "cacheTimestamps",
                new HashMap<>(Map.of("access_settings_enabled_partner1", System.currentTimeMillis())));
        ReflectionTestUtils.setField(courseAccessService, "cacheTtlMs", 99999999L);
        BitSet bit = new BitSet();
        bit.set(1);
        Map<String, Object> ug = new HashMap<>();
        ug.put(Constants.USER_GROUP_CRITERIA_LIST, List.of(Map.of(Constants.CRITERIA_KEY, "cadre", Constants.CRITERIA_VALUE, bit)));
        Map<String, Object> accessControl = Map.of(Constants.USER_GROUPS, List.of(ug));

        CachedAccessSettingRule rule = mock(CachedAccessSettingRule.class);
        when(rule.getContextId()).thenReturn("C1");
        when(rule.getContextData()).thenReturn(Map.of(Constants.ACCESS_CONTROL_ID, accessControl));
        when(mockAccessSettingRuleCacheMgr.getOrLoadAccessSettingRule("C1", Constants.EXTERNAL_COURSES))
                .thenReturn(rule);

        when(mockUserProfileService.getUserProfile(userId)).thenReturn(Map.of("cadre", 1));

        when(contentInfoService.readContent(eq("C1"), anyList()))
                .thenReturn(Map.of("content", Map.of(Constants.IDENTIFIER, "C1", "name", "External Course")));

        ApiResponse resp = courseAccessService.getAssignedExternalCoursesForUser(request, authToken);

        assertEquals(HttpStatus.OK, resp.getResponseCode());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> content = (List<Map<String, Object>>) resp.getResult().get(Constants.CONTENT);
        assertNotNull(content);
        assertEquals(1, content.size());
        assertEquals("C1", content.get(0).get(Constants.IDENTIFIER));
    }

    @Test
    void testGetCoursesFromCacheOrServiceForExternalCourse_CacheHit_viaReflection() {
        String partnerId = "partnerCache";
        String cacheKey = "access_settings_enabled_" + partnerId;
        Map<String, List<String>> partnerCache = new HashMap<>();
        partnerCache.put(cacheKey, List.of("E1", "E2"));
        Map<String, Long> timestamps = new HashMap<>();
        timestamps.put(cacheKey, System.currentTimeMillis());

        ReflectionTestUtils.setField(courseAccessService, "courseCategoryCache", partnerCache);
        ReflectionTestUtils.setField(courseAccessService, "cacheTimestamps", timestamps);
        ReflectionTestUtils.setField(courseAccessService, "cacheTtlMs", 99999999L);

        @SuppressWarnings("unchecked")
        List<String> result = (List<String>) ReflectionTestUtils.invokeMethod(
                courseAccessService, "getCoursesFromCacheOrServiceForExternalCourse", partnerId);

        assertNotNull(result);
        assertEquals(2, result.size());
        assertTrue(result.contains("E1"));
        assertTrue(result.contains("E2"));
    }

    // Test cases for getPersonalContentInfo
    @Test
    void testGetPersonalContentInfo_ValidTokenAndSuccess() {
        String testAuthToken = "validToken123";
        String userId = "user123";
        String orgId = "org456";

        Map<String, Object> tokenData = new HashMap<>();
        tokenData.put("userId", userId);
        tokenData.put("org", orgId);

        when(mockAccessTokenValidator.fetchUserIdAndOrg(testAuthToken)).thenReturn(tokenData);
        when(redisCacheMgr.getFromCache(anyString())).thenReturn(null);

        ApiResponse cbPlanResponse = new ApiResponse();
        cbPlanResponse.setResult(new HashMap<>());
        when(cbPlanLearnerServiceImpl.getCBPlanListForUser(orgId, userId, true)).thenReturn(cbPlanResponse);

        doNothing().when(redisCacheMgr).putInCache(anyString(), anyString());

        ApiResponse result = courseAccessService.getPersonalContentInfo(testAuthToken);

        assertNotNull(result);
        assertNotNull(result.getResult());
    }

    @Test
    void testGetPersonalContentInfo_InvalidTokenEmptyUserId() {
        String testAuthToken = "invalidToken";

        Map<String, Object> tokenData = new HashMap<>();
        tokenData.put("userId", "");
        tokenData.put("org", "org456");

        when(mockAccessTokenValidator.fetchUserIdAndOrg(testAuthToken)).thenReturn(tokenData);

        ApiResponse result = courseAccessService.getPersonalContentInfo(testAuthToken);
        
        assertNotNull(result);
        assertEquals(Constants.FAILED, result.getParams().getStatus());
        assertEquals(HttpStatus.UNAUTHORIZED, result.getResponseCode());
        assertEquals("Invalid auth token", result.getParams().getErrMsg());
    }

    @Test
    void testGetPersonalContentInfo_InvalidTokenNullUserId() {
        String testAuthToken = "invalidToken";

        Map<String, Object> tokenData = new HashMap<>();
        tokenData.put("userId", null);
        tokenData.put("org", "org456");

        when(mockAccessTokenValidator.fetchUserIdAndOrg(testAuthToken)).thenReturn(tokenData);

        ApiResponse result = courseAccessService.getPersonalContentInfo(testAuthToken);
        
        assertNotNull(result);
        assertEquals(Constants.FAILED, result.getParams().getStatus());
        assertEquals(HttpStatus.UNAUTHORIZED, result.getResponseCode());
        assertEquals("Invalid auth token", result.getParams().getErrMsg());
    }

    @Test
    void testGetPersonalContentInfo_ExceptionHandling() {
        String testAuthToken = "token123";

        when(mockAccessTokenValidator.fetchUserIdAndOrg(testAuthToken))
                .thenThrow(new RuntimeException("Token validation failed"));

        ApiResponse result = courseAccessService.getPersonalContentInfo(testAuthToken);
        
        assertNotNull(result);
        assertEquals(Constants.FAILED, result.getParams().getStatus());
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, result.getResponseCode());
        assertTrue(result.getParams().getErrMsg().contains("Failed to fetch personal content info"));
    }

    @Test
    void testGetPersonalContentInfo_CacheHitForPersonalContent() {
        String testAuthToken = "validToken123";
        String userId = "user123";
        String orgId = "org456";

        Map<String, Object> tokenData = new HashMap<>();
        tokenData.put("userId", userId);
        tokenData.put("org", orgId);

        Map<String, Object> cachedContent = new HashMap<>();
        cachedContent.put(Constants.TRAINING_PLAN, 5);
        cachedContent.put(Constants.APAR, 2);

        ObjectMapper objectMapper = new ObjectMapper();

        when(mockAccessTokenValidator.fetchUserIdAndOrg(testAuthToken)).thenReturn(tokenData);
        when(redisCacheMgr.getFromCache(anyString())).thenReturn(null);
        doNothing().when(redisCacheMgr).putInCache(anyString(), anyString());

        ReflectionTestUtils.setField(courseAccessService, "objectMapper", objectMapper);

        ApiResponse result = courseAccessService.getPersonalContentInfo(testAuthToken);
        
        assertNotNull(result);
        verify(redisCacheMgr, atLeastOnce()).getFromCache(Constants.PERSONAL_CONTENT_INFO_REDIS_KEY_PREFIX + userId);
    }

    @Test
    void testGetPersonalContentInfo_CacheMissForPersonalContent() {
        String testAuthToken = "validToken123";
        String userId = "user123";
        String orgId = "org456";

        Map<String, Object> tokenData = new HashMap<>();
        tokenData.put("userId", userId);
        tokenData.put("org", orgId);

        when(mockAccessTokenValidator.fetchUserIdAndOrg(testAuthToken)).thenReturn(tokenData);
        when(redisCacheMgr.getFromCache(anyString())).thenReturn(null);

        ApiResponse cbPlanResponse = new ApiResponse();
        Map<String, Object> cbPlanResult = new HashMap<>();
        cbPlanResponse.setResult(cbPlanResult);
        when(cbPlanLearnerServiceImpl.getCBPlanListForUser(orgId, userId, true)).thenReturn(cbPlanResponse);

        doNothing().when(redisCacheMgr).putInCache(anyString(), anyString());

        ObjectMapper objectMapper = new ObjectMapper();
        ReflectionTestUtils.setField(courseAccessService, "objectMapper", objectMapper);

        ApiResponse result = courseAccessService.getPersonalContentInfo(testAuthToken);
        
        assertNotNull(result);
    }

    @Test
    void testGetPersonalContentInfo_WithCachedModeratedContent() throws Exception {
        String testAuthToken = "validToken123";
        String userId = "user123";
        String orgId = "org456";

        Map<String, Object> tokenData = new HashMap<>();
        tokenData.put("userId", userId);
        tokenData.put("org", orgId);

        Map<String, Object> moderatedCacheData = new HashMap<>();
        moderatedCacheData.put(orgId, 5);

        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.writeValueAsString(moderatedCacheData);

        when(mockAccessTokenValidator.fetchUserIdAndOrg(testAuthToken)).thenReturn(tokenData);
        when(redisCacheMgr.getFromCache(anyString())).thenReturn(null);

        ApiResponse cbPlanResponse = new ApiResponse();
        cbPlanResponse.setResult(new HashMap<>());
        when(cbPlanLearnerServiceImpl.getCBPlanListForUser(orgId, userId, true)).thenReturn(cbPlanResponse);

        doNothing().when(redisCacheMgr).putInCache(anyString(), anyString());

        ReflectionTestUtils.setField(courseAccessService, "objectMapper", objectMapper);

        ApiResponse result = courseAccessService.getPersonalContentInfo(testAuthToken);
        
        assertNotNull(result);
    }

    @Test
    void testGetPersonalContentInfo_WithCBPlansAPAR() {
        String testAuthToken = "validToken123";
        String userId = "user123";
        String orgId = "org456";

        Map<String, Object> tokenData = new HashMap<>();
        tokenData.put("userId", userId);
        tokenData.put("org", orgId);

        when(mockAccessTokenValidator.fetchUserIdAndOrg(testAuthToken)).thenReturn(tokenData);
        when(redisCacheMgr.getFromCache(anyString())).thenReturn(null);
        
        ApiResponse cbPlanResponse = new ApiResponse();
        Map<String, Object> cbPlanResult = new HashMap<>();
        
        List<Map<String, Object>> plans = new ArrayList<>();
        Map<String, Object> plan1 = new HashMap<>();
        plan1.put(Constants.IS_APAR, true);
        plans.add(plan1);
        
        Map<String, Object> plan2 = new HashMap<>();
        plan2.put(Constants.IS_APAR, false);
        plans.add(plan2);
        
        cbPlanResult.put(Constants.CONTENT, plans);
        cbPlanResponse.setResult(cbPlanResult);
        
        when(cbPlanLearnerServiceImpl.getCBPlanListForUser(orgId, userId, true)).thenReturn(cbPlanResponse);
        
        ObjectMapper objectMapper = new ObjectMapper();
        ReflectionTestUtils.setField(courseAccessService, "objectMapper", objectMapper);

        ApiResponse result = courseAccessService.getPersonalContentInfo(testAuthToken);

        assertNotNull(result);
        assertNotNull(result.getResult());
    }

    @Test
    void testGetPersonalContentInfo_ResponseStructure() {
        String testAuthToken = "validToken123";
        String userId = "user123";
        String orgId = "org456";

        Map<String, Object> tokenData = new HashMap<>();
        tokenData.put("userId", userId);
        tokenData.put("org", orgId);

        when(mockAccessTokenValidator.fetchUserIdAndOrg(testAuthToken)).thenReturn(tokenData);
        when(redisCacheMgr.getFromCache(anyString())).thenReturn(null);

        ApiResponse cbPlanResponse = new ApiResponse();
        cbPlanResponse.setResult(new HashMap<>());
        when(cbPlanLearnerServiceImpl.getCBPlanListForUser(orgId, userId, true)).thenReturn(cbPlanResponse);

        doNothing().when(redisCacheMgr).putInCache(anyString(), anyString());

        ObjectMapper objectMapper = new ObjectMapper();
        ReflectionTestUtils.setField(courseAccessService, "objectMapper", objectMapper);

        ApiResponse result = courseAccessService.getPersonalContentInfo(testAuthToken);

        assertNotNull(result);
        assertNotNull(result.getParams());
        assertNotNull(result.getResponseCode());
        assertNotNull(result.getResult());
    }

    // ===================== Additional coverage tests =====================

    @Test
    void testGetAssignedCoursesForUserByAdmin_NullRequest() {
        ApiResponse response = courseAccessService.getAssignedCoursesForUserByAdmin("u1", null);
        assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
    }

    @Test
    void testGetAssignedCoursesForUserByAdmin_MissingCourseCategory() {
        ApiResponse response = courseAccessService.getAssignedCoursesForUserByAdmin("u1", Map.of("other", "x"));
        assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
    }

    @Test
    void testGetAssignedCoursesForUserByAdmin_NoCourseIdentifiers() {
        when(redisCacheMgr.getFromCache(anyString())).thenReturn(null);
        when(outboundRequestHandlerService.fetchResultUsingPost(anyString(), anyMap(), isNull()))
                .thenReturn(Map.of(Constants.RESULT, Map.of(Constants.CONTENT, List.of())));
        ApiResponse response = courseAccessService.getAssignedCoursesForUserByAdmin("u1",
                Map.of(Constants.COURSE_CATEGORY, "cat2"));
        assertEquals(HttpStatus.OK, response.getResponseCode());
        assertTrue(((List<?>) response.getResult().get(Constants.CONTENT)).isEmpty());
    }

    @Test
    void testGetAssignedCoursesForUserByAdmin_NoRulesFound() {
        when(redisCacheMgr.getFromCache(anyString())).thenReturn(null);
        when(outboundRequestHandlerService.fetchResultUsingPost(anyString(), anyMap(), isNull()))
                .thenReturn(Map.of(Constants.RESULT,
                        Map.of(Constants.CONTENT, List.of(Map.of(Constants.IDENTIFIER, "C1")))));
        when(mockAccessSettingRuleCacheMgr.getOrLoadAccessSettingRule(anyString(), anyString())).thenReturn(null);
        when(mockUserProfileService.getUserProfile("u1")).thenReturn(Map.of("cadre", 1));
        ApiResponse response = courseAccessService.getAssignedCoursesForUserByAdmin("u1",
                Map.of(Constants.COURSE_CATEGORY, "cat3"));
        assertEquals(HttpStatus.OK, response.getResponseCode());
        assertTrue(((List<?>) response.getResult().get(Constants.CONTENT)).isEmpty());
    }

    @Test
    void testGetAssignedCoursesForUserByAdmin_ExceptionHandling() {
        when(redisCacheMgr.getFromCache(anyString())).thenReturn(null);
        when(outboundRequestHandlerService.fetchResultUsingPost(anyString(), anyMap(), isNull()))
                .thenReturn(Map.of(Constants.RESULT,
                        Map.of(Constants.CONTENT, List.of(Map.of(Constants.IDENTIFIER, "C1")))));
        when(mockUserProfileService.getUserProfile("u1")).thenThrow(new RuntimeException("profile error"));
        ApiResponse response = courseAccessService.getAssignedCoursesForUserByAdmin("u1",
                Map.of(Constants.COURSE_CATEGORY, "cat4"));
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getResponseCode());
    }

    @SuppressWarnings("unchecked")
    @Test
    void testGetAssignedCoursesForUserByAdmin_CourseUnitsLogicForComprehensiveAssessmentProgram() {
        String userId = "u5";
        when(redisCacheMgr.getFromCache(anyString())).thenReturn(null);
        when(outboundRequestHandlerService.fetchResultUsingPost(anyString(), anyMap(), isNull()))
                .thenReturn(Map.of(Constants.RESULT,
                        Map.of(Constants.CONTENT, List.of(Map.of(Constants.IDENTIFIER, "C1")))));
        BitSet bit = new BitSet();
        bit.set(1);
        Map<String, Object> accessControl = Map.of(
                Constants.USER_GROUPS,
                List.of(Map.of(
                        Constants.USER_GROUP_ID, "G1",
                        Constants.USER_GROUP_CRITERIA_LIST,
                        List.of(Map.of(Constants.CRITERIA_KEY, "cadre", Constants.CRITERIA_VALUE, bit))))
        );
        CachedAccessSettingRule rule = mock(CachedAccessSettingRule.class);
        when(rule.getContextId()).thenReturn("C1");
        when(rule.getContextData()).thenReturn(Map.of(Constants.ACCESS_CONTROL_ID, accessControl));
        when(mockAccessSettingRuleCacheMgr.getOrLoadAccessSettingRule(anyString(), anyString())).thenReturn(rule);
        when(mockUserProfileService.getUserProfile(userId)).thenReturn(Map.of("cadre", 1));

        Map<String, Object> contentDetails = new HashMap<>();
        contentDetails.put(Constants.IDENTIFIER, "C1");
        contentDetails.put(Constants.COURSE_CATEGORY, Constants.COURSE_CATEGORY_COMPREHENSIVE_ASSESSMENT_PROGRAM);
        contentDetails.put(Constants.CHILD_NODES, List.of("child1", "child2"));
        contentDetails.put(Constants.LEAF_NODES, List.of("child2"));
        contentDetails.put(Constants.END_DATE_CAMEL, "2030-01-01");
        when(contentInfoService.readContent(eq("C1"), anyList())).thenReturn(contentDetails);

        ApiResponse response = courseAccessService.getAssignedCoursesForUserByAdmin(userId,
                Map.of(Constants.COURSE_CATEGORY, Constants.COURSE_CATEGORY_COMPREHENSIVE_ASSESSMENT_PROGRAM));

        assertEquals(HttpStatus.OK, response.getResponseCode());
        List<Map<String, Object>> content = (List<Map<String, Object>>) response.getResult().get(Constants.CONTENT);
        assertEquals(1, content.size());
        List<String> courseUnits = (List<String>) content.get(0).get(Constants.COURSE_UNITS);
        assertEquals(List.of("child1"), courseUnits);
    }

    @Test
    void testAddUserCourseIfAccessible_NullContextData() {
        CachedAccessSettingRule rule = mock(CachedAccessSettingRule.class);
        when(rule.getContextData()).thenReturn(null);
        when(rule.getCacheKey()).thenReturn("key1");
        List<Map<String, Object>> userCourses = new ArrayList<>();
        ReflectionTestUtils.invokeMethod(courseAccessService, "addUserCourseIfAccessible",
                rule, Map.of("cadre", 1), userCourses);
        assertTrue(userCourses.isEmpty());
    }

    @Test
    void testAddUserCourseIfAccessible_MissingAccessControlKey() {
        CachedAccessSettingRule rule = mock(CachedAccessSettingRule.class);
        when(rule.getContextData()).thenReturn(Map.of("other", "value"));
        when(rule.getCacheKey()).thenReturn("key2");
        List<Map<String, Object>> userCourses = new ArrayList<>();
        ReflectionTestUtils.invokeMethod(courseAccessService, "addUserCourseIfAccessible",
                rule, Map.of("cadre", 1), userCourses);
        assertTrue(userCourses.isEmpty());
    }

    @Test
    void testFetchAccessSettingsEnabledCoursesForCategory_EmptyCompositeSearchResponse() {
        when(outboundRequestHandlerService.fetchResultUsingPost(anyString(), anyMap(), isNull()))
                .thenReturn(Collections.emptyMap());
        Map<String, Object> result = courseAccessService.fetchAccessSettingsEnabledCoursesForCategory("catX");
        assertTrue(result.isEmpty());
    }

    @SuppressWarnings("unchecked")
    @Test
    void testFetchAccessSettingsEnabledCoursesForCategory_MultiPagePagination() {
        ReflectionTestUtils.setField(courseAccessService, "searchLimit", 2);
        Map<String, Object> firstResult = new HashMap<>();
        firstResult.put(Constants.COUNT, 5);
        firstResult.put(Constants.CONTENT,
                new ArrayList<>(List.of(Map.of(Constants.IDENTIFIER, "A"), Map.of(Constants.IDENTIFIER, "B"))));
        Map<String, Object> firstPage = Map.of(Constants.RESULT, firstResult);

        Map<String, Object> secondResult = new HashMap<>();
        secondResult.put(Constants.CONTENT, List.of(Map.of(Constants.IDENTIFIER, "C"), Map.of(Constants.IDENTIFIER, "D")));
        Map<String, Object> secondPage = Map.of(Constants.RESULT, secondResult);

        Map<String, Object> thirdResult = new HashMap<>();
        thirdResult.put(Constants.CONTENT, List.of(Map.of(Constants.IDENTIFIER, "E")));
        Map<String, Object> thirdPage = Map.of(Constants.RESULT, thirdResult);

        when(outboundRequestHandlerService.fetchResultUsingPost(anyString(), anyMap(), isNull()))
                .thenReturn(firstPage, secondPage, thirdPage);

        Map<String, Object> result = courseAccessService.fetchAccessSettingsEnabledCoursesForCategory("catPage");

        Map<String, Object> resultMap = (Map<String, Object>) result.get(Constants.RESULT);
        List<Map<String, Object>> allContent = (List<Map<String, Object>>) resultMap.get(Constants.CONTENT);
        assertEquals(5, allContent.size());
    }

    @SuppressWarnings("unchecked")
    @Test
    void testFetchAccessSettingsEnabledCoursesForCategory_StopsWhenPageEmpty() {
        ReflectionTestUtils.setField(courseAccessService, "searchLimit", 2);
        Map<String, Object> firstResult = new HashMap<>();
        firstResult.put(Constants.COUNT, 10);
        firstResult.put(Constants.CONTENT,
                new ArrayList<>(List.of(Map.of(Constants.IDENTIFIER, "A"), Map.of(Constants.IDENTIFIER, "B"))));
        Map<String, Object> firstPage = Map.of(Constants.RESULT, firstResult);

        Map<String, Object> emptyPageResult = new HashMap<>();
        emptyPageResult.put(Constants.CONTENT, new ArrayList<>());
        Map<String, Object> emptyPage = Map.of(Constants.RESULT, emptyPageResult);

        when(outboundRequestHandlerService.fetchResultUsingPost(anyString(), anyMap(), isNull()))
                .thenReturn(firstPage, emptyPage);

        Map<String, Object> result = courseAccessService.fetchAccessSettingsEnabledCoursesForCategory("catStop");

        Map<String, Object> resultMap = (Map<String, Object>) result.get(Constants.RESULT);
        List<Map<String, Object>> allContent = (List<Map<String, Object>>) resultMap.get(Constants.CONTENT);
        assertEquals(2, allContent.size());
    }

    @Test
    void testReadCachedCourseList_InvalidJson_ReturnsNull() {
        List<String> result = ReflectionTestUtils.invokeMethod(courseAccessService, "readCachedCourseList",
                "invalid-json", "catZ");
        assertNull(result);
    }

    @Test
    void testGetCoursesFromCacheOrService_CacheHitInvalidJsonFallsBackToService() {
        when(redisCacheMgr.getFromCache("access_settings_enabled_catY")).thenReturn("not-json");
        when(outboundRequestHandlerService.fetchResultUsingPost(anyString(), anyMap(), isNull()))
                .thenReturn(Map.of(Constants.RESULT,
                        Map.of(Constants.CONTENT, List.of(Map.of(Constants.IDENTIFIER, "Z1")))));
        List<String> result = ReflectionTestUtils.invokeMethod(courseAccessService, "getCoursesFromCacheOrService",
                "catY");
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("Z1", result.get(0));
    }

    @Test
    void testExtractIdentifiers_NullResult() {
        Map<String, Object> fetchedCourses = new HashMap<>();
        List<String> result = ReflectionTestUtils.invokeMethod(courseAccessService, "extractIdentifiers",
                fetchedCourses, "catA");
        assertTrue(result.isEmpty());
    }

    @Test
    void testExtractIdentifiers_NoContentKey() {
        Map<String, Object> fetchedCourses = Map.of(Constants.RESULT, Map.of("other", "x"));
        List<String> result = ReflectionTestUtils.invokeMethod(courseAccessService, "extractIdentifiers",
                fetchedCourses, "catA");
        assertTrue(result.isEmpty());
    }

    @Test
    void testExtractIdentifiers_FiltersNullIdentifiers() {
        List<Map<String, Object>> contentList = new ArrayList<>();
        Map<String, Object> item1 = new HashMap<>();
        item1.put(Constants.IDENTIFIER, "I1");
        Map<String, Object> item2 = new HashMap<>();
        item2.put(Constants.IDENTIFIER, null);
        contentList.add(item1);
        contentList.add(item2);
        Map<String, Object> fetchedCourses = Map.of(Constants.RESULT, Map.of(Constants.CONTENT, contentList));
        List<String> result = ReflectionTestUtils.invokeMethod(courseAccessService, "extractIdentifiers",
                fetchedCourses, "catA");
        assertEquals(1, result.size());
        assertEquals("I1", result.get(0));
    }

    @Test
    void testExtractIdentifiers_ExceptionCaught() {
        Map<String, Object> fetchedCourses = Map.of(Constants.RESULT, "not-a-map");
        List<String> result = ReflectionTestUtils.invokeMethod(courseAccessService, "extractIdentifiers",
                fetchedCourses, "catA");
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void testFetchFromRedisCache_BlankData_ReturnsEmptyList() {
        when(redisCacheMgr.getFromCache("keyBlank")).thenReturn("");
        List<Map<String, Object>> result = ReflectionTestUtils.invokeMethod(courseAccessService, "fetchFromRedisCache",
                "keyBlank");
        assertTrue(result.isEmpty());
    }

    @Test
    void testFetchFromRedisCache_ValidJson_ReturnsList() {
        when(redisCacheMgr.getFromCache("keyValid")).thenReturn("[{\"identifier\":\"X1\"}]");
        List<Map<String, Object>> result = ReflectionTestUtils.invokeMethod(courseAccessService, "fetchFromRedisCache",
                "keyValid");
        assertEquals(1, result.size());
        assertEquals("X1", result.get(0).get("identifier"));
    }

    @Test
    void testGetAssignedExternalCoursesForUser_InvalidToken() {
        when(mockAccessTokenValidator.fetchUserIdFromAccessToken(eq(authToken), any(ApiResponse.class)))
                .thenReturn("");
        ApiResponse response = courseAccessService.getAssignedExternalCoursesForUser(
                Map.of(Constants.PARTNER_ID, "p1"), authToken);
        assertEquals(HttpStatus.OK, response.getResponseCode());
    }

    @Test
    void testGetAssignedExternalCoursesForUser_EmptyRequest() {
        when(mockAccessTokenValidator.fetchUserIdFromAccessToken(eq(authToken), any(ApiResponse.class)))
                .thenReturn("u1");
        ApiResponse response = courseAccessService.getAssignedExternalCoursesForUser(Map.of(), authToken);
        assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
    }

    @Test
    void testGetAssignedExternalCoursesForUser_MissingPartnerId() {
        when(mockAccessTokenValidator.fetchUserIdFromAccessToken(eq(authToken), any(ApiResponse.class)))
                .thenReturn("u1");
        ApiResponse response = courseAccessService.getAssignedExternalCoursesForUser(Map.of("other", "x"), authToken);
        assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
    }

    @SuppressWarnings("unchecked")
    @Test
    void testGetAssignedExternalCoursesForUser_CacheHit() {
        when(mockAccessTokenValidator.fetchUserIdFromAccessToken(eq(authToken), any(ApiResponse.class)))
                .thenReturn("u1");
        when(redisCacheMgr.getFromCache(anyString())).thenReturn("[{\"identifier\":\"E1\"}]");
        ApiResponse response = courseAccessService.getAssignedExternalCoursesForUser(
                Map.of(Constants.PARTNER_ID, "p2"), authToken);
        assertEquals(HttpStatus.OK, response.getResponseCode());
        List<Map<String, Object>> content = (List<Map<String, Object>>) response.getResult().get(Constants.CONTENT);
        assertEquals(1, content.size());
        assertEquals("E1", content.get(0).get("identifier"));
    }

    @Test
    void testGetAssignedExternalCoursesForUser_NoCourseIdentifiers() {
        when(mockAccessTokenValidator.fetchUserIdFromAccessToken(eq(authToken), any(ApiResponse.class)))
                .thenReturn("u1");
        when(redisCacheMgr.getFromCache(anyString())).thenReturn(null);
        ReflectionTestUtils.setField(courseAccessService, "courseCategoryCache", new HashMap<>());
        ReflectionTestUtils.setField(courseAccessService, "cacheTimestamps", new HashMap<>());
        when(outboundRequestHandlerService.fetchResultUsingPost(anyString(), anyMap(), isNull()))
                .thenReturn(Map.of(Constants.DATA, List.of()));
        ApiResponse response = courseAccessService.getAssignedExternalCoursesForUser(
                Map.of(Constants.PARTNER_ID, "p3"), authToken);
        assertEquals(HttpStatus.OK, response.getResponseCode());
        assertTrue(((List<?>) response.getResult().get(Constants.CONTENT)).isEmpty());
    }

    @Test
    void testGetAssignedExternalCoursesForUser_NoRulesFound() {
        when(mockAccessTokenValidator.fetchUserIdFromAccessToken(eq(authToken), any(ApiResponse.class)))
                .thenReturn("u1");
        when(redisCacheMgr.getFromCache(anyString())).thenReturn(null);
        ReflectionTestUtils.setField(courseAccessService, "courseCategoryCache",
                new HashMap<>(Map.of("access_settings_enabled_p4", List.of("EC1"))));
        ReflectionTestUtils.setField(courseAccessService, "cacheTimestamps",
                new HashMap<>(Map.of("access_settings_enabled_p4", System.currentTimeMillis())));
        ReflectionTestUtils.setField(courseAccessService, "cacheTtlMs", 99999999L);
        when(mockAccessSettingRuleCacheMgr.getOrLoadAccessSettingRule("EC1", Constants.EXTERNAL_COURSES))
                .thenReturn(null);
        when(mockUserProfileService.getUserProfile("u1")).thenReturn(Map.of("cadre", 1));
        ApiResponse response = courseAccessService.getAssignedExternalCoursesForUser(
                Map.of(Constants.PARTNER_ID, "p4"), authToken);
        assertEquals(HttpStatus.OK, response.getResponseCode());
        assertTrue(((List<?>) response.getResult().get(Constants.CONTENT)).isEmpty());
    }

    @Test
    void testGetAssignedExternalCoursesForUser_NullContextDataCausesInternalError() {
        when(mockAccessTokenValidator.fetchUserIdFromAccessToken(eq(authToken), any(ApiResponse.class)))
                .thenReturn("u1");
        when(redisCacheMgr.getFromCache(anyString())).thenReturn(null);
        ReflectionTestUtils.setField(courseAccessService, "courseCategoryCache",
                new HashMap<>(Map.of("access_settings_enabled_p5", List.of("EC2"))));
        ReflectionTestUtils.setField(courseAccessService, "cacheTimestamps",
                new HashMap<>(Map.of("access_settings_enabled_p5", System.currentTimeMillis())));
        ReflectionTestUtils.setField(courseAccessService, "cacheTtlMs", 99999999L);
        CachedAccessSettingRule rule = mock(CachedAccessSettingRule.class);
        when(rule.getContextData()).thenReturn(null);
        when(mockAccessSettingRuleCacheMgr.getOrLoadAccessSettingRule("EC2", Constants.EXTERNAL_COURSES))
                .thenReturn(rule);
        when(mockUserProfileService.getUserProfile("u1")).thenReturn(Map.of("cadre", 1));
        ApiResponse response = courseAccessService.getAssignedExternalCoursesForUser(
                Map.of(Constants.PARTNER_ID, "p5"), authToken);
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getResponseCode());
    }

    @Test
    void testFetchAccessSettingsEnabledCoursesForExternalCourses_WithData() {
        Map<String, Object> response = Map.of(Constants.DATA, List.of(
                Map.of(Constants.CONTENT_ID, "CID1"),
                Map.of(Constants.CONTENT_ID, "CID2")
        ));
        when(outboundRequestHandlerService.fetchResultUsingPost(anyString(), anyMap(), isNull()))
                .thenReturn(response);
        List<String> result = courseAccessService.fetchAccessSettingsEnabledCoursesForExternalCourses("partnerX");
        assertEquals(2, result.size());
        assertTrue(result.contains("CID1"));
        assertTrue(result.contains("CID2"));
    }

    @Test
    void testFetchAccessSettingsEnabledCoursesForExternalCourses_NoDataArray() {
        when(outboundRequestHandlerService.fetchResultUsingPost(anyString(), anyMap(), isNull()))
                .thenReturn(Map.of("other", "value"));
        List<String> result = courseAccessService.fetchAccessSettingsEnabledCoursesForExternalCourses("partnerY");
        assertTrue(result.isEmpty());
    }

    @Test
    void testGetCoursesFromCacheOrServiceForExternalCourse_CacheMissFetchSuccess() {
        String partnerId = "partnerFetch";
        ReflectionTestUtils.setField(courseAccessService, "courseCategoryCache", new HashMap<>());
        ReflectionTestUtils.setField(courseAccessService, "cacheTimestamps", new HashMap<>());
        Map<String, Object> ciosResponse = Map.of(Constants.DATA, List.of(Map.of(Constants.CONTENT_ID, "F1")));
        when(outboundRequestHandlerService.fetchResultUsingPost(anyString(), anyMap(), isNull()))
                .thenReturn(ciosResponse);
        List<String> result = ReflectionTestUtils.invokeMethod(courseAccessService,
                "getCoursesFromCacheOrServiceForExternalCourse", partnerId);
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("F1", result.get(0));
    }

    @Test
    void testGetCoursesFromCacheOrServiceForExternalCourse_Exception() {
        String partnerId = "partnerErr";
        ReflectionTestUtils.setField(courseAccessService, "courseCategoryCache", new HashMap<>());
        ReflectionTestUtils.setField(courseAccessService, "cacheTimestamps", new HashMap<>());
        when(outboundRequestHandlerService.fetchResultUsingPost(anyString(), anyMap(), isNull()))
                .thenThrow(new RuntimeException("ERR"));
        List<String> result = ReflectionTestUtils.invokeMethod(courseAccessService,
                "getCoursesFromCacheOrServiceForExternalCourse", partnerId);
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void testIsCompleted_EmptyMap() {
        boolean result = Boolean.TRUE.equals(
                ReflectionTestUtils.invokeMethod(courseAccessService, "isCompleted", Map.of()));
        assertFalse(result);
    }

    @Test
    void testIsCompleted_StatusCompleted() {
        Map<String, Object> enrollment = Map.of("status", 2);
        boolean result = Boolean.TRUE.equals(
                ReflectionTestUtils.invokeMethod(courseAccessService, "isCompleted", enrollment));
        assertTrue(result);
    }

    @Test
    void testIsCompleted_PercentageCompleted() {
        Map<String, Object> enrollment = Map.of("completionPercentage", 100);
        boolean result = Boolean.TRUE.equals(
                ReflectionTestUtils.invokeMethod(courseAccessService, "isCompleted", enrollment));
        assertTrue(result);
    }

    @Test
    void testIsCompleted_NotCompleted() {
        Map<String, Object> enrollment = Map.of("status", 1, "completionPercentage", 50);
        boolean result = Boolean.TRUE.equals(
                ReflectionTestUtils.invokeMethod(courseAccessService, "isCompleted", enrollment));
        assertFalse(result);
    }

    @Test
    void testIsFutureBatchEndDate_EmptyBatch() {
        boolean result = Boolean.TRUE.equals(ReflectionTestUtils.invokeMethod(courseAccessService,
                "isFutureBatchEndDate", Map.of(), LocalDate.now()));
        assertFalse(result);
    }

    @Test
    void testIsFutureBatchEndDate_MissingEndDate() {
        boolean result = Boolean.TRUE.equals(ReflectionTestUtils.invokeMethod(courseAccessService,
                "isFutureBatchEndDate", Map.of("other", "x"), LocalDate.now()));
        assertFalse(result);
    }

    @Test
    void testIsFutureBatchEndDate_FutureDate() {
        Map<String, Object> batch = Map.of("endDate", LocalDate.now().plusDays(5).toString());
        boolean result = Boolean.TRUE.equals(ReflectionTestUtils.invokeMethod(courseAccessService,
                "isFutureBatchEndDate", batch, LocalDate.now()));
        assertTrue(result);
    }

    @Test
    void testIsFutureBatchEndDate_PastDate() {
        Map<String, Object> batch = Map.of("endDate", LocalDate.now().minusDays(5).toString());
        boolean result = Boolean.TRUE.equals(ReflectionTestUtils.invokeMethod(courseAccessService,
                "isFutureBatchEndDate", batch, LocalDate.now()));
        assertFalse(result);
    }

    @Test
    void testIsBatchEndDateValidForStandalone_EmptyEnrollment() {
        boolean result = Boolean.TRUE.equals(ReflectionTestUtils.invokeMethod(courseAccessService,
                "isBatchEndDateValidForStandalone", Map.of()));
        assertFalse(result);
    }

    @Test
    void testIsBatchEndDateValidForStandalone_ContentNotMap() {
        Map<String, Object> enrollment = Map.of("content", "not-a-map");
        boolean result = Boolean.TRUE.equals(ReflectionTestUtils.invokeMethod(courseAccessService,
                "isBatchEndDateValidForStandalone", enrollment));
        assertFalse(result);
    }

    @Test
    void testIsBatchEndDateValidForStandalone_BatchesNotList() {
        Map<String, Object> content = Map.of("batches", "not-a-list");
        Map<String, Object> enrollment = Map.of("content", content);
        boolean result = Boolean.TRUE.equals(ReflectionTestUtils.invokeMethod(courseAccessService,
                "isBatchEndDateValidForStandalone", enrollment));
        assertFalse(result);
    }

    @Test
    void testIsBatchEndDateValidForStandalone_HasFutureBatch() {
        Map<String, Object> batch = Map.of("endDate", LocalDate.now().plusDays(10).toString());
        Map<String, Object> content = Map.of("batches", List.of(batch));
        Map<String, Object> enrollment = Map.of("content", content);
        boolean result = Boolean.TRUE.equals(ReflectionTestUtils.invokeMethod(courseAccessService,
                "isBatchEndDateValidForStandalone", enrollment));
        assertTrue(result);
    }

    @Test
    void testIsBatchEndDateValidForStandalone_AllPastBatches() {
        Map<String, Object> batch = Map.of("endDate", LocalDate.now().minusDays(10).toString());
        Map<String, Object> content = Map.of("batches", List.of(batch));
        Map<String, Object> enrollment = Map.of("content", content);
        boolean result = Boolean.TRUE.equals(ReflectionTestUtils.invokeMethod(courseAccessService,
                "isBatchEndDateValidForStandalone", enrollment));
        assertFalse(result);
    }

    @Test
    void testIsEligibleStandaloneAssessment_EmptyEnrollment() {
        boolean result = Boolean.TRUE.equals(ReflectionTestUtils.invokeMethod(courseAccessService,
                "isEligibleStandaloneAssessment", Map.of()));
        assertFalse(result);
    }

    @Test
    void testIsEligibleStandaloneAssessment_Completed() {
        Map<String, Object> enrollment = Map.of("status", 2);
        boolean result = Boolean.TRUE.equals(ReflectionTestUtils.invokeMethod(courseAccessService,
                "isEligibleStandaloneAssessment", enrollment));
        assertFalse(result);
    }

    @Test
    void testIsEligibleStandaloneAssessment_NotCompletedWithFutureBatch() {
        Map<String, Object> batch = Map.of("endDate", LocalDate.now().plusDays(3).toString());
        Map<String, Object> content = Map.of("batches", List.of(batch));
        Map<String, Object> enrollment = Map.of("status", 1, "content", content);
        boolean result = Boolean.TRUE.equals(ReflectionTestUtils.invokeMethod(courseAccessService,
                "isEligibleStandaloneAssessment", enrollment));
        assertTrue(result);
    }

    @Test
    void testFilterStandaloneAssessmentIdentifiers_EmptyIds() {
        List<String> result = ReflectionTestUtils.invokeMethod(courseAccessService,
                "filterStandaloneAssessmentIdentifiers", Collections.emptyList(), Map.of());
        assertTrue(result.isEmpty());
    }

    @Test
    void testFilterStandaloneAssessmentIdentifiers_MixedEligibility() {
        Map<String, Object> futureBatch = Map.of("endDate", LocalDate.now().plusDays(2).toString());
        Map<String, Object> eligibleContent = Map.of("batches", List.of(futureBatch));
        Map<String, Object> eligibleEnrollment = Map.of("status", 1, "content", eligibleContent);
        Map<String, Object> completedEnrollment = Map.of("status", 2);
        Map<String, Map<String, Object>> dictionary = new HashMap<>();
        dictionary.put("A1", eligibleEnrollment);
        dictionary.put("A2", completedEnrollment);
        List<String> ids = List.of("A1", "A2", "A3");
        List<String> result = ReflectionTestUtils.invokeMethod(courseAccessService,
                "filterStandaloneAssessmentIdentifiers", ids, dictionary);
        assertEquals(1, result.size());
        assertEquals("A1", result.get(0));
    }

    @Test
    void testIsCaProgramIdentifierEligible_NoEndDate() {
        Map<String, Object> course = new HashMap<>();
        boolean result = Boolean.TRUE.equals(ReflectionTestUtils.invokeMethod(courseAccessService,
                "isCaProgramIdentifierEligible", course, "C1", LocalDate.now(), Map.of()));
        assertTrue(result);
    }

    @Test
    void testIsCaProgramIdentifierEligible_EndDateInPast() {
        Map<String, Object> course = Map.of(Constants.END_DATE_KEY, LocalDate.now().minusDays(1).toString());
        boolean result = Boolean.TRUE.equals(ReflectionTestUtils.invokeMethod(courseAccessService,
                "isCaProgramIdentifierEligible", course, "C1", LocalDate.now(), Map.of()));
        assertFalse(result);
    }

    @Test
    void testIsCaProgramIdentifierEligible_FutureEndDateNoEnrolment() {
        Map<String, Object> course = Map.of(Constants.END_DATE_KEY, LocalDate.now().plusDays(10).toString());
        boolean result = Boolean.TRUE.equals(ReflectionTestUtils.invokeMethod(courseAccessService,
                "isCaProgramIdentifierEligible", course, "C1", LocalDate.now(), Map.of()));
        assertTrue(result);
    }

    @Test
    void testIsCaProgramIdentifierEligible_FutureEndDateCompletedEnrolment() {
        Map<String, Object> course = Map.of(Constants.END_DATE_KEY, LocalDate.now().plusDays(10).toString());
        Map<String, Map<String, Object>> dict = Map.of("C1", Map.of("status", 2));
        boolean result = Boolean.TRUE.equals(ReflectionTestUtils.invokeMethod(courseAccessService,
                "isCaProgramIdentifierEligible", course, "C1", LocalDate.now(), dict));
        assertFalse(result);
    }

    @Test
    void testCallEnrolmentDictionaryApi_EmptyApiResponse() {
        when(outboundRequestHandlerService.fetchResultUsingGet(anyString(), anyMap()))
                .thenReturn(Collections.emptyMap());
        Map<String, Map<String, Object>> result = ReflectionTestUtils.invokeMethod(courseAccessService,
                "callEnrolmentDictionaryApi", "token1");
        assertTrue(result.isEmpty());
    }

    @Test
    void testCallEnrolmentDictionaryApi_NoResultKey() {
        when(outboundRequestHandlerService.fetchResultUsingGet(anyString(), anyMap()))
                .thenReturn(Map.of("other", "x"));
        Map<String, Map<String, Object>> result = ReflectionTestUtils.invokeMethod(courseAccessService,
                "callEnrolmentDictionaryApi", "token1");
        assertTrue(result.isEmpty());
    }

    @Test
    void testCallEnrolmentDictionaryApi_Success() {
        Map<String, Object> enrolment = Map.of("someKey", "someVal");
        Map<String, Object> result1 = Map.of(Constants.RESPONSE, Map.of("courseA", enrolment));
        Map<String, Object> apiResponse = Map.of(Constants.RESULT, result1);
        when(outboundRequestHandlerService.fetchResultUsingGet(anyString(), anyMap()))
                .thenReturn(apiResponse);
        Map<String, Map<String, Object>> result = ReflectionTestUtils.invokeMethod(courseAccessService,
                "callEnrolmentDictionaryApi", "token1");
        assertEquals(1, result.size());
        assertTrue(result.containsKey("courseA"));
    }

    @Test
    void testGetStandaloneAssessmentIdentifiersFromSystem_ExceptionReturnsEmpty() {
        List<String> result = ReflectionTestUtils.invokeMethod(courseAccessService,
                "getStandaloneAssessmentIdentifiersFromSystem");
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void testGetStandaloneAssessmentIdentifiersFromSystem_Success() {
        ReflectionTestUtils.setField(courseAccessService, "standaloneAssessmentSearchRequest", "{\"request\":{}}");
        Map<String, Object> searchResponse = Map.of(Constants.RESULT,
                Map.of(Constants.CONTENT, List.of(Map.of(Constants.IDENTIFIER, "SA1"), Map.of(Constants.IDENTIFIER, "SA2"))));
        when(outboundRequestHandlerService.fetchResultUsingPost(anyString(), anyMap(), isNull()))
                .thenReturn(searchResponse);
        List<String> result = ReflectionTestUtils.invokeMethod(courseAccessService,
                "getStandaloneAssessmentIdentifiersFromSystem");
        assertEquals(2, result.size());
        assertTrue(result.contains("SA1"));
        assertTrue(result.contains("SA2"));
    }

    @Test
    void testCallAssessmentEnrollmentDetailsApi_EmptyIds() {
        Map<String, Map<String, Object>> result = ReflectionTestUtils.invokeMethod(courseAccessService,
                "callAssessmentEnrollmentDetailsApi", "token1", Collections.emptyList());
        assertTrue(result.isEmpty());
    }

    @Test
    void testCallAssessmentEnrollmentDetailsApi_EmptyApiResponse() {
        when(outboundRequestHandlerService.fetchResultUsingPost(anyString(), anyMap(), anyMap()))
                .thenReturn(Collections.emptyMap());
        Map<String, Map<String, Object>> result = ReflectionTestUtils.invokeMethod(courseAccessService,
                "callAssessmentEnrollmentDetailsApi", "token1", List.of("A1"));
        assertTrue(result.isEmpty());
    }

    @Test
    void testCallAssessmentEnrollmentDetailsApi_Success() {
        Map<String, Object> enrollment1 = Map.of("courseId", "A1");
        Map<String, Object> enrollment2 = new HashMap<>();
        enrollment2.put("courseId", "");
        Map<String, Object> result = Map.of("courses", List.of(enrollment1, enrollment2));
        Map<String, Object> apiResponse = Map.of(Constants.RESULT, result);
        when(outboundRequestHandlerService.fetchResultUsingPost(anyString(), anyMap(), anyMap()))
                .thenReturn(apiResponse);
        Map<String, Map<String, Object>> dict = ReflectionTestUtils.invokeMethod(courseAccessService,
                "callAssessmentEnrollmentDetailsApi", "token1", List.of("A1"));
        assertEquals(1, dict.size());
        assertTrue(dict.containsKey("A1"));
    }

    @Test
    void testGetAssignedCourseCount_ReturnsIdentifiers() {
        when(redisCacheMgr.getFromCache(anyString())).thenReturn(null);
        when(outboundRequestHandlerService.fetchResultUsingPost(anyString(), anyMap(), isNull()))
                .thenReturn(Map.of(Constants.RESULT,
                        Map.of(Constants.CONTENT, List.of(Map.of(Constants.IDENTIFIER, "LP2")))));
        when(mockUserProfileService.getUserProfile("u2")).thenReturn(Map.of("cadre", 1));
        BitSet bit = new BitSet();
        bit.set(1);
        CachedAccessSettingRule rule = mock(CachedAccessSettingRule.class);
        when(rule.getContextId()).thenReturn("LP2");
        when(rule.getContextData()).thenReturn(Map.of(Constants.ACCESS_CONTROL_ID,
                Map.of(Constants.USER_GROUPS, List.of(Map.of(
                        Constants.USER_GROUP_ID, "G1",
                        Constants.USER_GROUP_CRITERIA_LIST,
                        List.of(Map.of(Constants.CRITERIA_KEY, "cadre", Constants.CRITERIA_VALUE, bit)))))));
        when(mockAccessSettingRuleCacheMgr.getOrLoadAccessSettingRule(anyString(), anyString())).thenReturn(rule);
        when(contentInfoService.readContent(eq("LP2"), anyList())).thenReturn(Map.of(Constants.IDENTIFIER, "LP2"));
        List<String> result = ReflectionTestUtils.invokeMethod(courseAccessService, "getAssignedCourseCount", "u2",
                Constants.LEARNING_PATHWAY);
        assertEquals(1, result.size());
        assertEquals("LP2", result.get(0));
    }

    @Test
    void testGetAssignedCourseCount_WhenUnderlyingFetchFails_ReturnsEmptyList() {
        when(redisCacheMgr.getFromCache(anyString())).thenReturn(null);
        when(outboundRequestHandlerService.fetchResultUsingPost(anyString(), anyMap(), isNull()))
                .thenThrow(new RuntimeException("boom"));
        List<String> result = ReflectionTestUtils.invokeMethod(courseAccessService, "getAssignedCourseCount", "u3",
                Constants.LEARNING_PATHWAY);
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void testGetFilteredCaProgramIdentifiers_NoAssignedCourses() {
        when(redisCacheMgr.getFromCache(anyString())).thenReturn(null);
        when(outboundRequestHandlerService.fetchResultUsingPost(anyString(), anyMap(), isNull()))
                .thenReturn(Map.of(Constants.RESULT, Map.of(Constants.CONTENT, List.of())));
        List<String> result = ReflectionTestUtils.invokeMethod(courseAccessService,
                "getFilteredCaProgramIdentifiers", "u1", Map.of());
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void testGetFilteredCaProgramIdentifiers_FiltersEligibleOnes() {
        when(redisCacheMgr.getFromCache(anyString())).thenReturn(null);
        when(outboundRequestHandlerService.fetchResultUsingPost(anyString(), anyMap(), isNull()))
                .thenReturn(Map.of(Constants.RESULT, Map.of(Constants.CONTENT,
                        List.of(Map.of(Constants.IDENTIFIER, "CAP1"), Map.of(Constants.IDENTIFIER, "CAP2")))));
        when(mockUserProfileService.getUserProfile("u2")).thenReturn(Map.of("cadre", 1));
        BitSet bit = new BitSet();
        bit.set(1);
        Map<String, Object> accessControl = Map.of(Constants.USER_GROUPS, List.of(Map.of(
                Constants.USER_GROUP_ID, "G1",
                Constants.USER_GROUP_CRITERIA_LIST,
                List.of(Map.of(Constants.CRITERIA_KEY, "cadre", Constants.CRITERIA_VALUE, bit)))));

        CachedAccessSettingRule rule1 = mock(CachedAccessSettingRule.class);
        when(rule1.getContextId()).thenReturn("CAP1");
        when(rule1.getContextData()).thenReturn(Map.of(Constants.ACCESS_CONTROL_ID, accessControl));
        CachedAccessSettingRule rule2 = mock(CachedAccessSettingRule.class);
        when(rule2.getContextId()).thenReturn("CAP2");
        when(rule2.getContextData()).thenReturn(Map.of(Constants.ACCESS_CONTROL_ID, accessControl));

        when(mockAccessSettingRuleCacheMgr.getOrLoadAccessSettingRule("CAP1",
                Constants.COURSE_CATEGORY_COMPREHENSIVE_ASSESSMENT_PROGRAM)).thenReturn(rule1);
        when(mockAccessSettingRuleCacheMgr.getOrLoadAccessSettingRule("CAP2",
                Constants.COURSE_CATEGORY_COMPREHENSIVE_ASSESSMENT_PROGRAM)).thenReturn(rule2);

        Map<String, Object> content1 = new HashMap<>();
        content1.put(Constants.IDENTIFIER, "CAP1");
        content1.put(Constants.END_DATE_KEY, LocalDate.now().plusDays(10).toString());

        Map<String, Object> content2 = new HashMap<>();
        content2.put(Constants.IDENTIFIER, "CAP2");
        content2.put(Constants.END_DATE_KEY, LocalDate.now().minusDays(10).toString());

        when(contentInfoService.readContent(eq("CAP1"), anyList())).thenReturn(content1);
        when(contentInfoService.readContent(eq("CAP2"), anyList())).thenReturn(content2);

        List<String> result = ReflectionTestUtils.invokeMethod(courseAccessService,
                "getFilteredCaProgramIdentifiers", "u2", Collections.emptyMap());

        assertEquals(1, result.size());
        assertEquals("CAP1", result.get(0));
    }

    @SuppressWarnings("unchecked")
    @Test
    void testGetModeratedContentIdentifiers_CacheHitWithOrgId() throws Exception {
        String userId = "u1";
        String orgId = "org1";
        Map<String, Object> cachedOrgData = Map.of("identifiers", List.of("M1"), "count", 1);
        Map<String, Object> moderatedMap = Map.of(orgId, cachedOrgData);
        ObjectMapper realMapper = new ObjectMapper();
        String cachedJson = realMapper.writeValueAsString(moderatedMap);
        when(redisCacheMgr.getFromCache(Constants.MODERATED_COURSE_COUNT_REDIS_KEY_PREFIX + userId))
                .thenReturn(cachedJson);

        Map<String, Object> result = ReflectionTestUtils.invokeMethod(courseAccessService,
                "getModeratedContentIdentifiers", userId, orgId);

        assertNotNull(result);
        assertEquals(List.of("M1"), result.get("identifiers"));
        verify(mockUserProfileService, never()).readUserProfile(anyString(), any());
    }

    @Test
    void testGetModeratedContentIdentifiers_CacheMissBuildsAndCaches() {
        String userId = "u2";
        String orgId = "org2";
        ReflectionTestUtils.setField(courseAccessService, "moderatedCourseSearchRequest",
                "{\"request\":{\"filters\":{}}}");
        when(redisCacheMgr.getFromCache(Constants.MODERATED_COURSE_COUNT_REDIS_KEY_PREFIX + userId)).thenReturn(null);
        when(mockUserProfileService.readUserProfile(userId, null)).thenReturn(Map.of());
        Map<String, Object> searchResultMap = new HashMap<>();
        searchResultMap.put(Constants.CONTENT, List.of(Map.of(Constants.IDENTIFIER, "MOD1")));
        searchResultMap.put("count", 1);
        Map<String, Object> searchResponse = Map.of(Constants.RESULT, searchResultMap);
        when(outboundRequestHandlerService.fetchResultUsingPost(anyString(), anyMap(), isNull()))
                .thenReturn(searchResponse);

        Map<String, Object> result = ReflectionTestUtils.invokeMethod(courseAccessService,
                "getModeratedContentIdentifiers", userId, orgId);

        assertNotNull(result);
        assertEquals(List.of("MOD1"), result.get("identifiers"));
        verify(redisCacheMgr).putInCache(eq(Constants.MODERATED_COURSE_COUNT_REDIS_KEY_PREFIX + userId), anyString());
    }

    @Test
    void testGetModeratedCourseIdentifiers_VerifiedProfileSkipsFilter() throws Exception {
        ReflectionTestUtils.setField(courseAccessService, "moderatedCourseSearchRequest",
                "{\"request\":{\"filters\":{}}}");
        Map<String, Object> profileDetails = Map.of("profileStatus", "VERIFIED");
        String profileDetailsJson = new ObjectMapper().writeValueAsString(profileDetails);
        Map<String, Object> userProfileDetails = Map.of("profiledetails", profileDetailsJson);

        Map<String, Object> searchResponse = Map.of(Constants.RESULT, Map.of(Constants.CONTENT, List.of()));
        when(outboundRequestHandlerService.fetchResultUsingPost(anyString(), anyMap(), isNull()))
                .thenReturn(searchResponse);

        Map<String, Object> result = ReflectionTestUtils.invokeMethod(courseAccessService,
                "getModeratedCourseIdentifiers", "orgV", userProfileDetails);

        assertNotNull(result);
        assertEquals(List.of(), result.get("identifiers"));
    }

    @Test
    void testGetModeratedCourseIdentifiers_ExceptionReturnsDefaultResponse() {
        Map<String, Object> result = ReflectionTestUtils.invokeMethod(courseAccessService,
                "getModeratedCourseIdentifiers", "orgErr", Map.of());
        assertNotNull(result);
        assertEquals(Collections.emptyList(), result.get("identifiers"));
        assertEquals(0, result.get("count"));
    }

    @Test
    void testGetPersonalContentInfo_RealCacheHit() throws Exception {
        String testAuthToken = "tok";
        String userId = "userCacheHit";
        String orgId = "orgX";
        Map<String, Object> tokenData = Map.of("userId", userId, "org", orgId);
        when(mockAccessTokenValidator.fetchUserIdAndOrg(testAuthToken)).thenReturn(tokenData);

        Map<String, Object> cachedContent = Map.of(Constants.TRAINING_PLAN, 3);
        String cachedJson = new ObjectMapper().writeValueAsString(cachedContent);
        when(redisCacheMgr.getFromCache(Constants.PERSONAL_CONTENT_INFO_REDIS_KEY_PREFIX + userId))
                .thenReturn(cachedJson);

        ApiResponse result = courseAccessService.getPersonalContentInfo(testAuthToken);

        assertNotNull(result);
        assertEquals(3, result.getResult().get(Constants.TRAINING_PLAN));
        verify(cbPlanLearnerServiceImpl, never()).getCBPlanListForUser(anyString(), anyString(), anyBoolean());
    }

    @Test
    void testGetPersonalContentInfo_CbPlanContentEmptyList() {
        String testAuthToken = "tok2";
        String userId = "u10";
        String orgId = "org10";
        when(mockAccessTokenValidator.fetchUserIdAndOrg(testAuthToken))
                .thenReturn(Map.of("userId", userId, "org", orgId));
        when(redisCacheMgr.getFromCache(anyString())).thenReturn(null);
        ApiResponse cbPlanResponse = new ApiResponse();
        Map<String, Object> cbPlanResult = new HashMap<>();
        cbPlanResult.put(Constants.CONTENT, new ArrayList<>());
        cbPlanResponse.setResult(cbPlanResult);
        when(cbPlanLearnerServiceImpl.getCBPlanListForUser(orgId, userId, true)).thenReturn(cbPlanResponse);
        doNothing().when(redisCacheMgr).putInCache(anyString(), anyString());

        ApiResponse result = courseAccessService.getPersonalContentInfo(testAuthToken);

        assertNotNull(result);
        assertEquals(0, result.getResult().get(Constants.TRAINING_PLAN));
        assertEquals(0, result.getResult().get(Constants.APAR));
        assertEquals(0, result.getResult().get(Constants.AI_CBP));
    }

    @Test
    void testGetPersonalContentInfo_PlanCategorization() {
        String testAuthToken = "tok3";
        String userId = "u11";
        String orgId = "org11";
        when(mockAccessTokenValidator.fetchUserIdAndOrg(testAuthToken))
                .thenReturn(Map.of("userId", userId, "org", orgId));
        when(redisCacheMgr.getFromCache(anyString())).thenReturn(null);

        Map<String, Object> aparPlan = new HashMap<>();
        aparPlan.put(Constants.IS_APAR, true);
        aparPlan.put(Constants.CONTENT_LIST, List.of(Map.of(Constants.IDENTIFIER, "AP1")));

        Map<String, Object> aiCbpPlan = new HashMap<>();
        aiCbpPlan.put(Constants.IS_APAR, false);
        aiCbpPlan.put(Constants.PLAN_TYPE, Constants.PLAN_TYPE_AI_CBP);
        aiCbpPlan.put(Constants.CONTENT_LIST, List.of(Map.of(Constants.IDENTIFIER, "AI1")));

        Map<String, Object> trainingPlan = new HashMap<>();
        trainingPlan.put(Constants.IS_APAR, false);
        trainingPlan.put(Constants.PLAN_TYPE, "OTHER");
        trainingPlan.put(Constants.CONTENT_LIST, List.of(Map.of(Constants.IDENTIFIER, "TP1")));

        ApiResponse cbPlanResponse = new ApiResponse();
        Map<String, Object> cbPlanResult = new HashMap<>();
        cbPlanResult.put(Constants.CONTENT, List.of(aparPlan, aiCbpPlan, trainingPlan));
        cbPlanResponse.setResult(cbPlanResult);
        when(cbPlanLearnerServiceImpl.getCBPlanListForUser(orgId, userId, true)).thenReturn(cbPlanResponse);
        doNothing().when(redisCacheMgr).putInCache(anyString(), anyString());

        ApiResponse result = courseAccessService.getPersonalContentInfo(testAuthToken);

        assertEquals(1, result.getResult().get(Constants.TRAINING_PLAN));
        assertEquals(1, result.getResult().get(Constants.APAR));
        assertEquals(1, result.getResult().get(Constants.AI_CBP));
    }

}

