package com.igot.cb.formConfiguration.repository;


import com.igot.cb.formConfiguration.entity.FormConfigurationEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface FormConfigurationRepository extends JpaRepository<FormConfigurationEntity,Long> {
    @Query("""
           SELECT w
           FROM FormConfigurationEntity w
           WHERE w.type = :type
             AND w.subtype = :subtype
             AND w.portal = :portal
           """)
    Optional<FormConfigurationEntity> getformConfigData(
            @Param("type") String type,
            @Param("subtype") String subtype,
            @Param("portal") String portal);

}
