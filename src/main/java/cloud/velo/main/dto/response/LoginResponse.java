package cloud.velo.main.dto.response;

import cloud.velo.main.domain.Role;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class LoginResponse {
    private String accessToken;
    private String name;
    private String email;
    private String model;
    private String picture;
    private Role role;
    private boolean isNewUser;
}