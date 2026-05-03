package com.B2B.repository;

import com.B2B.model.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface ProductRepository extends JpaRepository<Product, Integer> {
    List<Product> findByCategory(String category);
    List<Product> findByIsDeletedFalseAndStatus(String status);
    List<Product> findByBusinessIdAndIsDeletedFalse(int businessId);
}
