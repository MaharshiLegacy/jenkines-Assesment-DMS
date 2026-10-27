package com.example.dms.controller;

import com.example.dms.dto.LoginRequest;
import com.example.dms.dto.TokenResponse;
import com.example.dms.dto.UserResponse;
import com.example.dms.exception.ApiException;
import com.example.dms.repository.UserRepository;
import com.example.dms.security.AppUserDetails;
import com.example.dms.security.JwtService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/** There is no self-registration: accounts are created by an admin (POST /api/admin/users). */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final UserRepository users;

    @PostMapping("/login")
    public TokenResponse login(@Valid @RequestBody LoginRequest req) {
        Authentication auth;
        try {
            auth = authenticationManager.authenticate(
                    UsernamePasswordAuthenticationToken.unauthenticated(req.username().trim(), req.password()));
        } catch (AuthenticationException e) {
            // same message for unknown user / wrong password / disabled account (no account enumeration)
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Invalid username or password");
        }
        return jwtService.issue((AppUserDetails) auth.getPrincipal());
    }

    @GetMapping("/me")
    public UserResponse me(@AuthenticationPrincipal AppUserDetails caller) {
        return users.findById(caller.getId()).map(UserResponse::from)
                .orElseThrow(() -> ApiException.notFound("User not found"));
    }
}
