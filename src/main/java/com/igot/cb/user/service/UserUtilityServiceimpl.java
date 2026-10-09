package com.igot.cb.user.service;

import com.igot.cb.cassandra.CassandraOperation;
import com.igot.cb.user.UserUtilityService;
import com.igot.cb.util.Constants;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;



@Service
@Slf4j
public class UserUtilityServiceimpl implements UserUtilityService {


    private Logger logger = LoggerFactory.getLogger(getClass().getName());

    private final CassandraOperation cassandraOperation;

    private final DecryptServiceImpl decryptService;

    public UserUtilityServiceimpl(CassandraOperation cassandraOperation, DecryptServiceImpl decryptService) {
        this.cassandraOperation = cassandraOperation;
        this.decryptService = decryptService;
    }


    @Override
    public void getUserDetailsFromDB(List<String> userIds, List<String> fields,
                                     Map<String, Map<String, String>> userInfoMap) {
        try {
            for (int i = 0; i < userIds.size(); i += 10) {
                List<String> userList = userIds.subList(i, Math.min(userIds.size(), i + 10));
                processUserBatch(userList, fields, userInfoMap);
            }
        } catch (Exception e) {
            logger.error("Failed to get user details from DB. Exception: ", e);
        }
    }

    private void processUserBatch(List<String> userList, List<String> fields,
                                   Map<String, Map<String, String>> userInfoMap) {
        Map<String, Object> propertyMap = new HashMap<>();
        propertyMap.put(Constants.ID, userList);

        List<Map<String, Object>> userInfoList = cassandraOperation
                .getRecordsByProperties(Constants.KEYSPACE_SUNBIRD, Constants.TABLE_USER, propertyMap, fields, null);
        for (Map<String, Object> user : userInfoList) {
            processUserRecord(user, fields, userInfoMap);
        }
    }

    private void processUserRecord(Map<String, Object> user, List<String> fields,
                                    Map<String, Map<String, String>> userInfoMap) {
        Map<String, String> userMap = new HashMap<>();
        String userId = (String) user.get(Constants.USER_ID);

        if (userInfoMap.containsKey(userId)) {
            return;
        }

        for (String field : fields) {
            if (user.containsKey(field)) {
                populateField(user, field, userId, userMap);
            }
        }
        userInfoMap.put(userId, userMap);
    }

    private void populateField(Map<String, Object> user, String field, String userId, Map<String, String> userMap) {
        if (Constants.getDecryptedFields().contains(field)) {
            if (StringUtils.isNotBlank((String) user.get(field))) {
                String value = decryptService.decryptString((String) user.get(field));
                if (StringUtils.isBlank(value)) {
                    logger.error("Invalid valid for field {} for user {}", field, userId);
                }
                userMap.put(field, value);
            }
        } else {
            userMap.put(field, (String) user.get(field));
        }
    }
}
