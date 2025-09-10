package com.github.wnameless.spring.boot.up.keycloakconfig;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Test application for Keycloak plugin with manual configuration.
 * 
 * <p>This test application demonstrates the use of the Keycloak plugin
 * with explicit security configuration rather than using the
 * {@code @EnableKeycloakPlugin} annotation.
 * 
 * @author Wei-Ming Wu
 * @since 1.0.0
 */
@SpringBootApplication
public class SpringKeycloakPluginConfigTestApp {

  /**
   * Main method to run the test application.
   * 
   * @param args command line arguments
   */
  public static void main(String[] args) {
    SpringApplication.run(SpringKeycloakPluginConfigTestApp.class, args);
  }

}
