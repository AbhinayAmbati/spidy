# Spidey — an on-device Android agent

Spidey is a native Android app (Kotlin + Jetpack Compose) that acts as a personal AI
agent. You type a task in plain language — *"Open Chrome and search for Kubernetes
tutorials"* — and it plans the steps, drives the phone through Android's official
APIs, watches what happens on screen, and keeps going until the task is done or it
needs your approval.

It is not a chatbot in an app, and not an automation dashboard. The whole interface
answers one question: **"What do you want me to do?"**

---

## Status

This repository contains the **foundation and MVP-1 runtime**: a complete, coherent
architecture with real implementations for the AI layer, the accessibility layer, the
tool system, the agent loop, and the full UI shell (Home, live task, history,
settings, provider setup, onboarding).

| Area | State |
| --- | --- |
| Gradle / project structure | Done |
| Design system (black / white / brown, light + dark, spacing, type, shapes) | Done |
| Splash screen (black background, red cursive wordmark) | Done |
| Home, Task, History, Task detail, Settings, Provider, Memory, Onboarding screens + navigation | Done |
| AI provider abstraction (OpenAI-compatible, Anthropic, Gemini, OpenRouter, Custom) | Done |
| BYOK with encrypted credential storage | Done |
| AccessibilityService, controller, screen observer | Done |
| Tool system + registry + action schema + validator | Done |
| Agent runtime loop + task state machine + pause/resume + confirmation gate | Done |
| Bounded conversation context (older screen dumps trimmed) | Done |
| Room persistence (task history incl. per-step actions) + DataStore preferences | Done |
| Long-term memory (learned app shortcuts) + Memory screen | Done |
| Voice input (platform speech recognizer) | Done |
| Foreground service + notifications | Done |
| Reduced-motion support, headings, touch targets | Done |
| Unit tests (parsing, validator, registry, state machine, memory, history codec) | Done |
| Release signing config + GitHub Actions CI | Done |
| Real-device verification across Chrome, Settings, YouTube, Gmail, messaging | Pending — needs a device |
| Multi-step "compare prices" style planning quality | Iterating — depends on model |

> **Build note:** this code was authored and reviewed in an environment without the
> Android SDK or a JDK, so it has **not been compiled here**. It is written to compile
> against AGP 8.5.2 / Kotlin 1.9.24 / Compose BOM 2024.06.00 — open it in Android
> Studio and run `./gradlew assembleDebug`. Nothing is faked: where a feature is not
> built yet it is marked `TODO` in the code rather than stubbed to look finished.

---

## Architecture

```
User request
     |
Agent Runtime  (core/agent/AgentRuntime)
     |
Context Builder  (core/agent/PromptBuilder + automation/observer)
     |
AI Provider  (core/ai/AIProvider -> provider/*)
     |
Tool Decision  (structured AgentAction, never code)
     |
Action Validator  (core/agent/ActionValidator)
     |
Tool Executor  (automation/tools/*)
     |
Android  (Intents, PackageManager, AccessibilityService)
     |
UI Observer  (automation/observer/ScreenObserver)
     |
Updated state  ->  back to Agent Runtime
```

The loop is `OBSERVE -> PLAN -> SELECT TOOL -> VALIDATE -> EXECUTE -> OBSERVE`, bounded
by `TaskProgress.MAX_AGENT_STEPS` (30).

### Package map

```
com.example.agent
├── core
│   ├── ai            AIProvider, AgentTool, factory, Json, provider/*
│   ├── agent         AgentRuntime, ActionValidator, PromptBuilder
│   ├── security      SecureCredentialStore (EncryptedSharedPreferences)
│   ├── permissions   PermissionUtils
│   └── memory        AgentMemory
├── data
│   ├── local         Room: AgentDatabase, TaskDao, TaskEntity
│   ├── preferences   SettingsRepository (DataStore)
│   └── repository    TaskHistoryRepository
├── domain
│   └── model         UIElement, AgentAction, ToolResult, TaskState, ProviderConfig...
├── automation
│   ├── accessibility SpideyAccessibilityService, AccessibilityController, bridge
│   ├── observer      ScreenObserver
│   ├── tools         ToolSchemas, BaseTool, Tools, ToolRegistry
│   └── AgentForegroundService
├── di                Hilt modules
└── ui
    ├── theme         Color, Type, Shapes, Dimensions, Theme
    ├── components     GlassCard, AgentInput, AgentStatusCard, cards, ConfirmationSheet
    ├── home / task / history / settings / onboarding / navigation
    └── MainActivity
```

