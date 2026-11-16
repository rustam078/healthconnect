package in.healthconnect.security.service;

import in.healthconnect.security.entity.RolePermission;
import in.healthconnect.security.entity.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.List;

// Creates and reads signed JWTs. The signature is what makes the token trustworthy:
// a user cannot change their role/permissions in it without breaking the signature.
@Service
public class JwtService {

    private final SecretKey key;
    private final long expiryMinutes;

    public JwtService(@Value("${jwt.secret:dev-only-change-me-secret-key-min-32-bytes-long!!}") String secret,
                      @Value("${jwt.expiry-minutes:120}") long expiryMinutes) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expiryMinutes = expiryMinutes;
    }

    // Build a signed token carrying the user's id, role, and permission authorities.
    public String generateToken(User user) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(user.getUsername())
                .claim("uid", user.getId())
                .claim("role", user.getRole().getName())
                .claim("authorities", authoritiesOf(user))
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(expiryMinutes, ChronoUnit.MINUTES)))
                .signWith(key)
                .compact();
    }

    public boolean isValid(String token) {
        try {
            parse(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    public String getUsername(String token) {
        return parse(token).getSubject();
    }

    public Integer getUserId(String token) {
        return parse(token).get("uid", Integer.class);
    }

    @SuppressWarnings("unchecked")
    public List<String> getAuthorities(String token) {
        return parse(token).get("authorities", List.class);
    }

    private Claims parse(String token) {
        return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
    }

    private List<String> authoritiesOf(User user) {
        return user.getRole().getPermissions().stream().map(RolePermission::authority).toList();
    }
}
