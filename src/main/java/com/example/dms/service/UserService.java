package com.example.dms.service;

import com.example.dms.dto.CreateUserRequest;
import com.example.dms.dto.UpdateUserRequest;
import com.example.dms.dto.UserResponse;
import com.example.dms.exception.ApiException;
import com.example.dms.model.User;
import com.example.dms.repository.DocumentRepository;
import com.example.dms.repository.UserRepository;
import com.example.dms.security.AppUserDetails;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository users;
    private final DocumentRepository documents;
    private final PasswordEncoder encoder;

    @Transactional(readOnly = true)
    public List<UserResponse> list() {
        return users.findAll().stream().map(UserResponse::from).toList();
    }

    @Transactional
    public UserResponse create(CreateUserRequest req) {
        if (users.existsByUsernameIgnoreCase(req.username())) throw ApiException.conflict("Username already taken");
        if (users.existsByEmailIgnoreCase(req.email())) throw ApiException.conflict("Email already registered");
        User u = new User();
        u.setUsername(req.username().trim());
        u.setEmail(req.email().trim());
        u.setPassword(encoder.encode(req.password()));
        u.setRole(req.role());
        return UserResponse.from(users.save(u));
    }

    @Transactional
    public UserResponse update(Long id, UpdateUserRequest req, AppUserDetails caller) {
        User u = find(id);
        boolean self = u.getId().equals(caller.getId());
        boolean demotes = req.role() != null && req.role() != u.getRole();
        if (self && (demotes || Boolean.FALSE.equals(req.enabled()))) {
            throw ApiException.badRequest("Admins cannot demote or disable their own account");
        }
        if (req.role() != null) u.setRole(req.role());
        if (req.enabled() != null) u.setEnabled(req.enabled());
        if (req.password() != null) u.setPassword(encoder.encode(req.password()));
        return UserResponse.from(u);
    }

    @Transactional
    public void delete(Long id, AppUserDetails caller) {
        if (id.equals(caller.getId())) throw ApiException.badRequest("You cannot delete your own account");
        User u = find(id);
        if (documents.existsByOwnerId(id)) {
            throw ApiException.conflict("User owns documents; delete or reassign them first (or disable the account)");
        }
        users.delete(u);
        log.info("Admin {} deleted user {}", caller.getUsername(), u.getUsername());
    }

    private User find(Long id) {
        return users.findById(id).orElseThrow(() -> ApiException.notFound("User " + id + " not found"));
    }
}
