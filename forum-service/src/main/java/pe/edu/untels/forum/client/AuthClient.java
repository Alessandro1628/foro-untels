
package pe.edu.untels.forum.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.server.ResponseStatusException;

import pe.edu.untels.forum.dto.AuthUserResponse;

@Component
public class AuthClient {

    private final RestClient restClient;

    public AuthClient(
            @Value("${auth.service.url}") String authServiceUrl
    ) {
        this.restClient = RestClient.builder()
                .baseUrl(authServiceUrl)
                .build();
    }

    public AuthUserResponse getAuthenticatedUser(
            String authorization
    ) {

        // Comprobar que el token fue enviado
        if (authorization == null ||
                !authorization.startsWith("Bearer ") ||
                authorization.substring(7).isBlank()) {

            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Token de acceso requerido"
            );
        }

        try {
            // Consultar el perfil protegido en Auth Service
            AuthUserResponse user = restClient.get()
                    .uri("/api/v1/auth/me")
                    .header(
                            HttpHeaders.AUTHORIZATION,
                            authorization
                    )
                    .retrieve()
                    .body(AuthUserResponse.class);

            // Verificar que la respuesta contiene un usuario
            if (user == null || user.id() == null) {
                throw new ResponseStatusException(
                        HttpStatus.SERVICE_UNAVAILABLE,
                        "Respuesta invalida de Auth Service"
                );
            }

            return user;

        } catch (RestClientResponseException ex) {

            // Auth Service rechazo el token
            if (ex.getStatusCode().value() == 401) {
                throw new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED,
                        "Token invalido o expirado"
                );
            }

            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "No se pudo validar el usuario"
            );

        } catch (ResourceAccessException ex) {

            // Auth Service no responde
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Auth Service no disponible"
            );
        }
    }
}
