package com.github.wnameless.spring.boot.up.keycloakannotation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.boot.test.context.SpringBootTest;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * End-to-end SP-initiated SAML login flow against the embedded Keycloak IdP: a user is created
 * through the Keycloak admin REST API, then the full browser flow is replayed — protected page
 * redirect, SAML authn request, Keycloak login form submission, SAMLResponse post back to the
 * assertion consumer service, and finally the authenticated page.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@SpringBootTest(classes = SpringKeycloakPluginAnnotationTestApp.class,
    webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT,
    properties = {"server.port=18081", "keycloak.plugin.baseUrl=http://localhost:18081",
        "keycloak.connectionsJpa.url=jdbc:h2:mem:pluginSamlTest;DB_CLOSE_DELAY=-1"})
public class SamlLoginFlowTest {

  static final String BASE = "http://localhost:18081";

  HttpClient http = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build();
  ObjectMapper objectMapper = new ObjectMapper();
  Map<String, String> cookies = new LinkedHashMap<>();

  @Test
  @Order(1)
  public void samlLoginFlowSucceeds() throws Exception {
    createRealmUser();

    // 1. Protected page redirects into the SAML flow
    HttpResponse<String> res = get(BASE + "/");
    assertEquals(302, res.statusCode());
    String authenticateUrl = location(res);
    assertTrue(authenticateUrl.contains("/saml2/authenticate"),
        "Unexpected redirect target: " + authenticateUrl);

    // 2. SP builds the SAML authn request and redirects to the IdP
    res = get(absolute(authenticateUrl));
    assertEquals(302, res.statusCode());
    String idpUrl = location(res);
    assertTrue(idpUrl.contains("/auth/realms/webmvc/protocol/saml"));

    // 3. IdP renders the login form
    res = get(idpUrl);
    assertEquals(200, res.statusCode());
    String loginAction = unescapeHtml(firstMatch(res.body(),
        "<form[^>]*id=\"kc-form-login\"[^>]*action=\"([^\"]+)\""));
    assertNotNull(loginAction, "Login form action not found in login page");

    // 4. Submit credentials; IdP responds with the SAMLResponse auto-post page
    res = postForm(loginAction, Map.of("username", "tester", "password", "secret"));
    assertEquals(200, res.statusCode());
    String acsUrl = unescapeHtml(firstMatch(res.body(), "<form[^>]*action=\"([^\"]+)\""));
    String samlResponse = unescapeHtml(firstMatch(res.body(),
        "name=\"SAMLResponse\"[^>]*value=\"([^\"]+)\""));
    assertNotNull(acsUrl, "SAML post-binding form action not found");
    assertNotNull(samlResponse, "SAMLResponse not found in post-binding page");

    // 5. Post the SAMLResponse to the assertion consumer service
    res = postForm(acsUrl, Map.of("SAMLResponse", samlResponse));
    assertEquals(302, res.statusCode());

    // 6. The protected page is now accessible
    res = get(absolute(location(res)));
    assertEquals(200, res.statusCode());
    assertTrue(res.body().contains("TEST"));
  }

  private void createRealmUser() throws Exception {
    HttpResponse<String> tokenRes = postForm(BASE + "/auth/realms/master/protocol/openid-connect/token",
        Map.of("grant_type", "password", "client_id", "admin-cli", "username", "admin",
            "password", "admin"));
    assertEquals(200, tokenRes.statusCode(), "Admin token request failed: " + tokenRes.body());
    String accessToken = objectMapper.readTree(tokenRes.body()).get("access_token").asText();

    String user = """
        {"username":"tester","enabled":true,"firstName":"Test","lastName":"User",
         "email":"tester@example.com","emailVerified":true,
         "credentials":[{"type":"password","value":"secret","temporary":false}]}""";
    HttpResponse<String> createRes = http.send(HttpRequest.newBuilder()
        .uri(URI.create(BASE + "/auth/admin/realms/webmvc/users"))
        .header("Authorization", "Bearer " + accessToken)
        .header("Content-Type", "application/json")
        .POST(BodyPublishers.ofString(user)).build(), BodyHandlers.ofString());
    assertTrue(createRes.statusCode() == 201 || createRes.statusCode() == 409,
        "User creation failed: " + createRes.statusCode() + " " + createRes.body());
  }

  private HttpResponse<String> get(String url) throws Exception {
    HttpResponse<String> res = http.send(withCookies(HttpRequest.newBuilder(URI.create(url)))
        .GET().build(), BodyHandlers.ofString());
    storeCookies(res);
    return res;
  }

  private HttpResponse<String> postForm(String url, Map<String, String> form) throws Exception {
    String body = form.entrySet().stream()
        .map(e -> URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8) + "="
            + URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8))
        .collect(Collectors.joining("&"));
    HttpResponse<String> res = http.send(withCookies(HttpRequest.newBuilder(URI.create(url)))
        .header("Content-Type", "application/x-www-form-urlencoded")
        .POST(BodyPublishers.ofString(body)).build(), BodyHandlers.ofString());
    storeCookies(res);
    return res;
  }

  private HttpRequest.Builder withCookies(HttpRequest.Builder builder) {
    if (!cookies.isEmpty()) {
      builder.header("Cookie", cookies.entrySet().stream()
          .map(e -> e.getKey() + "=" + e.getValue()).collect(Collectors.joining("; ")));
    }
    return builder;
  }

  private void storeCookies(HttpResponse<String> res) {
    res.headers().allValues("Set-Cookie").forEach(c -> {
      String pair = c.split(";", 2)[0];
      int eq = pair.indexOf('=');
      if (eq > 0) cookies.put(pair.substring(0, eq).trim(), pair.substring(eq + 1).trim());
    });
  }

  private String location(HttpResponse<String> res) {
    return res.headers().firstValue("Location").orElseThrow();
  }

  private String absolute(String url) {
    return url.startsWith("http") ? url : BASE + url;
  }

  private static String firstMatch(String text, String regex) {
    Matcher m = Pattern.compile(regex, Pattern.DOTALL).matcher(text);
    return m.find() ? m.group(1) : null;
  }

  private static String unescapeHtml(String s) {
    if (s == null) return null;
    return s.replace("&amp;", "&").replace("&quot;", "\"").replace("&lt;", "<")
        .replace("&gt;", ">").replace("&#39;", "'").replace("&#43;", "+").replace("&#47;", "/")
        .replace("&#61;", "=");
  }

}
