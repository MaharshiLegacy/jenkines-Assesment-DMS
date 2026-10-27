package com.example.dms.security;

import com.example.dms.model.Role;
import com.example.dms.model.User;
import java.util.Collection;
import java.util.List;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

/** Authenticated principal: carries the user id so services never need to re-query for it. */
public class AppUserDetails implements UserDetails {

    private final Long id;
    private final String username;
    private final String password;
    private final Role role;
    private final boolean enabled;

    private AppUserDetails(User u) {
        this.id = u.getId();
        this.username = u.getUsername();
        this.password = u.getPassword();
        this.role = u.getRole();
        this.enabled = u.isEnabled();
    }

    public static AppUserDetails from(User u) { return new AppUserDetails(u); }

    public Long getId() { return id; }
    public Role getRole() { return role; }
    public boolean isAdmin() { return role == Role.ADMIN; }

    @Override public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }
    @Override public String getPassword() { return password; }
    @Override public String getUsername() { return username; }
    @Override public boolean isEnabled() { return enabled; }
}
