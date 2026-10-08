package com.igot.cb.util;

import java.util.*;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.igot.cb.model.ApiRequest;
import com.igot.cb.model.CbPlanDto;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

@Component
@SuppressWarnings("unchecked")
public class RequestValidator {
    private final ObjectMapper mapper = new ObjectMapper();
    private final CbExtServerProperties cbExtServerProperties;

    public RequestValidator(CbExtServerProperties cbExtServerProperties) {
        this.cbExtServerProperties = cbExtServerProperties;
    }

    public List<String> validateCbPlanCreateRequest(ApiRequest request, boolean isCCA, String loggedInOrgId) {
        return validateCbPlanCreateRequest(request, isCCA, loggedInOrgId, false);
    }

    public List<String> validateCbPlanCreateRequest(ApiRequest request, boolean isCCA, String loggedInOrgId,
            boolean isAdmin) {
        Map<String, Object> rawRequest = (Map<String, Object>) request.getRequest();
        List<String> errors = validateCbPlanRequest(rawRequest);
        if (CollectionUtils.isNotEmpty(errors)) {
            return errors;
        }
        return validateContextData(rawRequest, isCCA, loggedInOrgId, null, isAdmin);
    }

    public List<String> validateCbPlanRequest(Map<String, Object> request) {
        CbPlanDto cbPlanDto = mapper.convertValue(request, CbPlanDto.class);
        if (cbPlanDto.getIsApar() == null) {
            cbPlanDto.setIsApar(false);
        }
        request.put(Constants.IS_APAR, cbPlanDto.getIsApar() != null && cbPlanDto.getIsApar());
        List<String> validationErrors = new ArrayList<>();

        ValidatorFactory validatorFactory = Validation.buildDefaultValidatorFactory();
        Validator validator = validatorFactory.getValidator();

        Set<ConstraintViolation<CbPlanDto>> violations = validator.validate(cbPlanDto);

        // Check for violations
        if (!violations.isEmpty()) {
            for (ConstraintViolation<CbPlanDto> violation : violations) {
                String errorMessage = "Validation Error: " + violation.getMessage();
                validationErrors.add(errorMessage);
            }
        }
        return validationErrors;
    }

    public List<String> validateContextData(Map<String, Object> request, boolean isCCA, String userOrgId) {
        return validateContextData(request, isCCA, userOrgId, null, false);
    }

    public List<String> validateContextData(Map<String, Object> request, boolean isCCA, String userOrgId,
            Set<String> rootOrgIdsInCriteria) {
        return validateContextData(request, isCCA, userOrgId, rootOrgIdsInCriteria, false);
    }

    public List<String> validateContextData(Map<String, Object> request, boolean isCCA, String userOrgId,
            Set<String> rootOrgIdsInCriteria, boolean isAdmin) {
        List<String> errors = new ArrayList<>();

        Map<String, Object> contextData = resolveContextData(request, errors);
        if (!errors.isEmpty()) {
            return errors;
        }

        List<Map<String, Object>> userGroups = resolveUserGroups(contextData, errors);
        if (!errors.isEmpty()) {
            return errors;
        }

        if (rootOrgIdsInCriteria == null) {
            rootOrgIdsInCriteria = new HashSet<>();
        }
        boolean rootOrgCriteriaNotFoundInUserGroup = false;
        for (Map<String, Object> userGroup : userGroups) {
            boolean rootOrgCriteriaFound = processUserGroupCriteria(userGroup, rootOrgIdsInCriteria, errors);
            if (!errors.isEmpty()) {
                return errors;
            }
            if (!rootOrgCriteriaFound) {
                if (!isCCA) {
                    errors.add(
                            "Validation Error: ROOT_ORG_ID criteria is missing in userGroup and organization is not CCA");
                    return errors; // rootOrgId criteria is mandatory if not CCA
                } else {
                    rootOrgCriteriaNotFoundInUserGroup = true;
                }
            }
        }

        determineOrgScope(request, isCCA, userOrgId, rootOrgIdsInCriteria, isAdmin,
                rootOrgCriteriaNotFoundInUserGroup, errors);
        if (!errors.isEmpty()) {
            return errors;
        }

        if (CollectionUtils.isEmpty(errors)) {
            request.put(Constants.ORG_ID_LIST, Arrays.asList(userOrgId));
        }

        return errors;
    }

