package com.igot.cb.formConfiguration.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;
import  com.vladmihalcea.hibernate.type.json.JsonType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Type;

import java.io.Serializable;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "form_configuration")
@JsonIgnoreProperties(ignoreUnknown = true)
public class FormConfigurationEntity implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", length = 250, nullable = false, unique = true)
    private String name;

    @Column(name = "type", nullable = false)
    private String type;

    @Column(name = "subtype", nullable = false)
    private String subtype;

    @Column(name = "portal", nullable = false)
    private String portal;

    @Type(JsonType.class)
    @Column(name = "criteria", columnDefinition = "jsonb")
    private JsonNode criteria;

    @Type(JsonType.class)
    @Column(name = "data", columnDefinition = "jsonb")
    private JsonNode data;

    @Column(name = "client_version", nullable = false)
    private Double clientVersion;

    @Column
    private String createdAt;

    @Column
    private String  updatedAt;

    @Column
    private String createdBy;

    @Column
    private String updatedBy;
}
