package ku_rum.backend.domain.oauth.application;

import ku_rum.backend.domain.oauth.domain.OAuthAttributes;
import ku_rum.backend.domain.oauth.domain.UserProfile;
import ku_rum.backend.domain.user.domain.User;
import ku_rum.backend.domain.user.domain.repository.UserRepository;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.Map;

@Service
public class CustomOAuth2UserService implements OAuth2UserService<OAuth2UserRequest, OAuth2User> {

    private final UserRepository userRepository;

    public CustomOAuth2UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {

        String accessToken = userRequest.getAccessToken().getTokenValue();

        // Access Token 검증
        if (!tokenValidator.validateAccessToken(accessToken)) {
            throw new OAuth2AuthenticationException("Invalid Access Token");
        }

        OAuth2UserService<OAuth2UserRequest, OAuth2User> delegate = new DefaultOAuth2UserService();
        OAuth2User oAuth2User = delegate.loadUser(userRequest);

        String registrationId = userRequest.getClientRegistration().getRegistrationId(); // ex) "naver"
        Map<String, Object> attributes = oAuth2User.getAttributes();

        // 네이버 사용자 정보 추출
        UserProfile userProfile = OAuthAttributes.extract(registrationId, attributes);

        // 사용자 저장 또는 업데이트
        User user = saveOrUpdate(userProfile);

        return new DefaultOAuth2User(
                Collections.singleton(new SimpleGrantedAuthority("ROLE_USER")),
                attributes,
                "email"
        );
    }

    private User saveOrUpdate(UserProfile userProfile) {
        return userRepository.findUserByEmail(userProfile.getEmail())
                .orElseGet(() -> {
                    User newUser = User.builder()
                            .email(userProfile.getEmail())
                            .nickname("default") // 디폴트 닉네임
                            .password("") // 디폴트 비밀번호
                            .studentId("") // 디폴트 학번
                            .department(null) // Department는 null로 설정
                            .build();
                    return userRepository.save(newUser);
                });
    }
}
