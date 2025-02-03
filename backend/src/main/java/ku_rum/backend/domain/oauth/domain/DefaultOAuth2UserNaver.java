package ku_rum.backend.domain.oauth.domain;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.util.Assert;

import java.io.Serializable;
import java.util.*;

public class DefaultOAuth2UserNaver implements OAuth2User, Serializable {
    private static final long serialVersionUID = 620L;
    private final Set<GrantedAuthority> authorities;
    private final Map<String, Object> attributes;
    private final String nameAttributeKey;

    public DefaultOAuth2UserNaver(Collection<? extends GrantedAuthority> authorities, Map<String, Object> attributes, String nameAttributeKey) {
        Assert.notEmpty(attributes, "attributes cannot be empty");
        Assert.hasText(nameAttributeKey, "nameAttributeKey cannot be empty");

        //naver의 경우
        Map<String, Object> response = (Map<String, Object>) attributes.get("response");
        if (!response.containsKey(nameAttributeKey)) {
            throw new IllegalArgumentException("Missing attribute '" + nameAttributeKey + "' in attributes");
        } else {
            this.authorities = authorities != null ? Collections.unmodifiableSet(new LinkedHashSet(this.sortAuthorities(authorities))) : Collections.unmodifiableSet(new LinkedHashSet(AuthorityUtils.NO_AUTHORITIES));
            this.attributes = Collections.unmodifiableMap(new LinkedHashMap(response));
            this.nameAttributeKey = nameAttributeKey;
        }
    }


    public String getName() {
        return this.getAttribute(this.nameAttributeKey).toString();
    }

    public Collection<? extends GrantedAuthority> getAuthorities() {
        return this.authorities;
    }

    public Map<String, Object> getAttributes() {
        return this.attributes;
    }

    private Set<GrantedAuthority> sortAuthorities(Collection<? extends GrantedAuthority> authorities) {
        SortedSet<GrantedAuthority> sortedAuthorities = new TreeSet(Comparator.comparing(GrantedAuthority::getAuthority));
        sortedAuthorities.addAll(authorities);
        return sortedAuthorities;
    }

    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        } else if (obj != null && this.getClass() == obj.getClass()) {
            DefaultOAuth2User that = (DefaultOAuth2User)obj;
            if (!this.getName().equals(that.getName())) {
                return false;
            } else {
                return !this.getAuthorities().equals(that.getAuthorities()) ? false : this.getAttributes().equals(that.getAttributes());
            }
        } else {
            return false;
        }
    }

    public int hashCode() {
        int result = this.getName().hashCode();
        result = 31 * result + this.getAuthorities().hashCode();
        result = 31 * result + this.getAttributes().hashCode();
        return result;
    }

    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("Name: [");
        sb.append(this.getName());
        sb.append("], Granted Authorities: [");
        sb.append(this.getAuthorities());
        sb.append("], User Attributes: [");
        sb.append(this.getAttributes());
        sb.append("]");
        return sb.toString();
    }
}

