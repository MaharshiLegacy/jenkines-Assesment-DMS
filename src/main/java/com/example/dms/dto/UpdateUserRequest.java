package com.example.dms.dto;

import com.example.dms.model.Role;
import jakarta.validation.constraints.Size;

/** Partial update: null fields are ignored. */
public record UpdateUserRequest(Role role, Boolean enabled, @Size(min = 8, max = 72) String password) {}
