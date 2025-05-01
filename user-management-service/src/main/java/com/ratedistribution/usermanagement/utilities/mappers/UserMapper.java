package com.ratedistribution.usermanagement.utilities.mappers;

import com.ratedistribution.usermanagement.dto.requests.CreateUserRequest;
import com.ratedistribution.usermanagement.dto.responses.GetUserResponse;
import com.ratedistribution.usermanagement.entity.Role;
import com.ratedistribution.usermanagement.entity.User;
import com.ratedistribution.usermanagement.utilities.HospitalIdGenerator;
import lombok.AllArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Mapper class responsible for converting between {@link User} entity and various data transfer objects (DTOs).
 * This class provides methods to map from request DTOs to entity and from entity to response DTOs.
 * The mapper also uses a {@link HospitalIdGenerator} to generate unique hospital IDs for users during the mapping process.
 *
 * @author Ömer Asaf BALIKÇI
 */

@Component
@AllArgsConstructor
public class UserMapper {
    @Autowired
    private HospitalIdGenerator hospitalIdGenerator;

    public User toUser(CreateUserRequest request) {
        if (request == null) {
            return null;
        }
        User user = new User();
        user.setFirstName(request.getFirstName());
        user.setLastName(request.getLastName());
        user.setUsername(request.getUsername());
        user.setHospitalId(this.hospitalIdGenerator.generateUniqueHospitalId());
        user.setEmail(request.getEmail());
        user.setRoles(request.getRoles());
        user.setGender(request.getGender());
        return user;
    }

    public GetUserResponse toGetUserResponse(User user) {
        if (user == null) {
            return null;
        }
        List<String> roles = user.getRoles().stream()
                .map(Role::name)
                .toList();
        return new GetUserResponse(
                user.getId(),
                user.getFirstName(),
                user.getLastName(),
                user.getUsername(),
                user.getHospitalId(),
                user.getEmail(),
                roles,
                user.getGender().toString()
        );
    }
}
