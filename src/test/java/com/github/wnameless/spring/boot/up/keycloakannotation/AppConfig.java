package com.github.wnameless.spring.boot.up.keycloakannotation;

import org.springframework.context.annotation.Configuration;
import com.github.wnameless.spring.boot.up.plugin.keycloak.config.EnableKeycloakPlugin;

/**
 * Application configuration that enables the Keycloak plugin.
 * 
 * <p>This configuration class demonstrates the simplest way to enable
 * Keycloak SAML2 authentication in a Spring Boot application by using
 * the {@code @EnableKeycloakPlugin} annotation.
 * 
 * @author Wei-Ming Wu
 * @since 1.0.0
 * @see com.github.wnameless.spring.boot.up.plugin.keycloak.config.EnableKeycloakPlugin
 */
@EnableKeycloakPlugin
@Configuration
public class AppConfig {}
