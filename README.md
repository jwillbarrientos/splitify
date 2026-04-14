# Splitify

Aplicación web que organiza tus playlists de Spotify por idioma, género musical y década de lanzamiento, usando IA para la clasificación.

https://github.com/user-attachments/assets/eb34b5c4-20a2-4d76-9ec8-9d1bd07cbc19

## Requisitos Previos

Antes de empezar, necesitas tener instalado:

- **Java 25** — el backend usa esta versión. Puedes verificar con `java -version`.
- **Node.js 20+** — el frontend lo necesita. Puedes verificar con `node -v`.
- **npm** — viene incluido con Node.js. Puedes verificar con `npm -v`.

No necesitas instalar Gradle manualmente. El proyecto incluye `gradlew` (Gradle Wrapper) que descarga la versión correcta automáticamente.

## Estructura del Proyecto

```
splitify/
├── frontend/                 ← Proyecto React (Vite + Tailwind CSS)
│   ├── src/
│   │   ├── main.jsx          ← Punto de entrada
│   │   ├── App.jsx           ← Router (define las rutas/páginas)
│   │   ├── pages/            ← Páginas de la aplicación
│   │   ├── components/       ← Componentes reutilizables
│   │   └── services/         ← Clientes HTTP para el backend
│   ├── vite.config.js        ← Configuración de Vite
│   ├── index.html            ← HTML base
│   └── package.json          ← Dependencias del frontend
├── src/main/java/            ← Código Java del backend (Spring Boot)
├── src/main/resources/
│   ├── application.properties
│   └── static/               ← Aquí Vite deposita el build (gitignored)
├── build.gradle.kts          ← Configuración de Gradle (backend + frontend)
├── REQUIREMENTS.adoc         ← Requerimientos del sistema
└── ARCHITECTURE.adoc         ← Decisiones de arquitectura
```

## Desarrollo Local

En desarrollo se trabaja con **dos procesos corriendo al mismo tiempo**: el backend de Spring Boot y el servidor de desarrollo de Vite. Cada uno corre en su propia terminal.

### 1. Instalar dependencias del frontend (solo la primera vez, o cuando cambie `package.json`)

```bash
cd frontend
npm install
```

### 2. Iniciar el backend

Desde la raíz del proyecto, en una terminal:

```bash
./gradlew bootRun
```

Esto levanta el servidor de Spring Boot en `http://localhost:8080`.

### 3. Iniciar el frontend

En otra terminal:

```bash
cd frontend
npm run dev
```

Esto levanta Vite en `http://localhost:5173` con Hot Module Replacement (los cambios en el código se reflejan al instante en el navegador sin recargar la página).

### ¿Por qué dos procesos?

- **Vite** (`localhost:5173`) sirve el frontend y refresca automáticamente cuando editas archivos `.jsx` o `.css`. Cualquier petición a `/api/**` la redirige automáticamente al backend en `:8080` (configurado en `vite.config.js`).
- **Spring Boot** (`localhost:8080`) sirve la API REST.

**Durante desarrollo, abre `http://localhost:5173`** (no `:8080`) en tu navegador.

## Build de Producción

Un solo comando compila todo (frontend + backend) y genera un JAR:

```bash
./gradlew build
```

Lo que ocurre internamente:
1. Gradle ejecuta `npm install` en `frontend/`
2. Gradle ejecuta `npm run build`, que hace que Vite compile el React y deposite los archivos en `src/main/resources/static/`
3. Gradle compila el código Java
4. Todo se empaqueta en un único archivo JAR

Para ejecutar el JAR:

```bash
java -jar build/libs/sorted-music-0.0.1-SNAPSHOT.jar
```

En producción, Spring Boot sirve tanto la API (`/api/**`) como el frontend estático desde el mismo servidor.

## Tests

```bash
# Ejecutar todos los tests
./gradlew test

# Ejecutar un test específico
./gradlew test --tests "io.jona.smusic.sorted_music.SortedMusicApplicationTests"
```

## Documentación

- **REQUIREMENTS.adoc** — qué hace la aplicación (requerimientos funcionales y no funcionales)
- **ARCHITECTURE.adoc** — cómo está diseñada (decisiones técnicas, stack, flujos de integración)
- **CLAUDE.md** — guía para Claude Code (contexto técnico para asistencia con IA)
