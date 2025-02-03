package ku_rum.backend.domain.oauth.application;

import ku_rum.backend.domain.oauth.domain.DefaultOAuth2UserNaver;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.convert.converter.Converter;
import org.springframework.http.RequestEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.http.OAuth2ErrorResponseErrorHandler;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequestEntityConverter;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthorizationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2UserAuthority;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestOperations;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.client.UnknownContentTypeException;

import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Map;


public class DefaultOAuth2UserServiceNaver implements OAuth2UserService<OAuth2UserRequest, OAuth2User> {
    private static final String MISSING_USER_INFO_URI_ERROR_CODE = "missing_user_info_uri";
    private static final String MISSING_USER_NAME_ATTRIBUTE_ERROR_CODE = "missing_user_name_attribute";
    private static final String INVALID_USER_INFO_RESPONSE_ERROR_CODE = "invalid_user_info_response";
    private static final ParameterizedTypeReference<Map<String, Object>> PARAMETERIZED_RESPONSE_TYPE = new ParameterizedTypeReference<Map<String, Object>>() {
    };
    private Converter<OAuth2UserRequest, RequestEntity<?>> requestEntityConverter = new OAuth2UserRequestEntityConverter();
    private Converter<OAuth2UserRequest, Converter<Map<String, Object>, Map<String, Object>>> attributesConverter = (request) -> {
        return (attributes) -> {
            return attributes;
        };
    };
    private RestOperations restOperations;

    public DefaultOAuth2UserServiceNaver() {
        RestTemplate restTemplate = new RestTemplate();
        restTemplate.setErrorHandler(new OAuth2ErrorResponseErrorHandler());
        this.restOperations = restTemplate;
    }

    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        Assert.notNull(userRequest, "userRequest cannot be null");
        String userNameAttributeName = this.getUserNameAttributeName(userRequest);
        RequestEntity<?> request = (RequestEntity)this.requestEntityConverter.convert(userRequest);
        ResponseEntity<Map<String, Object>> response = this.getResponse(userRequest, request);
        OAuth2AccessToken token = userRequest.getAccessToken();
        Map<String, Object> attributes = (Map)((Converter)this.attributesConverter.convert(userRequest)).convert((Map)response.getBody());
        Collection<GrantedAuthority> authorities = this.getAuthorities(token, attributes);
        return new DefaultOAuth2UserNaver(authorities, attributes, userNameAttributeName);
    }

    public void setAttributesConverter(Converter<OAuth2UserRequest, Converter<Map<String, Object>, Map<String, Object>>> attributesConverter) {
        Assert.notNull(attributesConverter, "attributesConverter cannot be null");
        this.attributesConverter = attributesConverter;
    }

    private ResponseEntity<Map<String, Object>> getResponse(OAuth2UserRequest userRequest, RequestEntity<?> request) {
        OAuth2Error oauth2Error;
        try {
            return this.restOperations.exchange(request, PARAMETERIZED_RESPONSE_TYPE);
        } catch (OAuth2AuthorizationException var6) {
            OAuth2AuthorizationException ex = var6;
            oauth2Error = ex.getError();
            StringBuilder errorDetails = new StringBuilder();
            errorDetails.append("Error details: [");
            errorDetails.append("UserInfo Uri: ").append(userRequest.getClientRegistration().getProviderDetails().getUserInfoEndpoint().getUri());
            errorDetails.append(", Error Code: ").append(oauth2Error.getErrorCode());
            if (oauth2Error.getDescription() != null) {
                errorDetails.append(", Error Description: ").append(oauth2Error.getDescription());
            }

            errorDetails.append("]");
            oauth2Error = new OAuth2Error("invalid_user_info_response", "An error occurred while attempting to retrieve the UserInfo Resource: " + errorDetails.toString(), (String)null);
            throw new OAuth2AuthenticationException(oauth2Error, oauth2Error.toString(), ex);
        } catch (UnknownContentTypeException var7) {
            UnknownContentTypeException ex = var7;
            String var10000 = userRequest.getClientRegistration().getProviderDetails().getUserInfoEndpoint().getUri();
            String errorMessage = "An error occurred while attempting to retrieve the UserInfo Resource from '" + var10000 + "': response contains invalid content type '" + ex.getContentType().toString() + "'. The UserInfo Response should return a JSON object (content type 'application/json') that contains a collection of name and value pairs of the claims about the authenticated End-User. Please ensure the UserInfo Uri in UserInfoEndpoint for Client Registration '" + userRequest.getClientRegistration().getRegistrationId() + "' conforms to the UserInfo Endpoint, as defined in OpenID Connect 1.0: 'https://openid.net/specs/openid-connect-core-1_0.html#UserInfo'";
            OAuth2Error oauth2Error1 = new OAuth2Error("invalid_user_info_response", errorMessage, (String)null);
            throw new OAuth2AuthenticationException(oauth2Error1, oauth2Error1.toString(), ex);
        } catch (RestClientException var8) {
            RestClientException ex = var8;
            oauth2Error = new OAuth2Error("invalid_user_info_response", "An error occurred while attempting to retrieve the UserInfo Resource: " + ex.getMessage(), (String)null);
            throw new OAuth2AuthenticationException(oauth2Error, oauth2Error.toString(), ex);
        }
    }

    private String getUserNameAttributeName(OAuth2UserRequest userRequest) {
        if (!StringUtils.hasText(userRequest.getClientRegistration().getProviderDetails().getUserInfoEndpoint().getUri())) {
            OAuth2Error oauth2Error = new OAuth2Error("missing_user_info_uri", "Missing required UserInfo Uri in UserInfoEndpoint for Client Registration: " + userRequest.getClientRegistration().getRegistrationId(), (String)null);
            throw new OAuth2AuthenticationException(oauth2Error, oauth2Error.toString());
        } else {
            String userNameAttributeName = userRequest.getClientRegistration().getProviderDetails().getUserInfoEndpoint().getUserNameAttributeName();
            if (!StringUtils.hasText(userNameAttributeName)) {
                OAuth2Error oauth2Error = new OAuth2Error("missing_user_name_attribute", "Missing required \"user name\" attribute name in UserInfoEndpoint for Client Registration: " + userRequest.getClientRegistration().getRegistrationId(), (String)null);
                throw new OAuth2AuthenticationException(oauth2Error, oauth2Error.toString());
            } else {
                return userNameAttributeName;
            }
        }
    }

    private Collection<GrantedAuthority> getAuthorities(OAuth2AccessToken token, Map<String, Object> attributes) {
        Collection<GrantedAuthority> authorities = new LinkedHashSet();
        authorities.add(new OAuth2UserAuthority(attributes));
        Iterator var4 = token.getScopes().iterator();

        while(var4.hasNext()) {
            String authority = (String)var4.next();
            authorities.add(new SimpleGrantedAuthority("SCOPE_" + authority));
        }

        return authorities;
    }

    public final void setRequestEntityConverter(Converter<OAuth2UserRequest, RequestEntity<?>> requestEntityConverter) {
        Assert.notNull(requestEntityConverter, "requestEntityConverter cannot be null");
        this.requestEntityConverter = requestEntityConverter;
    }

    public final void setRestOperations(RestOperations restOperations) {
        Assert.notNull(restOperations, "restOperations cannot be null");
        this.restOperations = restOperations;
    }
}
