package io.jona.smusic.sorted_music.config;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class SpaController {

    @GetMapping(value = "/{path:[^\\.]*}")
    public String forward() {
        return "forward:/index.html";
    }

    @GetMapping(value = "/{path:(?!api|assets|oauth2|login\\/oauth2)[^\\.]*}/**")
    public String forwardNested() {
        return "forward:/index.html";
    }
}
