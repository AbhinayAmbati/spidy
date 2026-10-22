# Contributing to Spidey

Thanks for helping. Spidey holds an accessibility service, so changes here are reviewed for
safety as well as correctness. Please read [SECURITY.md](SECURITY.md) and
[docs/SECURITY.md](docs/SECURITY.md) before touching the agent loop, the tools or the
validator, and follow the [Code of Conduct](CODE_OF_CONDUCT.md).

## Building and testing

Spidey is built and tested by **GitHub Actions**. Most contributors do not have a local
Android toolchain set up, and the CI workflow is the reference build. A pull request is not
ready until the `Android CI` run is green.

The workflow (`.github/workflows/android.yml`) runs, in order:

1. `python3 tools/fetch_needle_engine.py` - downloads the pinned Needle engine into
   `app/src/main/cpp/engine/` (git-ignored; see [docs/ON_DEVICE.md](docs/ON_DEVICE.md)).
2. `python3 tools/check_imports.py app/src` and `python3 tools/check_symbols.py app/src` -
   fast checks for missing imports. Run these locally before pushing; they need only Python.
3. `./gradlew lintDebug` (report only), `./gradlew testDebugUnitTest`, `./gradlew assembleDebug`.

With a local setup (JDK 17 and the Android SDK, `minSdk 26`, `compileSdk 34`) the same Gradle
tasks work. The native part builds for `arm64-v8a` only.

To get an installable APK, run the **Release APK** workflow from the Actions tab and download
the `spidey-apk` artifact.

## Workflow

- Branch from `main`; keep a branch to one concern.
- Write commit messages in the conventional style used in `git log`:
  `fix(agent): ...`, `feat(ui): ...`, `refactor(agent): ...`, `ci: ...`, `docs: ...`.
  One logical change per commit; keep behaviour fixes separate from refactors.
- Add or update a unit test with every fix. The tests live in `app/src/test/java/com/example/agent/`;
  `AgentRuntimeTest` drives the agent loop end to end with fakes and is the place for loop bugs.
- Open a pull request against `main` and fill in the template.

## Where things live

```
app/src/main/java/com/example/agent/
  core/agent/        AgentRuntime, LoopGuard, ActionValidator, ContextCompactor
  core/ai/           AIProvider seam, AgentActionCodec, provider/*, local/* (Needle, downloader)
  automation/        accessibility service, screen observer, tools/*
  data/, domain/     persistence and models
  ui/                Compose screens and components
app/src/main/cpp/    JNI bridge to the Needle engine
```

## Adding a tool

1. Add the tool name to `ToolNames` in `domain/model/AgentAction.kt`.
2. Add its argument schema in `automation/tools/ToolSchemas.kt` and teach
   `core/ai/AgentActionCodec.kt` to decode its arguments.
3. Implement it in `automation/tools/Tools.kt` (extend `BaseTool`; `validate` rejects bad
   arguments, `execute` does the work and returns a `ToolResult`).
4. Register it in `provideRegistry` in `di/AppModule.kt`.
5. Decide how risky it is in `ActionValidator`; anything that sends, pays, deletes or calls must
   require confirmation.
6. Add tests. The on-device model sees at most five tools per turn
   (`NeedlePrompt.toolsFor`), so a new tool also needs a decision about when it is offered.

## Reporting problems

Use the issue templates. For a bug, include the diagnostics text from the app. Report
security problems privately, as described in [SECURITY.md](SECURITY.md).
