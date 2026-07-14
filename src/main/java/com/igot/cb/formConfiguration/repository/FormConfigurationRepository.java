package com.igot.cb.formConfiguration.repository;


import com.igot.cb.formConfiguration.entity.FormConfigurationEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface FormConfigurationRepository extends JpaRepository<FormConfigurationEntity,Long> {
   @Query(value = """
    SELECT *
    FROM form_configuration
    WHERE type = :type
      AND subtype = :subtype
      AND portal = :portal
      AND criteria ->> 'rootOrg' = :rootOrg
      AND criteria ->> 'role' IN (:roles)
    LIMIT 1
    """, nativeQuery = true)
    Optional<FormConfigurationEntity> getFormConfigDataByCriteria(
            @Param("type") String type,
            @Param("subtype") String subtype,
            @Param("portal") String portal,
            @Param("rootOrg") String rootOrg,
            @Param("roles") List<String> roles
    );

    @Query(value = """
    SELECT *
    FROM form_configuration
    WHERE type = :type
      AND subtype = :subtype
      AND portal = :portal
      AND criteria ->> 'rootOrg' = :rootOrg
      AND criteria ->> 'role' IN (:roles)
      AND client_version = :clientVersion
    LIMIT 1
    """, nativeQuery = true)
    Optional<FormConfigurationEntity> getFormConfigDataByCriteriaAndVersion(
            @Param("type") String type,
            @Param("subtype") String subtype,
            @Param("portal") String portal,
            @Param("rootOrg") String rootOrg,
            @Param("roles") List<String> roles,
            @Param("clientVersion") Double clientVersion
    );

}
