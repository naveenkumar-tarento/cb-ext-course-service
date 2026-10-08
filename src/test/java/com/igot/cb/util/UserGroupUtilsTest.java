package com.igot.cb.util;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UserGroupUtilsTest {

    private Map<String, Object> criteria(Object key, Object value) {
        Map<String, Object> criteria = new HashMap<>();
        criteria.put(Constants.CRITERIA_KEY, key);
        criteria.put(Constants.CRITERIA_VALUE, value);
        return criteria;
    }

    private Map<String, Object> userGroup(List<Map<String, Object>> criteriaList) {
        Map<String, Object> userGroup = new HashMap<>();
        userGroup.put(Constants.USER_GROUP_CRITERIA_LIST, criteriaList);
        return userGroup;
    }

    @Test
    void testEmptyUserGroupsListReturnsNull() {
        String result = UserGroupUtils.validateUserGroupsNoEmptyCriteria(new ArrayList<>());
        assertNull(result);
    }

    @Test
    void testCriteriaListObjectNotAListIsSkipped() {
        Map<String, Object> userGroup = new HashMap<>();
        userGroup.put(Constants.USER_GROUP_CRITERIA_LIST, "not-a-list");
        String result = UserGroupUtils.validateUserGroupsNoEmptyCriteria(
                Collections.singletonList(userGroup));
        assertNull(result);
    }

    @Test
    void testCriteriaListObjectAbsentIsSkipped() {
        Map<String, Object> userGroup = new HashMap<>();
        String result = UserGroupUtils.validateUserGroupsNoEmptyCriteria(
                Collections.singletonList(userGroup));
        assertNull(result);
    }

    @Test
    void testEmptyCriteriaListReturnsNull() {
        List<Map<String, Object>> userGroups = Collections.singletonList(userGroup(new ArrayList<>()));
        String result = UserGroupUtils.validateUserGroupsNoEmptyCriteria(userGroups);
        assertNull(result);
    }

    @Test
    void testCriteriaKeyNullReturnsError() {
        List<Map<String, Object>> criteriaList = Collections.singletonList(criteria(null, "value"));
        List<Map<String, Object>> userGroups = Collections.singletonList(userGroup(criteriaList));
        String result = UserGroupUtils.validateUserGroupsNoEmptyCriteria(userGroups);
        assertEquals("Criteria key and value must not be empty", result);
    }

    @Test
    void testCriteriaKeyBlankReturnsError() {
        List<Map<String, Object>> criteriaList = Collections.singletonList(criteria("   ", "value"));
        List<Map<String, Object>> userGroups = Collections.singletonList(userGroup(criteriaList));
        String result = UserGroupUtils.validateUserGroupsNoEmptyCriteria(userGroups);
        assertEquals("Criteria key and value must not be empty", result);
    }

    @Test
    void testCriteriaValueNullReturnsError() {
        List<Map<String, Object>> criteriaList = Collections.singletonList(criteria("key", null));
        List<Map<String, Object>> userGroups = Collections.singletonList(userGroup(criteriaList));
        String result = UserGroupUtils.validateUserGroupsNoEmptyCriteria(userGroups);
        assertEquals("Criteria key and value must not be empty", result);
    }

    @Test
    void testCriteriaValueBlankStringReturnsError() {
        List<Map<String, Object>> criteriaList = Collections.singletonList(criteria("key", "   "));
        List<Map<String, Object>> userGroups = Collections.singletonList(userGroup(criteriaList));
        String result = UserGroupUtils.validateUserGroupsNoEmptyCriteria(userGroups);
        assertEquals("Criteria key and value must not be empty", result);
    }

    @Test
    void testCriteriaValueNonEmptyStringIsValid() {
        List<Map<String, Object>> criteriaList = Collections.singletonList(criteria("key", "someValue"));
        List<Map<String, Object>> userGroups = Collections.singletonList(userGroup(criteriaList));
        String result = UserGroupUtils.validateUserGroupsNoEmptyCriteria(userGroups);
        assertNull(result);
    }

    @Test
    void testCriteriaValueBooleanTrueIsValid() {
        List<Map<String, Object>> criteriaList = Collections.singletonList(criteria("key", Boolean.TRUE));
        List<Map<String, Object>> userGroups = Collections.singletonList(userGroup(criteriaList));
        String result = UserGroupUtils.validateUserGroupsNoEmptyCriteria(userGroups);
        assertNull(result);
    }

    @Test
    void testCriteriaValueBooleanFalseIsValid() {
        List<Map<String, Object>> criteriaList = Collections.singletonList(criteria("key", Boolean.FALSE));
        List<Map<String, Object>> userGroups = Collections.singletonList(userGroup(criteriaList));
        String result = UserGroupUtils.validateUserGroupsNoEmptyCriteria(userGroups);
        assertNull(result);
    }

    @Test
    void testCriteriaValueUnsupportedTypeReturnsError() {
        List<Map<String, Object>> criteriaList = Collections.singletonList(criteria("key", 123));
        List<Map<String, Object>> userGroups = Collections.singletonList(userGroup(criteriaList));
        String result = UserGroupUtils.validateUserGroupsNoEmptyCriteria(userGroups);
        assertEquals("Criteria value must be a String, Boolean, or List", result);
    }

    @Test
    void testCriteriaValueEmptyListReturnsError() {
        List<Map<String, Object>> criteriaList = Collections.singletonList(criteria("key", new ArrayList<>()));
        List<Map<String, Object>> userGroups = Collections.singletonList(userGroup(criteriaList));
        String result = UserGroupUtils.validateUserGroupsNoEmptyCriteria(userGroups);
        assertEquals("Criteria value list must not be empty", result);
    }

    @Test
    void testCriteriaValueListWithNullElementReturnsError() {
        List<Object> valueList = Arrays.asList("validValue", null);
        List<Map<String, Object>> criteriaList = Collections.singletonList(criteria("key", valueList));
        List<Map<String, Object>> userGroups = Collections.singletonList(userGroup(criteriaList));
        String result = UserGroupUtils.validateUserGroupsNoEmptyCriteria(userGroups);
        assertEquals("Criteria value list must not contain null values", result);
    }

    @Test
    void testCriteriaValueListWithBlankStringElementReturnsError() {
        List<Object> valueList = Arrays.asList("validValue", "   ");
        List<Map<String, Object>> criteriaList = Collections.singletonList(criteria("key", valueList));
        List<Map<String, Object>> userGroups = Collections.singletonList(userGroup(criteriaList));
        String result = UserGroupUtils.validateUserGroupsNoEmptyCriteria(userGroups);
        assertEquals("Criteria value list must not contain empty or blank values", result);
    }

    @Test
    void testCriteriaValueListWithValidStringsAndBooleansIsValid() {
        List<Object> valueList = Arrays.asList("validValue", Boolean.TRUE, Boolean.FALSE);
        List<Map<String, Object>> criteriaList = Collections.singletonList(criteria("key", valueList));
        List<Map<String, Object>> userGroups = Collections.singletonList(userGroup(criteriaList));
        String result = UserGroupUtils.validateUserGroupsNoEmptyCriteria(userGroups);
        assertNull(result);
    }

    @Test
    void testFirstValidCriteriaDoesNotShortCircuitSecondInvalidCriteria() {
        List<Map<String, Object>> criteriaList = Arrays.asList(
                criteria("validKey", "validValue"),
                criteria(null, "value"));
        List<Map<String, Object>> userGroups = Collections.singletonList(userGroup(criteriaList));
        String result = UserGroupUtils.validateUserGroupsNoEmptyCriteria(userGroups);
        assertEquals("Criteria key and value must not be empty", result);
    }

    @Test
    void testFirstValidUserGroupDoesNotShortCircuitSecondInvalidUserGroup() {
        Map<String, Object> validUserGroup = userGroup(
                Collections.singletonList(criteria("validKey", "validValue")));
        Map<String, Object> invalidUserGroup = userGroup(
                Collections.singletonList(criteria("key", null)));
        List<Map<String, Object>> userGroups = Arrays.asList(validUserGroup, invalidUserGroup);
        String result = UserGroupUtils.validateUserGroupsNoEmptyCriteria(userGroups);
        assertEquals("Criteria key and value must not be empty", result);
    }

    @Test
    void testPrivateConstructorIsInaccessibleButInstantiable() throws NoSuchMethodException,
            IllegalAccessException, InvocationTargetException, InstantiationException {
        Constructor<UserGroupUtils> constructor = UserGroupUtils.class.getDeclaredConstructor();
        assertTrue(Modifier.isPrivate(constructor.getModifiers()));
        constructor.setAccessible(true);
        UserGroupUtils instance = constructor.newInstance();
        assertNotNull(instance);
    }
}
