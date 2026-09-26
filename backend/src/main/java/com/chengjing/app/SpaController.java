package com.chengjing.app;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/** React routes remain refreshable when the production jar serves the built frontend. */
@Controller
public class SpaController {
    @GetMapping({"/", "/modules/{moduleId}", "/settings/models", "/voice-lab", "/live-lab"})
    public String index() { return "forward:/index.html"; }
}
