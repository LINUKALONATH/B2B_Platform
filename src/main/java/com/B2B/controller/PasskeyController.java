package com.B2B.controller;

import com.B2B.service.WebAuthnService;
import com.yubico.webauthn.data.PublicKeyCredentialCreationOptions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/passkey")
@CrossOrigin
public class PasskeyController {

    @Autowired
    private WebAuthnService webAuthnService;

    @GetMapping("/register/start")
    public ResponseEntity<String> startRegistration(@RequestParam String email) {
        try {
            PublicKeyCredentialCreationOptions options = webAuthnService.startRegistration(email);
            return ResponseEntity.ok(options.toCredentialsCreateJson());
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError().body("Error generating passkey challenge");
        }
    }

    @GetMapping("/login/start")
    public ResponseEntity<String> startLogin(@RequestParam String email) {
        try {
            var options = webAuthnService.startLogin(email);
            return ResponseEntity.ok(options.toCredentialsGetJson());
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError().body("Error generating login challenge");
        }
    }

    @PostMapping("/login/finish")
    public ResponseEntity<?> finishLogin(@RequestParam String email, @RequestBody String assertionJson) {
        try {
            com.B2B.model.business b = webAuthnService.finishLogin(email, assertionJson);
            return ResponseEntity.ok(b);
        } catch (Exception e) {
            e.printStackTrace();
            String msg = e.getMessage() != null ? e.getMessage() : "Unknown verification error";
            return ResponseEntity.status(401).body("Fingerprint error: " + msg);
        }
    }
}
