package com.github.wnameless.spring.boot.up.plugin.keycloak.bootstrap;

import java.io.File;
import java.io.FileWriter;
import org.springframework.core.io.ClassPathResource;

public class KeycloakRealmBootstrap {

  public static void main(String[] args) throws Exception {
    String targetDir = System.getProperty("targetDir");
    String baseDir = null;
    if (targetDir != null) {
      baseDir = targetDir;
    } else {
      baseDir = "./src/main/resources";
    }

    if (new File(baseDir + "/keycloak-realm.json").exists()) return;
    if (new File(baseDir + "/app_private_key.pem").exists()) return;
    if (new File(baseDir + "/app_certificate.pem").exists()) return;
    if (new File(baseDir + "/keycloak_certificate.pem").exists()) return;

    SelfSignedX509Certificate app = new SelfSignedX509Certificate("webmvc-app", 3650);
    SelfSignedX509Certificate keycloak = new SelfSignedX509Certificate("webmvc", 3650);

    ClassPathResource jsonTemplate =
        new ClassPathResource("spring-boot-up-keycloak-plugin-realm-template.json");
    String realmTemplate = new String(jsonTemplate.getInputStream().readAllBytes());
    String realmJson =
        String.format(realmTemplate, app.getTrimPrivateKeyPem(), app.getTrimCertificatePem(),
            keycloak.getTrimPrivateKeyPem(), keycloak.getTrimCertificatePem());

    try (FileWriter writer = new FileWriter(baseDir + "/keycloak-realm.json")) {
      writer.write(realmJson);
    }
    try (FileWriter writer = new FileWriter(baseDir + "/app_private_key.pem")) {
      writer.write(app.getPrivateKeyPem());
    }
    try (FileWriter writer = new FileWriter(baseDir + "/app_certificate.pem")) {
      writer.write(app.getCertificatePem());
    }
    try (FileWriter writer = new FileWriter(baseDir + "/keycloak_certificate.pem")) {
      writer.write(keycloak.getCertificatePem());
    }
  }

}
