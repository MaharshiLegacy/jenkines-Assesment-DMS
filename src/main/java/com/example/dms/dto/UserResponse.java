package com.example.dms.dto;

import com.example.dms.model.Role;
import com.example.dms.model.User;
import java.time.Instant;

public record UserResponse(Long id, String username, String email, Role role, boolean enabled, Instant createdAt) {
    public static UserResponse from(User u) {
        return new UserResponse(u.getId(), u.getUsername(), u.getEmail(), u.getRole(), u.isEnabled(), u.getCreatedAt());
    }
}
