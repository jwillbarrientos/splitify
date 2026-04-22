# Splitify

Aplicación web que organiza tus playlists de Spotify por **idioma**, **género musical** y **artista**, usando IA (ChatGPT, GPT-4.1-mini) para clasificar cada canción. Para mejorar la precisión, el prompt a ChatGPT incluye los *tags específicos de cada canción que Last.fm tiene curados por la comunidad* (ej: "reggaeton, spanish, puerto rican"; "ballad, acoustic, english"; "film score, soundtrack, instrumental") — tags a nivel track con señales directas de idioma y género. Si Last.fm no tiene tags para la canción, se usa fallback a los tags del artista. Se integra con Spotify mediante OAuth, sincroniza tus playlists (incluyendo *Liked Songs*) y crea nuevas playlists ordenadas por fecha de lanzamiento.

## Funcionalidades

- **Login con Spotify** (OAuth 2.0) con perfil del usuario en el header.
- **Sincronización de playlists** desde Spotify (incluye *Liked Songs*) con clasificación automática de género e idioma vía ChatGPT (GPT-4.1-mini). Durante la sincronización, el sistema consulta `track.gettoptags` de Last.fm por cada canción para obtener tags específicos (con señales de idioma, género, mood) y los pasa a ChatGPT como contexto. Fallback automático a `artist.gettoptags` para canciones oscuras que Last.fm no cubre. Reduce drásticamente los géneros inválidos y clasificaciones erróneas. La resincronización también refleja en la app cualquier cambio de nombre o foto que se haya hecho directamente en Spotify, incluso sobre las playlists creadas por Splitify.
- **Crear playlists organizadas** por idioma, por género, por fecha de lanzamiento, o combinaciones de los tres en una sola operación. Antes de crear, se muestra un modal con los nombres por defecto editables. Al organizar por idioma, los idiomas con menos de 5 canciones se agrupan en una única `Splitify Other Languages Songs` en vez de generar playlists testimoniales por idioma.
- **Crear playlists personalizadas** combinando filtros de idioma + género + artista (filtros dinámicos: solo aparecen los valores presentes en las playlists origen). Los filtros se persisten y se re-aplican al actualizar.
- **Actualizar playlists de Splitify**: detecta canciones nuevas en las fuentes, conserva canciones agregadas manualmente desde Spotify y reordena por fecha de lanzamiento. Dos diálogos simétricos de confirmación con checkboxes individuales: uno para *restaurar* canciones que quitaste a mano en Spotify pero siguen en las fuentes, y otro para *quitar del hijo* canciones cuya fuente desapareció (playlist origen vaciada/borrada o que quitó la canción). Si eliges conservar canciones sin fuente, pasan a tratarse como manualmente agregadas y no se vuelve a preguntar por ellas.
- **Editar metadatos de playlists Splitify** desde la home: renombrar inline (ícono de lápiz siempre visible) y cambiar foto (overlay al pasar el cursor sobre la imagen). Los cambios se propagan a Spotify al instante.
- **Gestión en lote** de playlists de Splitify (eliminar / actualizar varias a la vez).

> **Nota sobre la clasificación automática.** La clasificación de género e idioma usa ChatGPT (un LLM no determinista) sobre tags de Last.fm curados por la comunidad. **No es siempre exacta**: ocasionalmente puede marcar un género o idioma incorrecto, especialmente con artistas oscuros o colaboraciones con nombres ambiguos. Podés corregir una canción editando manualmente la playlist en Spotify — la edición se respeta en futuros refreshes. Detalle en `REQUIREMENTS.adoc` sección 3.4.

Detalle completo en `REQUIREMENTS.adoc` y `ARCHITECTURE.adoc`.

https://github.com/user-attachments/assets/74ce2c59-ac1c-4d40-a940-ec9f0c735f47

## Requisitos Previos

- **Java 25** — verificar con `java -version`.
- **Node.js 20+** — verificar con `node -v`.
- **npm** (incluido con Node.js) — verificar con `npm -v`.
- **Cuenta de Spotify Developer** con una app registrada (Client ID y Client Secret).
- **API Key de OpenAI** para la clasificación de género e idioma.
- **API Key de Last.fm** (gratis, opcional pero muy recomendada): registrarse en `https://www.last.fm/api/account/create`. Sin ella la clasificación sigue funcionando pero ChatGPT pierde el hint de tags por artista.

No hace falta instalar Gradle: el proyecto incluye `gradlew` (Gradle Wrapper).

## Variables de Entorno

Antes de levantar el backend hay que exportar:

| Variable | Descripción |
|---|---|
| `SPOTIFY_CLIENT_ID` | Client ID de la app en el Spotify Developer Dashboard |
| `SPOTIFY_CLIENT_SECRET` | Client Secret de la app de Spotify |
| `OPENAI_API_KEY` | API Key de OpenAI (clasificación con GPT-4.1-mini) |
| `LASTFM_API_KEY` | API Key de Last.fm (tags de género por artista como contexto para ChatGPT). Gratis en https://www.last.fm/api/account/create. Opcional: si falta, la clasificación sigue sin el hint. |

En IntelliJ se configuran en la *Run Configuration* de `SortedMusicApplication`.

> **Nota OAuth:** en el Spotify Developer Dashboard hay que tener registradas **dos** Redirect URIs: la de dev (`http://127.0.0.1:8080/login/oauth2/code/spotify`) y la de prod (`https://splitifyapp.app/login/oauth2/code/spotify`). No apuntar al puerto de Vite.