    private Map<String, Object> resolveContextData(Map<String, Object> request, List<String> errors) {
        if (!request.containsKey(Constants.CONTEXT_DATA_REQUEST)) {
            errors.add("Validation Error: contextData is missing in request");
            return Collections.emptyMap(); // no contextData = no extra validation
        }

        Map<String, Object> contextData;
        Object contextDataObj = request.get(Constants.CONTEXT_DATA_REQUEST);
        if (contextDataObj instanceof String str) {
            try {
                contextData = mapper.readValue(str, new TypeReference<Map<String, Object>>() {
                });
            } catch (Exception e) {
                errors.add("Validation Error: Failed to parse contextData");
                return Collections.emptyMap();
            }
        } else if (contextDataObj instanceof Map) {
            contextData = (Map<String, Object>) contextDataObj;
        } else {
            errors.add("Validation Error: contextData is of invalid type");
            return Collections.emptyMap();
        }

        if (MapUtils.isEmpty(contextData) || !contextData.containsKey(Constants.ACCESS_CONTROL)) {
            errors.add("Validation Error: accessControl is missing in contextData");
            return Collections.emptyMap(); // no accessControl = no extra validation
        }
        return contextData;
    }

    private List<Map<String, Object>> resolveUserGroups(Map<String, Object> contextData, List<String> errors) {
        Map<String, Object> accessControl = (Map<String, Object>) contextData.get(Constants.ACCESS_CONTROL);
        List<Map<String, Object>> userGroups = (List<Map<String, Object>>) accessControl.get(Constants.USER_GROUPS);

        if (CollectionUtils.isEmpty(userGroups)) {
            errors.add("Validation Error: User groups are missing in accessControl");
            return Collections.emptyList();
        }
        return userGroups;
    }

    private boolean processUserGroupCriteria(Map<String, Object> userGroup, Set<String> rootOrgIdsInCriteria,
            List<String> errors) {
        if (!userGroup.containsKey(Constants.USER_GROUP_CRITERIA_LIST)) {
            errors.add("Validation Error: criteriaList is missing in userGroup");
            return false; // no criteriaList = no extra validation
        }

        List<Map<String, Object>> criteriaList = (List<Map<String, Object>>) userGroup
                .get(Constants.USER_GROUP_CRITERIA_LIST);
        if (CollectionUtils.isEmpty(criteriaList)) {
            errors.add("Validation Error: criteriaList is empty in userGroup");
            return false; // empty criteriaList = no extra validation
        }

        boolean rootOrgCriteriaFound = false;
        for (Map<String, Object> criteria : criteriaList) {
            boolean matched = processSingleCriteria(criteria, rootOrgIdsInCriteria, errors);
            if (!errors.isEmpty()) {
                return false;
            }
            if (matched) {
                rootOrgCriteriaFound = true;
            }
        }
        return rootOrgCriteriaFound;
    }

    private boolean processSingleCriteria(Map<String, Object> criteria, Set<String> rootOrgIdsInCriteria,
            List<String> errors) {
        String criteriaKey = (String) criteria.get(Constants.CRITERIA_KEY);
        if (StringUtils.isEmpty(criteriaKey)) {
            errors.add("Validation Error: criteriaKey is missing in userGroup");
            return false; // no criteriaKey = no extra validation
        }
        if (!criteria.containsKey(Constants.CRITERIA_VALUE)) {
            errors.add("Validation Error: criteriaValue is missing for criteriaKey: " + criteriaKey);
            return false; // no criteriaValue = no extra validation
        }
        if (!criteria.containsKey(Constants.CRITERIA_VALUE)) {
            errors.add("Validation Error: criteriaValue is missing for criteriaKey: " + criteriaKey);
            return false;
        }

        List<String> criteriaValues = resolveCriteriaValues(criteria.get(Constants.CRITERIA_VALUE), criteriaKey,
                errors);
        if (!errors.isEmpty()) {
            return false;
        }
        criteria.put(Constants.CRITERIA_VALUE, criteriaValues);
        if (Constants.ROOT_ORG_ID.equalsIgnoreCase(criteriaKey)
                || Constants.TARGETED_ORGANISATION.equalsIgnoreCase(criteriaKey)) {
            rootOrgIdsInCriteria.addAll(criteriaValues);
            return true;
        }
        return false;
    }

