# Splitify

Aplicación web que organiza tus playlists de Spotify por **idioma**, **género musical** y **artista**, usando IA (ChatGPT) para clasificar cada canción. Se integra con Spotify mediante OAuth, sincroniza tus playlists (incluyendo *Liked Songs*) y crea nuevas playlists ordenadas por fecha de lanzamiento.

## Funcionalidades

- **Login con Spotify** (OAuth 2.0) con perfil del usuario en el header.
- **Sincronización de playlists** desde Spotify (incluye *Liked Songs*) con clasificación automática de género e idioma vía ChatGPT (GPT-4.1-nano).
- **Crear playlists organizadas** por idioma, por género, por fecha de lanzamiento, o combinaciones de los tres en una sola operación.
- **Crear playlists personalizadas** combinando filtros de idioma + género + artista (filtros dinámicos: solo aparecen los valores presentes en las playlists origen).
- **Actualizar playlists de Splitify**: detecta canciones nuevas en las fuentes, conserva canciones agregadas manualmente desde Spotify, recuerda decisiones de exclusión y reordena por fecha de lanzamiento.
- **Gestión en lote** de playlists de Splitify (eliminar / actualizar varias a la vez).

Detalle completo en `REQUIREMENTS.adoc` y `ARCHITECTURE.adoc`.

https://github.com/user-attachments/assets/49249b4d-f1a3-4a17-987b-bc185c4b47ff

## Requisitos Previos

- **Java 25** — verificar con `java -version`.
- **Node.js 20+** — verificar con `node -v`.
- **npm** (incluido con Node.js) — verificar con `npm -v`.
- **Cuenta de Spotify Developer** con una app registrada (Client ID y Client Secret).
- **API Key de OpenAI** para la clasificación de género e idioma.

No hace falta instalar Gradle: el proyecto incluye `gradlew` (Gradle Wrapper).

## Variables de Entorno

Antes de levantar el backend hay que exportar:

| Variable | Descripción |
|---|---|
| `SPOTIFY_CLIENT_ID` | Client ID de la app en el Spotify Developer Dashboard |
| `SPOTIFY_CLIENT_SECRET` | Client Secret de la app de Spotify |
| `OPENAI_API_KEY` | API Key de OpenAI (clasificación con GPT-4.1-nano) |

En IntelliJ se configuran en la *Run Configuration* de `SortedMusicApplication`.

> **Nota OAuth:** la *Redirect URI* registrada en Spotify debe apuntar al backend (`http://127.0.0.1:8080/login/oauth2/code/spotify`), no al puerto de Vite.

## Stack Técnico

- **Backend:** Java 25 + Spring Boot 4.0.4, Spring Security (OAuth2), Spring Data JPA, SQLite (dev) / PostgreSQL (prod), Lombok, JUnit 5.
- **Frontend:** React 19, Vite 8, Tailwind CSS 4, React Router v7.
- **Build:** Gradle (Kotlin DSL) con plugin `node-gradle` que orquesta el build del frontend y produce un único JAR.
- **APIs externas:** Spotify Web API y OpenAI API (GPT-4.1-nano).

## Estructura del Proyecto

```
splitify/
├── frontend/                              ← React SPA (Vite + Tailwind)
│   ├── src/
│   │   ├── main.jsx
│   │   ├── App.jsx                        ← Rutas + AuthLayout
│   │   ├── pages/                         ← LoginPage, MainPage, PlaylistDetailPage
│   │   ├── components/                    ← Header, SyncOverlay, PlaylistCard,
│   │   │                                    SplitifyCard, OrganizeModal,
│   │   │                                    CustomOrganizeModal, ErrorModal
│   │   └── services/api.js
│   ├── vite.config.js                     ← Proxy /api → :8080, build → static/
│   └── package.json
├── src/main/java/io/jona/smusic/sorted_music/
│   ├── SortedMusicApplication.java
│   ├── config/SecurityConfig.java         ← OAuth2 + reglas de Spring Security
│   ├── controller/                        ← PlaylistController, UserController, SpaController
│   ├── service/                           ← SpotifyService, ClassificationService, SpotifyApiClient
│   ├── model/                             ← Playlist, Song, SongClassification, SplitifyPlaylistSource
│   ├── dto/                               ← SpotifyDto, PlaylistDto, SongDto, UserProfileDto,
│   │                                        CreatePlaylistsRequest, CreateCustomPlaylistRequest,
│   │                                        AvailableFiltersDto, RefreshPreviewDto
│   └── repository/                        ← *Repository (Spring Data JPA)
├── src/main/resources/
│   ├── application.properties
│   └── static/                            ← build de Vite (gitignored)
├── build.gradle.kts
├── REQUIREMENTS.adoc
├── ARCHITECTURE.adoc
└── CLAUDE.md
```

## Desarrollo Local

En desarrollo se trabaja con **dos procesos**: el backend de Spring Boot y el dev server de Vite.

### 1. Instalar dependencias del frontend (primera vez o cambios en `package.json`)

```bash
cd frontend
npm install
```

### 2. Iniciar el backend (terminal 1, raíz del proyecto)

```bash
./gradlew bootRun
```

Levanta Spring Boot en `http://localhost:8080`.

### 3. Iniciar el frontend (terminal 2)

```bash
cd frontend
npm run dev
```

Levanta Vite en `http://localhost:5173` con HMR.

### Qué URL abrir en el navegador

Para probar la app **abrí `http://127.0.0.1:8080`**, no `:5173`. El callback de OAuth de Spotify redirige al puerto del backend, así que iniciar el flujo desde Vite rompe el login.

Vite sigue siendo útil: el proxy hace que las llamadas `/api/**`, `/oauth2/**` y `/login/oauth2/**` desde `:5173` se reenvíen al backend, pero por OAuth conviene operar directo contra `:8080`.

## Build de Producción

Un único comando compila frontend + backend en un JAR:

```bash
./gradlew build
```

Internamente:
1. Gradle ejecuta `npm install` en `frontend/`.
2. Gradle ejecuta `npm run build`: Vite genera el bundle en `src/main/resources/static/`.
3. Gradle compila el Java.
4. Empaqueta todo en un único JAR.

Ejecutar el JAR:

```bash
java -jar build/libs/sorted-music-0.0.1-SNAPSHOT.jar
```

En producción Spring Boot sirve la API y el frontend estático desde el mismo origen.

## Tests

```bash
# Todos los tests
./gradlew test

# Un test específico
./gradlew test --tests "io.jona.smusic.sorted_music.SortedMusicApplicationTests"
```

## Notas sobre Spotify API

Spotify aplica restricciones en *Development Mode* (relevantes desde feb 2026):

- `GET /playlists/{id}/items` solo devuelve canciones de playlists que el usuario **creó o colabora**. Las playlists *seguidas* devuelven 403.
- Endpoints renombrados: `/tracks` → `/items`; `POST /users/{id}/playlists` → `POST /me/playlists`; `DELETE /playlists/{id}/followers` → `DELETE /me/library`.
- Máximo 5 usuarios autorizados en el Developer Dashboard.

Para acceso completo se requiere *Extended Quota Mode* (aprobación manual de Spotify).

## Despliegue

Despliegue objetivo: **Linode**. Se ejecuta el JAR autónomo con perfil `prod` y PostgreSQL como base de datos. Variables de entorno requeridas: las del cuadro de arriba más `DATABASE_URL`.

## Documentación

- **REQUIREMENTS.adoc** — qué hace la aplicación (requerimientos funcionales y reglas de negocio).
- **ARCHITECTURE.adoc** — cómo está diseñada (stack, capas, modelo de datos, integraciones).
