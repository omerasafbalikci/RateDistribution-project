package com.ratedistribution.consumerdb.repository;

import com.ratedistribution.consumerdb.model.RateEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RateRepository extends JpaRepository<RateEntity, Long> {
}
