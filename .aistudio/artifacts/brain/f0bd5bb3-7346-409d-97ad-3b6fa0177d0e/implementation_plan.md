# TomTom Satellite Map & Route Planner

A single-screen Android application built with Jetpack Compose and the TomTom Navigation & Maps SDK for Android. The application renders a satellite imagery map centered on the user's location (with fallback to Amsterdam), accepts coordinate input or quick destination presets, and calculates and displays turn-by-turn route geometry with distance and estimated travel time.

## User Review & Critical Decisions

> [!IMPORTANT]
> The following architectural decisions have been aligned based on your specifications:
> - **API Key Configuration**: Injected securely via `BuildConfig.TOMTOM_API_KEY` through the Secrets Gradle plugin and `.env` / AI Studio Secrets panel.
> - **Location Source Strategy**: Defaulting initially to Amsterdam (`52.37616, 4.90828`) with real-time device GPS updates enabled when location permissions are granted.
> - **Destination Selection**: Support for both manual numeric latitude/longitude coordinate fields and one-tap quick preset destination chips (e.g., Amsterdam Airport Schiphol, Utrecht, Rotterdam, Dam Square) for instant testing in the emulator.

---

## 1. Overview & Core Concept

- **What It Does**: Provides a satellite-rendered mapping experience using TomTom's Map Display Compose and Routing SDK. Users see their real-time location (or default testing location) represented with a TomTom chevron marker. Users can input or tap target coordinates to plan a route, view the route polyline drawn over the satellite tiles, and review route metrics (kilometers, estimated time, and route summary).
- **Target Audience & Context**: Android developers, navigation testers, and end users wanting an aerial satellite view with route planning calculations.
- **Key Value**: Direct visualization of satellite imagery with real-time routing calculation without needing manual route tracing.

---

## 2. User Experience & Visual Design

### Key User Flows
1. **App Launch & Initial View**:
   - The app requests runtime location permissions (`ACCESS_FINE_LOCATION`, `ACCESS_COARSE_LOCATION`).
   - The map loads with TomTom satellite style (`StandardStyles.SATELLITE`), centered at the user's location or Amsterdam center (`52.37616, 4.90828`).
   - A `CurrentLocationMarker` indicates the origin coordinates.
   - The current coordinates are displayed in an elevated header badge.

2. **Destination Specification**:
   - The user can tap a quick-select chip (e.g., "Airport Schiphol", "Rotterdam Centraal", "Utrecht Dom Tower") to autofill coordinates, or enter custom numeric latitude and longitude in outlined text fields with input validation.

3. **Route Planning & Visualization**:
   - Tapping "Plan Route" initiates the routing calculation via `TomTomSdk.createRoutePlanner()`.
   - A loading indicator provides visual feedback while the network request executes.
   - Upon completion, the camera animates to frame the route, the polyline is rendered on the satellite map, and an informational summary card displays the travel distance in km and duration in minutes/hours.
   - If an error occurs (invalid coordinates or network failure), a descriptive Snackbar and inline alert notify the user.

### Visual Identity & Theme
- **Theme Vibe**: Aerospace / Navigation Dark Utility with emerald green route accents and sleek semi-transparent glass cards over high-contrast satellite photography.
- **Color Palette**:
  - Primary Accent: Emerald Mint (`#00E676` / `#00C853`) for high contrast against dark satellite maps.
  - Surface Background: Dark Slate / Charcoal glass (`#1E222B` with 92% opacity) with subtle borders (`#374151`).
  - Text & Accents: Pure White (`#FFFFFF`) for primary typography, Muted Cool Gray (`#9CA3AF`) for metadata and labels.
  - Warning/Error: Soft Coral Red (`#FF5252`) for routing errors and validation alerts.
- **Component Styling & Layout**:
  - Full-screen map foundation with edge-to-edge support.
  - Floating top status pill showing current GPS coordinates.
  - Floating bottom panel card hosting destination inputs, preset chips, action button, and routing result summary.
  - Minimum 48dp touch targets and ripple animations across all buttons and chips.