## Stack Técnico

- **Backend:** Java 25 + Spring Boot 4.0.4, Spring Security (OAuth2), Spring Data JPA, SQLite (dev y prod, con WAL mode en prod), Lombok, JUnit 5.
- **Frontend:** React 19, Vite 8, Tailwind CSS 4, React Router v7.
- **Build:** Gradle (Kotlin DSL) con plugin `node-gradle` que orquesta el build del frontend y produce un único JAR.
- **APIs externas:** Spotify Web API, OpenAI API (GPT-4.1-mini), Last.fm API (tags de género por artista).

## Estructura del Proyecto

```
splitify/
├── .github/workflows/deploy.yml           ← CI/CD: auto-deploy a prod en push a master
├── frontend/                              ← React SPA (Vite + Tailwind)
│   ├── src/
│   │   ├── main.jsx
│   │   ├── App.jsx                        ← Rutas + AuthLayout
│   │   ├── pages/                         ← LoginPage, MainPage, PlaylistDetailPage
│   │   ├── components/                    ← Header, SyncOverlay, PlaylistCard,
│   │   │                                    SplitifyCard (rename + cambiar foto),
│   │   │                                    OrganizeModal, ConfirmPlaylistsModal (nombres editables),
│   │   │                                    CustomOrganizeModal, RefreshConfirmModal,
│   │   │                                    ErrorModal
│   │   └── services/api.js
│   ├── vite.config.js                     ← Proxy /api → :8080, build → static/
│   └── package.json
├── src/main/java/io/jona/smusic/sorted_music/
│   ├── SortedMusicApplication.java
│   ├── config/SecurityConfig.java         ← OAuth2 + reglas de Spring Security
│   ├── controller/                        ← PlaylistController, UserController, SpaController
│   ├── service/                           ← SpotifyService, ClassificationService, SpotifyApiClient,
│   │                                        LastFmClient (cliente de tags de género)
│   ├── model/                             ← Playlist, Song, SongClassification,
│   │                                        TrackTags (cache tags Last.fm por canción),
│   │                                        ArtistGenres (cache tags Last.fm por artista, fallback),
│   │                                        SplitifyPlaylistSource
│   ├── dto/                               ← SpotifyDto, PlaylistDto, SongDto, UserProfileDto,
│   │                                        CreatePlaylistsRequest, CreatePlaylistsFromSpecsRequest,
│   │                                        PlaylistCreateSpec, CreateCustomPlaylistRequest,
│   │                                        AvailableFiltersDto, RefreshPreviewDto, RefreshRequest,
│   │                                        BatchRefreshRequest, BatchRefreshPreviewDto
│   └── repository/                        ← *Repository (Spring Data JPA)
├── src/main/resources/
│   ├── application.properties             ← Config base (dev)
│   ├── application-prod.properties        ← Overrides de producción (SQLite WAL, HTTPS redirect URI)
│   └── static/                            ← build de Vite (gitignored)
├── deploy.sh                              ← Deploy manual al server (alternativa al CI)
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

La app corre en una **Linode Nanode 1GB (Ubuntu 24.04)** en `https://splitifyapp.app`, detrás de **Caddy** (reverse proxy con HTTPS automático via Let's Encrypt). Spring Boot corre como servicio `systemd` (`splitify.service`) bajo el usuario no-root `splitify`. La base SQLite vive en `/opt/splitify/splitify.db` con WAL mode.

Topología resumida:

```
Cloudflare DNS (proxy off) → Linode Cloud Firewall (22/80/443) →
Caddy (HTTPS, Let's Encrypt) → Spring Boot en localhost:8080 (systemd) → SQLite WAL
```

**Secretos en el server:** `/etc/splitify.env` contiene `SPOTIFY_CLIENT_ID`, `SPOTIFY_CLIENT_SECRET`, `OPENAI_API_KEY`, `LASTFM_API_KEY` y `SPRING_PROFILES_ACTIVE=prod` (permisos `640`). Son inyectados por `systemd` como variables de entorno al arrancar el servicio.

### Deploy automático (CI/CD)

Cada `git push` a `master` dispara el workflow `.github/workflows/deploy.yml` que:

1. Compila el JAR en un runner de GitHub Actions (JDK 25 Temurin + Node 20).
2. Sube el JAR por SCP a `/opt/splitify/splitify.jar`.
3. Reinicia el servicio con `sudo systemctl restart splitify`.
4. Health-check con `systemctl is-active splitify`.

Requiere tres secretos en el repo (Settings → Secrets and variables → Actions):

| Secreto | Valor |
|---|---|
| `SSH_PRIVATE_KEY` | Llave privada SSH **dedicada para CI** (distinta de la personal, generada sin passphrase) |
| `SSH_HOST` | IP o dominio del server |
| `SSH_USER` | `splitify` |

La llave pública correspondiente tiene que estar en `/home/splitify/.ssh/authorized_keys` del server.

### Deploy manual (fallback)

Existe `deploy.sh` en la raíz del repo para deploys puntuales (emergencias, probar un hotfix no commiteado, CI caído):

```bash
./deploy.sh
```

Hace lo mismo que el workflow pero desde tu máquina, usando tu llave SSH personal.

Detalle completo en `ARCHITECTURE.adoc` (sección 11).

## Documentación

- **REQUIREMENTS.adoc** — qué hace la aplicación (requerimientos funcionales y reglas de negocio).
- **ARCHITECTURE.adoc** — cómo está diseñada (stack, capas, modelo de datos, integraciones).
