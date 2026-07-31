package com.igot.cb.authentication.model;

import lombok.Getter;
import lombok.Setter;
import org.springframework.stereotype.Component;

import java.util.List;

@Setter
@Getter
@Component
public class UserDetails {
    private String org;
    private List<String> userRoles;
    private String userId;
    private List<String> designations;
    private String ministryOrStateType;

}
