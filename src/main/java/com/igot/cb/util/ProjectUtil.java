package com.igot.cb.util;

import java.sql.Timestamp;
import java.util.Arrays;
import java.util.Date;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

import org.apache.commons.collections4.MapUtils;
import org.joda.time.DateTime;
import org.springframework.http.HttpStatus;

import com.igot.cb.model.ApiRespParam;
import com.igot.cb.model.ApiResponse;
import com.igot.cb.service.UserAndOrgServiceImpl;

public class ProjectUtil {

    private ProjectUtil() {
    }

    public static ApiResponse createDefaultResponse(String api) {
        ApiResponse response = new ApiResponse();
        response.setId(api);
        response.setVer(Constants.API_VERSION_1);
        response.setParams(new ApiRespParam(UUID.randomUUID().toString()));
        response.getParams().setStatus(Constants.SUCCESS);
        response.setResponseCode(HttpStatus.OK);
        response.setTs(DateTime.now().toString());
        return response;
    }

    public static void errorResponse(ApiResponse response, String errorMessage, HttpStatus httpStatus) {
        response.setResponseCode(httpStatus);
        response.getParams().setErrMsg(errorMessage);
        response.getParams().setStatus(Constants.FAILED);
    }


    public static Date getTimeStamp() {
        return new Timestamp(System.currentTimeMillis());
    }

    /**
     * Sets error response with BAD_REQUEST status.
     */
    public static void setFailedResponse(ApiResponse response, String errorMessage) {
        setFailedResponse(response, errorMessage, HttpStatus.BAD_REQUEST);
    }

    /**
     * Sets error response with custom HTTP status.
     */
    public static void setFailedResponse(ApiResponse response, String errorMessage, HttpStatus httpStatus) {
        response.getParams().setStatus(Constants.FAILED);
        response.setResponseCode(httpStatus);
        response.getParams().setErrMsg(errorMessage);
    }

    public static String getRootOrgFromUser(UserAndOrgServiceImpl userAndOrgService, String userId,
            ApiResponse response) {
        Map<String, Object> userMap = userAndOrgService.readUserProfileFromDB(userId,
                Arrays.asList(Constants.ID, Constants.ROOT_ORG_ID));
        if (MapUtils.isEmpty(userMap)) {
            setFailedResponse(response, "Failed to read user details from DB. UserId: " + userId,
                    HttpStatus.INTERNAL_SERVER_ERROR);
            return null;
        }
        return (String) userMap.get(Constants.ROOT_ORG_ID);
    }

    public static Boolean validateEmailPattern(String email) {
        String emailRegex = "^[a-zA-Z0-9_+&*-]++(?:\\." + "[a-zA-Z0-9_+&*-]++)*+@" + "(?:[a-zA-Z0-9-]++\\.)++[a-z"
                + "A-Z]{2,7}$";
        Pattern pat = Pattern.compile(emailRegex);
        if (pat.matcher(email).matches()) {
            return Boolean.TRUE;
        }
        return Boolean.FALSE;
    }
}
