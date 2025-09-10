# Spring Boot Up Keycloak Plugin

[![Maven Central](https://img.shields.io/maven-central/v/com.github.wnameless.spring.boot.up/spring-boot-up-keycloak-plugin.svg)](https://search.maven.org/artifact/com.github.wnameless.spring.boot.up/spring-boot-up-keycloak-plugin)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)

A Spring Boot plugin that provides a standalone authentication solution powered by Embedded Keycloak with SAML2 support.

## Features

- 🔐 **Embedded Keycloak Server** - No external Keycloak installation required
- 🎯 **SAML2 Authentication** - Full SAML2 Service Provider implementation
- ⚡ **Zero Configuration** - Works out of the box with sensible defaults
- 🛠️ **Easy Setup** - Single annotation to enable authentication
- 📜 **Certificate Management** - Automatic X.509 certificate generation
- 🔧 **Customizable** - Flexible configuration options

## Requirements

- Java 17 or higher
- Spring Boot 3.5.5 or compatible version
- Maven 3.6+

## Quick Start

### 1. Add Dependency

Add the following dependency to your `pom.xml`:

```xml
<dependency>
    <groupId>com.github.wnameless.spring.boot.up</groupId>
    <artifactId>spring-boot-up-keycloak-plugin</artifactId>
    <version>3.24.0</version>
</dependency>
```

### 2. Enable the Plugin

Add the `@EnableKeycloakPlugin` annotation to your Spring Boot application:

```java
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import com.github.wnameless.spring.boot.up.plugin.keycloak.config.EnableKeycloakPlugin;

@SpringBootApplication
@EnableKeycloakPlugin
public class MyApplication {
    public static void main(String[] args) {
        SpringApplication.run(MyApplication.class, args);
    }
}
```

### 3. Generate Keycloak Configuration

Run the bootstrap command to generate necessary configuration files:

```bash
mvn exec:java -Dexec.mainClass="com.github.wnameless.spring.boot.up.plugin.keycloak.bootstrap.KeycloakRealmBootstrap"
```

This will generate:
- `keycloak-realm.json` - Keycloak realm configuration
- `app_private_key.pem` - Application private key
- `app_certificate.pem` - Application certificate
- `keycloak_certificate.pem` - Keycloak server certificate

## Configuration

### Application Properties

Configure the plugin in your `application.properties` or `application.yml`:

```properties
# Base URL for the application (optional, auto-detected if not set)
keycloak.plugin.baseUrl=http://localhost:8080
# Can also use dynamic property reference
# keycloak.plugin.baseUrl=${app.url}

# Keycloak realm name (default: webmvc)
keycloak.plugin.realmName=webmvc

# SAML client ID (default: webmvc-app)
keycloak.plugin.clientId=webmvc-app

# Certificate paths (default values shown)
keycloak.plugin.serverCertPem=keycloak_certificate.pem
keycloak.plugin.appCertPem=app_certificate.pem
keycloak.plugin.appPrivateKeyPem=app_private_key.pem

# Embedded Keycloak server settings
keycloak.server.contextPath=/auth
keycloak.server.adminUser=admin
keycloak.server.adminPassword=admin

# Database configuration (optional, uses in-memory H2 by default)
# To persist Keycloak data across restarts, use file-based H2:
# keycloak.connectionsJpa.url=jdbc:h2:file:./keycloak-db;DB_CLOSE_DELAY=-1
```

### Advanced Configuration

This plugin is built on top of [spring-boot-up-embedded-keycloak](https://github.com/wnameless/spring-boot-up-embedded-keycloak). For additional configuration options and advanced settings, please refer to the embedded Keycloak documentation.

### Custom Security Configuration

For advanced use cases, you can create a custom security configuration instead of using `@EnableKeycloakPlugin`:

```java
@Configuration
@EnableWebSecurity
@EnableEmbeddedKeycloak
public class CustomKeycloakSecurityConfig {
    // Custom security configuration
    // See DefaultKeycloakPluginSecurityConfig for reference
}
```

## Bootstrap Options

The KeycloakRealmBootstrap utility supports system properties for customization:

```bash
# Specify target directory for generated files
mvn exec:java -Dexec.mainClass="..." -DtargetDir=./custom/path

# Specify realm name and client ID
mvn exec:java -Dexec.mainClass="..." -DrealmName=myrealm -DclientId=myapp

# Generate Spring Security configuration class
mvn exec:java -Dexec.mainClass="..." -DconfigPackage=com.example.config
```

### Using exec-maven-plugin

You can configure the exec-maven-plugin in your `pom.xml` for easier execution:

```xml
<build>
  <plugins>
    <plugin>
      <groupId>org.codehaus.mojo</groupId>
      <artifactId>exec-maven-plugin</artifactId>
      <version>3.5.1</version>
      <configuration>
        <mainClass>
          com.github.wnameless.spring.boot.up.plugin.keycloak.bootstrap.KeycloakRealmBootstrap
        </mainClass>
        <systemProperties>
          <!-- Target directory for generated files -->
          <systemProperty>
            <key>targetDir</key>
            <value>${project.build.sourceDirectory}/../resources</value>
          </systemProperty>
          <!-- Optional: Package for generated security config -->
          <systemProperty>
            <key>configPackage</key>
            <value>com.example.security</value>
          </systemProperty>
          <!-- Optional: Custom realm name (default: webmvc) -->
          <systemProperty>
            <key>realmName</key>
            <value>my-realm</value>
          </systemProperty>
          <!-- Optional: Custom client ID (default: webmvc-app) -->
          <systemProperty>
            <key>clientId</key>
            <value>my-application</value>
          </systemProperty>
        </systemProperties>
      </configuration>
    </plugin>
  </plugins>
</build>
```

With this configuration, you can simply run:

```bash
# Execute the bootstrap with configured properties
mvn exec:java

# Or if you have multiple executions configured, specify the execution ID
mvn exec:java@bootstrap-keycloak
```

### Example with Multiple Execution Configurations

For different environments or configurations, you can define multiple executions:

```xml
<plugin>
  <groupId>org.codehaus.mojo</groupId>
  <artifactId>exec-maven-plugin</artifactId>
  <version>3.5.1</version>
  <executions>
    <!-- Development environment -->
    <execution>
      <id>bootstrap-dev</id>
      <configuration>
        <mainClass>
          com.github.wnameless.spring.boot.up.plugin.keycloak.bootstrap.KeycloakRealmBootstrap
        </mainClass>
        <systemProperties>
          <systemProperty>
            <key>targetDir</key>
            <value>src/main/resources</value>
          </systemProperty>
          <systemProperty>
            <key>realmName</key>
            <value>dev-realm</value>
          </systemProperty>
          <systemProperty>
            <key>clientId</key>
            <value>dev-app</value>
          </systemProperty>
        </systemProperties>
      </configuration>
    </execution>
    <!-- Production environment -->
    <execution>
      <id>bootstrap-prod</id>
      <configuration>
        <mainClass>
          com.github.wnameless.spring.boot.up.plugin.keycloak.bootstrap.KeycloakRealmBootstrap
        </mainClass>
        <systemProperties>
          <systemProperty>
            <key>targetDir</key>
            <value>src/main/resources/prod</value>
          </systemProperty>
          <systemProperty>
            <key>realmName</key>
            <value>production</value>
          </systemProperty>
          <systemProperty>
            <key>clientId</key>
            <value>prod-app</value>
          </systemProperty>
        </systemProperties>
      </configuration>
    </execution>
  </executions>
</plugin>
```

Then execute specific configurations:

```bash
# Generate development configuration
mvn exec:java@bootstrap-dev

# Generate production configuration
mvn exec:java@bootstrap-prod
```

### Generated Files

The bootstrap process will generate the following files in the target directory:

| File | Description |
|------|-------------|
| `keycloak-realm.json` | Complete Keycloak realm configuration with SAML client settings |
| `app_private_key.pem` | Application's RSA private key for SAML signing |
| `app_certificate.pem` | Application's X.509 certificate for SAML |
| `keycloak_certificate.pem` | Keycloak server's X.509 certificate |
| `KeycloakPluginSecurityConfig.java` | (Optional) Spring Security configuration class |

**Note**: The bootstrap process will skip individual files that already exist to prevent accidental overwrites. Each file is checked independently, so the process will continue generating other files even if some already exist. To regenerate specific files, delete them first and run the bootstrap again.

## Usage Example

### Protected Controller

Create a controller that requires authentication:

```java
@Controller
public class SecureController {
    
    @GetMapping("/")
    public String index(Model model, Authentication auth) {
        model.addAttribute("username", auth.getName());
        return "index";
    }
    
    @GetMapping("/profile")
    public String profile(Model model, Authentication auth) {
        model.addAttribute("user", auth.getPrincipal());
        return "profile";
    }
}
```

### Accessing Keycloak Admin Console

The embedded Keycloak admin console is available at:
```
http://localhost:8080/auth
```

Default credentials:
- Username: `admin`
- Password: `admin`

## SAML Endpoints

The plugin automatically configures the following SAML endpoints:

- **Metadata**: `/saml2/service-provider-metadata/{registrationId}`
- **SSO**: `/login/saml2/sso/{registrationId}`
- **SLO**: `/logout/saml2/slo`

## Development

### Building from Source

```bash
# Clone the repository
git clone https://github.com/wnameless/spring-boot-up-keycloak-plugin.git

# Build the project
mvn clean install

# Run tests
mvn test
```

### Running Test Applications

Two test applications are provided:

1. **Manual Configuration Test**:
```bash
mvn spring-boot:run -Dstart-class=com.github.wnameless.spring.boot.up.keycloakconfig.SpringKeycloakPluginConfigTestApp
```

2. **Annotation-based Configuration Test**:
```bash
mvn spring-boot:run -Dstart-class=com.github.wnameless.spring.boot.up.keycloakannotation.SpringKeycloakPluginAnnotationTestApp
```

## Troubleshooting

### Liquibase Compatibility Issues

If you encounter the following error after upgrading Spring Boot:
```
org.keycloak.services : 'boolean liquibase.lockservice.StandardLockService.isDatabaseChangeLogLockTableCreated()'
```

This is due to a version incompatibility between the Liquibase version used by Keycloak and the one provided by Spring Boot. To fix this issue, add an explicit Liquibase dependency with a compatible version (typically the newest version):

```xml
<dependency>
    <groupId>org.liquibase</groupId>
    <artifactId>liquibase-core</artifactId>
    <version>4.33.0</version>
</dependency>
<dependency>
    <groupId>com.github.wnameless.spring.boot.up</groupId>
    <artifactId>spring-boot-up-keycloak-plugin</artifactId>
    <version>3.24.0</version>
</dependency>
<dependency>
    <groupId>com.h2database</groupId>
    <artifactId>h2</artifactId>
</dependency>
```

The explicit Liquibase dependency ensures compatibility between Keycloak's embedded database migrations and Spring Boot's version management.

### Certificate Issues

If you encounter certificate-related errors:

1. Ensure all `.pem` files are in the classpath (typically `src/main/resources`)
2. Regenerate certificates using the bootstrap command
3. Check file permissions

### Port Conflicts

If port 8080 is already in use:

```properties
server.port=8081
keycloak.plugin.baseUrl=http://localhost:8081
```

### Realm Not Found

If Keycloak reports "realm not found":

1. Check that `keycloak-realm.json` exists in resources
2. Verify the realm name matches in configuration
3. Restart the application

## Architecture

The plugin integrates several components:

- **Spring Security SAML2** - Handles SAML authentication flow
- **Embedded Keycloak** - Provides identity provider functionality
- **Spring Boot Auto-configuration** - Simplifies setup and configuration
- **X.509 Certificate Management** - Manages signing and encryption certificates

## Contributing

Contributions are welcome! Please feel free to submit a Pull Request.

1. Fork the repository
2. Create your feature branch (`git checkout -b feature/AmazingFeature`)
3. Commit your changes (`git commit -m 'Add some AmazingFeature'`)
4. Push to the branch (`git push origin feature/AmazingFeature`)
5. Open a Pull Request

## License

This project is licensed under the Apache License 2.0 - see the [LICENSE](LICENSE) file for details.

## Author

**Wei-Ming Wu** - [wnameless](https://github.com/wnameless)

## Acknowledgments

- Spring Security team for SAML2 support
- Keycloak team for the identity provider
- Spring Boot team for the excellent framework

## Support

For issues, questions, or suggestions, please use the [GitHub Issues](https://github.com/wnameless/spring-boot-up-keycloak-plugin/issues) page.