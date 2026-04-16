package io.jona.smusic.sorted_music.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class SpaController {

    // Rutas de un solo nivel sin punto (ej: "/login", "/playlist").
    // [^\.]* = cualquier caracter que NO sea punto, asi los archivos estaticos
    // como "app.js" o "style.css" no caen aqui y Spring los sirve normal.
    @GetMapping(value = "/{path:[^\\.]*}")
    public String forward() {
        return "forward:/index.html";
    }

    // Rutas anidadas (ej: "/playlist/123").
    // (?!api|assets|oauth2|login\/oauth2) = "negative lookahead": excluye
    // rutas que empiecen con esos prefijos para que NO se reenvien al SPA
    // (esas las manejan los controllers REST y el OAuth de Spring).
    // [^\.]* otra vez evita archivos con extension, y /** permite sub-rutas.
    @GetMapping(value = "/{path:(?!api|assets|oauth2|login\\/oauth2)[^\\.]*}/**")
    public String forwardNested() {
        return "forward:/index.html";
    }
}
