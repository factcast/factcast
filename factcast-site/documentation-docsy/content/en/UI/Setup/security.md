---
title: "Security"
type: docs
weight: 300
---

## Authentication & Authorization

By default, the FactCast-Server UI supports the same authentication/authorization approach as the GRPC server
explained [here](/setup/security/).

However, you might want to make UI accessible via a centrally managed Active Directory. Luckily, we use Spring Security
under the hood and all this is possible.

### OIDC Example

The following example shows an OIDC integration:

1. You have to exclude the standard configuration, that configures Spring Security & Vaadin to support the default
   authentication/authorization approach.

```
@SpringBootApplication(exclude = {FactCastServerUISecurityAutoConfiguration.class})
```

2. Add necessary Spring Security dependencies and configurations:

```
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-oauth2-client</artifactId>
</dependency>
<dependency>
    <groupId>org.springframework.security</groupId>
    <artifactId>spring-security-oauth2-jose</artifactId>
</dependency>
```

3. Implement custom security configuration

```
@Configuration
@EnableWebSecurity
public class SecurityConfig extends VaadinWebSecurity {
  @Override
  protected void configure(HttpSecurity http) throws Exception {
    http.oauth2Login(Customizer.withDefaults()); // enable oauth2 login
    super.configure(http);
  }
}
```

4. Implement a `OAuth2UserService` that maps from a `OidcUser` to a `FactCastOidcUser` which is usable by the
   FactCast-Server UI.

```
@Component
public class FactCastUserService implements OAuth2UserService<OidcUserRequest, OidcUser> {
  private final OidcUserService oidcUserService = new OidcUserService();

  @Override
  public OidcUser loadUser(OidcUserRequest userRequest) throws OAuth2AuthenticationException {
    // that gives you a fully authenticated user via your OAuth/OIDC provider
    OidcUser user = oidcUserService.loadUser(userRequest);

    // either create the account yourself or reuse factcast-access.json here
    FactCastAccount account = new FactCastAccount(user.getEmail(), List.of());

    return new FactCastOidcUser(account, "unused", user);
  }
}
```

with this being the `FactCastOidcUser`

```
public class FactCastOidcUser extends FactCastUser implements OidcUser {
  private final OidcUser user;

  public FactCastOidcUser(FactCastAccount account, String secret, OidcUser user) {
    super(account, secret);
    this.user = user;
  }

  @Override
  public Map<String, Object> getClaims() {
    return user.getClaims();
  }

  @Override
  public OidcUserInfo getUserInfo() {
    return user.getUserInfo();
  }

  @Override
  public OidcIdToken getIdToken() {
    return user.getIdToken();
  }

  @Override
  public Map<String, Object> getAttributes() {
    return user.getAttributes();
  }

  @Override
  public String getName() {
    return user.getName();
  }
}
```
