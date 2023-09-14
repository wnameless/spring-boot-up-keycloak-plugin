package com.github.wnameless.spring.boot.up.web;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@RequestMapping("/")
@Controller
public class MainController {

  @GetMapping
  String index(Model model, Authentication auth) {
    model.addAttribute("username", auth.getName());
    return "index";
  }

}
