package com.codedoctor.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class HomeController {

    @GetMapping("/")
    public String workspace() {
        return "index";
    }

    @GetMapping("/history")
    public String history() {
        return "history";
    }
}
