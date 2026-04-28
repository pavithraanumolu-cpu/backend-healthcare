package com.example.demo.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.*;

import com.example.demo.entity.User;
import com.example.demo.repository.UserRepository;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;

import dev.samstevens.totp.secret.DefaultSecretGenerator;
import dev.samstevens.totp.qr.QrData;
import dev.samstevens.totp.qr.ZxingPngQrGenerator;
import dev.samstevens.totp.util.Utils;
import dev.samstevens.totp.code.DefaultCodeVerifier;
import dev.samstevens.totp.code.DefaultCodeGenerator;
import dev.samstevens.totp.time.SystemTimeProvider;
import dev.samstevens.totp.code.HashingAlgorithm;

@RestController
@RequestMapping("/api/auth")
@CrossOrigin(origins = "*")
public class AuthController {

    @Autowired
    private UserRepository repo;

    @Autowired
    private PasswordEncoder passwordEncoder;

    // ================= REGISTER =================
    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody User user) {

        String password = user.getPassword();
        if (password == null || password.length() < 7 || 
            !password.matches(".*[A-Z].*") || 
            !password.matches(".*[!@#$%^&*()_+\\-=\\[\\]{};':\"\\\\|,.<>\\/?].*")) {
            return ResponseEntity.badRequest().body("Password must be at least 7 characters long, contain at least 1 uppercase letter and 1 symbol.");
        }

        user.setRole("student");

        if (user.getEmail() == null || user.getEmail().isEmpty()) {
            user.setEmail(user.getUsername());
        }

        user.setPassword(passwordEncoder.encode(user.getPassword()));

        return ResponseEntity.ok(repo.save(user));
    }

    // ================= LOGIN =================
    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody User user) {

        System.out.println("Login attempt: " + user.getUsername());

        User existingUser = repo.findFirstByUsername(user.getUsername());

        if (existingUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("User not found");
        }

        boolean isValid = false;

        // ✅ ADMIN LOGIN (PLAIN PASSWORD)
        if ("admin".equals(existingUser.getRole())) {
            if (existingUser.getPassword().equals(user.getPassword())) {
                isValid = true;
            }
        }
        // ✅ STUDENT LOGIN (BCRYPT)
        else {
            System.out.println("Checking student password for: " + existingUser.getUsername());
            System.out.println("Raw password length: " + (user.getPassword() != null ? user.getPassword().length() : "null"));
            System.out.println("DB password length: " + (existingUser.getPassword() != null ? existingUser.getPassword().length() : "null"));
            if (passwordEncoder.matches(user.getPassword(), existingUser.getPassword())) {
                isValid = true;
                System.out.println("BCrypt match successful");
            } else if (existingUser.getPassword().equals(user.getPassword())) {
                // Fallback for older plain-text accounts created before BCrypt
                isValid = true;
                System.out.println("Plaintext match successful");
            } else {
                System.out.println("Password match FAILED");
            }
        }

        if (!isValid) {
            System.out.println("Login rejected: Invalid password");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid password");
        }

        // ✅ GOOGLE AUTHENTICATOR MFA SETUP OR VERIFICATION
        String secret = existingUser.getMfaSecret();
        boolean mfaSetupRequired = false;
        String qrCodeUrl = null;

        if (secret == null || secret.isEmpty()) {
            secret = new DefaultSecretGenerator().generate();
            existingUser.setMfaSecret(secret);
            repo.save(existingUser);
            mfaSetupRequired = true;

            try {
                String accountLabel = existingUser.getUsername();
                if (existingUser.getEmail() != null && !existingUser.getEmail().isEmpty()) {
                    accountLabel = existingUser.getEmail();
                }

                QrData data = new QrData.Builder()
                    .label(accountLabel)
                    .secret(secret)
                    .issuer("FitWell")
                    .algorithm(HashingAlgorithm.SHA1)
                    .digits(6)
                    .period(30)
                    .build();

                ZxingPngQrGenerator generator = new ZxingPngQrGenerator();
                byte[] imageData = generator.generate(data);
                qrCodeUrl = Utils.getDataUriForImage(imageData, generator.getImageMimeType());
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        // ✅ SEND RESPONSE (IMPORTANT FOR FRONTEND)
        Map<String, Object> response = new HashMap<>();
        response.put("username", existingUser.getUsername());
        response.put("email", existingUser.getEmail());
        response.put("role", existingUser.getRole());
        
        response.put("mfaEnabled", true);
        response.put("mfaSetupRequired", mfaSetupRequired);
        if (qrCodeUrl != null) {
            response.put("qrCodeUrl", qrCodeUrl);
        }

        return ResponseEntity.ok(response);
    }

    // ================= VERIFY OTP =================
    @PostMapping("/verify-otp")
    public ResponseEntity<?> verifyOtp(@RequestBody Map<String, String> request) {

        String username = request.get("username");
        String enteredOtp = request.get("otp");

        User existingUser = repo.findFirstByUsername(username);
        if (existingUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("User not found");
        }

        String secret = existingUser.getMfaSecret();
        if (secret == null || secret.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("MFA not set up for this user");
        }

        DefaultCodeVerifier verifier = new DefaultCodeVerifier(new DefaultCodeGenerator(), new SystemTimeProvider());
        verifier.setAllowedTimePeriodDiscrepancy(2);

        if (verifier.isValidCode(secret, enteredOtp)) {
            return ResponseEntity.ok("Login Successful");
        } else {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid OTP");
        }
    }

    // ================= RESET MFA (Re-scan QR) =================
    @PostMapping("/reset-mfa")
    public ResponseEntity<?> resetMfa(@RequestBody Map<String, String> request) {
        String username = request.get("username");
        if (username == null || username.isEmpty()) {
            return ResponseEntity.badRequest().body("Username is required");
        }

        User user = repo.findFirstByUsername(username);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("User not found");
        }

        user.setMfaSecret(null);
        repo.save(user);
        System.out.println("✅ MFA secret reset for: " + username);
        return ResponseEntity.ok("MFA reset successful. Please log in again to get a new QR code.");
    }

    // ================= ALL USERS =================
    @GetMapping("/users")
    public List<User> getAllUsers() {
        return repo.findAll();
    }

    // ================= STUDENTS =================
    @GetMapping("/students")
    public List<User> getStudents() {
        return repo.findAll()
                   .stream()
                   .filter(u -> "student".equals(u.getRole()))
                   .toList();
    }
}