
package pe.edu.untels.auth.dto;

public record UserProfileResponse(
        Long id,
        String fullName,
        String email,
        String role
) {
}
