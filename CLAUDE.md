# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

MingClaw is an Android AI Agent application built with Kotlin, designed with a **plugin-based microkernel architecture** supporting self-evolution (behavior, knowledge, and capability). The project is currently in the design phase — the app module has no source code yet, with implementation to follow the detailed design documents in `docs/design/`.

- **Package**: `com.loy.mingclaw`
- **Compile SDK / Target SDK**: 36 | **Min SDK**: 32
- **Gradle**: 8.13 with Kotlin DSL | **AGP**: 8.13.2 | **Kotlin**: 2.0.21
- **Status**: Design complete (v2.0), implementation pending

## Development Commands

```bash
# Build
./gradlew assembleDebug          # Debug APK
./gradlew assembleRelease        # Release APK
./gradlew clean                  # Clean build

# Test
./gradlew test                              # All unit tests
./gradlew test --tests com.loy.mingclaw.ExampleUnitTest  # Single test class
./gradlew connectedAndroidTest              # Instrumented tests (device required)

# Install
./gradlew installDebug           # Install debug build to device
```

## Architecture

MingClaw uses a **four-layer plugin-based microkernel** architecture:

1. **Self-Evolution Layer** — Behavior/Knowledge/Capability evolvers with feedback collection; persists to AGENTS.md, MEMORY.md, SKILLS/, EXPERIENCE/
2. **Application Layer** — Chat UI, Task Monitor, Plugin Manager, Settings
3. **Core Layer** — Microkernel (Plugin Registry, Event Bus, Task Dispatcher, Config Manager) + Core Services (Context, Memory, Session, LLM, Security)
4. **Data Layer** — Room DB, DataStore, File System, SharedPreferences

Key architectural patterns:
- **Offline-first**: Local database as source of truth
- **Unidirectional data flow**: Events flow down, data flows up
- **Reactive streams**: Kotlin Flow for data exposure
- **Modular by feature**: Each feature self-contained with `api/` (public contracts) and `impl/` (internal) submodules
- **Testable by design**: Test doubles instead of mocking libraries

### Planned Module Structure

```
app/                        # Application shell
feature/
  └── featurename/
      ├── api/              # Navigation contracts (public)
      └── impl/             # Screen, ViewModel, DI (internal)
core/
  ├── data/                 # Repositories
  ├── database/             # Room DAOs & entities
  ├── network/              # Retrofit & API models
  ├── model/                # Domain models (pure Kotlin)
  ├── ui/                   # Reusable Compose components
  └── designsystem/         # Theme & design tokens
```

## Design Documentation

The `docs/design/` directory contains the complete system design (~550KB across 16 documents). When implementing features, consult these in order:

| Priority | Document | Content |
|----------|----------|---------|
| Read first | `modules/01-architecture.md` | Overall architecture, layer design, core components |
| Read first | `modules/10-tech-stack.md` | Core technologies (Compose, Hilt, Room, Coroutines) and versions |
| Read first | `modules/13-implementation-guide.md` | Dev environment, coding standards, deployment |
| Per feature | `modules/14-api-reference.md` | Complete public interface definitions in Kotlin |
| As needed | `modules/03-core-modules.md` | Microkernel, event bus, configuration |
| As needed | `modules/05-plugin-system.md` | Plugin interface, tool system, loading |
| As needed | `modules/02-evolution.md` | Three-path evolution system |
| As needed | `modules/04-context-management.md` | Session, memory retrieval, token window |
| As needed | `modules/06-memory-management.md` | Memory storage, vector embedding, hybrid search |
| As needed | `modules/07-task-orchestration.md` | Task execution, workflow, dependency management |

The consolidated design is in `docs/design/mingclaw-android-agent-design.md`.

## Android Development Reference

The `claude-android-skill/` directory is a portable Claude Code skill with NowInAndroid-based patterns:

| Topic | Reference |
|-------|-----------|
| Architecture layers (UI, Domain, Data) | `claude-android-skill/references/architecture.md` |
| Jetpack Compose patterns | `claude-android-skill/references/compose-patterns.md` |
| Gradle & convention plugins | `claude-android-skill/references/gradle-setup.md` |
| Multi-module structure | `claude-android-skill/references/modularization.md` |
| Testing strategies | `claude-android-skill/references/testing.md` |
| Feature module scaffolding | `claude-android-skill/scripts/generate_feature.py` |

## Dependency Management

Dependencies are managed via `gradle/libs.versions.toml`. To add a dependency:

1. Add version to `[versions]` section
2. Add library to `[libraries]` section
3. Reference using `libs.library.name` in build files

The design docs specify a full tech stack (Compose 1.7+, Hilt 2.51+, Room 2.6+, Retrofit 2.11+, Coroutines 1.7+) that needs to be added to the version catalog during implementation. See `modules/10-tech-stack.md` for the complete planned dependency list and `claude-android-skill/assets/templates/libs.versions.toml.template` for a pre-built catalog template.

## Key Code Patterns

### ViewModel (from design docs)
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

### Repository (offline-first)
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
