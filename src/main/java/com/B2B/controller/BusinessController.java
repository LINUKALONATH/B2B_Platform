package com.B2B.controller;

import com.B2B.model.business;
import com.B2B.repository.BusinessRepository;
import com.B2B.service.WebAuthnService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;

@RestController
@RequestMapping("/api/business")
@CrossOrigin
public class BusinessController {

    @Autowired
    private BusinessRepository businessRepository;

    @Autowired
    private com.B2B.repository.ProductRepository productRepository;

    @Autowired
    private com.B2B.repository.OrderRepository orderRepository;

    @Autowired
    private WebAuthnService webAuthnService;

    @PostMapping("/register")
    public ResponseEntity<?> registerBusiness(@RequestBody business businessReq) {
        if (businessRepository.findByEmail(businessReq.getEmail()).isPresent()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Email is already registered. Please use another or login.");
        }
        business saved = businessRepository.save(businessReq);
        if (businessReq.getAttestationJson() != null && !businessReq.getAttestationJson().isEmpty()) {
            try {
                webAuthnService.finishRegistration(businessReq.getEmail(), saved, businessReq.getAttestationJson());
            } catch (Exception e) {
                System.err.println("Failed to finish fingerprint registration: " + e.getMessage());
                throw new RuntimeException("Fingerprint registration failed: " + e.getMessage());
            }
        }
        return ResponseEntity.ok(saved);
    }

    @PostMapping("/login")
    @jakarta.transaction.Transactional
    public ResponseEntity<?> loginBusiness(@RequestBody business loginDetails) {
        System.out.println("Login attempt for: " + loginDetails.getEmail());
        java.util.Optional<business> found = businessRepository.findByEmail(loginDetails.getEmail());
        if (found.isPresent()) {
            business b = found.get();

            // Check if account is suspended or locked
            if ("SUSPENDED".equals(b.getStatus())) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Your account has been suspended. Please contact administration.");
            }
            if ("LOCKED".equals(b.getStatus())) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Your account is locked due to too many failed attempts. Please contact administration.");
            }

            if (b.getPassword().equals(loginDetails.getPassword())) {
                // Successful login: reset failed attempts
                b.setFailedAttempts(0);
                businessRepository.save(b);
                return ResponseEntity.ok(b);
            } else {
                // Failed login attempt
                int attempts = b.getFailedAttempts() + 1;
                b.setFailedAttempts(attempts);
                if (attempts >= 5) {
                    b.setStatus("LOCKED");
                    businessRepository.save(b);
                    return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Too many failed attempts. Your account is now locked.");
                }
                businessRepository.save(b);
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid credentials. Attempts left: " + (5 - attempts));
            }
        }
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid credentials");
    }

    @PutMapping("/{id}")
    public ResponseEntity<business> updateBusiness(@PathVariable int id, @RequestBody business updatedBusiness) {
        return businessRepository.findById(id)
                .map(b -> {
                    b.setBusinessName(updatedBusiness.getBusinessName());
                    b.setIndustry(updatedBusiness.getIndustry());
                    b.setOwnerName(updatedBusiness.getOwnerName());
                    b.setEmail(updatedBusiness.getEmail());
                    b.setPhone(updatedBusiness.getPhone());
                    b.setStreet(updatedBusiness.getStreet());
                    b.setCity(updatedBusiness.getCity());
                    b.setState(updatedBusiness.getState());
                    b.setZipCode(updatedBusiness.getZipCode());
                    b.setCountry(updatedBusiness.getCountry());
                    b.setDescription(updatedBusiness.getDescription());
                    return ResponseEntity.ok(businessRepository.save(b));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    @jakarta.transaction.Transactional
    public ResponseEntity<Void> deleteBusiness(@PathVariable int id) {
        if (businessRepository.existsById(id)) {
            // Delete all products for this business
            productRepository.findAll().stream()
                .filter(p -> p.getBusiness() != null && p.getBusiness().getId() == id)
                .forEach(productRepository::delete);
            
            // Delete all orders where this business is buyer or supplier
            orderRepository.findAll().stream()
                .filter(o -> (o.getBuyer() != null && o.getBuyer().getId() == id) || 
                             (o.getSupplier() != null && o.getSupplier().getId() == id))
                .forEach(orderRepository::delete);

            businessRepository.deleteById(id);
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.notFound().build();
    }
}