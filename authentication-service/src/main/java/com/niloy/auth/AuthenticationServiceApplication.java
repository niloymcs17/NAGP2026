package com.niloy.auth;

import com.niloy.auth.model.User;
import com.niloy.auth.repository.UserRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.context.annotation.Bean;
import org.springframework.security.crypto.password.PasswordEncoder;

@SpringBootApplication
@EnableDiscoveryClient
public class AuthenticationServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(AuthenticationServiceApplication.class, args);
    }

    @Bean
    public CommandLineRunner seedDatabase(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        return args -> {
            if (userRepository.count() == 0) {
                userRepository.save(new User(null, "employee1", passwordEncoder.encode("password"), "EMPLOYEE"));
                userRepository.save(new User(null, "employee2", passwordEncoder.encode("password"), "EMPLOYEE"));
                userRepository.save(new User(null, "manager1", passwordEncoder.encode("password"), "MANAGER"));
                System.out.println("Mock users seeded in Authentication Service.");
            }
        };
    }
}
