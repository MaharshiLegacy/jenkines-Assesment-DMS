package com.example.dms.controller;

import com.example.dms.dto.*;
import com.example.dms.security.AppUserDetails;
import com.example.dms.service.DocumentService;
import com.example.dms.service.UserService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/** URL rule in SecurityConfig AND @PreAuthorize here: two independent layers (defence in depth). */
@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminController {

    private final UserService users;
    private final DocumentService documents;

    @GetMapping("/users")
    public List<UserResponse> listUsers() {
        return users.list();
    }

    @PostMapping("/users")
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse createUser(@Valid @RequestBody CreateUserRequest req) {
        return users.create(req);
    }

    @PatchMapping("/users/{id}")
    public UserResponse updateUser(@PathVariable Long id, @Valid @RequestBody UpdateUserRequest req,
                                   @AuthenticationPrincipal AppUserDetails caller) {
        return users.update(id, req, caller);
    }

    @DeleteMapping("/users/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteUser(@PathVariable Long id, @AuthenticationPrincipal AppUserDetails caller) {
        users.delete(id, caller);
    }

    /** All documents in the system (admins see everything through the normal list too). */
    @GetMapping("/documents")
    public PageResponse<DocumentResponse> allDocuments(@RequestParam(required = false) String q,
                                                       @RequestParam(defaultValue = "0") int page,
                                                       @RequestParam(defaultValue = "20") int size,
                                                       @AuthenticationPrincipal AppUserDetails caller) {
        return documents.list(q, page, size, caller);
    }
}
