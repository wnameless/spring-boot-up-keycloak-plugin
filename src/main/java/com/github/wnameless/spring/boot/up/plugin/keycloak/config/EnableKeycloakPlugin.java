package com.github.wnameless.spring.boot.up.plugin.keycloak.config;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.context.annotation.Import;
import com.github.wnameless.spring.boot.up.embedded.keycloak.config.EnableEmbeddedKeycloak;

/**
 * Enables the Keycloak plugin for Spring Boot applications.
 * 
 * <p>This annotation configures a complete SAML2 authentication solution
 * using an embedded Keycloak server. When applied to a Spring Boot
 * application, it:
 * <ul>
 *   <li>Starts an embedded Keycloak server</li>
 *   <li>Configures Spring Security for SAML2 authentication</li>
 *   <li>Sets up the necessary security filters and endpoints</li>
 *   <li>Provides metadata endpoints for SAML Service Provider</li>
 * </ul>
 * 
 * <p>Usage example:
 * <pre>
 * {@code
 * @SpringBootApplication
 * @EnableKeycloakPlugin
 * public class MyApplication {
 *     public static void main(String[] args) {
 *         SpringApplication.run(MyApplication.class, args);
 *     }
 * }
 * }
 * </pre>
 * 
 * @author Wei-Ming Wu
 * @since 1.0.0
 * @see DefaultKeycloakPluginSecurityConfig
 * @see com.github.wnameless.spring.boot.up.embedded.keycloak.config.EnableEmbeddedKeycloak
 */
@Inherited
@Documented
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Import(DefaultKeycloakPluginSecurityConfig.class)
@EnableEmbeddedKeycloak
public @interface EnableKeycloakPlugin {}
