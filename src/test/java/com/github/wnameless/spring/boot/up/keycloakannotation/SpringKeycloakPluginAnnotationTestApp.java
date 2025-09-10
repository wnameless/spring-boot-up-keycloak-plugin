package com.github.wnameless.spring.boot.up.keycloakannotation;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Test application for Keycloak plugin with annotation-based configuration.
 * 
 * <p>This test application demonstrates the use of the Keycloak plugin
 * through the {@code @EnableKeycloakPlugin} annotation, which provides
 * automatic configuration of SAML2 authentication with embedded Keycloak.
 * 
 * @author Wei-Ming Wu
 * @since 1.0.0
 * @see AppConfig
 */
@SpringBootApplication
public class SpringKeycloakPluginAnnotationTestApp {

  /**
   * Main method to run the test application.
   * 
   * @param args command line arguments
   */
  public static void main(String[] args) {
    SpringApplication.run(SpringKeycloakPluginAnnotationTestApp.class, args);
  }

}
