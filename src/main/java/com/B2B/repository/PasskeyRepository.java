package com.B2B.repository;

import com.B2B.model.PasskeyCredential;
import com.B2B.model.business;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PasskeyRepository extends JpaRepository<PasskeyCredential, Long> {
    Optional<PasskeyCredential> findByCredentialId(String credentialId);

    List<PasskeyCredential> findByBusiness(business business);
}
