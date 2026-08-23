package com.example.newsmaterial.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class SpaForwardingController {
    @GetMapping({
            "/", "/login", "/projects",
            "/projects/{id}", "/projects/{id}/new",
            "/materials/{id}", "/materials/{id}/edit",
            "/materials/{id}/versions/{number}"
    })
    String forwardKnownClientRoute() {
        return "forward:/index.html";
    }
}
