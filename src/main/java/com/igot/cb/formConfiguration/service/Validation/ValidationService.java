package com.igot.cb.formConfiguration.service.Validation;

import com.igot.cb.formConfiguration.entity.FormConfigurationEntity;
import com.igot.cb.formConfiguration.repository.FormConfigurationRepository;
import com.igot.cb.util.ApiResponse;
import com.igot.cb.util.Constants;
import org.apache.commons.collections.MapUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

@Service
public class ValidationService {

    public static final Logger LOGGER = LoggerFactory.getLogger(ValidationService.class);

    @Autowired
    private FormConfigurationRepository formConfigurationRepository;

    public String validateForm(Map<String,Object> formRequest,String operation) {

        if (MapUtils.isEmpty(formRequest)) {
            return Constants.CHECK_REQUEST_PARAMS;
        }
        // validate for create operation
        if(!formRequest.containsKey(Constants.Parameters.REQUEST) && ObjectUtils.isEmpty(formRequest.get(Constants.Parameters.REQUEST))) {
            return Constants.CHECK_REQUEST_PARAMS;
        }


        Map<String,Object> requestObject = (Map<String, Object>) formRequest.get(Constants.Parameters.REQUEST);
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

        if (Constants.Parameters.CREATE.equalsIgnoreCase(operation) || Constants.Parameters.READ.equalsIgnoreCase(operation)) {
            if(ObjectUtils.isEmpty(requestObject.get(Constants.CRITERIA))){
                   return Constants.ResponseMessages.FIELD_CRIETERIA_MISSING;
            }else if(requestObject.get(Constants.CRITERIA) instanceof Map<?,?>){

                   Map<String, Object> criteria = (Map<String, Object>) requestObject.get(Constants.CRITERIA);

                   if (!(criteria.get(Constants.ROLE) instanceof String) || StringUtils.isBlank((String) criteria.get(Constants.ROLE))) {
                       return Constants.ResponseMessages.FIELD_ROLE_MISSING;
                   }
                   if (!(criteria.get(Constants.ROOTORG) instanceof String) || StringUtils.isBlank((String) criteria.get(Constants.ROOTORG))) {
                       return Constants.ResponseMessages.FIELD_ROOTORG_MISSING;
                   }
               }

        }


        if (ObjectUtils.isEmpty(requestObject.get(Constants.CLIENT_VERSION)) || Objects.isNull(requestObject.get(Constants.CLIENT_VERSION))) {
            return Constants.ResponseMessages.FIELD_CLIENTVERSION_MISSING;
        }

        return Constants.SUCCESSFUL;

    }


    public FormConfigurationEntity validateFormData(Map<String, Object> request, ApiResponse response) {
        Optional<FormConfigurationEntity> formConfigurationEntity = formConfigurationRepository.getformConfigData(request.get(Constants.TYPE).toString(),request.get("subType").toString(),request.get(Constants.PORTAL).toString());
        return formConfigurationEntity.orElse(null);
    }

}
