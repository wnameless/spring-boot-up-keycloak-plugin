package com.github.wnameless.spring.boot.up.plugin.keycloak.config;

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
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityCustomizer;
import org.springframework.security.saml2.core.OpenSamlInitializationService;
import org.springframework.security.saml2.core.Saml2X509Credential;
import org.springframework.security.saml2.provider.service.metadata.OpenSaml4MetadataResolver;
import org.springframework.security.saml2.provider.service.registration.InMemoryRelyingPartyRegistrationRepository;
import org.springframework.security.saml2.provider.service.registration.RelyingPartyRegistration;
import org.springframework.security.saml2.provider.service.registration.RelyingPartyRegistrationRepository;
import org.springframework.security.saml2.provider.service.web.DefaultRelyingPartyRegistrationResolver;
import org.springframework.security.saml2.provider.service.web.RelyingPartyRegistrationResolver;
import org.springframework.security.saml2.provider.service.web.Saml2MetadataFilter;
import org.springframework.security.saml2.provider.service.web.authentication.Saml2WebSsoAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import com.github.wnameless.spring.boot.up.embedded.keycloak.config.KeycloakServerProperties;
import com.github.wnameless.spring.boot.up.plugin.keycloak.utils.PathUtils;

/**
 * Default Spring Security configuration for Keycloak SAML2 authentication.
 * 
 * <p>This configuration class provides comprehensive SAML2 authentication setup
 * with an embedded Keycloak server. It handles:
 * <ul>
 *   <li>SAML2 relying party registration with Keycloak</li>
 *   <li>X.509 certificate loading for signing and encryption</li>
 *   <li>Security filter chain configuration</li>
 *   <li>Metadata endpoint exposure</li>
 *   <li>Keycloak admin path exclusion from security</li>
 * </ul>
 * 
 * <p>Configuration properties:
 * <ul>
 *   <li>{@code keycloak.plugin.baseUrl} - Base URL for the application</li>
 *   <li>{@code keycloak.plugin.realmName} - Keycloak realm name (default: webmvc)</li>
 *   <li>{@code keycloak.plugin.clientId} - SAML client ID (default: webmvc-app)</li>
 *   <li>{@code keycloak.plugin.serverCertPem} - Path to Keycloak certificate</li>
 *   <li>{@code keycloak.plugin.appCertPem} - Path to application certificate</li>
 *   <li>{@code keycloak.plugin.appPrivateKeyPem} - Path to application private key</li>
 * </ul>
 * 
 * @author Wei-Ming Wu
 * @since 1.0.0
 * @see EnableKeycloakPlugin
 */
@ConditionalOnBean(annotation = {EnableKeycloakPlugin.class})
@EnableWebSecurity
@Configuration
public class DefaultKeycloakPluginSecurityConfig {

  private static final Logger LOG =
      LoggerFactory.getLogger(DefaultKeycloakPluginSecurityConfig.class);

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

  /**
   * Configures web security to exclude Keycloak admin paths.
   * 
   * @param props Keycloak server properties
   * @return WebSecurityCustomizer that ignores Keycloak admin paths
   */
  @Bean
  WebSecurityCustomizer webSecurityCustomizer(KeycloakServerProperties props) {
    return (web) -> web.ignoring()
        .requestMatchers(PathUtils.joinPath(props.getContextPath(), "/**"));
  }

  static {
    OpenSamlInitializationService.initialize();
  }

  /**
   * Gets the hostname for the loopback address.
   * 
   * @return the hostname of the loopback address
   * @throws UnknownHostException if the hostname cannot be determined
   */
  String getHostName() throws UnknownHostException {
    return InetAddress.getLoopbackAddress().getHostName();
  }

  /**
   * Determines the base URL for the application.
   * 
   * <p>Uses the configured base URL if available, otherwise constructs
   * it from the server protocol, hostname, and port.
   * 
   * @return the base URL for the application
   * @throws RuntimeException if hostname cannot be determined
   */
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

  /**
   * Creates the SAML2 relying party registration repository.
   * 
   * <p>Configures the relying party (service provider) settings including:
   * entity ID, signing/decryption credentials, and asserting party metadata.
   * 
   * @return repository containing the relying party registration
   */
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
        .assertingPartyMetadata((metadata) -> {
          metadata.entityId(PathUtils.joinPath(baseUrl, keycloakServerProperties.getContextPath(),
              "/realms/" + realmName));
          metadata.singleSignOnServiceLocation(
              PathUtils.joinPath(baseUrl, keycloakServerProperties.getContextPath(),
                  "/realms/" + realmName + "/protocol/saml"));
          metadata.singleLogoutServiceLocation(
              PathUtils.joinPath(baseUrl, keycloakServerProperties.getContextPath(),
                  "/realms/" + realmName + "/protocol/saml"));
          metadata.encryptionX509Credentials(
              (c) -> c.add(Saml2X509Credential.encryption(keycloakCert)));
          metadata.verificationX509Credentials(
              (c) -> c.add(Saml2X509Credential.verification(keycloakCert)));
          metadata.wantAuthnRequestsSigned(true);
        }).build();
    return new InMemoryRelyingPartyRegistrationRepository(registration);
  }

  /**
   * Loads the Keycloak server X.509 certificate from classpath.
   * 
   * @return the Keycloak server certificate
   * @throws RuntimeException if certificate cannot be loaded
   */
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

  /**
   * Loads the application private key from classpath.
   * 
   * @return the application private key
   * @throws RuntimeException if private key cannot be loaded
   */
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

  /**
   * Loads the application X.509 certificate from classpath.
   * 
   * @return the application certificate
   * @throws RuntimeException if certificate cannot be loaded
   */
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

  /**
   * Configures the Spring Security filter chain for SAML2 authentication.
   * 
   * <p>Sets up:
   * <ul>
   *   <li>Authorization rules requiring authentication for all requests</li>
   *   <li>SAML2 login and logout support</li>
   *   <li>Metadata filter for exposing SP metadata</li>
   * </ul>
   * 
   * @param http the HttpSecurity to configure
   * @return the configured security filter chain
   * @throws Exception if configuration fails
   */
  @Lazy
  @Bean
  SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
    RelyingPartyRegistrationResolver relyingPartyRegistrationResolver =
        new DefaultRelyingPartyRegistrationResolver(relyingPartyRegistrations());
    Saml2MetadataFilter metadataFilter =
        new Saml2MetadataFilter(relyingPartyRegistrationResolver, new OpenSaml4MetadataResolver());
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
