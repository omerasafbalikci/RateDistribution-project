package com.ratedistribution.consumerdb.repository;

import com.ratedistribution.consumerdb.model.RateEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repository interface for accessing and managing {@link RateEntity} records.
 * Provides standard CRUD operations through Spring Data JPA.
 *
 * @author Ömer Asaf BALIKÇI
 */

@Repository
public interface RateRepository extends JpaRepository<RateEntity, Long> {
}
