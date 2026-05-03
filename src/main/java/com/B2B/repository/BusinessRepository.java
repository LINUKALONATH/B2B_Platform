package com.B2B.repository;

import com.B2B.model.business;
import org.springframework.data.jpa.repository.JpaRepository;

import org.springframework.stereotype.Repository;

@Repository
public interface BusinessRepository extends JpaRepository<business, Integer> {

    java.util.Optional<business> findByEmail(String email);
}
