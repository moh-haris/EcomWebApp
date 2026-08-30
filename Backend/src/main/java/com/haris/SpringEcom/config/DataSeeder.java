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

@Component
@RequiredArgsConstructor
@Slf4j
public class DataSeeder implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) throws Exception {

        boolean adminExists = userRepository.findByUsername("admin").isPresent();

        if (!adminExists) {

            User adminUser = User.builder()
                    .username("admin")
                    .password(passwordEncoder.encode("admin123")) 
                    .role(Role.ADMIN)                             
                    .providerType(AuthProviderType.EMAIL)         
                    .build();

            userRepository.save(adminUser);
            log.info(">>> DataSeeder: Admin user created successfully with username='admin'");
        } else {
            log.info(">>> DataSeeder: Admin user already exists, skipping creation.");
        }
    }
}
