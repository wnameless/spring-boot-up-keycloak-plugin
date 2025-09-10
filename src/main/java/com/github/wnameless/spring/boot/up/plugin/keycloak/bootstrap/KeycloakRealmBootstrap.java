package com.github.wnameless.spring.boot.up.plugin.keycloak.bootstrap;

import java.io.File;
import java.io.FileWriter;
import java.nio.file.Files;
import java.nio.file.Paths;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import com.github.wnameless.spring.boot.up.plugin.keycloak.utils.PathUtils;
import com.google.common.base.Strings;

/**
 * Bootstrap utility for generating Keycloak realm configuration and certificates.
 * 
 * <p>This class provides a main method that generates necessary configuration files
 * for setting up a Keycloak realm with SAML2 authentication, including:
 * <ul>
 *   <li>Keycloak realm JSON configuration</li>
 *   <li>Application private key and certificate</li>
 *   <li>Keycloak server certificate</li>
 *   <li>Optional Spring Security configuration class</li>
 * </ul>
 * 
 * <p>System properties can be used to customize the bootstrap process:
 * <ul>
 *   <li>targetDir - Target directory for generated files (default: ./src/main/resources)</li>
 *   <li>configPackage - Java package for generated security config class</li>
 *   <li>realmName - Name of the Keycloak realm (default: webmvc)</li>
 *   <li>clientId - SAML client ID (default: webmvc-app)</li>
 * </ul>
 * 
 * @author Wei-Ming Wu
 * @since 1.0.0
 */
public class KeycloakRealmBootstrap {

  private static final Logger LOG = LoggerFactory.getLogger(KeycloakRealmBootstrap.class);

  private static final String REALM_JSON = "keycloak-realm.json";
  private static final String APP_PK = "app_private_key.pem";
  private static final String APP_CERT = "app_certificate.pem";
  private static final String SERVER_CERT = "keycloak_certificate.pem";

  /**
   * Main method that bootstraps Keycloak realm configuration.
   * 
   * <p>Generates all necessary files for Keycloak SAML2 authentication setup.
   * Will skip generation if any of the target files already exist.
   * 
   * @param args command line arguments (not used)
   * @throws Exception if any error occurs during file generation
   */
  public static void main(String[] args) throws Exception {
    String targetDir = System.getProperty("targetDir");
    String baseDir = null;
    if (targetDir != null) {
      baseDir = targetDir;
    } else {
      baseDir = "./src/main/resources";
    }
    LOG.info("Base Dir: " + baseDir);

    if (new File(baseDir + "/" + REALM_JSON).exists()) {
      LOG.warn("Bootstrap stopping: " + baseDir + "/" + REALM_JSON + " is already existed");
      return;
    }
    if (new File(baseDir + "/" + APP_PK).exists()) {
      LOG.warn("Bootstrap stopping: " + baseDir + "/" + APP_PK + " is already existed");
      return;
    }
    if (new File(baseDir + "/" + APP_CERT).exists()) {
      LOG.warn("Bootstrap stopping: " + baseDir + "/" + APP_CERT + " is already existed");
      return;
    }
    if (new File(baseDir + "/" + SERVER_CERT).exists()) {
      LOG.warn("Bootstrap stopping: " + baseDir + "/" + SERVER_CERT + " is already existed");
      return;
    }

    String configPackage = System.getProperty("configPackage");
    if (!Strings.isNullOrEmpty(configPackage)) {
      String configPackagePath = configPackage.replace('.', '/');
      configPackagePath = PathUtils.joinPath(baseDir, "..", "java", configPackagePath);

      if (new File(configPackagePath + "/KeycloakPluginSecurityConfig.java").exists()) {
        LOG.warn("Bootstrap stopping: " + configPackagePath + "/KeycloakPluginSecurityConfig.java"
            + " is already existed");
        return;
      }

      ClassPathResource securityConfigTemplate =
          new ClassPathResource("KeycloakPluginSecurityConfig.template");
      String securityConfigJava =
          new String(securityConfigTemplate.getInputStream().readAllBytes());
      securityConfigJava = "package " + configPackage + ";\n" + securityConfigJava;
      Files.createDirectories(Paths.get(configPackagePath));
      try (FileWriter writer =
          new FileWriter(configPackagePath + "/KeycloakPluginSecurityConfig.java")) {
        LOG.info("Generating: " + configPackagePath + "/KeycloakPluginSecurityConfig.java");
        writer.write(securityConfigJava);
      }
    }

    String realmName = System.getProperty("realmName");
    if (Strings.isNullOrEmpty(realmName)) realmName = "webmvc";
    LOG.info("Realm Name: " + realmName);
    String clientId = System.getProperty("clientId");
    if (Strings.isNullOrEmpty(clientId)) clientId = "webmvc-app";
    LOG.info("Client ID: " + clientId);

    SelfSignedX509Certificate app = new SelfSignedX509Certificate(clientId, 3650);
    SelfSignedX509Certificate keycloak = new SelfSignedX509Certificate(realmName, 3650);

    ClassPathResource jsonTemplate =
        new ClassPathResource("spring-boot-up-keycloak-plugin-realm-template.json");
    String realmTemplate = new String(jsonTemplate.getInputStream().readAllBytes());
    String realmJson =
        String.format(realmTemplate, app.getTrimPrivateKeyPem(), app.getTrimCertificatePem(),
            keycloak.getTrimPrivateKeyPem(), keycloak.getTrimCertificatePem());
    realmJson = realmJson.replace("${realmName}", realmName);
    realmJson = realmJson.replace("${clientId}", clientId);

    try (FileWriter writer = new FileWriter(baseDir + "/" + REALM_JSON)) {
      LOG.info("Generating: " + baseDir + "/" + REALM_JSON);
      writer.write(realmJson);
    }
    try (FileWriter writer = new FileWriter(baseDir + "/" + APP_PK)) {
      LOG.info("Generating: " + baseDir + "/" + APP_PK);
      writer.write(app.getPrivateKeyPem());
    }
    try (FileWriter writer = new FileWriter(baseDir + "/" + APP_CERT)) {
      LOG.info("Generating: " + baseDir + "/" + APP_CERT);
      writer.write(app.getCertificatePem());
    }
    try (FileWriter writer = new FileWriter(baseDir + "/" + SERVER_CERT)) {
      LOG.info("Generating: " + baseDir + "/" + SERVER_CERT);
      writer.write(keycloak.getCertificatePem());
    }
  }

}
