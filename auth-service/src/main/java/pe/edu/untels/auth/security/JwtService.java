
package pe.edu.untels.auth.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import pe.edu.untels.auth.entity.User;

import javax.crypto.SecretKey;
import java.time.Instant;
import java.util.Date;

@Service
public class JwtService {

    private final SecretKey signingKey;
    private final long expirationMs;

    public JwtService(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.expiration-ms}") long expirationMs
    ) {
        byte[] keyBytes = Decoders.BASE64.decode(secret);

        this.signingKey = Keys.hmacShaKeyFor(keyBytes);
        this.expirationMs = expirationMs;
    }

    // GENERAR TOKEN JWT
    public String generateToken(User user) {

        Instant now = Instant.now();

        return Jwts.builder()
                .issuer("foro-untels-auth")
                .subject(user.getId().toString())
                .claim("role", user.getRole())
                .issuedAt(Date.from(now))
                .expiration(
                        Date.from(now.plusMillis(expirationMs))
                )
                .signWith(signingKey, Jwts.SIG.HS256)
                .compact();
    }

    // VERIFICAR TOKEN Y OBTENER ID DEL USUARIO
    public Long validateAndGetUserId(String token) {

        try {
            Claims claims = Jwts.parser()
                    .verifyWith(signingKey)
                    .requireIssuer("foro-untels-auth")
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            // Rechazar tokens sin fecha de vencimiento
            if (claims.getExpiration() == null) {
                throw new IllegalArgumentException(
                        "Token sin fecha de vencimiento"
                );
            }

            // Recuperar ID del usuario
            return Long.valueOf(claims.getSubject());

        } catch (JwtException | IllegalArgumentException ex) {

            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Token invalido o expirado"
            );
        }
    }
}
