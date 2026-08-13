package com.igot.cb.util;

import java.util.HashMap;

/**
 * @author Mahesh RV
 */

public interface Constants {

    interface ServiceRepositories {
        static final String FORM_SERVICE = "formsService";
        static final String FORM_REPO = "formDao";
        static final String ELASTICSEARCH_REPO = "elasticSearchRepository";
    }

    interface RequestMethods {
        static final String GET = "GET";
        static final String POST = "POST";
        static final String OPTIONS = "OPTIONS";
        static final String DELETE = "DELETE";
        static final String PUT = "PUT";
    }

    interface ResponseCodes {
        static final int UNAUTHORIZED_ID = 401;
        static final int SUCCESS_ID = 200;
        static final int FAILURE_ID = 320;
        static final String UNAUTHORIZED = "Invalid credentials. Please try again.";
        static final String PROCESS_FAIL = "Process failed, Please try again.";
        static final String SUCCESS = "success";
    }

    interface Parameters {
        static final String REQUEST = "request";
        static final String X_AUTH_TOKEN = "x-authenticated-user-token";
        static final String CREATE = "create";
        static final String READ = "read";
        static final String UPDATE = "update";
        static final String DELETE = "delete";
    }

    interface WorkflowActions {
        static final String SAVE_FORM_NOTES = "Save Form Notes";
        static final String ASSIGN_INSPECTOR = "Assign Inspector";
        static final String RETURN_APPLICATION = "Return Application";
        static final String COMPLETED_INSPECTION = "Complete Inspection";
        static final String APPROVE_APPLICATION = "Approve Application";
        static final String REJECT_APPLICATION = "Reject Application";
        static final String LEAD_INSPECTION_COMPLETED = "Lead inspection";
    }

    interface ResponseMessages {

        // form Validation
        static final String ERROR_MESSAGE = "Unable to fetch the details. Please try again later!";
        static final String FIELD_PORTAL_MISSING = "Field Portal Missing";
        static final String FIELD_SUBTYPE_MISSING = "Field Subtype Missing";
        static final String FIELD_CRIETERIA_MISSING = "Field Criteria Missing";
        static final String FIELD_ROOTORG_MISSING = "Field RootOrg Missing";
        static final String FIELD_ROLE_MISSING = "Field Role Missing";
        static final String FIELD_TYPE_MISSING = "Field type is missing";
        static final String INVALID_CLIENT_VERSION = "Invalid client version";
        static final String FIELD_CLIENTVERSION_MISSING = "Field clientVersion is missing";
        static final String FIELD_NAME_MISSING = "Field name is missing";
        static final String FIELD_NAME_INVALID_LENGTH = "Field name length exceeds 250 characters";
        static final String FIELD_MINISTRY_OR_STATE_TYPE_MISSING = "Field ministryOrStateType is missing";
        static final String FIELD_NAME_ALREADY_EXISTS = "Form configuration with this name already exists";
        static final String FIELD_CRITERIA_ALREADY_EXISTS = "Duplicate form configuration detected for the selected Type, Sub-Type, Portal, and Client Version. Please review and remove the duplicate.";

        static final String BAD_REQUEST = "Bad Request";

    }


    public static final String CLIENT_VERSION = "clientVersion";
    public static final String NAME = "name";


    public static final String SUCCESSFUL = "successful";
    public static final String FAILED = "failed";
    public static final String API_VERSION_1 = "1.0";

    interface ServiceTypes {
        public static final String APPLICATION = "Application";
    }



    public static final String CREATE_FORMS_CONFIG_API = "api.form.create";
    public static final String UPDATE_FORMS_CONFIG_API = "api.form.update";
    public static final String READ_FORMS_CONFIG_API = "api.form.read";
    public static final String TIME_FORMAT = "yyyy-MM-dd'T'HH:mm:ss.SSSZ";
    public static final String INVALID_AUTH_TOKEN = "";
    public static final String CREATED_ON = "";
    public static final String SUBTYPE ="subType" ;
    public static final String PORTAL ="portal" ;
    public static final String DATA ="data" ;
    public static final String FORM ="form" ;
    public static final String ROLE ="role";
    public static final String ROOTORG ="rootOrg";
    public static final String DESIGNATION = "designation";
    public static final String MINISTRY_OR_STATE_TYPE = "ministryOrStateType";
    public static final String SB_ORG_TYPE = "sbOrgType";
    public static final String MINISTRY = "ministry";
    public static final String STATE = "state";
    public static final String ORGANISATION_ID = "organisationId";
    static final String TYPE ="type" ;
    public static final String CRITERIA ="criteria" ;
    public static final String CHECK_REQUEST_PARAMS="Invalid Payload";
    public static final String ERROR = "ERROR";
    static final boolean TRUE = true;
    static final boolean FALSE = false ;

    public static final String ORG_READ_BASE_URL = "/v1/org/read";
    public static final String USER_READ_BASE_URL = "/v5/user/read";
    public static final String LMS_SER_HOST = "lms.service.host";

    public static final String HEADER_AUTHORIZATION = "Authorization";

    public static final String SSO_URL = "sso.url";
    public static final String SSO_REALM = "sso.realm";
    public static final String DOT_SEPARATOR = ".";
    public static final String ACCESS_TOKEN_PUBLICKEY_BASEPATH = "accesstoken.publickey.basepath";
    public static final String UNAUTHORIZED = "Unauthorized";
    public static final String SHA_256_WITH_RSA = "SHA256withRSA";
    public static final String SUB = "sub";
    public static final String FORM_CONFIG_RESULT="form.config.result";

    public static final String API_REDIS_DELETE = "api.redis.delete";
    public  static final String ERROR_REDIS_KEY_NOTFOUND = "No Keys found, Redis cache is empty";
    public static final String X_AUTH_USER_ORG_ID = "x-authenticated-user-orgid";
    public static final String X_AUTH_USER_ROLES = "x-authenticated-user-roles";
    ;
}
