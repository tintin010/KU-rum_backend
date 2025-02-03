package ku_rum.backend.domain.oauth.domain;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.Arrays;
import java.util.Map;
import java.util.function.Function;

@Getter
@Slf4j
public enum OAuthAttributes {


    GOOGLE("google", (attributes) -> {
        UserProfile userProfile = new UserProfile();
        userProfile.setUserName((String) attributes.get("name"));
        userProfile.setEmail((String) attributes.get("email"));
        return userProfile;
    }),

    NAVER("naver", (attributes) -> {
        UserProfile userProfile = new UserProfile();

//        Map<String, Object> response = (Map<String, Object>) attributes.get("response");

//        if (response != null) {
//            userProfile.setUserName((String) response.get("name"));
//            userProfile.setEmail((String) response.get("email"));
//            userProfile.setId((String) response.get("id"));  // OAuth 고유 ID
//        } else {
//            log.warn("Naver response is null: {}", attributes);
//        }
//        return userProfile;

        if (attributes != null) {
            userProfile.setUserName((String) attributes.get("name"));
            userProfile.setEmail((String) attributes.get("email"));
            userProfile.setId((String) attributes.get("id"));  // OAuth 고유 ID
        } else {
            log.warn("Naver response is null: {}", attributes);
        }
        return userProfile;
    }),

    KAKAO("kakao", (attributes) -> {
        Map<String, Object> account = (Map<String, Object>) attributes.get("kakao_account");
        Map<String, Object> profile = (Map<String, Object>) account.get("profile");

        UserProfile userProfile = new UserProfile();
            userProfile.setUserName((String) profile.get("nickname"));
        userProfile.setEmail((String) account.get("email"));
        return userProfile;
    });

    private final String registrationId; // OAuth 플랫폼 이름
    private final Function<Map<String, Object>, UserProfile> of; // 사용자 정보를 UserProfile로 변환하는 함수

    OAuthAttributes(String registrationId, Function<Map<String, Object>, UserProfile> of) {
        this.registrationId = registrationId;
        this.of = of;
    }

    public static UserProfile extract(String registrationId, Map<String, Object> attributes) {
        return Arrays.stream(OAuthAttributes.values())
                .filter(value -> value.registrationId.equals(registrationId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unsupported OAuth Provider"))
                .of.apply(attributes);
    }
}