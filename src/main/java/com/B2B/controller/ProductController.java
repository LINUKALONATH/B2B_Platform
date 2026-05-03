package com.B2B.controller;

import com.B2B.model.Product;
import com.B2B.repository.ProductRepository;
import com.B2B.repository.BusinessRepository;
import com.B2B.model.business;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api")
@CrossOrigin
public class ProductController {

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private BusinessRepository businessRepository;

    @GetMapping("/businesses")
    public List<business> getAllBusinesses() {
        return businessRepository.findAll();
    }

    @GetMapping("/products")
    public List<Product> getAllProducts() {
        return productRepository.findAll().stream()
                .filter(p -> !p.isDeleted())
                .collect(Collectors.toList());
    }

    @GetMapping("/products/{id}")
    public Product getProductById(@PathVariable int id) {
        return productRepository.findById(id).orElse(null);
    }

    @GetMapping("/admin/products/pending")
    public List<Product> getPendingProducts() {
        return productRepository.findByIsDeletedFalseAndStatus("PENDING");
    }

    @PutMapping("/admin/products/{id}/status")
    public Product updateProductStatus(@PathVariable int id, @RequestBody Map<String, String> statusData) {
        String status = statusData.get("status");
        String reason = statusData.get("reason");
        
        return productRepository.findById(id).map(p -> {
            p.setStatus(status);
            if ("REJECTED".equals(status)) {
                p.setRejectionReason(reason);
            } else {
                p.setRejectionReason(null);
            }
            return productRepository.save(p);
        }).orElse(null);
    }

    @GetMapping("/business/{id}/products")
    public List<Product> getBusinessProducts(@PathVariable int id) {
        return productRepository.findByBusinessIdAndIsDeletedFalse(id);
    }

    @PostMapping("/products")
    public Product addProduct(@RequestBody Product product) {
        if (product.getBasePrice() > 9999999) return null;
        if (product.getMoq() >= product.getStock()) return null;
        product.setStatus("PENDING");
        return productRepository.save(product);
    }

    @PutMapping("/products/{id}")
    public Product updateProduct(@PathVariable int id, @RequestBody Product productDetails) {
        return productRepository.findById(id).map(p -> {
            p.setName(productDetails.getName());
            p.setCategory(productDetails.getCategory());
            p.setSubCategory(productDetails.getSubCategory());
            p.setDescription(productDetails.getDescription());
            p.setBasePrice(productDetails.getBasePrice());
            p.setStock(productDetails.getStock());
            p.setMoq(productDetails.getMoq());
            p.setUnit(productDetails.getUnit());
            p.setImageUrl(productDetails.getImageUrl());
            p.setAdditionalImages(productDetails.getAdditionalImages());
            p.setStatus("PENDING"); // Re-verify on edit
            return productRepository.save(p);
        }).orElse(null);
    }

    @jakarta.transaction.Transactional
    @PutMapping("/products/{id}/reduce-stock")
    public Product reduceProductStock(@PathVariable int id, @RequestBody Map<String, Object> data) {
        return productRepository.findById(id).map(p -> {
            if (data.containsKey("quantity")) {
                Object qtyObj = data.get("quantity");
                int qty = (qtyObj instanceof Integer) ? (Integer) qtyObj : Integer.parseInt(qtyObj.toString());
                p.setStock(Math.max(0, p.getStock() - qty));
                return productRepository.save(p);
            }
            return p;
        }).orElse(null);
    }

    @DeleteMapping("/products/{id}")

    public Map<String, Boolean> deleteProduct(@PathVariable int id) {
        return productRepository.findById(id).map(p -> {
            p.setDeleted(true);
            productRepository.save(p);
            Map<String, Boolean> response = new HashMap<>();
            response.put("deleted", true);
            return response;
        }).orElse(null);
    }

    @GetMapping("/categories")
    public List<Map<String, String>> getCategories() {
        List<String> categories = productRepository.findAll().stream()
                .map(Product::getCategory)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
        
        // Return as objects with 'name' property as expected by frontend
        return categories.stream().map(c -> {
            Map<String, String> map = new HashMap<>();
            map.put("name", c);
            return map;
        }).collect(Collectors.toList());
    }

    @GetMapping("/products/{id}/pricing")
    public List<Map<String, Object>> getProductPricing(@PathVariable int id) {
        // Mock bulk pricing tiers
        List<Map<String, Object>> tiers = new ArrayList<>();
        
        Map<String, Object> tier1 = new HashMap<>();
        tier1.put("minQty", 100);
        tier1.put("discount", 0.05);
        tiers.add(tier1);
        
        Map<String, Object> tier2 = new HashMap<>();
        tier2.put("minQty", 500);
        tier2.put("discount", 0.15);
        tiers.add(tier2);
        
        return tiers;
    }

    @GetMapping("/products/{id}/calculate-price")
    public Map<String, Object> calculatePrice(@PathVariable int id, @RequestParam int quantity) {
        Optional<Product> productOpt = productRepository.findById(id);
        Map<String, Object> result = new HashMap<>();
        
        if (productOpt.isPresent()) {
            Product p = productOpt.get();
            double unitPrice = p.getBasePrice();
            double discount = 0;
            
            if (quantity >= 500) discount = 0.15;
            else if (quantity >= 100) discount = 0.05;
            
            double finalUnitPrice = unitPrice * (1 - discount);
            double total = finalUnitPrice * quantity;
            
            result.put("unitPrice", unitPrice);
            result.put("discount", discount * 100);
            result.put("finalUnitPrice", finalUnitPrice);
            result.put("totalPrice", total);
            result.put("savings", (unitPrice - finalUnitPrice) * quantity);
        }
        
        return result;
    }
}
