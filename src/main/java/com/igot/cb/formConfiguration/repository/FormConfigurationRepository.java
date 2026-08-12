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

    boolean existsByName(String name);

    /**
     * Same uniqueness check as existsByName, but excluding the row being updated — so renaming a row
     * to its own current name isn't flagged as a conflict.
     */
    boolean existsByNameAndIdNot(String name, Long id);

    /**
     * Every row sharing the same compound lookup key as a candidate create/update, regardless of its
     * own criteria shape — the full candidate set a duplicate-criteria check needs to compare against.
     */
    List<FormConfigurationEntity> findByTypeAndSubtypeAndPortalAndClientVersion(
            String type, String subtype, String portal, Double clientVersion);

    /**
     * Fallback rule for rows with no criteria at all (e.g. created via /v2/create, which never sets
     * criteria) — plain match on the compound key, no role/org/designation scoping.
     */
    Optional<FormConfigurationEntity> findByTypeAndSubtypeAndPortalAndClientVersionAndCriteriaIsNull(
            String type, String subtype, String portal, Double clientVersion);

    /**
     * Rule 1: matches on designation overlap (a user can hold more than one designation) AND the
     * row's own "ministryOrStateType" criteria array, when it declares one, containing the caller's
     * resolved type ("ministry"/"state" — resolved upstream in FormsConfigurationServiceImpl /
     * DesignationConfigurationRule). Rows that omit "ministryOrStateType" entirely (or declare an
     * empty array) are treated as unscoped/legacy and match any caller. When both a scoped row and
     * an unscoped row would otherwise match, the scoped one wins (ORDER BY).
     */
    @Query(value = """
    SELECT *
    FROM form_configuration
    WHERE type = :type
      AND subtype = :subtype
      AND portal = :portal
      AND client_version = :clientVersion
      AND EXISTS (
          SELECT 1 FROM jsonb_array_elements_text(criteria -> 'designation') AS d(designation)
          WHERE d.designation IN (:designations)
      )
      AND (
            criteria -> 'ministryOrStateType' IS NULL
            OR jsonb_array_length(criteria -> 'ministryOrStateType') = 0
            OR EXISTS (
                SELECT 1 FROM jsonb_array_elements_text(criteria -> 'ministryOrStateType') AS m(scope)
                WHERE m.scope = :ministryOrStateType
            )
          )
    ORDER BY (criteria -> 'ministryOrStateType' IS NOT NULL AND jsonb_array_length(criteria -> 'ministryOrStateType') > 0) DESC
    LIMIT 1
    """, nativeQuery = true)
    Optional<FormConfigurationEntity> getFormConfigByDesignation(
            @Param("type") String type,
            @Param("subtype") String subtype,
            @Param("portal") String portal,
            @Param("clientVersion") Double clientVersion,
            @Param("designations") List<String> designations,
            @Param("ministryOrStateType") String ministryOrStateType
    );

    /**
     * Fallback match for the "default"/role+rootOrg rule. Runs after the designation rule. The
     * "designation IS NULL" filter below means this rule's candidate set is disjoint from
     * DesignationConfigurationRule's — a row with any designation array is only ever reachable via
     * that rule, never this one, regardless of whether the caller's own designation matched it.
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