---

## The AI layer (BYOK)

All providers implement one interface, so the runtime never depends on a vendor:

```kotlin
interface AIProvider {
    suspend fun generateResponse(
        messages: List<AgentMessage>,
        tools: List<AgentToolSpec>,
    ): AgentResponse
}
```

Supported out of the box: **OpenAI**, **Anthropic**, **Google Gemini**, **OpenRouter**,
and any **custom OpenAI-compatible** endpoint. Adding a provider is one `when` branch in
`AIProviderFactory` plus, at most, one adapter class.

The model is given tools via native function/tool calling and must return a structured
`AgentAction` — never Kotlin, shell, or any executable payload.

---

## The tool system

Tools are strongly typed and registered in one place (`ToolRegistry`):

`open_app`, `close_app`, `read_screen`, `find_text`, `click`, `long_click`, `type_text`,
`scroll`, `swipe`, `press_back`, `press_home`, `wait`, `launch_url`, `get_notifications`,
`ask_user`, `finish`.

Every proposed action passes through `ActionValidator`, which checks the tool exists,
its arguments are valid, the step limit is not exceeded, and — via a sensitivity
heuristic plus per-tool risk — whether the user must confirm first.

---

## Safety and privacy

- The model is treated as **untrusted input**. It can only influence the device through
  the fixed tool surface.
- **Sensitive actions** (send, pay, delete, call, install, confirm, …) pause and require
  an explicit Allow from a confirmation sheet.
- **API keys** are stored only in `EncryptedSharedPreferences` (Android Keystore), are
  masked in the UI, are never logged, and are sent only to the chosen provider.
- The screen is summarised into a small list of labelled, interactive elements before
  anything is shared with a model — raw accessibility trees are never dumped.
- Task history and preferences are local-only. Backups of credentials and databases are
  explicitly excluded.
- Android restrictions are respected and explained rather than bypassed (for example,
  one app cannot force-stop another; Spidey says so and returns home instead).

---

## Permissions

- **Accessibility** — required. Lets the agent see and control supported interfaces.
- **Notifications** — shows progress while a task runs; optional notification access
  additionally enables the `get_notifications` tool.
- **Internet** — to reach the AI provider you configured.

The onboarding and settings screens explain each one and never trick the user.

---

## Build

```bash
# In Android Studio (Ladybug or newer), or:
./gradlew assembleDebug     # requires a JDK 17 and the Android SDK
./gradlew test              # unit tests
```

`minSdk 26`, `targetSdk 34`, `compileSdk 34`.

---

## Releasing (signed APK from CI)

`Release APK` builds a signed APK with `assembleRelease` on every `v*` tag push, and
on manual dispatch (artifact only). Signing material comes from repository secrets —
the keystore is never committed.

Create a keystore once, if you don't have one:

```bash
keytool -genkeypair -v \
  -keystore release.keystore \
  -alias spidey \
  -keyalg RSA -keysize 2048 -validity 10000
```

Then add four secrets under **Settings → Secrets and variables → Actions**:

| Secret | Value |
| --- | --- |
| `KEYSTORE_BASE64` | `base64 -w0 release.keystore` (on macOS: `base64 -i release.keystore`) |
| `KEYSTORE_PASSWORD` | the store password you chose |
| `KEY_ALIAS` | `spidey` |
| `KEY_PASSWORD` | the key password you chose |

Cut a release:

```bash
git tag v0.1.0
git push origin v0.1.0
```

The workflow runs the unit tests, builds, **verifies the APK signature** with
`apksigner` (so a debug-signed build can't slip through), uploads it as an artifact,
and attaches it to a GitHub Release for the tag.

For local release builds, copy `keystore.properties.example` to `keystore.properties`
and fill it in. That file is git-ignored. With no such file the release build falls
back to the debug key so the project still builds.

---

## Roadmap

1. On-device screenshots, only when a provider explicitly needs them.
2. Broaden automation coverage and add device tests across Chrome, Settings,
   Calculator, YouTube, Gmail and a messaging app.
3. Instrumented (Espresso / Compose UI) tests for the agent screens.
