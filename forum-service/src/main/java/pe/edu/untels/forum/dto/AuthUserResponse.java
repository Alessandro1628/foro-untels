
package pe.edu.untels.forum.dto;

public record AuthUserResponse(
        Long id,
        String fullName,
        String email,
        String role
) {
}
