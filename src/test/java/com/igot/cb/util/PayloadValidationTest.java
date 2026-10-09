package com.igot.cb.util;



import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;


@ExtendWith(MockitoExtension.class)
class PayloadValidationTest {

  private PayloadValidation payloadValidation;

  @BeforeEach
  void setUp() {
    payloadValidation = new PayloadValidation();
  }

  @Test
  void testValidPayload() {
    Map<String, Object> payload = new HashMap<>();
    payload.put(Constants.CONTENT_ID, "content123");

    Map<String, Object> accessControl = new HashMap<>();
    List<Map<String, Object>> userGroups = new ArrayList<>();
    Map<String, Object> userGroup = new HashMap<>();
    List<Map<String, Object>> criteriaList = new ArrayList<>();
    Map<String, Object> criteria = new HashMap<>();
    criteria.put(Constants.CRITERIA_VALUE, Collections.singletonList("val1"));
    criteriaList.add(criteria);
    userGroup.put(Constants.USER_GROUP_CRITERIA_LIST, criteriaList);
    userGroups.add(userGroup);
    accessControl.put(Constants.USER_GROUPS, userGroups);

    payload.put(Constants.ACCESS_CONTROL, accessControl);

    String result = payloadValidation.validateAccessControlPayload(payload);
    assertTrue(result.isEmpty(), "Result should be empty for valid payload");
  }

  @Test
  void testMissingContentId() {
    Map<String, Object> payload = new HashMap<>();
    payload.put(Constants.ACCESS_CONTROL, new HashMap<>());

    String result = payloadValidation.validateAccessControlPayload(payload);
    assertTrue(result.contains("contentId"), "Result should mention missing contentId");
  }

  @Test
  void testBlankContentId() {
    Map<String, Object> payload = new HashMap<>();
    payload.put(Constants.CONTENT_ID, "   ");
    payload.put(Constants.ACCESS_CONTROL, new HashMap<>());

    String result = payloadValidation.validateAccessControlPayload(payload);
    assertTrue(result.contains("contentId"));
  }

  @Test
  void testMissingAccessControl() {
    Map<String, Object> payload = new HashMap<>();
    payload.put(Constants.CONTENT_ID, "content123");

    String result = payloadValidation.validateAccessControlPayload(payload);
    assertTrue(result.contains(Constants.ACCESS_CONTROL));
  }

  @Test
  void testInvalidAccessControlType() {
    Map<String, Object> payload = new HashMap<>();
    payload.put(Constants.CONTENT_ID, "content123");
    payload.put(Constants.ACCESS_CONTROL, "notAMap");

    String result = payloadValidation.validateAccessControlPayload(payload);
    assertTrue(result.contains(Constants.ACCESS_CONTROL));
  }

  @Test
  void testMissingUserGroups() {
    Map<String, Object> payload = new HashMap<>();
    payload.put(Constants.CONTENT_ID, "content123");
    Map<String, Object> accessControl = new HashMap<>();
    payload.put(Constants.ACCESS_CONTROL, accessControl);

    String result = payloadValidation.validateAccessControlPayload(payload);
    // Should expect an error for missing accessControl (empty map)
    assertTrue(result.contains(Constants.ACCESS_CONTROL));
  }

  @Test
  void testEmptyCriteriaValue() {
    Map<String, Object> payload = new HashMap<>();
    payload.put(Constants.CONTENT_ID, "content123");

    Map<String, Object> accessControl = new HashMap<>();
    List<Map<String, Object>> userGroups = new ArrayList<>();
    Map<String, Object> userGroup = new HashMap<>();
    List<Map<String, Object>> criteriaList = new ArrayList<>();
    Map<String, Object> criteria = new HashMap<>();
    criteria.put(Constants.CRITERIA_VALUE, new ArrayList<>());
    criteriaList.add(criteria);
    userGroup.put(Constants.USER_GROUP_CRITERIA_LIST, criteriaList);
    userGroups.add(userGroup);
    accessControl.put(Constants.USER_GROUPS, userGroups);

    payload.put(Constants.ACCESS_CONTROL, accessControl);

    String result = payloadValidation.validateAccessControlPayload(payload);
    assertTrue(result.contains("criteriaValue"));
  }

  @Test
  void testNullPayload() {
    String result = payloadValidation.validateAccessControlPayload(null);
    assertEquals(Constants.USER_GROUPDETAILS_ERR_VALIDATION_MSG, result);
  }

  @Test
  void testContentIdWrongType() {
    Map<String, Object> payload = new HashMap<>();
    payload.put(Constants.CONTENT_ID, 12345); // not a String
    payload.put(Constants.ACCESS_CONTROL, new HashMap<>());

    String result = payloadValidation.validateAccessControlPayload(payload);
    assertTrue(result.contains("contentId"));
  }

  @Test
  void testAccessControlNonEmptyButUserGroupsNotList() {
    Map<String, Object> payload = new HashMap<>();
    payload.put(Constants.CONTENT_ID, "content123");
    Map<String, Object> accessControl = new HashMap<>();
    accessControl.put("someOtherKey", "value"); // non-empty map, no userGroups key
    payload.put(Constants.ACCESS_CONTROL, accessControl);

    String result = payloadValidation.validateAccessControlPayload(payload);
    assertEquals("", result);
  }

  @Test
  void testUserGroupNotMap() {
    Map<String, Object> payload = new HashMap<>();
    payload.put(Constants.CONTENT_ID, "content123");
    Map<String, Object> accessControl = new HashMap<>();
    List<Object> userGroups = new ArrayList<>();
    userGroups.add("notAMap");
    accessControl.put(Constants.USER_GROUPS, userGroups);
    payload.put(Constants.ACCESS_CONTROL, accessControl);

    String result = payloadValidation.validateAccessControlPayload(payload);
    assertEquals("", result);
  }

