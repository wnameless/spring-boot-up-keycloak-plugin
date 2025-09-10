package com.github.wnameless.spring.boot.up.keycloakannotation.web;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * Test controller for the annotation-based Keycloak configuration.
 * 
 * <p>This controller demonstrates authenticated access in an application
 * that uses the {@code @EnableKeycloakPlugin} annotation for automatic
 * SAML2 authentication configuration.
 * 
 * @author Wei-Ming Wu
 * @since 1.0.0
 */
@RequestMapping("/")
@Controller
public class MainController {

  /**
   * Displays the index page with the authenticated user's name.
   * 
   * @param model the Spring MVC model
   * @param auth the authentication object containing user details
   * @return the name of the view template to render
   */
  @GetMapping
  String index(Model model, Authentication auth) {
    model.addAttribute("username", auth.getName());
    return "index";
  }

}
