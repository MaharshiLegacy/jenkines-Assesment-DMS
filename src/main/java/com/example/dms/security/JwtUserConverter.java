package com.example.dms.security;

import com.example.dms.model.User;
import com.example.dms.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

/**
 * Runs after the signature/expiry check. Loads the user from the DB on every request so that:
 *  - a disabled or deleted user is locked out immediately (their old token stops working),
 *  - role changes take effect immediately (the role is never read from the token).
 */
@Component
@RequiredArgsConstructor
public class JwtUserConverter implements Converter<Jwt, UsernamePasswordAuthenticationToken> {

    private final UserRepository users;

    @Override
    public UsernamePasswordAuthenticationToken convert(Jwt jwt) {
        long id;
        try {
            id = Long.parseLong(jwt.getSubject());
        } catch (NumberFormatException e) {
            throw new BadCredentialsException("Invalid token subject");
        }
        User user = users.findById(id).orElseThrow(() -> new BadCredentialsException("User no longer exists"));
        if (!user.isEnabled()) throw new DisabledException("Account is disabled");

        AppUserDetails principal = AppUserDetails.from(user);
        return new UsernamePasswordAuthenticationToken(principal, jwt.getTokenValue(), principal.getAuthorities());
    }
}
