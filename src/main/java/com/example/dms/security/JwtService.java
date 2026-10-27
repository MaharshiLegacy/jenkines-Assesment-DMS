package com.example.dms.security;

import com.example.dms.config.AppProperties;
import com.example.dms.dto.TokenResponse;
import java.time.Duration;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class JwtService {

    private final JwtEncoder encoder;
    private final AppProperties props;

    public TokenResponse issue(AppUserDetails user) {
        Duration ttl = Duration.ofMinutes(props.jwt().expiryMinutes());
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(props.jwt().issuer())
                .issuedAt(now)
                .expiresAt(now.plus(ttl))
                .subject(String.valueOf(user.getId()))          // user id, not the (renamable) username
                .claim("username", user.getUsername())
                .build();                                        // role is deliberately NOT trusted from the token
        String token = encoder.encode(
                JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
        return new TokenResponse(token, "Bearer", ttl.toSeconds(), user.getUsername(), user.getRole());
    }
}
