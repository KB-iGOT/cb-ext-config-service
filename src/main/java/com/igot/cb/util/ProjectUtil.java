package com.igot.cb.util;

import org.joda.time.DateTime;
import org.springframework.http.HttpStatus;

import java.util.UUID;

public class ProjectUtil {

    public static ApiResponse createDefaultResponse(String api) {
        ApiResponse response = new ApiResponse();
        response.setId(api);
        response.setVer(Constants.API_VERSION_1);
        response.setResponseCode(HttpStatus.OK);
        response.setTs(DateTime.now().toString());
        return response;
    }

    public static ApiResponse returnErrorMsg(String error, HttpStatus type, ApiResponse response, String status) {
        response.setResponseCode(type);
      //  response.getParams().setErr(error);
        response.setMessage(status);
        return response;
    }

}
