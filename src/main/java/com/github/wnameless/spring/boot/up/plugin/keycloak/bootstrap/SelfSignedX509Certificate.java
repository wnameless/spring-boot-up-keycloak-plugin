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

public class SelfSignedX509Certificate {

  private final String alias;
  private final int validDays;
  private final KeyPair keyPair;
  private final X509Certificate certificate;

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

  public String getAlias() {
    return this.alias;
  }

  public int getValidDays() {
    return this.validDays;
  }

  public KeyPair getKeyPair() {
    return this.keyPair;
  }

  public X509Certificate getCertificate() {
    return this.certificate;
  }

  public String getTrimPrivateKeyPem() {
    return Base64.getEncoder().encodeToString(keyPair.getPrivate().getEncoded());
  }

  public String getPrivateKeyPem() {
    return "-----BEGIN " + "PRIVATE KEY" + "-----\n" + getTrimPrivateKeyPem() + "\n-----END "
        + "PRIVATE KEY" + "-----\n";
  }

  public String getTrimCertificatePem() {
    try {
      return Base64.getEncoder().encodeToString(certificate.getEncoded());
    } catch (CertificateEncodingException e) {
      throw new RuntimeException(e);
    }
  }

  public String getCertificatePem() {
    return "-----BEGIN " + "CERTIFICATE" + "-----\n" + getTrimCertificatePem() + "\n-----END "
        + "CERTIFICATE" + "-----\n";
  }

  public KeyStore createPKCS12(String password)
      throws KeyStoreException, NoSuchAlgorithmException, CertificateException, IOException {
    KeyStore pkcs12Store = KeyStore.getInstance("PKCS12");
    pkcs12Store.load(null, null); // Init empty keystore
    pkcs12Store.setKeyEntry(alias, keyPair.getPrivate(), password.toCharArray(),
        new X509Certificate[] {certificate});
    return pkcs12Store;
  }

}
