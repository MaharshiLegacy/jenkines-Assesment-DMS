package com.example.dms.dto;

import com.example.dms.model.Role;
import jakarta.validation.constraints.*;

public record CreateUserRequest(
        @NotBlank @Size(min = 3, max = 50) @Pattern(regexp = "^[A-Za-z0-9._-]+$", message = "only letters, digits, . _ - allowed") String username,
        @NotBlank @Email @Size(max = 120) String email,
        @NotBlank @Size(min = 8, max = 72) String password,   // 72 = BCrypt input limit
        @NotNull Role role) {}
