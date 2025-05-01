package com.ratedistribution.auth.dto.requests;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for password used as input.
 *
 * @author Ömer Asaf BALIKÇI
 */

@Data
@AllArgsConstructor
@NoArgsConstructor
public class PasswordRequest {
    @NotBlank(message = "Old password must not be blank")
    String oldPassword;
    @Size(min = 8, message = "New password must be at least 8 characters")
    @NotBlank(message = "New password must not be blank")
    String newPassword;
}
