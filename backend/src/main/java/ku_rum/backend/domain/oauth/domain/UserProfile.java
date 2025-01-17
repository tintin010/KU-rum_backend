package ku_rum.backend.domain.oauth.domain;

import lombok.Getter;

@Getter
public class UserProfile {
    private final String oauthId; // OAuth 고유 ID
    private final String email;   // 이메일

    public UserProfile(String oauthId, String email) {
        this.oauthId = oauthId;
        this.email = email;
    }

}
