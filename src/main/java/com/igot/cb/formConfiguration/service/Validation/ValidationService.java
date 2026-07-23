package com.igot.cb.formConfiguration.service.Validation;

import com.igot.cb.formConfiguration.entity.FormConfigurationEntity;
import com.igot.cb.formConfiguration.repository.FormConfigurationRepository;
import com.igot.cb.util.ApiResponse;
import com.igot.cb.util.Constants;
import com.igot.cb.util.ProjectUtil;
import org.apache.commons.collections.MapUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.jclouds.rest.annotations.Api;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

@Service
public class ValidationService {

    public static final Logger LOGGER = LoggerFactory.getLogger(ValidationService.class);

    @Autowired
    private FormConfigurationRepository formConfigurationRepository;

    public String validateForm(Map<String,Object> formRequest, String operation) {
        String validationMsg = Constants.SUCCESSFUL;
        if (MapUtils.isEmpty(formRequest)) {
            validationMsg = Constants.CHECK_REQUEST_PARAMS;
        }
        // validate for create operation
        if(!formRequest.containsKey(Constants.Parameters.REQUEST) || ObjectUtils.isEmpty(formRequest.get(Constants.Parameters.REQUEST))) {
            validationMsg = Constants.CHECK_REQUEST_PARAMS;
        }


        Map<String,Object> requestObject = (Map<String, Object>) formRequest.get(Constants.Parameters.REQUEST);
        if (ObjectUtils.isEmpty(requestObject.get(Constants.TYPE)) ||
                !(requestObject.get(Constants.TYPE) instanceof String) ||
                StringUtils.isBlank((String) requestObject.get(Constants.TYPE))) {
            validationMsg = Constants.ResponseMessages.FIELD_TYPE_MISSING;
        }

        if (ObjectUtils.isEmpty(requestObject.get(Constants.SUBTYPE)) ||
                !(requestObject.get(Constants.SUBTYPE) instanceof String) ||
                StringUtils.isBlank((String) requestObject.get(Constants.SUBTYPE))) {
            validationMsg = Constants.ResponseMessages.FIELD_SUBTYPE_MISSING;
        }

        if (ObjectUtils.isEmpty(requestObject.get(Constants.PORTAL)) ||
                !(requestObject.get(Constants.PORTAL) instanceof String) ||
                StringUtils.isBlank((String) requestObject.get(Constants.PORTAL))) {
            validationMsg = Constants.ResponseMessages.FIELD_PORTAL_MISSING;
        }

        if (Constants.Parameters.CREATE.equalsIgnoreCase(operation) || Constants.Parameters.UPDATE.equalsIgnoreCase(operation)) {
            if (ObjectUtils.isEmpty(requestObject.get(Constants.NAME)) ||
                    !(requestObject.get(Constants.NAME) instanceof String) ||
                    StringUtils.isBlank((String) requestObject.get(Constants.NAME))) {
                validationMsg = Constants.ResponseMessages.FIELD_NAME_MISSING;
            } else if (((String) requestObject.get(Constants.NAME)).length() > 250) {
                validationMsg = Constants.ResponseMessages.FIELD_NAME_INVALID_LENGTH;
            }
            if(ObjectUtils.isEmpty(requestObject.get(Constants.CRITERIA))){
                validationMsg = Constants.ResponseMessages.FIELD_CRIETERIA_MISSING;
            }else if(requestObject.get(Constants.CRITERIA) instanceof Map<?,?>){

                   Map<String, Object> criteria = (Map<String, Object>) requestObject.get(Constants.CRITERIA);

                   if (!(criteria.get(Constants.ROLE) instanceof String) || StringUtils.isBlank((String) criteria.get(Constants.ROLE))) {
                       validationMsg = Constants.ResponseMessages.FIELD_ROLE_MISSING;
                   }
                   if (!(criteria.get(Constants.ROOTORG) instanceof String) || StringUtils.isBlank((String) criteria.get(Constants.ROOTORG))) {
                       validationMsg = Constants.ResponseMessages.FIELD_ROOTORG_MISSING;
                   }
               }

        }
        if (Constants.Parameters.READ.equalsIgnoreCase(operation) ) {
            if(ObjectUtils.isNotEmpty(requestObject.get(Constants.CRITERIA)) || ObjectUtils.isNotEmpty(requestObject.get(Constants.DATA)) ){
                validationMsg = Constants.ResponseMessages.BAD_REQUEST;
            }
        }


        if (ObjectUtils.isEmpty(requestObject.get(Constants.CLIENT_VERSION)) || Objects.isNull(requestObject.get(Constants.CLIENT_VERSION))) {
            validationMsg = Constants.ResponseMessages.FIELD_CLIENTVERSION_MISSING;
        }
        return  validationMsg;
    }


