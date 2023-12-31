package com.github.wnameless.spring.boot.up;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
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
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
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
import com.github.wnameless.spring.boot.up.embedded.keycloak.config.KeycloakServerProperties;
import com.github.wnameless.spring.boot.up.plugin.keycloak.config.EnableKeycloakPlugin;
import com.github.wnameless.spring.boot.up.plugin.keycloak.utils.PathUtils;

@EnableKeycloakPlugin
@Configuration
public class KeycloakPluginSecurityConfig {

  private static final Logger LOG = LoggerFactory.getLogger(KeycloakPluginSecurityConfig.class);

  @Value("${server.ssl.enabled:false}")
  boolean serverSslEnabled;
  @Value("${server.port:8080}")
  int serverPort;

  @Value("${keycloak.plugin.baseUrl:}")
  String keycloakPluginBaseUrl;
  @Value("${keycloak.plugin.realmName:webmvc}")
  String realmName;
  @Value("${keycloak.plugin.clientId:webmvc-app}")
  String clientId;
  @Value("${keycloak.plugin.serverCertPem:keycloak_certificate.pem}")
  String serverCert;
  @Value("${keycloak.plugin.appCertPem:app_certificate.pem}")
  String appCert;
  @Value("${keycloak.plugin.appPrivateKeyPem:app_private_key.pem}")
  String appPK;

  @Autowired
  KeycloakServerProperties keycloakServerProperties;

  @Bean
  WebSecurityCustomizer webSecurityCustomizer(KeycloakServerProperties props) {
    return (web) -> web.ignoring().requestMatchers(
        new AntPathRequestMatcher(PathUtils.joinPath(props.getContextPath(), "/**")));
  }

  static {
    OpenSamlInitializationService.initialize();
  }

  String getHostName() throws UnknownHostException {
    return InetAddress.getLoopbackAddress().getHostName();
  }

  String getBaseUrl() {
    if (!keycloakPluginBaseUrl.isBlank()) {
      LOG.info("Keycloak plugin base URL: " + keycloakPluginBaseUrl);
      return keycloakPluginBaseUrl;
    }

    try {
      String protocolSchema = serverSslEnabled ? "https" : "http";
      return protocolSchema + "://" + getHostName() + ":" + serverPort;
    } catch (UnknownHostException e) {
      throw new RuntimeException(e);
    }
  }

  @Lazy
  @Bean
  RelyingPartyRegistrationRepository relyingPartyRegistrations() {
    PrivateKey webmvcPK = loadWebmvPK();
    X509Certificate webmvcCert = loadWebmvCert();
    X509Certificate keycloakCert = loadKeycloakCert();
    String baseUrl = getBaseUrl();
    RelyingPartyRegistration registration = RelyingPartyRegistration //
        .withRegistrationId(realmName) //
        .entityId(clientId) //
        .signingX509Credentials((c) -> c.add(Saml2X509Credential.signing(webmvcPK, webmvcCert)))
        .decryptionX509Credentials(
            (c) -> c.add(Saml2X509Credential.decryption(webmvcPK, webmvcCert)))
        .assertingPartyDetails((details) -> {
          details.entityId(PathUtils.joinPath(baseUrl, keycloakServerProperties.getContextPath(),
              "/realms/" + realmName));
          details.singleSignOnServiceLocation(
              PathUtils.joinPath(baseUrl, keycloakServerProperties.getContextPath(),
                  "/realms/" + realmName + "/protocol/saml"));
          details.singleLogoutServiceLocation(
              PathUtils.joinPath(baseUrl, keycloakServerProperties.getContextPath(),
                  "/realms/" + realmName + "/protocol/saml"));
          details.encryptionX509Credentials(
              (c) -> c.add(Saml2X509Credential.encryption(keycloakCert)));
          details.verificationX509Credentials(
              (c) -> c.add(Saml2X509Credential.verification(keycloakCert)));
          details.wantAuthnRequestsSigned(true);
        }).build();
    return new InMemoryRelyingPartyRegistrationRepository(registration);
  }

  X509Certificate loadKeycloakCert() {
    Resource keystoreRes = new ClassPathResource(serverCert);
    try (var localByteArrayInputStream = keystoreRes.getInputStream()) {
      CertificateFactory localCertificateFactory = CertificateFactory.getInstance("X.509");
      X509Certificate localX509Certificate =
          (X509Certificate) localCertificateFactory.generateCertificate(localByteArrayInputStream);
      localByteArrayInputStream.close();
      return localX509Certificate;
    } catch (CertificateException | IOException e) {
      LOG.error("Classpath: " + serverCert + " NOT found", e);
      throw new RuntimeException(e);
    }
  }

  PrivateKey loadWebmvPK() {
    Resource pkRes = new ClassPathResource(appPK);
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
      LOG.error("Classpath: " + appPK + " NOT found", e);
      throw new RuntimeException(e);
    }
  }

  X509Certificate loadWebmvCert() {
    Resource keystoreRes = new ClassPathResource(appCert);
    try (var localByteArrayInputStream = keystoreRes.getInputStream()) {
      CertificateFactory localCertificateFactory = CertificateFactory.getInstance("X.509");
      X509Certificate localX509Certificate =
          (X509Certificate) localCertificateFactory.generateCertificate(localByteArrayInputStream);
      localByteArrayInputStream.close();
      return localX509Certificate;
    } catch (CertificateException | IOException e) {
      LOG.error("Classpath: " + appCert + " NOT found", e);
      throw new RuntimeException(e);
    }
  }

  @Lazy
  @Bean
  SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
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
