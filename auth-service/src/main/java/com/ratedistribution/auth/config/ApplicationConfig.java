package com.ratedistribution.auth.config;

import com.ratedistribution.auth.repository.UserRepository;
import com.ratedistribution.auth.service.concretes.UserDetailsServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

/**
 * This configuration class sets up security settings for the application,
 * including JWT-based authentication, password encoding, and session management.
 *
 * @author Ömer Asaf BALIKÇI
 */

@Configuration
@RequiredArgsConstructor
public class ApplicationConfig {
    private final UserRepository userRepository;

    /**
     * Configures the security filter chain.
     * Disables CSRF protection, allows public access to all requests,
     * and sets session management to stateless.
     *
     * @param httpSecurity the security configuration
     * @return the configured security filter chain
     * @throws Exception if configuration fails
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity httpSecurity) throws Exception {
        return httpSecurity
                .csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> auth
                        .anyRequest().permitAll()
                )
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .build();
    }

    /**
     * Configures the {@link UserDetailsService} implementation that loads user-specific data.
     *
     * @return a {@link UserDetailsService} implementation using {@link UserDetailsServiceImpl}
     */
    @Bean
    public UserDetailsService userDetailsService() {
        return new UserDetailsServiceImpl(userRepository);
    }

    /**
     * Provides the {@link AuthenticationManager} bean to manage authentication processes.
     *
     * @param configuration the {@link AuthenticationConfiguration} to obtain the manager from
     * @return the configured {@link AuthenticationManager}
     * @throws Exception if an error occurs while obtaining the authentication manager
     */
    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration configuration) throws Exception {
        return configuration.getAuthenticationManager();
    }

    /**
     * Provides the {@link PasswordEncoder} bean to encode passwords using BCrypt.
     *
     * @return a {@link BCryptPasswordEncoder} instance
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
