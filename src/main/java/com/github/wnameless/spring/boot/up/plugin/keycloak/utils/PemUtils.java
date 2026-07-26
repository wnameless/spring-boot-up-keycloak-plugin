package com.github.wnameless.spring.boot.up.plugin.keycloak.utils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;

/**
 * Utility class for loading PEM encoded X.509 certificates and private keys.
 *
 * <p>Locations are resolved through a {@link DefaultResourceLoader}, so all of the following are
 * accepted:
 * <ul>
 * <li>{@code app_certificate.pem} - a bare name, resolved from the classpath</li>
 * <li>{@code classpath:certs/app_certificate.pem} - an explicit classpath location</li>
 * <li>{@code file:/etc/myapp/app_certificate.pem} - a file outside the application archive</li>
 * </ul>
 *
 * <p>Keeping credentials outside the packaged jar is the reason the file prefix is supported: a
 * private key baked into the artifact cannot be rotated without a rebuild and is distributed to
 * everyone who receives the artifact.
 *
 * @author Wei-Ming Wu
 * @since 26.3.0.0
 */
public final class PemUtils {

  private static final ResourceLoader RESOURCE_LOADER = new DefaultResourceLoader();

  private static final String PK_HEADER = "-----BEGIN PRIVATE KEY-----";
  private static final String PK_FOOTER = "-----END PRIVATE KEY-----";

  /**
   * Private constructor to prevent instantiation of utility class.
   */
  private PemUtils() {}

  /**
   * Loads a PEM encoded X.509 certificate.
   *
   * @param location a bare classpath name, or a {@code classpath:}/{@code file:}/URL location
   * @return the certificate
   * @throws IllegalStateException if the location cannot be resolved or does not hold a valid
   *         X.509 certificate
   */
  public static X509Certificate loadCertificate(String location) {
    try (InputStream in = resolve(location).getInputStream()) {
      return (X509Certificate) CertificateFactory.getInstance("X.509").generateCertificate(in);
    } catch (GeneralSecurityException | IOException e) {
      throw new IllegalStateException("Cannot load X.509 certificate from '" + location + "'", e);
    }
  }

  /**
   * Loads a PEM encoded PKCS#8 RSA private key.
   *
   * @param location a bare classpath name, or a {@code classpath:}/{@code file:}/URL location
   * @return the private key
   * @throws IllegalStateException if the location cannot be resolved or does not hold a valid
   *         PKCS#8 RSA private key
   */
  public static PrivateKey loadPrivateKey(String location) {
    String pem;
    try (InputStream in = resolve(location).getInputStream()) {
      pem = new String(in.readAllBytes(), StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new IllegalStateException("Cannot read private key from '" + location + "'", e);
    }

    String base64 = pem.replace(PK_HEADER, "").replace(PK_FOOTER, "").replaceAll("\\s", "");
    try {
      return KeyFactory.getInstance("RSA")
          .generatePrivate(new PKCS8EncodedKeySpec(Base64.getDecoder().decode(base64)));
    } catch (GeneralSecurityException | IllegalArgumentException e) {
      throw new IllegalStateException(
          "Cannot parse a PKCS#8 RSA private key from '" + location + "'", e);
    }
  }

  /**
   * Resolves a location, failing with an actionable message when nothing is found there.
   *
   * @param location a bare classpath name, or a {@code classpath:}/{@code file:}/URL location
   * @return the resolved resource
   * @throws IllegalStateException if the resource does not exist
   */
  static Resource resolve(String location) {
    Resource resource = RESOURCE_LOADER.getResource(location);
    if (!resource.exists()) {
      throw new IllegalStateException("PEM resource not found: '" + location
          + "'. Expected a bare classpath name (app_certificate.pem), an explicit classpath"
          + " location (classpath:certs/app_certificate.pem) or a file location"
          + " (file:/etc/myapp/app_certificate.pem).");
    }
    return resource;
  }

}
