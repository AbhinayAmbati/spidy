# Spidey — an on-device Android agent

Spidey is a native Android app (Kotlin + Jetpack Compose) that acts as a personal AI
agent. You type a task in plain language — *"Open Chrome and search for Kubernetes
tutorials"* — and it plans the steps, drives the phone through Android's official
APIs, watches what happens on screen, and keeps going until the task is done or it
needs your approval.

It is not a chatbot in an app, and not an automation dashboard. The whole interface
answers one question: **"What do you want me to do?"**

Spidey runs **on the device**. Its default path uses `spidey.cact`, a small model fine-tuned
from Cactus Needle 3, to turn one sentence into one of five intents (open an app, send a
message, call a contact, set an alarm, set a timer). Every value the model produces is then
checked against the phone's real apps and contacts, and sends and calls wait for your
confirmation. No API key and no network are needed to run a task.

**New here? Read [docs/USAGE.md](docs/USAGE.md)** — setup, how the agent works, how to
phrase tasks, what each tool does, and what to do when a step fails. (Parts of it still
describe the older provider setup; see [Status](#status).)

---

## Status

Early and under active development. Builds are produced by GitHub Actions; nothing in this
repository has been compiled by hand on a developer machine, so treat each CI build as the
source of truth.

| Area | State |
| --- | --- |
| On-device intent model (`spidey.cact`, five intents) + JNI bridge to the vendor engine | Done |
| Model download on first launch (resume, SHA-256 check, metered-network check) | Done |
| Intent runtime: router -> guard -> confirm gate -> Android intent executor | Done |
| Name resolution against the phone's apps and contacts; spoken time parser | Done |
| Audit trail, data export and delete-all, managed configuration | Done |
| Whistle speech-to-text download and status in Settings | Done |
| Voice input: mic button, on-device Whistle transcription into the intent runtime; listening glow and animated blue character on the overlay | Built; **needs hand checks on a phone** |
| AccessibilityService agent loop (observe, act) and tool system | Done, used for screen-driven tasks |
| Cloud providers (OpenAI, Anthropic, Gemini, OpenRouter, custom) in code | Present; the provider screens were removed from the UI |
| Chat-style UI: Home, Task, History, Routines, Settings, Onboarding | Done |
| CI: build, unit tests, lint baseline, coverage, supply-chain and model-pin checks | Done |
| Signed release APK from a `v*` tag | Done ([docs/RELEASING.md](docs/RELEASING.md)) |
| Measured accuracy and latency of the model on a phone | **Pending** |
| Multi-step, screen-reading tasks | Iterating; the model does not see the screen |

### What the model can and cannot do

`spidey.cact` reads one sentence and returns calls to five tools. It does not see the
screen, and it is English only. It is a fast path for simple commands, not a general planner.

The reported figures (81.4% on a hard held-out set, 84.6% with name checks, 98.7% on a
hand-written set) reproduce arithmetically but come from the Python runtime on a server,
not from a phone, and the 98.7% set was also used to pick the model. Read
[docs/EVALUATION.md](docs/EVALUATION.md) before quoting any of them.

Known limits: one-sentence chains of several commands are weak, spoken durations ("half an
hour") are not trained, and message-body edges still cause wrong actions, which is why every
send and call needs confirmation.

---

## Architecture

```
Default path (one sentence, on device):

User request
     |
IntentRuntime  (core/intent)  ->  SpideyRouter  ->  spidey.cact (JNI)
     |
IntentGuard  (checks names against real apps and contacts)  ->  Confirm gate
     |
AndroidActionExecutor  (core/actions: Intents, PackageManager)

Screen-driven path (AccessibilityService):

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
│   ├── ai            AIProvider, AgentTool, factory, Json, provider/*, local/* (Spidey engine)
│   ├── intent        IntentRuntime, SpideyRouter, guard, app/contact resolvers, TimeParser
│   ├── actions       AndroidActionExecutor, ActionLauncher
│   ├── audit         audit events, sanitizer, export, managed config
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

## The AI layer

**On-device (default).** `core/ai/local` wraps the vendor inference engine (`libneedle.a`,
`needle.h`, arm64-v8a, Apache-2.0 upstream) behind a JNI bridge. `spidey.cact`
(63,437,076 bytes) is downloaded on first launch from Hugging Face, checked against a pinned
SHA-256, and memory-mapped. It was fine-tuned with no system prompt, so the input is the raw
sentence plus exactly five tools: `open_app(app)`, `send_message(contact, text)`,
`call_contact(contact)`, `set_alarm(time)`, `set_timer(minutes)`. Details, pins and engine
behaviour are in [docs/ON_DEVICE.md](docs/ON_DEVICE.md).

Fetch the engine before building with native inference (it is verified against pinned hashes
and not committed):

```bash
python tools/fetch_engine.py
```

Without it the app still builds and `SpideyNative.isAvailable` is false.

**Cloud providers (BYOK).** All providers implement one interface, so the runtime never
depends on a vendor:

```kotlin
interface AIProvider {
    suspend fun generateResponse(
        messages: List<AgentMessage>,
        tools: List<AgentToolSpec>,
    ): AgentResponse
}
```

OpenAI, Anthropic, Google Gemini, Sarvam AI, OpenRouter and custom OpenAI-compatible
endpoints are implemented, but the provider and model screens are no longer in the UI.

The model is given tools via native function/tool calling and must return a structured
action — never Kotlin, shell, or any executable payload.

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
  the fixed tool surface, and `IntentGuard` rejects names that are not on the phone and
  values that are not in your request.
- **Sensitive actions** (send, pay, delete, call, install, confirm, …) pause and require
  an explicit Allow from a confirmation sheet.
- Inference is on-device by default: your sentence does not leave the phone.
- An **audit trail** records what ran, with a sanitizer; Settings can export or delete all
  data. See [docs/SECURITY.md](docs/SECURITY.md).
- **API keys** (cloud providers only) are stored only in `EncryptedSharedPreferences` (Android Keystore), are
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
- **Internet** — to download the models once, and to reach a cloud provider if you use one.
- **Contacts** — requested at run time so a name in your sentence can be matched to a real
  contact.

The onboarding and settings screens explain each one and never trick the user.

---

## Build

```bash
# In Android Studio (Ladybug or newer), or:
./gradlew assembleDebug     # requires a JDK 17 and the Android SDK
./gradlew test              # unit tests
```

Run `python tools/fetch_engine.py` first if you want the native on-device engine.

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

1. Measure accuracy, latency and RAM of `spidey.cact` on real phones
   (`tools/spidey_eval/run_eval.py`).
2. Train the next model version from real failures the app logs, with consent.
3. On-device screenshots, only when a provider explicitly needs them.
4. Broaden automation coverage and add device tests across Chrome, Settings,
   Calculator, YouTube, Gmail and a messaging app.
5. Instrumented (Espresso / Compose UI) tests for the agent screens.

---

## Fonts

Two OFL-licensed families are bundled in `app/src/main/res/font/`:

- **Inter** — body and UI text, included as static 400/500/600 instances
  (the upstream variable font is instanced rather than used directly, so weights
  resolve exactly without experimental variation APIs).
- **Instrument Serif** — display and headline styles.

Full licence texts are in `licenses/`. Both fonts are SIL Open Font License 1.1,
which permits bundling and redistribution.

---

## Contributing and policies

- [CONTRIBUTING.md](CONTRIBUTING.md) - how to build, test and send changes
- [CODE_OF_CONDUCT.md](CODE_OF_CONDUCT.md) - how we work together
- [SECURITY.md](SECURITY.md) - reporting a vulnerability, and the security posture
- [CHANGELOG.md](CHANGELOG.md) - what changed
- [docs/ON_DEVICE.md](docs/ON_DEVICE.md), [docs/EVALUATION.md](docs/EVALUATION.md),
  [docs/RELEASING.md](docs/RELEASING.md), [docs/SECURITY.md](docs/SECURITY.md) - model,
  evaluation, release and privacy details
- [LICENSE](LICENSE), [NOTICE](NOTICE) and [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) -
  Apache-2.0 and the components Spidey uses

---

## Legal

The in-app Terms of Service lives in `ui/settings/TermsContent.kt` and is reachable
from Settings > Legal.

**It is a template, not legal advice.** Before publishing, replace the placeholders
(`ENTITY`, `JURISDICTION`, `CONTACT` at the top of that file) and have a qualified
lawyer in your jurisdiction review it. The liability, indemnity and governing-law
clauses in particular are not enforceable in the same way everywhere, and a
"zero liability" clause may be limited or void in some jurisdictions.

### Provider marks

The provider icons in `res/drawable/ic_provider_*.xml` are original line-art marks
drawn for this app. The vendors' actual logos are trademarks, and redistributing
them would require brand permission — swap them in only if you obtain it.
