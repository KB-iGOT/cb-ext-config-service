package com.igot.cb.formConfiguration.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;


@Data
@NoArgsConstructor
@AllArgsConstructor
public class FormConfigRequest {

    private String type;
    private String subtype;
    private String portal;
    private Criteria criteria;
    private Map<String,Object> data;
    private Long clientVersion;

}

