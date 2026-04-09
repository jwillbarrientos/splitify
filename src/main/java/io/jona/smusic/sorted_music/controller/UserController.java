package io.jona.smusic.sorted_music.controller;

import io.jona.smusic.sorted_music.dto.UserProfileDto;
import io.jona.smusic.sorted_music.service.SpotifyService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
public class UserController {

    private final SpotifyService spotifyService;

    @GetMapping("/me")
    public UserProfileDto getProfile(OAuth2AuthenticationToken authentication) {
        return spotifyService.getUserProfile(authentication);
    }
}