---

## 3. Key Product Decisions & Trade-Offs

- **Decision 1: Modular TomTom SDK Integration**
  - *Chosen Approach*: Configure the TomTom Artifactory Maven repository in `settings.gradle.kts` and include `com.tomtom.sdk.maps:map-display-compose-premium`, `com.tomtom.sdk:init`, `com.tomtom.sdk.routing:route-planner`, and `com.tomtom.sdk:location:provider-simulation`.
  - *Why*: Official TomTom Compose modules ensure native composition lifecycle integration, preventing texture tearing and gesture conflicts.
  - *Alternatives Considered*: Legacy Fragment-based `MapView` inside `AndroidView`, which lacks idiomatic declarative state management.

- **Decision 2: Dual Location Handling (Real GPS + Fallback)**
  - *Chosen Approach*: Device GPS location provider coupled with automatic fallback to Amsterdam test coordinates.
  - *Why*: In browser-based streaming emulators or when GPS lock takes time, the map instantly renders meaningful satellite tiles rather than an empty void or blocking dialog.

- **Decision 3: Secrets & API Key Management**
  - *Chosen Approach*: `.env` and `BuildConfig.TOMTOM_API_KEY` using Maps Platform Secrets Gradle Plugin.
  - *Why*: Prevents hardcoded credentials in source control while allowing immediate injection via AI Studio's Secrets panel.

---

## 4. Technical Architecture & Data Strategy

### System Architecture Diagram

```
┌───────────────────────────────────────────────────────────────┐
│                      MainActivity / App                       │
│  - TomTomSdk.init(context, TomTomSdkConfiguration(apiKey))   │
│  - Edge-to-edge Window Insets & Permissions Launcher          │
└───────────────────────────────┬───────────────────────────────┘
                                │
                                ▼
┌───────────────────────────────────────────────────────────────┐
│                        MapViewModel                           │
│  - uiState: StateFlow<MapUiState>                             │
│  - destinationLat / destinationLng: StateFlow<String>         │
│  - userLocation: StateFlow<GeoPoint?>                         │
│  - routePlanner: RoutePlanner (via TomTomSdk)                 │
│  - planRoute(origin: GeoPoint, destination: GeoPoint)         │
└───────────────┬───────────────────────────────▲───────────────┘
                │ StateFlow                     │ Intent Calls
                ▼                               │
┌───────────────────────────────────────────────────────────────┐
│                          MapScreen                            │
│  ┌─────────────────────────────────────────────────────────┐  │
│  │ TomTomMap (Composable)                                  │  │
│  │  - Style: StandardStyles.SATELLITE                      │  │
│  │  - CurrentLocationMarker (Origin indicator)            │  │
│  │  - addRoute() Polyline geometry overlay                 │  │
│  └─────────────────────────────────────────────────────────┘  │
│  ┌─────────────────────────────────────────────────────────┐  │
│  │ Top Location Status Badge (Current Lat / Lng)           │  │
│  └─────────────────────────────────────────────────────────┘  │
│  ┌─────────────────────────────────────────────────────────┐  │
│  │ Bottom Controls Card                                    │  │
│  │  - Destination Chips (Preset locations)                 │  │
│  │  - OutlinedTextField (Latitude & Longitude)             │  │
│  │  - "Plan Route" Action Button + Progress Indicator      │  │
│  │  - Route Metrics Banner (Distance km, Travel time)      │  │
│  └─────────────────────────────────────────────────────────┘  │
└───────────────────────────────────────────────────────────────┘
```

### State Model & Mapping
- `MapUiState`:
  - `currentLocation: GeoPoint?`
  - `destination: GeoPoint?`
  - `isLoadingRoute: Boolean`
  - `routeResult: RouteResultData?` (geometry points, distanceMeters, travelTimeSeconds, formattedSummary)
  - `errorMessage: String?`
- User interactions (selecting preset chips, typing coordinates, tapping "Plan Route") directly trigger ViewModel coroutines running asynchronous `routePlanner.planRoute` requests.
