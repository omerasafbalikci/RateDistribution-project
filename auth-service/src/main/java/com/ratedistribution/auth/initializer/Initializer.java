package com.ratedistribution.auth.initializer;

import com.ratedistribution.auth.entity.Role;
import com.ratedistribution.auth.entity.User;
import com.ratedistribution.auth.repository.RoleRepository;
import com.ratedistribution.auth.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * This component initializes the application with default roles and users when the application starts.
 * Implements {@link CommandLineRunner} to execute the initialization logic upon application startup.
 *
 * @author Ömer Asaf BALIKÇI
 */

@Component
@RequiredArgsConstructor
public class Initializer implements CommandLineRunner {
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private static final String ADMIN = "ADMIN";
    private static final String TECHNICIAN = "TECHNICIAN";
    private static final String SECRETARY = "SECRETARY";

    @Override
    public void run(String... args) {
        initializeRoles();
        initializeSecretaryUser();
        initializeTechnicianUser();
        initializeAdminUser();
        initializeSuperUser();
    }

    private void initializeRoles() {
        List<Role> roles = this.roleRepository.findAll();
        if (roles.isEmpty()) {
            List<Role> roleList = List.of(
                    new Role(1L, ADMIN, ADMIN, null),
                    new Role(2L, SECRETARY, SECRETARY, null),
                    new Role(3L, TECHNICIAN, TECHNICIAN, null)
            );
            this.roleRepository.saveAll(roleList);
        }
    }

    private void initializeSecretaryUser() {
        boolean result = userRepository.existsByUsernameAndDeletedIsFalse("ozlembalikci");
        if (!result) {
            String encodedPassword = this.passwordEncoder.encode("omerasaf18991");
            Role secretaryRole = this.roleRepository.findByName(SECRETARY).orElseThrow();

            this.userRepository.save(User.builder()
                    .username("ozlembalikci")
                    .password(encodedPassword)
                    .emailVerified(true)
                    .emailVerificationToken(null)
                    .resetToken(null)
                    .resetTokenExpiration(null)
                    .roles(List.of(secretaryRole))
                    .build());
        }
    }

    private void initializeTechnicianUser() {
        boolean result = userRepository.existsByUsernameAndDeletedIsFalse("kadircanbalikci");
        if (!result) {
            String encodedPassword = this.passwordEncoder.encode("omerasaf18992");
            Role technicianRole = this.roleRepository.findByName(TECHNICIAN).orElseThrow();

            this.userRepository.save(User.builder()
                    .username("kadircanbalikci")
                    .password(encodedPassword)
                    .emailVerified(true)
                    .emailVerificationToken(null)
                    .resetToken(null)
                    .resetTokenExpiration(null)
                    .roles(List.of(technicianRole))
                    .build());
        }
    }

    private void initializeAdminUser() {
        boolean result = userRepository.existsByUsernameAndDeletedIsFalse("omerasafbalikci");
        if (!result) {
            String encodedPassword = this.passwordEncoder.encode("omerasaf18993");
            Role adminRole = this.roleRepository.findByName(ADMIN).orElseThrow();

            this.userRepository.save(User.builder()
                    .username("omerasafbalikci")
                    .password(encodedPassword)
                    .emailVerified(true)
                    .emailVerificationToken(null)
                    .resetToken(null)
                    .resetTokenExpiration(null)
                    .roles(List.of(adminRole))
                    .build());
        }
    }

    private void initializeSuperUser() {
        boolean result = userRepository.existsByUsernameAndDeletedIsFalse("super");
        if (!result) {
            String encodedPassword = this.passwordEncoder.encode("omerasaf18994");
            Role adminRole = this.roleRepository.findByName(ADMIN).orElseThrow();
            Role technicianRole = this.roleRepository.findByName(TECHNICIAN).orElseThrow();
            Role secretaryRole = this.roleRepository.findByName(SECRETARY).orElseThrow();

            this.userRepository.save(User.builder()
                    .username("super")
                    .password(encodedPassword)
                    .emailVerified(true)
                    .emailVerificationToken(null)
                    .resetToken(null)
                    .resetTokenExpiration(null)
                    .roles(List.of(adminRole, technicianRole, secretaryRole))
                    .build());
        }
    }
}
