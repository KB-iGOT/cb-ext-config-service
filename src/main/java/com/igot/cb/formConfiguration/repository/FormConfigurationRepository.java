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

    /**
     * Fallback rule for rows with no criteria at all (e.g. created via /v2/create, which never sets
     * criteria) — plain match on the compound key, no role/org/designation scoping.
     */
    Optional<FormConfigurationEntity> findByTypeAndSubtypeAndPortalAndClientVersionAndCriteriaIsNull(
            String type, String subtype, String portal, Double clientVersion);

    /**
     * Rule 1: matches a row scoped to the same ministryOrStateType — stored in the criteria column
     * under the 'rootOrg' key, same as rule 2 — whose designation array overlaps the user's
     * designations (a user can hold more than one designation).
     */
    @Query(value = """
    SELECT *
    FROM form_configuration
    WHERE type = :type
      AND subtype = :subtype
      AND portal = :portal
      AND client_version = :clientVersion
      AND criteria ->> 'rootOrg' = :ministryOrStateType
      AND EXISTS (
          SELECT 1 FROM jsonb_array_elements_text(criteria -> 'designation') AS d(designation)
          WHERE d.designation IN (:designations)
      )
    LIMIT 1
    """, nativeQuery = true)
    Optional<FormConfigurationEntity> getFormConfigByDesignationAndMinistry(
            @Param("type") String type,
            @Param("subtype") String subtype,
            @Param("portal") String portal,
            @Param("clientVersion") Double clientVersion,
            @Param("ministryOrStateType") String ministryOrStateType,
            @Param("designations") List<String> designations
    );

    /**
     * Exact match for the "default"/role-only rule: only rows with NO designation at all.
     * Deliberately excludes rows that have some other designation set, so this rule never
     * accidentally answers for a differently-scoped, designation-specific row.
     */
    @Query(value = """
    SELECT *
    FROM form_configuration
    WHERE type = :type
      AND subtype = :subtype
      AND portal = :portal
      AND criteria ->> 'rootOrg' = :rootOrg
      AND criteria ->> 'role' IN (:roles)
      AND client_version = :clientVersion
      AND criteria -> 'designation' IS NULL
    LIMIT 1
    """, nativeQuery = true)
    Optional<FormConfigurationEntity> getDefaultFormConfigDataByCriteria(
            @Param("type") String type,
            @Param("subtype") String subtype,
            @Param("portal") String portal,
            @Param("rootOrg") String rootOrg,
            @Param("roles") List<String> roles,
            @Param("clientVersion") Double clientVersion
    );

}
