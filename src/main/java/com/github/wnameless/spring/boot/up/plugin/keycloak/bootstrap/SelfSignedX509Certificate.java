package com.github.wnameless.spring.boot.up.plugin.keycloak.bootstrap;

import java.io.IOException;
import java.math.BigInteger;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.Security;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Base64;
import java.util.Date;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;

/**
 * Utility class for generating self-signed X.509 certificates.
 * 
 * <p>This class creates RSA key pairs and self-signed X.509 certificates
 * for use in SAML authentication. It uses the Bouncy Castle provider
 * for cryptographic operations.
 * 
 * <p>Generated certificates include:
 * <ul>
 *   <li>2048-bit RSA key pair</li>
 *   <li>SHA256withRSA signature algorithm</li>
 *   <li>Configurable validity period</li>
 *   <li>PEM format export capabilities</li>
 * </ul>
 * 
 * @author Wei-Ming Wu
 * @since 1.0.0
 */
public class SelfSignedX509Certificate {

  private final String alias;
  private final int validDays;
  private final KeyPair keyPair;
  private final X509Certificate certificate;

  /**
   * Creates a new self-signed X.509 certificate with the specified alias and validity period.
   * 
   * @param alias the certificate alias, used as the Common Name (CN) in the certificate
   * @param validDays the number of days the certificate should be valid
   * @throws NoSuchAlgorithmException if RSA algorithm is not available
   * @throws NoSuchProviderException if Bouncy Castle provider is not available
   * @throws OperatorCreationException if content signer cannot be created
   * @throws CertificateException if certificate generation fails
   */
  public SelfSignedX509Certificate(String alias, int validDays) throws NoSuchAlgorithmException,
      NoSuchProviderException, OperatorCreationException, CertificateException {
    this.alias = alias;
    this.validDays = validDays;
    Security.addProvider(new BouncyCastleProvider());

    // Generate the RSA KeyPair
    KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA", "BC");
    keyPairGenerator.initialize(2048);
    keyPair = keyPairGenerator.generateKeyPair();

    // Generate X.509 certificate
    X500Name issuer = new X500Name("CN=" + alias);
    X500Name subject = new X500Name("CN=" + alias);
    BigInteger serial = BigInteger.valueOf(System.currentTimeMillis());
    X509v3CertificateBuilder certificateBuilder = new JcaX509v3CertificateBuilder(issuer, serial,
        // Ten year validity
        new Date(), new Date(System.currentTimeMillis() + (1000L * 60 * 60 * 24 * validDays)),
        subject, keyPair.getPublic());
    ContentSigner signer =
        new JcaContentSignerBuilder("SHA256withRSA").setProvider("BC").build(keyPair.getPrivate());
    certificate = new JcaX509CertificateConverter().setProvider("BC")
        .getCertificate(certificateBuilder.build(signer));
  }

  /**
   * Gets the certificate alias.
   * 
   * @return the certificate alias
   */
  public String getAlias() {
    return this.alias;
  }

  /**
   * Gets the validity period in days.
   * 
   * @return the number of days the certificate is valid
   */
  public int getValidDays() {
    return this.validDays;
  }

  /**
   * Gets the RSA key pair.
   * 
   * @return the RSA key pair
   */
  public KeyPair getKeyPair() {
    return this.keyPair;
  }

  /**
   * Gets the X.509 certificate.
   * 
   * @return the self-signed X.509 certificate
   */
  public X509Certificate getCertificate() {
    return this.certificate;
  }

  /**
   * Gets the private key in Base64 encoded format without PEM headers.
   * 
   * @return the Base64 encoded private key
   */
  public String getTrimPrivateKeyPem() {
    return Base64.getEncoder().encodeToString(keyPair.getPrivate().getEncoded());
  }

  /**
   * Gets the private key in PEM format.
   * 
   * @return the private key with PEM headers
   */
  public String getPrivateKeyPem() {
    return "-----BEGIN " + "PRIVATE KEY" + "-----\n" + getTrimPrivateKeyPem() + "\n-----END "
        + "PRIVATE KEY" + "-----\n";
  }

  /**
   * Gets the certificate in Base64 encoded format without PEM headers.
   * 
   * @return the Base64 encoded certificate
   * @throws RuntimeException if certificate encoding fails
   */
  public String getTrimCertificatePem() {
    try {
      return Base64.getEncoder().encodeToString(certificate.getEncoded());
    } catch (CertificateEncodingException e) {
      throw new RuntimeException(e);
    }
  }

  /**
   * Gets the certificate in PEM format.
   * 
   * @return the certificate with PEM headers
   */
  public String getCertificatePem() {
    return "-----BEGIN " + "CERTIFICATE" + "-----\n" + getTrimCertificatePem() + "\n-----END "
        + "CERTIFICATE" + "-----\n";
  }

  /**
   * Creates a PKCS12 keystore containing the private key and certificate.
   * 
   * @param password the password to protect the keystore
   * @return a PKCS12 keystore containing the key pair and certificate
   * @throws KeyStoreException if keystore cannot be created
   * @throws NoSuchAlgorithmException if PKCS12 algorithm is not available
   * @throws CertificateException if certificate cannot be stored
   * @throws IOException if keystore initialization fails
   */
  public KeyStore createPKCS12(String password)
      throws KeyStoreException, NoSuchAlgorithmException, CertificateException, IOException {
    KeyStore pkcs12Store = KeyStore.getInstance("PKCS12");
    pkcs12Store.load(null, null); // Init empty keystore
    pkcs12Store.setKeyEntry(alias, keyPair.getPrivate(), password.toCharArray(),
        new X509Certificate[] {certificate});
    return pkcs12Store;
  }

}