    private List<String> resolveCriteriaValues(Object criteriaValueObj, String criteriaKey, List<String> errors) {
        List<String> criteriaValues;
        if (criteriaValueObj instanceof List) {
            criteriaValues = (List<String>) criteriaValueObj;
        } else if (criteriaValueObj instanceof Boolean) {
            // Convert boolean (or isOnCentralDeputation key) to string list
            criteriaValues = Collections.singletonList(String.valueOf(criteriaValueObj));
        } else if (criteriaValueObj instanceof String string) {
            // Wrap single string into a list
            criteriaValues = Collections.singletonList(string);
        } else {
            errors.add("Validation Error: Unsupported criteriaValue type for criteriaKey: "
                    + criteriaKey + ", type=" + criteriaValueObj.getClass().getSimpleName());
            return Collections.emptyList();
        }
        return criteriaValues;
    }

    private void determineOrgScope(Map<String, Object> request, boolean isCCA, String userOrgId,
            Set<String> rootOrgIdsInCriteria, boolean isAdmin, boolean rootOrgCriteriaNotFoundInUserGroup,
            List<String> errors) {
        if (isCCA) {
            determineOrgScopeForCca(request, rootOrgIdsInCriteria, rootOrgCriteriaNotFoundInUserGroup, errors);
        } else {
            determineOrgScopeForNonCca(request, userOrgId, rootOrgIdsInCriteria, isAdmin, errors);
        }
    }

    private void determineOrgScopeForCca(Map<String, Object> request, Set<String> rootOrgIdsInCriteria,
            boolean rootOrgCriteriaNotFoundInUserGroup, List<String> errors) {
        if (rootOrgIdsInCriteria.isEmpty()) {
            request.put(Constants.ORG_SCOPE, Constants.ALL);
        } else if (rootOrgCriteriaNotFoundInUserGroup) {
            errors.add(cbExtServerProperties.getMsgOnUserGroupRestrictionForAllOrg());
        } else if (rootOrgIdsInCriteria.size() == 1) {
            request.put(Constants.ORG_SCOPE, Constants.SINGLE);
        } else {
            request.put(Constants.ORG_SCOPE, Constants.CUSTOM);
        }
    }

    private void determineOrgScopeForNonCca(Map<String, Object> request, String userOrgId,
            Set<String> rootOrgIdsInCriteria, boolean isAdmin, List<String> errors) {
        if (rootOrgIdsInCriteria.size() > 1) {
            errors.add("Validation Error: Multiple ROOT_ORG_IDs found in criteria but organization is not CCA");
            return; // only one rootOrgId allowed if not CCA
        } else if (rootOrgIdsInCriteria.size() == 1) {
            String rootOrgId = rootOrgIdsInCriteria.iterator().next();
            if (!isAdmin && !StringUtils.equalsIgnoreCase(rootOrgId, userOrgId)) {
                errors.add("Validation Error: ROOT_ORG_ID in criteria does not match logged-in user's orgId");
                return; // rootOrgId must match logged-in user's orgId
            }
        } else {
            errors.add("Validation Error: No ROOT_ORG_ID found in criteria but organization is not CCA");
            return; // rootOrgId is mandatory if not CCA
        }
        request.put(Constants.ORG_SCOPE, Constants.SINGLE);
    }
}