  @Test
  void testUserGroupCriteriaListMissing() {
    Map<String, Object> payload = new HashMap<>();
    payload.put(Constants.CONTENT_ID, "content123");
    Map<String, Object> accessControl = new HashMap<>();
    List<Map<String, Object>> userGroups = new ArrayList<>();
    Map<String, Object> userGroup = new HashMap<>(); // no USER_GROUP_CRITERIA_LIST key
    userGroups.add(userGroup);
    accessControl.put(Constants.USER_GROUPS, userGroups);
    payload.put(Constants.ACCESS_CONTROL, accessControl);

    String result = payloadValidation.validateAccessControlPayload(payload);
    assertEquals("", result);
  }

  @Test
  void testCriteriaNotMap() {
    Map<String, Object> payload = new HashMap<>();
    payload.put(Constants.CONTENT_ID, "content123");
    Map<String, Object> accessControl = new HashMap<>();
    List<Map<String, Object>> userGroups = new ArrayList<>();
    Map<String, Object> userGroup = new HashMap<>();
    List<Object> criteriaList = new ArrayList<>();
    criteriaList.add("notAMap");
    userGroup.put(Constants.USER_GROUP_CRITERIA_LIST, criteriaList);
    userGroups.add(userGroup);
    accessControl.put(Constants.USER_GROUPS, userGroups);
    payload.put(Constants.ACCESS_CONTROL, accessControl);

    String result = payloadValidation.validateAccessControlPayload(payload);
    assertEquals("", result);
  }

  @Test
  void testCriteriaValueMissingKey() {
    Map<String, Object> payload = new HashMap<>();
    payload.put(Constants.CONTENT_ID, "content123");
    Map<String, Object> accessControl = new HashMap<>();
    List<Map<String, Object>> userGroups = new ArrayList<>();
    Map<String, Object> userGroup = new HashMap<>();
    List<Map<String, Object>> criteriaList = new ArrayList<>();
    Map<String, Object> criteria = new HashMap<>(); // no CRITERIA_VALUE key -> null
    criteriaList.add(criteria);
    userGroup.put(Constants.USER_GROUP_CRITERIA_LIST, criteriaList);
    userGroups.add(userGroup);
    accessControl.put(Constants.USER_GROUPS, userGroups);
    payload.put(Constants.ACCESS_CONTROL, accessControl);

    String result = payloadValidation.validateAccessControlPayload(payload);
    assertTrue(result.contains("criteriaValue"));
  }

  @Test
  void testCriteriaValueBooleanTypeIsValid() {
    Map<String, Object> payload = new HashMap<>();
    payload.put(Constants.CONTENT_ID, "content123");
    Map<String, Object> accessControl = new HashMap<>();
    List<Map<String, Object>> userGroups = new ArrayList<>();
    Map<String, Object> userGroup = new HashMap<>();
    List<Map<String, Object>> criteriaList = new ArrayList<>();
    Map<String, Object> criteria = new HashMap<>();
    criteria.put(Constants.CRITERIA_VALUE, Boolean.TRUE);
    criteriaList.add(criteria);
    userGroup.put(Constants.USER_GROUP_CRITERIA_LIST, criteriaList);
    userGroups.add(userGroup);
    accessControl.put(Constants.USER_GROUPS, userGroups);
    payload.put(Constants.ACCESS_CONTROL, accessControl);

    String result = payloadValidation.validateAccessControlPayload(payload);
    assertEquals("", result);
  }

  @Test
  void testCriteriaValueStringTypeIsValid() {
    Map<String, Object> payload = new HashMap<>();
    payload.put(Constants.CONTENT_ID, "content123");
    Map<String, Object> accessControl = new HashMap<>();
    List<Map<String, Object>> userGroups = new ArrayList<>();
    Map<String, Object> userGroup = new HashMap<>();
    List<Map<String, Object>> criteriaList = new ArrayList<>();
    Map<String, Object> criteria = new HashMap<>();
    criteria.put(Constants.CRITERIA_VALUE, "singleStringValue");
    criteriaList.add(criteria);
    userGroup.put(Constants.USER_GROUP_CRITERIA_LIST, criteriaList);
    userGroups.add(userGroup);
    accessControl.put(Constants.USER_GROUPS, userGroups);
    payload.put(Constants.ACCESS_CONTROL, accessControl);

    String result = payloadValidation.validateAccessControlPayload(payload);
    assertEquals("", result);
  }

  @Test
  void testCriteriaValueUnsupportedTypeIsInvalid() {
    Map<String, Object> payload = new HashMap<>();
    payload.put(Constants.CONTENT_ID, "content123");
    Map<String, Object> accessControl = new HashMap<>();
    List<Map<String, Object>> userGroups = new ArrayList<>();
    Map<String, Object> userGroup = new HashMap<>();
    List<Map<String, Object>> criteriaList = new ArrayList<>();
    Map<String, Object> criteria = new HashMap<>();
    criteria.put(Constants.CRITERIA_VALUE, 42.5); // Double - unsupported type
    criteriaList.add(criteria);
    userGroup.put(Constants.USER_GROUP_CRITERIA_LIST, criteriaList);
    userGroups.add(userGroup);
    accessControl.put(Constants.USER_GROUPS, userGroups);
    payload.put(Constants.ACCESS_CONTROL, accessControl);

    String result = payloadValidation.validateAccessControlPayload(payload);
    assertTrue(result.contains("criteriaValue"));
  }
}