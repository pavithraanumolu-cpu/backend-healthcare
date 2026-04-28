package com.example.demo.entity;

import jakarta.persistence.*;

@Entity
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String username;
    private String password;
    private String email;   // ✅ email added
    private String role;
    private String mfaSecret;

    // GETTERS
    public Long getId() { return id; }
    public String getUsername() { return username; }
    public String getPassword() { return password; }
    public String getEmail() { return email; }   // ✅ ADD THIS
    public String getRole() { return role; }
    public String getMfaSecret() { return mfaSecret; }

    // SETTERS
    public void setId(Long id) { this.id = id; }
    public void setUsername(String username) { this.username = username; }
    public void setPassword(String password) { this.password = password; }
    public void setEmail(String email) { this.email = email; }   // ✅ ADD THIS
    public void setRole(String role) { this.role = role; }
    public void setMfaSecret(String mfaSecret) { this.mfaSecret = mfaSecret; }
}