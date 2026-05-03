package com.B2B.controller;

import com.B2B.model.Admin;
import com.B2B.model.business;
import com.B2B.repository.AdminRepository;
import com.B2B.repository.BusinessRepository;
import com.B2B.repository.OrderRepository;
import com.B2B.repository.ProductRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/admin")
@CrossOrigin
public class AdminController {

    @Autowired
    private AdminRepository adminRepository;

    @Autowired
    private BusinessRepository businessRepository;
    
    @Autowired
    private ProductRepository productRepository;
    
    @Autowired
    private OrderRepository orderRepository;

    @PostMapping("/register")
    public ResponseEntity<?> registerAdmin(@RequestBody Admin admin) {
        // Validate security key
        List<String> allowedKeys = List.of("00011", "00022", "00033", "00044", "00055");
        if (!allowedKeys.contains(admin.getSecurityKey())) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Security code is wrong");
        }

        if (adminRepository.findByEmail(admin.getEmail()).isPresent()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Email is already registered. Please use another or login.");
        }
        return ResponseEntity.ok(adminRepository.save(admin));
    }

    @PostMapping("/login")
    public ResponseEntity<?> loginAdmin(@RequestBody Admin loginDetails) {
        Optional<Admin> found = adminRepository.findByEmail(loginDetails.getEmail());
        if (found.isPresent() && found.get().getPassword().equals(loginDetails.getPassword())) {
            return ResponseEntity.ok(found.get());
        }
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid credentials");
    }

    @GetMapping("/users")
    public List<business> getAllBusinesses() {
        return businessRepository.findAll();
    }

    @PutMapping("/users/{id}")
    public ResponseEntity<business> updateBusiness(@PathVariable int id, @RequestBody business updatedBusiness) {
        return businessRepository.findById(id)
                .map(b -> {
                    b.setBusinessName(updatedBusiness.getBusinessName());
                    b.setIndustry(updatedBusiness.getIndustry());
                    b.setOwnerName(updatedBusiness.getOwnerName());
                    b.setEmail(updatedBusiness.getEmail());
                    b.setUsername(updatedBusiness.getEmail());
                    return ResponseEntity.ok(businessRepository.save(b));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/users/{id}")
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

    @PutMapping("/users/{id}/status")
    @jakarta.transaction.Transactional
    public ResponseEntity<business> updateBusinessStatus(@PathVariable int id, @RequestBody java.util.Map<String, String> body) {
        String newStatus = body.get("status");
        System.out.println("Updating status for business " + id + " to " + newStatus);
        return businessRepository.findById(id)
                .map(b -> {
                    b.setStatus(newStatus);
                    return ResponseEntity.ok(businessRepository.save(b));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/users/{id}/unlock")
    @jakarta.transaction.Transactional
    public ResponseEntity<business> unlockBusiness(@PathVariable int id) {
        System.out.println("Unlocking business " + id);
        return businessRepository.findById(id)
                .map(b -> {
                    b.setStatus("ACTIVE");
                    b.setFailedAttempts(0);
                    return ResponseEntity.ok(businessRepository.save(b));
                })
                .orElse(ResponseEntity.notFound().build());
    }
}
