package com.igot.cb.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import java.security.PublicKey;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

import com.igot.cb.model.ApiResponse;
import com.igot.cb.model.KeyData;

@ExtendWith(MockitoExtension.class)
class AccessTokenValidatorTest {

    @Mock
    private KeyManager keyManager;

    @InjectMocks
    private AccessTokenValidator validator;

    @Mock
    private PropertiesCache propertiesCache;

    private static PropertiesCache propertiesCacheMock;
    private static MockedStatic<PropertiesCache> mockedPropertiesCache;
    private final String ssoUrl = "https://sso.local/";
    private final String realm = "test-realm";

    private String base64Encode(String json) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(json.getBytes());
    }

    @BeforeAll
    static void setupStatic() {
        propertiesCacheMock = mock(PropertiesCache.class);
        mockedPropertiesCache = mockStatic(PropertiesCache.class);
        mockedPropertiesCache.when(PropertiesCache::getInstance).thenReturn(propertiesCacheMock);
    }

    @BeforeEach
    void setup() {
        when(propertiesCacheMock.getProperty(Constants.SSO_URL)).thenReturn(ssoUrl);
        when(propertiesCacheMock.getProperty(Constants.SSO_REALM)).thenReturn(realm);
        // AccessTokenValidator.cache is a static field initialized once via PropertiesCache.getInstance()
        // at class-load time. If AccessTokenValidator gets loaded by an earlier test (in the same JVM fork)
        // before this class's @BeforeAll mockStatic is active, that field keeps pointing at the real
        // singleton and the stubs above have no effect. Force it to our mock explicitly so behavior is
        // deterministic regardless of class-loading/test execution order.
        ReflectionTestUtils.setField(AccessTokenValidator.class, "cache", propertiesCacheMock);
    }

    @AfterAll
    static void tearDown() {
        mockedPropertiesCache.close();
    }

    @Test
    void testValidateToken_Success() {
        String headerJson = "{\"alg\":\"RS256\",\"kid\":\"test-key\"}";
        String bodyJson = "{\"exp\":" + (System.currentTimeMillis() / 1000 + 300) + ", \"iss\":\"" + ssoUrl + "realms/"
                + realm + "\", \"sub\":\"user:123\"}";

        String header = base64Encode(headerJson);
        String body = base64Encode(bodyJson);
        String payload = header + "." + body;
        String signature = base64Encode("fake-signature");

        String token = header + "." + body + "." + signature;

        PublicKey pubKey = mock(PublicKey.class);
        KeyData keyData = mock(KeyData.class);
        when(keyData.getPublicKey()).thenReturn(pubKey);
        when(keyManager.getPublicKey("test-key")).thenReturn(keyData);

        try (MockedStatic<CryptoUtil> cryptoMock = mockStatic(CryptoUtil.class)) {
            cryptoMock.when(
                    () -> CryptoUtil.verifyRSASign(eq(payload), any(), eq(pubKey), eq(Constants.SHA_256_WITH_RSA)))
                    .thenReturn(true);

            Map<String, Object> result = validator.validateToken(token);

            assertFalse(result.isEmpty());
            assertEquals("user:123", result.get("sub"));
        }
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"invalid.token"})
    void testValidateToken_InvalidInputsReturnEmptyMap(String token) {
        Map<String, Object> result = validator.validateToken(token);
        assertTrue(result.isEmpty());
    }

    @Test
    void testValidateToken_ExpiredToken() {
        String headerJson = "{\"alg\":\"RS256\",\"kid\":\"test-key\"}";
        String bodyJson = "{\"exp\":" + (System.currentTimeMillis() / 1000 - 300) + ", \"iss\":\"" + ssoUrl + "realms/"
                + realm + "\", \"sub\":\"user:123\"}";

        String header = base64Encode(headerJson);
        String body = base64Encode(bodyJson);
        String payload = header + "." + body;
        String signature = base64Encode("fake-signature");
        String token = header + "." + body + "." + signature;

        PublicKey pubKey = mock(PublicKey.class);
        KeyData keyData = mock(KeyData.class);
        when(keyData.getPublicKey()).thenReturn(pubKey);
        when(keyManager.getPublicKey("test-key")).thenReturn(keyData);

        try (MockedStatic<CryptoUtil> cryptoMock = mockStatic(CryptoUtil.class)) {
            cryptoMock.when(
                    () -> CryptoUtil.verifyRSASign(eq(payload), any(), eq(pubKey), eq(Constants.SHA_256_WITH_RSA)))
                    .thenReturn(true);

            Map<String, Object> result = validator.validateToken(token);
            assertTrue(result.isEmpty());
        }
    }

    @Test
    void testValidateToken_InvalidSignature() {
        String headerJson = "{\"alg\":\"RS256\",\"kid\":\"test-key\"}";
        String bodyJson = "{\"exp\":" + (System.currentTimeMillis() / 1000 + 300) + ", \"iss\":\"" + ssoUrl + "realms/"
                + realm + "\", \"sub\":\"user:123\"}";

        String header = base64Encode(headerJson);
        String body = base64Encode(bodyJson);
        String payload = header + "." + body;
        String signature = base64Encode("fake-signature");
        String token = header + "." + body + "." + signature;

        PublicKey pubKey = mock(PublicKey.class);
        KeyData keyData = mock(KeyData.class);
        when(keyData.getPublicKey()).thenReturn(pubKey);
        when(keyManager.getPublicKey("test-key")).thenReturn(keyData);

        try (MockedStatic<CryptoUtil> cryptoMock = mockStatic(CryptoUtil.class)) {
            cryptoMock.when(
                    () -> CryptoUtil.verifyRSASign(eq(payload), any(), eq(pubKey), eq(Constants.SHA_256_WITH_RSA)))
                    .thenReturn(false);

            Map<String, Object> result = validator.validateToken(token);
            assertTrue(result.isEmpty());
        }
    }

    @Test
    void testValidateToken_Exception() {
        String token = "invalid.token.format";
        Map<String, Object> result = validator.validateToken(token);
        assertTrue(result.isEmpty());
    }

    @Test
    void testVerifyUserToken_Success() {
        AccessTokenValidator spy = spy(validator);
        Map<String, Object> payload = new HashMap<>();
        payload.put("iss", ssoUrl + "realms/" + realm);
        payload.put("sub", "user:123");

        doReturn(payload).when(spy).validateToken(any());
        doReturn(true).when(spy).checkIss(any());

        String result = spy.verifyUserToken("valid-token");
        assertEquals("123", result);
    }

    @Test
    void testVerifyUserToken_EmptyPayload() {
        AccessTokenValidator spy = spy(validator);
        doReturn(Collections.emptyMap()).when(spy).validateToken(any());

        String result = spy.verifyUserToken("invalid-token");
        assertEquals(Constants.UNAUTHORIZED, result);
    }

    @Test
    void testVerifyUserToken_InvalidIssuer() {
        AccessTokenValidator spy = spy(validator);
        Map<String, Object> payload = new HashMap<>();
        payload.put("iss", "invalid-issuer");
        payload.put("sub", "user:123");

        doReturn(payload).when(spy).validateToken(any());
        doReturn(false).when(spy).checkIss(any());

        String result = spy.verifyUserToken("valid-token");
        assertEquals(Constants.UNAUTHORIZED, result);
    }

    @Test
    void testVerifyUserToken_BlankUserId() {
        AccessTokenValidator spy = spy(validator);
        Map<String, Object> payload = new HashMap<>();
        payload.put("iss", ssoUrl + "realms/" + realm);
        payload.put("sub", "");

        doReturn(payload).when(spy).validateToken(any());
        doReturn(true).when(spy).checkIss(any());

        String result = spy.verifyUserToken("valid-token");
        assertEquals("", result);
    }

    @Test
    void testVerifyUserToken_Exception() {
        AccessTokenValidator spy = spy(validator);
        doThrow(new RuntimeException("Test exception")).when(spy).validateToken(any());

        String result = spy.verifyUserToken("invalid-token");
        assertEquals(Constants.UNAUTHORIZED, result);
    }

    @Test
    void testCheckIss_Success() {
        String validIssuer = ssoUrl + "realm" + realm;
        assertFalse(validator.checkIss(validIssuer));
    }

    @Test
    void testCheckIss_Mismatch() {
        assertFalse(validator.checkIss("https://fake/issuer"));
    }

    @Test
    void testCheckIss_BlankExpectedUrl() {
        when(propertiesCacheMock.getProperty(Constants.SSO_URL)).thenReturn("");
        assertFalse(validator.checkIss("any-issuer"));
    }

    @Test
    void testCheckIss_MatchingIssuer_ReturnsTrue() {
        String matchingIssuer = ssoUrl + "realms/" + realm;
        assertTrue(validator.checkIss(matchingIssuer));
    }



    @Test
    void testFetchUserIdFromAccessToken_Valid() {
        AccessTokenValidator spy = spy(validator);
        ApiResponse response = new ApiResponse();

        doReturn("123").when(spy).verifyUserToken(any());
        String userId = spy.fetchUserIdFromAccessToken("valid-token", response);
        assertEquals("123", userId);
    }

    @Test
    void testFetchUserIdFromAccessToken_Unauthorized() {
        AccessTokenValidator spy = spy(validator);
        ApiResponse response = new ApiResponse();

        doReturn(Constants.UNAUTHORIZED).when(spy).verifyUserToken(any());
        String result = spy.fetchUserIdFromAccessToken("invalid-token", response);

        assertNull(result);
        assertEquals(Constants.FAILED, response.getParams().getStatus());
        assertEquals(Constants.ACCESS_TOKEN_IS_EXPIRED, response.getParams().getErrMsg());
        assertEquals(HttpStatus.UNAUTHORIZED, response.getResponseCode());
    }

    @Test
    void testFetchUserIdFromAccessToken_Exception() {
        AccessTokenValidator spy = spy(validator);
        ApiResponse response = new ApiResponse();

        doThrow(new RuntimeException("Test exception")).when(spy).verifyUserToken(any());
        String result = spy.fetchUserIdFromAccessToken("invalid-token", response);

        assertNull(result);
        assertEquals(Constants.FAILED, response.getParams().getStatus());
        assertEquals(Constants.ACCESS_TOKEN_VALIDATION_FAILED, response.getParams().getErrMsg());
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getResponseCode());
    }

    @Test
    void testFetchUserIdFromAccessToken_NullToken() {
        ApiResponse response = new ApiResponse();

        String result = validator.fetchUserIdFromAccessToken(null, response);

        assertNull(result);
        assertEquals(Constants.FAILED, response.getParams().getStatus());
        assertEquals(Constants.ACCESS_TOKEN_VALIDATION_FAILED, response.getParams().getErrMsg());
        assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
    }

    @Test
    void testFetchUserIdAndOrg_Success() {
        AccessTokenValidator spy = spy(validator);
        Map<String, Object> payload = new HashMap<>();
        payload.put("iss", ssoUrl + "realms/" + realm);
        payload.put("sub", "user:123");
        payload.put("org", "orgA");

        doReturn(payload).when(spy).validateToken(any());
        doReturn(true).when(spy).checkIss(any());

        Map<String, Object> result = spy.fetchUserIdAndOrg("valid-token");

        assertEquals("123", result.get("userId"));
        assertEquals("orgA", result.get("org"));
    }

    @Test
    void testFetchUserIdAndOrg_EmptyPayload() {
        AccessTokenValidator spy = spy(validator);
        doReturn(Collections.emptyMap()).when(spy).validateToken(any());

        Map<String, Object> result = spy.fetchUserIdAndOrg("invalid-token");

        assertTrue(result.isEmpty());
    }

    @Test
    void testFetchUserIdAndOrg_InvalidIssuer() {
        AccessTokenValidator spy = spy(validator);
        Map<String, Object> payload = new HashMap<>();
        payload.put("iss", "invalid-issuer");
        payload.put("sub", "user:123");

        doReturn(payload).when(spy).validateToken(any());
        doReturn(false).when(spy).checkIss(any());

        Map<String, Object> result = spy.fetchUserIdAndOrg("valid-token");

        assertTrue(result.isEmpty());
    }

    @Test
    void testFetchUserIdAndOrg_BlankUserId() {
        AccessTokenValidator spy = spy(validator);
        Map<String, Object> payload = new HashMap<>();
        payload.put("iss", ssoUrl + "realms/" + realm);
        payload.put("sub", "");
        payload.put("org", "orgB");

        doReturn(payload).when(spy).validateToken(any());
        doReturn(true).when(spy).checkIss(any());

        Map<String, Object> result = spy.fetchUserIdAndOrg("valid-token");

        assertEquals("", result.get("userId"));
        assertEquals("orgB", result.get("org"));
    }

    @Test
    void testFetchUserIdAndOrg_Exception() {
        AccessTokenValidator spy = spy(validator);
        doThrow(new RuntimeException("Test exception")).when(spy).validateToken(any());

        Map<String, Object> result = spy.fetchUserIdAndOrg("invalid-token");

        assertTrue(result.isEmpty());
    }

    @Test
    void testDecodeFromBase64() {
        AccessTokenValidator spy = spy(validator);
        String testData = "test";
        
        try (MockedStatic<Base64Util> base64Mock = mockStatic(Base64Util.class)) {
            byte[] expectedBytes = testData.getBytes();
            base64Mock.when(() -> Base64Util.decode(testData, 11)).thenReturn(expectedBytes);
            
            byte[] result = spy.decodeFromBase64(testData);
            assertEquals(expectedBytes, result);
        }
    }
}
