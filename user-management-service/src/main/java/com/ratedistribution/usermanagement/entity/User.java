package com.ratedistribution.usermanagement.entity;

import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

/**
 * User class represents a user entity in the database.
 *
 * @author Ömer Asaf BALIKÇI
 */

@Entity
@Table(name = "users", indexes = {
        @Index(name = "idx_firstName_username_email", columnList = "first_name, username, email")
})
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", unique = true, nullable = false, updatable = false)
    private Long id;

    @Column(name = "first_name")
    private String firstName;

    @Column(name = "last_name")
    private String lastName;

    @Column(name = "username")
    private String username;

    @Column(name = "hospital_id")
    private String hospitalId;

    @Column(name = "email")
    private String email;

    @Column(name = "roles")
    @ElementCollection(targetClass = Role.class)
    @Enumerated(value = EnumType.STRING)
    @CollectionTable(name = "user_roles", joinColumns = @JoinColumn(name = "user_id"))
    @Builder.Default
    private List<Role> roles = new ArrayList<>();

    @Enumerated(value = EnumType.STRING)
    @Column(name = "gender")
    private Gender gender;

    @Column(name = "deleted", nullable = false)
    @Builder.Default
    private boolean deleted = false;
}
