package com.ratedistribution.auth.entity;

import jakarta.persistence.*;
import lombok.*;

/**
 * Token class represents a token entity in the database.
 *
 * @author Ömer Asaf BALIKÇI
 */

@Entity(name = "tokens")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class Token {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", unique = true, nullable = false, updatable = false)
    private Long id;

    @Column(name = "token")
    private String token;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(name = "is_logged_out")
    private boolean loggedOut;
}
