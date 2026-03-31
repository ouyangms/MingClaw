# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

MingClaw is an Android application built with Kotlin. This project includes the `claude-android-skill` directory which contains comprehensive Android development patterns and best practices based on Google's NowInAndroid reference app.

**Package**: `com.loy.mingclaw`
**Build System**: Gradle with Kotlin DSL
**Compile SDK**: 36
**Min SDK**: 32
**Target SDK**: 36

## Development Commands

### Build
```bash
# Build debug APK
./gradlew assembleDebug

# Build release APK
./gradlew assembleRelease

# Clean build
./gradlew clean
```

### Testing
```bash
# Run unit tests
./gradlew test

# Run instrumented tests (requires connected device/emulator)
./gradlew connectedAndroidTest

# Run specific test class
./gradlew test --tests ExampleUnitTest
```

### Installation
```bash
# Install debug build to connected device
./gradlew installDebug
```

## Architecture Guidelines

This project follows the Android development patterns documented in `claude-android-skill/`. When implementing features, refer to:

| Topic | Documentation |
|-------|---------------|
| Project structure & modules | `claude-android-skill/references/modularization.md` |
| Architecture layers (UI, Domain, Data) | `claude-android-skill/references/architecture.md` |
| Jetpack Compose patterns | `claude-android-skill/references/compose-patterns.md` |
| Build configuration | `claude-android-skill/references/gradle-setup.md` |
| Testing strategies | `claude-android-skill/references/testing.md` |

### Core Principles

1. **Offline-first**: Local database as source of truth, sync with remote
2. **Unidirectional data flow**: Events flow down, data flows up
3. **Reactive streams**: Use Kotlin Flow for data exposure
4. **Modular by feature**: Each feature self-contained with clear boundaries
5. **Testable by design**: Use test doubles instead of mocking libraries

### Recommended Module Structure

```
app/                    # Application module
feature/
  ├── featurename/
  │   ├── api/          # Public navigation contracts
  │   └── impl/         # Screen, ViewModel, DI (internal)
core/
  ├── data/             # Repositories
  ├── database/         # Room DAOs & entities
  ├── network/          # Retrofit & API models
  ├── model/            # Domain models (pure Kotlin)
  ├── ui/               # Reusable Compose components
  ├── designsystem/     # Theme & design tokens
  └── testing/          # Test utilities
```

## Key Patterns

### Feature Module Pattern

When creating a new feature, use this structure:

```
feature/myfeature/
├── api/
│   └── MyFeatureNavigation.kt    # Navigation keys/routes
└── impl/
    ├── MyFeatureScreen.kt        # Composable UI
    ├── MyFeatureViewModel.kt     # State holder
    ├── MyFeatureUiState.kt       # Sealed interface for states
    └── di/MyFeatureModule.kt     # Hilt DI module
```

### ViewModel Pattern

```kotlin
@HiltViewModel
class MyFeatureViewModel @Inject constructor(
    private val repository: MyRepository,
) : ViewModel() {
    val uiState: StateFlow<MyFeatureUiState> = repository
        .getData()
        .map { MyFeatureUiState.Success(it) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = MyFeatureUiState.Loading,
        )
}
```

### Repository Pattern

```kotlin
interface MyRepository {
    fun getData(): Flow<List<MyModel>>
}

internal class OfflineFirstMyRepository @Inject constructor(
    private val dao: MyDao,
    private val api: MyNetworkApi,
) : MyRepository {
    override fun getData(): Flow<List<MyModel>> =
        dao.getAll().map { it.toModel() }
}
```

## Android Development Skill

The `claude-android-skill/` directory is a portable skill for Claude Code that can be used across Android projects. It contains:

- **SKILL.md**: Main skill definition with quick reference
- **references/**: Detailed documentation on architecture, Compose, Gradle, modularization, and testing
- **assets/templates/**: Project templates for Gradle configuration
- **scripts/**: Feature module generator script

When working on this project, always consult the reference documentation in `claude-android-skill/references/` for implementation guidance.

## Version Catalog

Dependencies are managed via `gradle/libs.versions.toml`. When adding new dependencies:

1. Add version to `[versions]` section
2. Add library to `[libraries]` section
3. Reference using `libs.library.name` in build files

## Notes

- The project currently uses basic AndroidX dependencies (core-ktx, appcompat, material)
- For modern Android development with Compose, Hilt, and Room, refer to the patterns in `claude-android-skill/references/gradle-setup.md`
- Use the feature generator script at `claude-android-skill/scripts/generate_feature.py` for scaffolding new modules
