package org.hvtra.identityservice.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController()
@RequestMapping("/api/identity")

public class AuthController {
    @GetMapping("/public/test")
    public String test() {
        return "test";
    }
}
