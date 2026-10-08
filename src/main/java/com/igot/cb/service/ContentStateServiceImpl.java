package com.igot.cb.service;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Collections;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.collections.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.igot.cb.cassandra.CassandraOperation;
import com.igot.cb.cassandra.exceptions.CustomException;
import com.igot.cb.model.ApiResponse;
import com.igot.cb.util.AccessTokenValidator;
import com.igot.cb.util.Constants;
import com.igot.cb.util.ProjectUtil;

import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class ContentStateServiceImpl {

    private final AccessTokenValidator accessTokenValidator;

    private final CassandraOperation cassandraOperation;

    private final ObjectMapper objectMapper;

    public ContentStateServiceImpl(CassandraOperation cassandraOperation, AccessTokenValidator accessTokenValidator) {
        this.cassandraOperation = cassandraOperation;
        this.accessTokenValidator = accessTokenValidator;
        this.objectMapper = new ObjectMapper();
    }

    @Value("${user.entity.consumption.allowed.fields}")
    private String allowedFieldsConfig;

    @Value("${content.state.update.required.fields}")
    private String requiredFieldsConfig;

    private static final String REQUEST_BODY_EMPTY_MSG = "Request body is empty";

    // Example date format: adjust to match your actual format
    private final SimpleDateFormat dateFormatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss:SSSZ");

    public ApiResponse readContentState(Map<String, Object> requestBody, String authToken) {
        log.info("CourseService::readContentState:inside");
        ApiResponse response = ProjectUtil.createDefaultResponse(Constants.API_CONTENT_V2_STATE_READ);
        try {
            String userId = "";
            userId = accessTokenValidator.fetchUserIdFromAccessToken(authToken, response);
            if (StringUtils.isEmpty(userId)) {
                return response;
            }
            // Payload validation
            if (MapUtils.isEmpty(requestBody)) {
                setFailedResponse(response, REQUEST_BODY_EMPTY_MSG);
                return response;
            }
            Object requestObj = requestBody.get(Constants.REQUEST);
            if (!(requestObj instanceof Map)) {
                setFailedResponse(response, "Missing or invalid 'request' object in payload");
                return response;
            }
            Map<String, Object> requestMap = (Map<String, Object>) requestObj;
            Object contentIdsObj = requestMap.get(Constants.CONTENT_IDS);
            if (!(contentIdsObj instanceof List) || ((List<?>) contentIdsObj).isEmpty()) {
                setFailedResponse(response, "'contentIds' is mandatory and should be a non-empty list");
                return response;
            }
            Map<String, Object> propertyMap = new HashMap<>();
            propertyMap.put(Constants.USER_ID_LOWER_CASE, userId);
            propertyMap.put(Constants.RESOURCE_ID, requestMap.get(Constants.CONTENT_IDS));

            Object fieldsObj = requestMap.get(Constants.FIELDS);
            log.info("fieldsObj class: {}, value: {}", fieldsObj != null ? fieldsObj.getClass() : "null", fieldsObj);
            List<String> fields = null;
            if (fieldsObj instanceof List<?> fieldsList) {
                FieldResolution resolution = resolveFields(fieldsList);
                if (!resolution.invalidFields().isEmpty()) {
                    setFailedResponse(response, "Invalid fields in request: " + resolution.invalidFields());
                    return response;
                }
                fields = resolution.fields();
            }
            List<Map<String, Object>> userContentDetails = cassandraOperation.getRecordsByProperties(
                    Constants.KEYSPACE_SUNBIRD_RESOURCE, Constants.USER_ENTITY_CONSUMPTION, propertyMap, fields, null);

            // Reverse mapping: Cassandra field -> Payload key
            Map<String, String> cassandraToPayloadMap = new HashMap<>();
            cassandraToPayloadMap.put(Constants.USER_ID_LOWER_CASE, Constants.USER_ID);
            cassandraToPayloadMap.put(Constants.RESOURCE_ID, Constants.CONTENT_ID);
            cassandraToPayloadMap.put(Constants.LAST_ACCESS_TIME_LOWER_CASE, Constants.LAST_ACCESS_TIME);
            cassandraToPayloadMap.put(Constants.LAST_COMPLETED_TIME_LOWER_CASE, Constants.LAST_COMPLETED_TIME);
            cassandraToPayloadMap.put(Constants.LAST_UPDATED_TIME_LOWER_CASE, Constants.LAST_UPDATED_TIME);
            cassandraToPayloadMap.put(Constants.PROGRESS, Constants.PROGRESS);
            cassandraToPayloadMap.put(Constants.PROGRESSDETAILS, Constants.PROGRESSDETAILS);
            cassandraToPayloadMap.put(Constants.STATUS, Constants.STATUS);
            cassandraToPayloadMap.put(Constants.COMPLETION_PERCENTAGE_LOWER_CASE, Constants.COMPLETION_PERCENTAGE);
            Set<String> allowedCassandraKeys = cassandraToPayloadMap.keySet();
            // Transform each record to use payload keys and only include allowed keys
            List<Map<String, Object>> mappedUserContentDetails = userContentDetails.stream()
                    .map(rec -> mapRecordToPayloadKeys(rec, cassandraToPayloadMap, allowedCassandraKeys))
                    .toList();

            Object convertInstantsToString = convertInstantsToString(mappedUserContentDetails);

            response.getResult().put(
                    Constants.CONTENT_LIST,
                    objectMapper.convertValue(convertInstantsToString, new TypeReference<Object>() {})
            );
            response.setResponseCode(HttpStatus.OK);
            return response;

        } catch (Exception e) {
            log.error("Error while upserting access settings", e);
            setFailedResponse(response, "Failed to create access settings: " + e.getMessage());
            return response;

        }
    }

    private record FieldResolution(List<String> fields, List<String> invalidFields) {}

    private FieldResolution resolveFields(List<?> fieldsObj) {
        List<String> allowedFields = Arrays.asList(allowedFieldsConfig.split(","));
        List<String> requestedFields = fieldsObj.stream()
                .filter(Objects::nonNull)
                .map(Object::toString)
                .toList();
        // Validate requested fields (camelCase)
        List<String> invalidFields = requestedFields.stream()
                .filter(f -> !allowedFields.contains(f))
                .toList();
        if (!invalidFields.isEmpty()) {
            return new FieldResolution(null, invalidFields);
        }
        // Map payload fields (camelCase) to Cassandra columns
        Map<String, String> payloadToCassandraMap = buildPayloadToCassandraMap();
        List<String> fields = requestedFields.stream()
                .map(f -> payloadToCassandraMap.getOrDefault(f, f))
                .toList();
        return new FieldResolution(fields, invalidFields);
    }

    private Map<String, String> buildPayloadToCassandraMap() {
        Map<String, String> payloadToCassandraMap = new HashMap<>();
        payloadToCassandraMap.put(Constants.USER_ID, Constants.USER_ID_LOWER_CASE);
        payloadToCassandraMap.put(Constants.CONTENT_ID, Constants.RESOURCE_ID);
        payloadToCassandraMap.put(Constants.LAST_ACCESS_TIME, Constants.LAST_ACCESS_TIME_LOWER_CASE);
        payloadToCassandraMap.put(Constants.LAST_COMPLETED_TIME, Constants.LAST_COMPLETED_TIME_LOWER_CASE);
        payloadToCassandraMap.put(Constants.LAST_UPDATED_TIME, Constants.LAST_UPDATED_TIME_LOWER_CASE);
        payloadToCassandraMap.put(Constants.PROGRESS, Constants.PROGRESS);
        payloadToCassandraMap.put(Constants.PROGRESSDETAILS, Constants.PROGRESSDETAILS);
        payloadToCassandraMap.put(Constants.STATUS, Constants.STATUS);
        payloadToCassandraMap.put(Constants.COMPLETION_PERCENTAGE, Constants.COMPLETION_PERCENTAGE_LOWER_CASE);
        return payloadToCassandraMap;
    }

    private Map<String, Object> mapRecordToPayloadKeys(Map<String, Object> rec, Map<String, String> cassandraToPayloadMap,
                                                         Set<String> allowedCassandraKeys) {
        Map<String, Object> mapped = new HashMap<>();
        for (Map.Entry<String, Object> entry : rec.entrySet()) {
            if (!allowedCassandraKeys.contains(entry.getKey())) continue;
            String payloadKey = cassandraToPayloadMap.getOrDefault(entry.getKey(), entry.getKey());
            if (payloadKey.equalsIgnoreCase(Constants.PROGRESSDETAILS) && entry.getValue() instanceof String string) {
                try {
                    mapped.put(payloadKey, objectMapper.readValue(string, Object.class));
                } catch (Exception ex) {
                    mapped.put(payloadKey, entry.getValue());
                }
            } else {
                mapped.put(payloadKey, entry.getValue());
            }
        }
        return mapped;
    }

    @SuppressWarnings("unchecked")
    private static Object convertInstantsToString(Object value) {
        if (value instanceof Instant) {
            return value.toString();
        } else if (value instanceof Map) {
            Map<String, Object> convertedMap = new HashMap<>();
            ((Map<?, ?>) value).forEach((k, v) ->
                    convertedMap.put(String.valueOf(k), convertInstantsToString(v))
            );
            return convertedMap;
        } else if (value instanceof List) {
            return ((List<?>) value).stream()
                    .map(ContentStateServiceImpl::convertInstantsToString)
                    .toList();
        } else {
            return value;
        }
    }



    public ApiResponse updateContentState(Map<String, Object> requestBody, String authToken) {
        log.info("CourseService::readContentState:inside");
        ApiResponse response = ProjectUtil.createDefaultResponse(Constants.API_CONTENT_V2_STATE_READ);
        try {
            String userId = accessTokenValidator.fetchUserIdFromAccessToken(authToken, response);
            if (StringUtils.isEmpty(userId)) {
                return response;
            }
            // Payload validation
            if (MapUtils.isEmpty(requestBody)) {
                setFailedResponse(response, REQUEST_BODY_EMPTY_MSG);
                return response;
            }
            String errMsg = validateContentStateUpdatePayload(requestBody);
            if (StringUtils.isNotBlank(errMsg)) {
                setFailedResponse(response, errMsg);
                return response;
            }
            Object requestObj = requestBody.get(Constants.REQUEST);
            if (requestObj instanceof Map) {
                Map<String, Object> requestMap = (Map<String, Object>) requestObj;
                Object contentsObj = requestMap.get(Constants.CONTENTS);
                if (contentsObj instanceof List && !((List<?>) contentsObj).isEmpty()) {
                    List<Map<String, Object>> contents = (List<Map<String, Object>>) contentsObj;
                    Map<String, String> payloadToCassandraMap = buildPayloadToCassandraMap();
                    for (Map<String, Object> content : contents) {
                        Object contentId = content.get(Constants.CONTENT_ID);
                        Map<String, Object> propertyMap = new HashMap<>();
                        propertyMap.put(Constants.USER_ID_LOWER_CASE, userId);
                        propertyMap.put(Constants.RESOURCE_ID, contentId);
                        List<Map<String, Object>> userContentDetails = cassandraOperation.getRecordsByProperties(
                                Constants.KEYSPACE_SUNBIRD_RESOURCE, Constants.USER_ENTITY_CONSUMPTION, propertyMap, null, null);
                        Map<String, Object> processContentConsumption = processContentConsumption(
                                content, userContentDetails.isEmpty() ? null : userContentDetails.get(0), userId);
                        Map<String, Object> cassandraMap = processContentConsumption.entrySet().stream()
                                .collect(Collectors.toMap(
                                        entry -> payloadToCassandraMap.getOrDefault(entry.getKey(), entry.getKey()),
                                        Map.Entry::getValue
                                ));
                        cassandraOperation.insertRecord(Constants.KEYSPACE_SUNBIRD_RESOURCE, Constants.USER_ENTITY_CONSUMPTION, cassandraMap);
                        response.getResult().put((String) contentId, Constants.SUCCESS);
                    }
                }
            }

            return response;

        } catch (Exception e) {
            log.error("Error while upserting access settings", e);
            setFailedResponse(response, "Failed to create access settings: " + e.getMessage());
            return response;
        }
    }

    private String validateContentStateUpdatePayload(Map<String, Object> requestBody) {
        List<String> errList = new ArrayList<>();
        Object requestObj = requestBody.get(Constants.REQUEST);
        if (!(requestObj instanceof Map)) {
            errList.add(Constants.REQUEST);
        } else {
            Map<String, Object> requestMap = (Map<String, Object>) requestObj;
            Object contentsObj = requestMap.get(Constants.CONTENTS);
            if (!(contentsObj instanceof List)) {
                errList.add(Constants.CONTENTS);
            } else {
                List<String> requiredAttributes = Arrays.asList(requiredFieldsConfig.split(","));
                validateContentsList((List<?>) contentsObj, requiredAttributes, errList);
            }
        }
        if (!errList.isEmpty()) {
            return "Missing or invalid fields: " + errList;
        }
        return "";
    }

    private void validateContentsList(List<?> contents, List<String> requiredAttributes, List<String> errList) {
        for (int i = 0; i < contents.size(); i++) {
            Object contentObj = contents.get(i);
            if (contentObj instanceof Map) {
                Map<String, Object> content = (Map<String, Object>) contentObj;
                for (String attr : requiredAttributes) {
                    Object value = content.get(attr);
                    if (value == null || (value instanceof String string && StringUtils.isBlank(string))) {
                        errList.add("contents[" + i + "]." + attr);
                    }
                }
            } else {
                errList.add("contents[" + i + "]");
            }
        }
    }

    public Map<String, Object> processContentConsumption(
            Map<String, Object> inputContent,
            Map<String, Object> existingContent,
            String userId) throws JsonProcessingException {

        int inputStatus = ((Number) inputContent.getOrDefault(Constants.STATUS, 0)).intValue();
        Map<String, Object> updatedContent = new HashMap<>(inputContent);

        applyProgressDetailsJson(inputContent, updatedContent);

        Date inputCompletedTime = parseDate((String) inputContent.getOrDefault(Constants.LAST_COMPLETED_TIME, ""));
        Date inputAccessTime = parseDate((String) inputContent.getOrDefault(Constants.LAST_ACCESS_TIME, ""));
        Object completionPercentage = updatedContent.get(Constants.COMPLETION_PERCENTAGE);
        double completionValue = validateAndConvertCompletionPercentage(completionPercentage);
        updatedContent.put(Constants.COMPLETION_PERCENTAGE, completionValue);

        if (existingContent != null && !existingContent.isEmpty()) {
            mergeWithExistingContent(updatedContent, inputContent, existingContent, inputAccessTime, inputCompletedTime, inputStatus);
        } else {
            applyNewContentDefaults(updatedContent, inputAccessTime, inputCompletedTime, inputStatus);
        }

        updatedContent.put(Constants.LAST_UPDATED_TIME, Instant.now());
        updatedContent.put(Constants.USER_ID, userId);
        updatedContent.replaceAll((k, v) -> v instanceof Date date ? date.toInstant() : v);
        return updatedContent;
    }

    private void applyProgressDetailsJson(Map<String, Object> inputContent, Map<String, Object> updatedContent) throws JsonProcessingException {
        Map<String, Object> parsedMap = new HashMap<>();
        Set<String> jsonFields = new HashSet<>();
        jsonFields.add("progressdetails");
        for (String field : jsonFields) {
            if (inputContent.containsKey(field)) {
                parsedMap.put(field, objectMapper.writeValueAsString(inputContent.get(field)));
            }
        }
        updatedContent.putAll(parsedMap);
    }

    private double validateAndConvertCompletionPercentage(Object completionPercentage) {
        double value;
        if (completionPercentage instanceof Integer integer) {
            value = integer.doubleValue();
        } else if (completionPercentage instanceof Double aDouble) {
            value = aDouble;
        } else {
            throw new CustomException(
                    "INVALID_COMPLETION_PERCENTAGE_TYPE",
                    "Completion percentage must be a number.",
                    HttpStatus.BAD_REQUEST
            );
        }
        if (value < 0.0 || value > 100.0) {
            throw new CustomException(
                    "INVALID_COMPLETION_PERCENTAGE",
                    "Completion percentage must be between 0 and 100.",
                    HttpStatus.BAD_REQUEST
            );
        }
        return value;
    }

    private Date resolveExistingDate(Map<String, Object> existingContent, String primaryKey, String fallbackKey) {
        Object dateObj = existingContent.get(primaryKey);
        Date date;
        if (dateObj instanceof String string) {
            date = parseDate(string);
        } else if (dateObj instanceof Date d) {
            date = d;
        } else {
            date = null;
        }
        if (date == null) {
            date = parseDate((String) existingContent.getOrDefault(fallbackKey, ""));
        }
        return date;
    }

    private void mergeWithExistingContent(Map<String, Object> updatedContent, Map<String, Object> inputContent,
                                           Map<String, Object> existingContent, Date inputAccessTime,
                                           Date inputCompletedTime, int inputStatus) {
        Date existingAccessTime = resolveExistingDate(existingContent, Constants.LAST_ACCESS_TIME, Constants.OLD_LAST_ACCESS_TIME);
        updatedContent.put(Constants.LAST_ACCESS_TIME, compareTime(existingAccessTime, inputAccessTime));

        int inputProgress = ((Number) inputContent.getOrDefault(Constants.PROGRESS, 0)).intValue();
        int existingProgress = ((Number) existingContent.getOrDefault(Constants.PROGRESS, 0)).intValue();
        updatedContent.put(Constants.PROGRESS, Math.max(inputProgress, existingProgress));

        int existingStatus = ((Number) existingContent.getOrDefault(Constants.STATUS, 0)).intValue();
        Date existingCompletedTime = resolveExistingDate(existingContent, Constants.LAST_COMPLETED_TIME, Constants.OLD_LAST_COMPLETED_TIME);

        if (inputStatus >= existingStatus) {
            if (inputStatus >= 2) {
                updatedContent.put(Constants.STATUS, 2);
                updatedContent.put(Constants.PROGRESS, 100);
                updatedContent.put(Constants.LAST_COMPLETED_TIME, compareTime(existingCompletedTime, inputCompletedTime));
            }
        } else {
            updatedContent.put(Constants.STATUS, existingStatus);
        }
    }

    private void applyNewContentDefaults(Map<String, Object> updatedContent, Date inputAccessTime,
                                          Date inputCompletedTime, int inputStatus) {
        if (inputStatus >= 2) {
            updatedContent.put(Constants.PROGRESS, 100);
            updatedContent.put(Constants.LAST_COMPLETED_TIME, compareTime(null, inputCompletedTime));
            updatedContent.put(Constants.STATUS, 2);
        } else {
            updatedContent.put(Constants.PROGRESS, 0);
        }
        updatedContent.put(Constants.LAST_ACCESS_TIME, compareTime(null, inputAccessTime));
    }
    public Date parseDate(String dateString) {
        if (StringUtils.isNotBlank(dateString) && !StringUtils.equalsIgnoreCase(Constants.NULL, dateString)) {
            try {
                return dateFormatter.parse(dateString);
            } catch (ParseException e) {
                log.error("Error parsing date: {}", dateString, e);
                // Log and return null if date format is invalid
                 // Replace with proper logging in production
            }
        }
        return null;
    }

    // Method to map payload keys to Cassandra columns
    public static Map<String, Object> mapPayloadToCassandraColumns(Map<String, Object> payload, Map<String, String> mapping) {
        Map<String, Object> cassandraMap = new HashMap<>();
        for (Map.Entry<String, Object> entry : payload.entrySet()) {
            String cassandraKey = mapping.getOrDefault(entry.getKey(), entry.getKey());
            cassandraMap.put(cassandraKey, entry.getValue());
        }
        return cassandraMap;
    }

    private void setFailedResponse(ApiResponse response, String errorMessage) {
        response.getParams().setStatus(Constants.FAILED);
        response.setResponseCode(HttpStatus.BAD_REQUEST);
        response.getParams().setErrMsg(errorMessage);
    }

    private Date compareTime(Date existingTime, Date inputTime) {
        if (existingTime == null && inputTime == null) {
            return ProjectUtil.getTimeStamp();
        } else if (existingTime == null) {
            return inputTime;
        } else if (inputTime == null) {
            return existingTime;
        } else {
            return inputTime.after(existingTime) ? inputTime : existingTime;
        }
    }

    public ApiResponse readUserContentConsumptionV2(Map<String, Object> requestBody) {
        log.info("CourseService::readUserContentConsumptionV2:inside");
        ApiResponse response = ProjectUtil.createDefaultResponse(Constants.API_USER_CONTENT_CONSUMPTION_V2_READ);
        try {
            if (MapUtils.isEmpty(requestBody)) {
                setFailedResponse(response, REQUEST_BODY_EMPTY_MSG);
                return response;
            }

            Map<String, Object> requestMap = validateAndGetRequest(response, requestBody);
            if (requestMap == null) return response;

            // Validate and extract mandatory fields
            String[] mandatoryFields = {Constants.USER_ID, Constants.COURSE_ID, Constants.BATCH_ID};
            for (String field : mandatoryFields) {
                String value = (String) requestMap.get(field);
                if (StringUtils.isBlank(value)) {
                    setFailedResponse(response, field + " is mandatory and cannot be empty or null");
                    return response;
                }
            }

            // Build property map and fetch records
            Map<String, Object> propertyMap = new HashMap<>();
            propertyMap.put(Constants.USER_ID, requestMap.get(Constants.USER_ID));
            propertyMap.put(Constants.COURSE_ID, requestMap.get(Constants.COURSE_ID));
            propertyMap.put(Constants.BATCH_ID, requestMap.get(Constants.BATCH_ID));

            List<String> fields = extractFieldsList(requestMap);
            List<Map<String, Object>> records = cassandraOperation.getRecordsByProperties(
                    Constants.KEYSPACE_SUNBIRD_COURSE, Constants.USER_CONTENT_CONSUMPTION_V2,
                    propertyMap, fields, null);

            log.info("Retrieved {} consumption records", records.size());

            // Transform records and set response
            List<Map<String, Object>> transformed = records.stream()
                    .map(this::transformRecord)
                    .toList();

            response.getResult().put(Constants.CONSUMPTION_RECORDS,
                    objectMapper.convertValue(convertInstantsToString(transformed), new TypeReference<Object>() {}));
            response.setResponseCode(HttpStatus.OK);
            return response;

        } catch (Exception e) {
            log.error("Error while reading user content consumption v2 records", e);
            setFailedResponse(response, "Failed to read consumption records: " + e.getMessage());
            return response;
        }
    }

    private Map<String, Object> validateAndGetRequest(ApiResponse response, Map<String, Object> requestBody) {
        Object requestObj = requestBody.get(Constants.REQUEST);
        if (!(requestObj instanceof Map)) {
            setFailedResponse(response, "Missing or invalid 'request' object in payload");
            return null;
        }
        return (Map<String, Object>) requestObj;
    }

    private List<String> extractFieldsList(Map<String, Object> requestMap) {
        Object fieldsObj = requestMap.get(Constants.FIELDS);
        if (fieldsObj instanceof List && !((List<?>) fieldsObj).isEmpty()) {
            List<String> fields = ((List<?>) fieldsObj).stream()
                    .filter(Objects::nonNull)
                    .map(Object::toString)
                    .toList();
            log.info("Requested fields: {}", fields);
            return fields;
        }
        log.info("No specific fields requested, fetching all fields");
        return Collections.emptyList();
    }

    private Map<String, Object> transformRecord(Map<String, Object> rec) {
        Map<String, Object> transformed = new HashMap<>();
        rec.forEach((key, value) -> {
            if (Constants.PROGRESSDETAILS.equalsIgnoreCase(key) && value instanceof String string) {
                try {
                    transformed.put(key, objectMapper.readValue(string, Object.class));
                } catch (Exception ex) {
                    log.error("Error parsing progressdetails JSON: {}", value, ex);
                    transformed.put(key, value);
                }
            } else {
                transformed.put(key, value);
            }
        });
        return transformed;
    }
}
