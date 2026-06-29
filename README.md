# Pokédex (Android)

A native Android Pokédex app built with Kotlin and Jetpack Compose.

## Features

- **Pokémon list** — browse every Pokémon with sprites, dex numbers and types. Search by
  name or number and filter by type and generation.
- **Detail screen** with tabs:
  - **About** — height, weight, abilities, generation and Pokédex flavor text.
  - **Stats** — base stats plus a full **stat calculator** (level, IVs, EVs and nature →
    real stats, using the Gen III+ formulas).
  - **Evolutions** — the full evolution chain with conditions; tap a stage to jump to it.
  - **Weaknesses** — defensive type matchups (×4 / ×2 / ×½ / ×¼ / immune) computed from a
    built-in type chart.
  - **Moves** — learnable moves with type, category, power, accuracy and PP.
- **Offline first** — data is stored locally (Room). The app ships with a bundled dataset
  and works without internet. **Settings → Update data** re-downloads the latest data from
  [PokeAPI](https://pokeapi.co).

## Architecture

- **UI**: Jetpack Compose + Material 3, Navigation Compose, MVVM (`ViewModel` + `StateFlow`).
- **Data**: Room (single source of truth), Retrofit + OkHttp + kotlinx.serialization for
  the network sync, Coil for images, DataStore for preferences.
- **DI**: a lightweight manual `AppContainer` (`di/AppContainer.kt`).
- **Domain logic**: `TypeChart` (type effectiveness) and `StatCalculator` are pure Kotlin
  with unit tests under `app/src/test`.

## Data

The bundled dataset (`app/src/main/assets/pokedex.json`) is generated from PokeAPI by
`tools/generate-dataset.mjs`. It is produced in CI before the APK is built (PokeAPI is not
needed at build time otherwise). If the asset is absent, the app falls back to downloading
the data on first launch / via the Settings update button.

To regenerate it locally:

```bash
node tools/generate-dataset.mjs            # writes app/src/main/assets/pokedex.json
MAX_ID=151 node tools/generate-dataset.mjs # only the first generation (faster)
```

## Building the APK

The repository builds in **GitHub Actions** (`.github/workflows/android.yml`): it generates
the dataset, runs unit tests, builds the debug APK and uploads it as the
**`pokedex-debug-apk`** artifact. Download it from the workflow run and install on a device
(enable "install from unknown sources").

To build locally (requires the Android SDK):

```bash
./gradlew assembleDebug    # app/build/outputs/apk/debug/app-debug.apk
./gradlew testDebugUnitTest
```

## Credits

Pokémon data and sprites from [PokeAPI](https://pokeapi.co). This is an unofficial,
non-commercial fan project. Pokémon and Pokémon character names are trademarks of Nintendo.
