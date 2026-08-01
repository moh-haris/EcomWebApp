package com.haris.SpringEcom.config;

import com.haris.SpringEcom.model.AuthProviderType;
import com.haris.SpringEcom.model.Role;
import com.haris.SpringEcom.model.User;
import com.haris.SpringEcom.repo.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * RBAC - Admin Data Seeder
 *
 * CommandLineRunner runs automatically once on application startup.
 * Its job: ensure a hardcoded ADMIN user exists in the database.
 *
 * WHY CommandLineRunner?
 *  - No manual SQL scripts needed.
 *  - The check "if admin doesn't exist, create it" is idempotent —
 *    safe to run every startup without duplicating data.
 *  - Clear and readable for interviews: shows understanding of Spring
 *    application lifecycle hooks.
 *
 * Credentials (for development):
 *   Username: admin
 *   Password: admin123
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DataSeeder implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) throws Exception {

        // Check if admin already exists in the DB to avoid duplicate creation.
        // This runs every startup, so we must be idempotent (safe to repeat).
        boolean adminExists = userRepository.findByUsername("admin").isPresent();

        if (!adminExists) {
            // Build the admin user with ROLE_ADMIN and a bcrypt-hashed password.
            // We use BCrypt (same PasswordEncoder bean as the rest of the app)
            // so the stored hash is verifiable by Spring Security's AuthenticationManager.
            User adminUser = User.builder()
                    .username("admin")
                    .password(passwordEncoder.encode("admin123")) // bcrypt hashed
                    .role(Role.ADMIN)                             // RBAC: give ADMIN role
                    .providerType(AuthProviderType.EMAIL)         // logs in via username/password
                    .build();

            userRepository.save(adminUser);
            log.info(">>> DataSeeder: Admin user created successfully with username='admin'");
        } else {
            log.info(">>> DataSeeder: Admin user already exists, skipping creation.");
        }
    }
}
