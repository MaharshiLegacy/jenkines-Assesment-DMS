package com.example.dms.dto;

import com.example.dms.model.Role;

public record TokenResponse(String accessToken, String tokenType, long expiresInSeconds, String username, Role role) {}