    public FormConfigurationEntity validateFormData(Map<String, Object> request) {
        Map<String, Object> criteria = (Map<String, Object>) request.get(Constants.CRITERIA);

        Optional<FormConfigurationEntity> formConfigurationEntity =
                formConfigurationRepository.getFormConfigDataByCriteria(
                        request.get(Constants.TYPE).toString(),
                        request.get(Constants.SUBTYPE).toString(),
                        request.get(Constants.PORTAL).toString(),
                        criteria.get(Constants.ROOTORG).toString(),
                        Collections.singletonList(criteria.get(Constants.ROLE).toString()),
                        Double.valueOf(request.get(Constants.CLIENT_VERSION).toString())
                );

        return formConfigurationEntity.orElse(null);
    }

    public String validateV2CreateForm(Map<String, Object> formRequest) {
        if (MapUtils.isEmpty(formRequest)) {
            return Constants.CHECK_REQUEST_PARAMS;
        }
        if (!formRequest.containsKey(Constants.Parameters.REQUEST) || ObjectUtils.isEmpty(formRequest.get(Constants.Parameters.REQUEST))) {
            return Constants.CHECK_REQUEST_PARAMS;
        }
        Map<String, Object> requestObject = (Map<String, Object>) formRequest.get(Constants.Parameters.REQUEST);

        for (String key : requestObject.keySet()) {
            if (!Constants.NAME.equals(key) &&
                    !Constants.TYPE.equals(key) &&
                    !Constants.SUBTYPE.equals(key) &&
                    !Constants.PORTAL.equals(key) &&
                    !Constants.CLIENT_VERSION.equals(key)) {
                return Constants.ResponseMessages.BAD_REQUEST;
            }
        }

        if (ObjectUtils.isEmpty(requestObject.get(Constants.NAME)) ||
                !(requestObject.get(Constants.NAME) instanceof String) ||
                StringUtils.isBlank((String) requestObject.get(Constants.NAME))) {
            return Constants.ResponseMessages.FIELD_NAME_MISSING;
        } else if (((String) requestObject.get(Constants.NAME)).length() > 250) {
            return Constants.ResponseMessages.FIELD_NAME_INVALID_LENGTH;
        }

        if (ObjectUtils.isEmpty(requestObject.get(Constants.TYPE)) ||
                !(requestObject.get(Constants.TYPE) instanceof String) ||
                StringUtils.isBlank((String) requestObject.get(Constants.TYPE))) {
            return Constants.ResponseMessages.FIELD_TYPE_MISSING;
        }

        if (ObjectUtils.isEmpty(requestObject.get(Constants.SUBTYPE)) ||
                !(requestObject.get(Constants.SUBTYPE) instanceof String) ||
                StringUtils.isBlank((String) requestObject.get(Constants.SUBTYPE))) {
            return Constants.ResponseMessages.FIELD_SUBTYPE_MISSING;
        }

        if (ObjectUtils.isEmpty(requestObject.get(Constants.PORTAL)) ||
                !(requestObject.get(Constants.PORTAL) instanceof String) ||
                StringUtils.isBlank((String) requestObject.get(Constants.PORTAL))) {
            return Constants.ResponseMessages.FIELD_PORTAL_MISSING;
        }

        if (ObjectUtils.isEmpty(requestObject.get(Constants.CLIENT_VERSION)) || Objects.isNull(requestObject.get(Constants.CLIENT_VERSION))) {
            return Constants.ResponseMessages.FIELD_CLIENTVERSION_MISSING;
        }

        return Constants.SUCCESSFUL;
    }

}
