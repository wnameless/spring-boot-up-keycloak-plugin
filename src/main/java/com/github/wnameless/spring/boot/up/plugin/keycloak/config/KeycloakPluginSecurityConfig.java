package com.github.wnameless.spring.boot.up.plugin.keycloak.config;

import java.io.IOException;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityCustomizer;
import org.springframework.security.saml2.core.OpenSamlInitializationService;
import org.springframework.security.saml2.core.Saml2X509Credential;
import org.springframework.security.saml2.provider.service.metadata.OpenSamlMetadataResolver;
import org.springframework.security.saml2.provider.service.registration.InMemoryRelyingPartyRegistrationRepository;
import org.springframework.security.saml2.provider.service.registration.RelyingPartyRegistration;
import org.springframework.security.saml2.provider.service.registration.RelyingPartyRegistrationRepository;
import org.springframework.security.saml2.provider.service.web.DefaultRelyingPartyRegistrationResolver;
import org.springframework.security.saml2.provider.service.web.RelyingPartyRegistrationResolver;
import org.springframework.security.saml2.provider.service.web.Saml2MetadataFilter;
import org.springframework.security.saml2.provider.service.web.authentication.Saml2WebSsoAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.web.servlet.handler.HandlerMappingIntrospector;
import com.github.wnameless.spring.boot.up.embedded.keycloak.config.KeycloakServerProperties;

@ConditionalOnBean(annotation = {EnableKeycloakPlugin.class, Configuration.class})
@EnableWebSecurity
@Configuration
public class KeycloakPluginSecurityConfig {

  private static final Logger LOG = LoggerFactory.getLogger(KeycloakPluginSecurityConfig.class);

  @Value("${keycloak.base.url:http://localhost:8080}")
  String keycloakBaseUrl;

  @Autowired
  KeycloakServerProperties keycloakServerProperties;

  @Bean
  public WebSecurityCustomizer webSecurityCustomizer(KeycloakServerProperties props) {
    return (web) -> web.ignoring()
        .requestMatchers(new AntPathRequestMatcher(props.getContextPath() + "/**"));
  }

  static {
    OpenSamlInitializationService.initialize();
  }

  @Bean
  RelyingPartyRegistrationRepository relyingPartyRegistrations() {
    PrivateKey webmvcPK = loadWebmvPK();
    X509Certificate webmvcCert = loadWebmvCert();
    X509Certificate keycloakCert = loadKeycloakCert();
    RelyingPartyRegistration registration = RelyingPartyRegistration //
        .withRegistrationId("webmvc") //
        .entityId("webmvc-app") //
        .signingX509Credentials((c) -> c.add(Saml2X509Credential.signing(webmvcPK, webmvcCert)))
        .decryptionX509Credentials(
            (c) -> c.add(Saml2X509Credential.decryption(webmvcPK, webmvcCert)))
        .assertingPartyDetails((details) -> {
          details.entityId(
              keycloakBaseUrl + keycloakServerProperties.getContextPath() + "/realms/webmvc");
          details.singleSignOnServiceLocation(keycloakBaseUrl
              + keycloakServerProperties.getContextPath() + "/realms/webmvc/protocol/saml");
          details.singleLogoutServiceLocation(keycloakBaseUrl
              + keycloakServerProperties.getContextPath() + "/realms/webmvc/protocol/saml");
          details
              .encryptionX509Credentials(c -> c.add(Saml2X509Credential.encryption(keycloakCert)));
          details.verificationX509Credentials(
              c -> c.add(Saml2X509Credential.verification(keycloakCert)));
          details.wantAuthnRequestsSigned(true);
        }).build();
    return new InMemoryRelyingPartyRegistrationRepository(registration);
  }

  private X509Certificate loadKeycloakCert() {
    Resource keystoreRes = new ClassPathResource("keycloak_certificate.pem");
    try (var localByteArrayInputStream = keystoreRes.getInputStream()) {
      CertificateFactory localCertificateFactory = CertificateFactory.getInstance("X.509");
      X509Certificate localX509Certificate =
          (X509Certificate) localCertificateFactory.generateCertificate(localByteArrayInputStream);
      localByteArrayInputStream.close();
      return localX509Certificate;
    } catch (CertificateException | IOException e) {
      LOG.error("Classpath: keycloak_certificate.pem NOT found", e);
      throw new RuntimeException(e);
    }
  }

  private PrivateKey loadWebmvPK() {
    Resource pkRes = new ClassPathResource("app_private_key.pem");
    String pkPem;
    try {
      pkPem = new String(pkRes.getInputStream().readAllBytes())
          .replace("-----BEGIN PRIVATE KEY-----", "").replace("-----END PRIVATE KEY-----", "")
          .replaceAll("\\s", "");
      byte[] pkcs8EncodedBytes = Base64.getDecoder().decode(pkPem);
      PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(pkcs8EncodedBytes);
      KeyFactory keyFactory = KeyFactory.getInstance("RSA"); // or "EC" for elliptic curve keys
      return keyFactory.generatePrivate(keySpec);
    } catch (Exception e) {
      LOG.error("Classpath: app_private_key.pem NOT found", e);
      throw new RuntimeException(e);
    }
  }

  private X509Certificate loadWebmvCert() {
    Resource keystoreRes = new ClassPathResource("app_certificate.pem");
    try (var localByteArrayInputStream = keystoreRes.getInputStream()) {
      CertificateFactory localCertificateFactory = CertificateFactory.getInstance("X.509");
      X509Certificate localX509Certificate =
          (X509Certificate) localCertificateFactory.generateCertificate(localByteArrayInputStream);
      localByteArrayInputStream.close();
      return localX509Certificate;
    } catch (CertificateException | IOException e) {
      LOG.error("Classpath: app_certificate.pem NOT found", e);
      throw new RuntimeException(e);
    }
  }

  @Bean
  SecurityFilterChain securityFilterChain(HttpSecurity http,
      HandlerMappingIntrospector introspector) throws Exception {
    RelyingPartyRegistrationResolver relyingPartyRegistrationResolver =
        new DefaultRelyingPartyRegistrationResolver(relyingPartyRegistrations());
    Saml2MetadataFilter metadataFilter =
        new Saml2MetadataFilter(relyingPartyRegistrationResolver, new OpenSamlMetadataResolver());
    // @formatter:off
		http
			.authorizeHttpRequests((authorize) -> authorize
				.anyRequest().authenticated()
			)
     .saml2Login(Customizer.withDefaults())
			.saml2Logout(Customizer.withDefaults())
			.addFilterBefore(metadataFilter, Saml2WebSsoAuthenticationFilter.class);
		// @formatter:on 
    return http.build();
  }

}
