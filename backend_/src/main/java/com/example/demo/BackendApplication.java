package com.example.demo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import com.example.demo.entity.User;
import com.example.demo.repository.UserRepository;

@SpringBootApplication
public class BackendApplication {

	public static void main(String[] args) {
		SpringApplication.run(BackendApplication.class, args);
	}

	@Bean
	CommandLineRunner initDatabase(UserRepository userRepository) {
		return args -> {
			User existingAdmin = userRepository.findFirstByUsername("admin");
			if (existingAdmin == null) {
				User admin = new User();
				admin.setUsername("admin");
				admin.setPassword("admin123"); // Required by AuthController logic
				admin.setEmail("admin@example.com");
				admin.setRole("admin");
				userRepository.save(admin);
				System.out.println("✅ Default Admin User Seeded: admin / admin123");
			} else {
				// Reset MFA secret for testing so QR code shows up again
				if (existingAdmin.getMfaSecret() != null) {
					existingAdmin.setMfaSecret(null);
					userRepository.save(existingAdmin);
					System.out.println("✅ Reset admin MFA secret for testing");
				}
			}
		};
	}
}
