package ku_rum.backend.domain.oauth.application;

import ku_rum.backend.domain.oauth.domain.OAuthAttributes;
import ku_rum.backend.domain.oauth.domain.UserProfile;
import ku_rum.backend.domain.oauth.util.TokenValidator;
import ku_rum.backend.domain.user.domain.User;
import ku_rum.backend.domain.user.domain.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

@Service
@Slf4j
public class CustomOAuth2UserService implements OAuth2UserService<OAuth2UserRequest, OAuth2User> {

    private final UserRepository userRepository;
    private final TokenValidator tokenValidator;

    public CustomOAuth2UserService(UserRepository userRepository, TokenValidator tokenValidator) {
        this.userRepository = userRepository;
        this.tokenValidator = tokenValidator;
    }

    @Override
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {

//        Access Token 검증
//        String accessToken = userRequest.getAccessToken().getTokenValue();
//        log.info("Access Token : {}", accessToken);
//        if (!tokenValidator.validateAccessToken(accessToken)) {
//            throw new OAuth2AuthenticationException("Invalid Access Token");
//        }

        OAuth2UserService<OAuth2UserRequest, OAuth2User> delegate = new DefaultOAuth2UserService();
        log.info("userRequest getClientRegistration : {}", userRequest.getClientRegistration());
        OAuth2User oAuth2User = delegate.loadUser(userRequest);

        String registrationId = userRequest.getClientRegistration().getRegistrationId(); // ex) "naver"
        String userNameAttributeName = userRequest.getClientRegistration().getProviderDetails()
                .getUserInfoEndpoint().getUserNameAttributeName();

        log.info("Registration ID: {}", registrationId);
        log.info("UserName Attribute Name: {}", userNameAttributeName);

        // Attributes 로깅
        Map<String, Object> attributes = oAuth2User.getAttributes();
        log.info("Attributes received: {}", attributes);

        // 네이버의 경우 response 내부 값 처리
        if ("naver".equals(registrationId)) {
            Map<String, Object> response = (Map<String, Object>) attributes.get("response");
            attributes = new HashMap<>(response); // `response`를 새로운 `attributes`로 대체

            if (!response.containsKey("id")) {
                // 임시 값 추가
                attributes.put("id", "temporary-id");
                log.warn("ID 값이 존재하지 않아 임시 값을 추가합니다.");
            } else {
                attributes.put("id", response.get("id"));
            }

            log.info("Processed Attributes for NAVER with id: {}", attributes);
        }

        // 네이버 사용자 정보 추출
        OAuthAttributes oAuthAttributes = OAuthAttributes.of(registrationId, userNameAttributeName, attributes);

        // 사용자 저장 또는 업데이트
        User user = saveOrUpdate(oAuthAttributes);

        // JWT 방식에서는 사용자 정보를 반환하는 데 필요
        return new DefaultOAuth2User(
                Collections.singleton(new SimpleGrantedAuthority("ROLE_USER")),
                attributes,
                userNameAttributeName
        );
    }

    private User saveOrUpdate(OAuthAttributes attributes) {
        // 이메일로 사용자 조회
        return userRepository.findUserByEmail(attributes.getEmail())
                .orElseGet(() -> {
                    // 새로운 사용자 생성
                    User newUser = User.of(
                            attributes.getEmail(),
                            attributes.getName() != null ? attributes.getName() : "default",
                            "",
                            "",
                            null
                    );
                    return userRepository.save(newUser);
                });
    }
}
