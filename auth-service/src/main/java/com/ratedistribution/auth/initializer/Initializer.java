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
    private static final String OPERATOR = "OPERATOR";
    private static final String ANALYST = "ANALYST";

    @Override
    public void run(String... args) {
        initializeRoles();
        initializeAnalystUser();
        initializeOperatorUser();
        initializeAdminUser();
        initializeSuperUser();
    }

    private void initializeRoles() {
        createRoleIfNotExists("ADMIN");
        createRoleIfNotExists("OPERATOR");
        createRoleIfNotExists("ANALYST");
    }

    private void createRoleIfNotExists(String roleName) {
        if (roleRepository.findByName(roleName).isEmpty()) {
            Role role = new Role();
            role.setName(roleName);
            role.setDescription(roleName);
            roleRepository.save(role);
        }
    }

    private void initializeAnalystUser() {
        boolean result = userRepository.existsByUsernameAndDeletedIsFalse("ozlembalikci");
        if (!result) {
            String encodedPassword = this.passwordEncoder.encode("omerasaf18991");
            Role analystRole = this.roleRepository.findByName(ANALYST).orElseThrow();

            this.userRepository.save(User.builder()
                    .username("ozlembalikci")
                    .password(encodedPassword)
                    .emailVerified(true)
                    .emailVerificationToken(null)
                    .resetToken(null)
                    .resetTokenExpiration(null)
                    .roles(List.of(analystRole))
                    .build());
        }
    }

    private void initializeOperatorUser() {
        boolean result = userRepository.existsByUsernameAndDeletedIsFalse("kadircanbalikci");
        if (!result) {
            String encodedPassword = this.passwordEncoder.encode("omerasaf18992");
            Role operatorRole = this.roleRepository.findByName(OPERATOR).orElseThrow();

            this.userRepository.save(User.builder()
                    .username("kadircanbalikci")
                    .password(encodedPassword)
                    .emailVerified(true)
                    .emailVerificationToken(null)
                    .resetToken(null)
                    .resetTokenExpiration(null)
                    .roles(List.of(operatorRole))
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
            Role operatorRole = this.roleRepository.findByName(OPERATOR).orElseThrow();
            Role analystRole = this.roleRepository.findByName(ANALYST).orElseThrow();

            this.userRepository.save(User.builder()
                    .username("super")
                    .password(encodedPassword)
                    .emailVerified(true)
                    .emailVerificationToken(null)
                    .resetToken(null)
                    .resetTokenExpiration(null)
                    .roles(List.of(adminRole, operatorRole, analystRole))
                    .build());
        }
    }
}
