# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Splitify is a Spring Boot web application that categorizes and organizes Spotify playlists by language, genre, and release era/decade. 
It uses Spotify OAuth for login, fetches user playlists (including Liked Songs), and leverages the ChatGPT API for genre classification. 
The project name in Gradle is "sorted-music".

## Build & Run Commands

```bash
# Build
./gradlew build

# Run the application
./gradlew bootRun

# Run all tests
./gradlew test

# Run a single test class
./gradlew test --tests "io.jona.smusic.sorted_music.SortedMusicApplicationTests"

# Run a single test method
./gradlew test --tests "io.jona.smusic.sorted_music.SortedMusicApplicationTests.contextLoads"

# Clean build
./gradlew clean build
```

## Tech Stack

- **Java 25** with **Spring Boot 4.0.4** (Gradle Kotlin DSL)
- **Spring Web MVC** + **Thymeleaf** (server-side rendered UI)
- **Spring Security** (Spotify OAuth integration)
- **Spring Data JPA** (persistence)
- **Lombok** (annotation-based code generation)
- **JUnit 5** (testing, via `useJUnitPlatform()`)

## Architecture

- **Base package:** `io.jona.smusic.sorted_music`
- **Entry point:** `SortedMusicApplication.java`
- **Config:** `src/main/resources/application.properties`
- **Templates:** `src/main/resources/templates/` (Thymeleaf)
- **Static assets:** `src/main/resources/static/`

## Key Domain Concepts (from REQUIREMENTS.md)

- **Languages:** English, Spanish, Portuguese, Korean, Japanese, French, German, Italian
- **Genres (10):** Pop, Rock, Hip-Hop/Rap, Electrónica/Dance, R&B/Soul/Funk, Jazz/Blues, Clásica/Orquestal, Reggae/Ska/World, Country/Folk, Latina/Urbano
- **Eras:** by decade (80s, 90s, etc.)
- Filtering supports combinations of language + genre + era
- Song metadata: ID, name, artist, release year, genre (genre via ChatGPT API)

## Notes

- Requirements document (REQUIREMENTS.md) is written in Spanish.
- Deployment target is Linode (not yet configured).
