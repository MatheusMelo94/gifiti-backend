package com.gifiti.api.mapper;

import com.gifiti.api.dto.response.RegisterResponse;
import com.gifiti.api.model.User;
import org.springframework.stereotype.Component;

/**
 * Mapper for User entity to DTO transformations.
 */
@Component
public class UserMapper {

    /**
     * Convert User entity to RegisterResponse DTO.
     *
     * @param user User entity
     * @param message Success message
     * @return RegisterResponse DTO
     */
    public RegisterResponse toRegisterResponse(User user, String message) {
        return RegisterResponse.builder()
                .id(user.getId())
                .email(user.getEmail())
                .message(message)
                .build();
    }
}
