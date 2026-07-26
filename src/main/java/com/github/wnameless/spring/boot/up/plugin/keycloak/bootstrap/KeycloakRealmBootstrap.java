package com.github.wnameless.spring.boot.up.plugin.keycloak.bootstrap;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;
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
 * <p>The realm JSON and the three PEM files are generated all-or-nothing. They share two RSA key
 * pairs - the realm JSON embeds both, while each PEM holds one half - so regenerating a subset
 * would pair a fresh certificate with a stale private key. That mismatch is invisible at build
 * time and only surfaces as a SAML signature failure at login, so a partially present set is
 * rejected instead of being completed.
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
   * The realm JSON and the three PEM files are treated as a single unit: they are skipped when all
   * of them already exist, generated when none of them exist, and rejected when only some exist.
   *
   * @param args command line arguments (not used)
   * @throws Exception if any error occurs during file generation
   * @throws IllegalStateException if the realm and certificate files are only partially present
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

    String configPackage = System.getProperty("configPackage");
    if (!Strings.isNullOrEmpty(configPackage)) {
      String configPackagePath = configPackage.replace('.', '/');
      configPackagePath = PathUtils.joinPath(baseDir, "..", "java", configPackagePath);
      File configFile = new File(configPackagePath + "/KeycloakPluginSecurityConfig.java");

      if (configFile.exists()) {
        LOG.warn("Skipping: " + configPackagePath + "/KeycloakPluginSecurityConfig.java already exists");
      } else {
        ClassPathResource securityConfigTemplate =
            new ClassPathResource("KeycloakPluginSecurityConfig.template");
        String securityConfigJava =
            new String(securityConfigTemplate.getInputStream().readAllBytes());
        securityConfigJava = "package " + configPackage + ";\n" + securityConfigJava;
        Files.createDirectories(Paths.get(configPackagePath));
        try (FileWriter writer = new FileWriter(configFile)) {
          LOG.info("Generating: " + configPackagePath + "/KeycloakPluginSecurityConfig.java");
          writer.write(securityConfigJava);
        }
      }
    }

    String realmName = System.getProperty("realmName");
    if (Strings.isNullOrEmpty(realmName)) realmName = "webmvc";
    LOG.info("Realm Name: " + realmName);
    String clientId = System.getProperty("clientId");
    if (Strings.isNullOrEmpty(clientId)) clientId = "webmvc-app";
    LOG.info("Client ID: " + clientId);

    // The four files below share two RSA key pairs and must therefore be generated as a set
    File realmJsonFile = new File(baseDir, REALM_JSON);
    File appPkFile = new File(baseDir, APP_PK);
    File appCertFile = new File(baseDir, APP_CERT);
    File serverCertFile = new File(baseDir, SERVER_CERT);
    List<File> bundle = List.of(realmJsonFile, appPkFile, appCertFile, serverCertFile);

    List<File> present = bundle.stream().filter(File::exists).toList();
    if (present.size() == bundle.size()) {
      LOG.warn("Skipping: " + baseDir + " already holds " + names(bundle)
          + ". Delete all of them to regenerate a matching set.");
      return;
    }
    if (!present.isEmpty()) {
      List<File> missing = bundle.stream().filter(f -> !f.exists()).toList();
      throw new IllegalStateException("Refusing to regenerate a partial realm/certificate set in "
          + baseDir + ". Already present: " + names(present) + ". Missing: " + names(missing)
          + ". These files share two RSA key pairs, so generating only the missing ones would pair"
          + " a fresh certificate with a stale private key and every SAML signature would fail."
          + " Delete the files that are still present, then run the bootstrap again.");
    }

    SelfSignedX509Certificate app = new SelfSignedX509Certificate(clientId, 3650);
    SelfSignedX509Certificate keycloak = new SelfSignedX509Certificate(realmName, 3650);

    ClassPathResource jsonTemplate =
        new ClassPathResource("spring-boot-up-keycloak-plugin-realm-template.json");
    String realmTemplate =
        new String(jsonTemplate.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
    String realmJson =
        String.format(realmTemplate, app.getTrimPrivateKeyPem(), app.getTrimCertificatePem(),
            keycloak.getTrimPrivateKeyPem(), keycloak.getTrimCertificatePem());
    realmJson = realmJson.replace("${realmName}", realmName);
    realmJson = realmJson.replace("${clientId}", clientId);

    // Everything is rendered before the first write, so a failure above leaves no partial set
    Files.createDirectories(Paths.get(baseDir));
    write(realmJsonFile, realmJson);
    write(appPkFile, app.getPrivateKeyPem());
    write(appCertFile, app.getCertificatePem());
    write(serverCertFile, keycloak.getCertificatePem());
  }

  /**
   * Writes generated content to a file, logging the destination.
   *
   * @param file the destination file
   * @param content the content to write
   * @throws IOException if the file cannot be written
   */
  private static void write(File file, String content) throws IOException {
    LOG.info("Generating: " + file.getPath());
    try (FileWriter writer = new FileWriter(file, StandardCharsets.UTF_8)) {
      writer.write(content);
    }
  }

  /**
   * Renders file names for the all-or-nothing diagnostics.
   *
   * @param files the files to name
   * @return a comma separated list of file names
   */
  private static String names(List<File> files) {
    return files.stream().map(File::getName).collect(Collectors.joining(", "));
  }

}
