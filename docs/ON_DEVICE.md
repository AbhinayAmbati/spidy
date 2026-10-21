# On-device inference (Cactus Needle 3 and Whistle)

Status: the engine bridge, the provider and its tests exist. It is **not wired into the app
yet**, because whether Needle 3 is accurate enough for phone control has to be measured on a
real device first (see "Feasibility spike").

## What is here

| Piece | Where |
|---|---|
| JNI bridge over `needle.h` | `app/src/main/cpp/needle_jni.cpp`, `CMakeLists.txt` |
| Kotlin binding | `core/ai/local/NeedleNative.kt`, `NativeNeedleEngine.kt` |
| Provider (`AIProvider`) | `core/ai/local/NeedleProvider.kt` |
| Prompt and response mapping | `NeedlePrompt.kt`, `NeedleResponseParser.kt` |
| Engine fetch script | `tools/fetch_needle_engine.py` |

## Building with the engine

The engine (`libneedle.a`, `needle.h`, android-arm64 only) is not committed.

```sh
python tools/fetch_needle_engine.py
```

Both files are verified against pinned SHA-256 hashes. Once `app/src/main/cpp/engine/libneedle.a`
exists, Gradle builds the native bridge (arm64-v8a, `c++_shared`); without it the app builds as
before and `NeedleNative.isAvailable` is false. CI does not fetch the engine, so it does not
compile the C++.

## Pinned artefacts (Hugging Face, Apache-2.0)

| File | Size | SHA-256 |
|---|---|---|
| `Cactus-Compute/needle3` `needle3.cact` | 35,335,380 B | `c9d915eca282ed42d1a09b143b592adb4cc6744ffe2d294adf5cfc5548170c38` |
| `Cactus-Compute/whistle` `whistle.cact` | 16,919,407 B | `b6e02f048568ac5d01a2042556c658061e699acbc0aa2a1439f52f3d461dffeb` |

The models are downloaded by the app on first launch, not bundled.

## How the engine behaves (from `needle.h`)

- One process-global, **non-thread-safe** model per kind (text and speech). Calls are serialised.
- `needle_load` takes the whole `.cact` in memory and reads it in place, so the file is mmapped
  and the mapping is kept for the life of the process.
- `needle_init(system, tools_json, tool_index_path)` fails when the system prompt plus tools do
  not fit the context. More than five tools makes the engine retrieve tools per turn.
- `needle_complete` takes one text input and returns one JSON object. The provider resets the
  conversation before every turn and sends a self-contained input.
- Telemetry is on by default in the engine; the bridge turns it off in `JNI_OnLoad`.

## Feasibility spike (needs a phone)

Needle 3 is built for single-turn requests such as "dim the living room to 30". Spidey asks for
more: choosing an element on a screen listing, several steps in a row. Before building more:

1. Fetch the engine, build a debug APK, and push `needle3.cact` to the app's files directory.
2. Replay 20-30 recorded Spidey tasks (task text + screen listings) through `NeedleProvider`.
3. Record per task: right first action, right whole sequence, latency, peak RAM, and whether
   `needle_init` accepts all 16 tools or needs fewer.
4. Decide: ship as the default, ship for simple tasks only (open an app, search, scroll), or
   keep a cloud provider as an option.

Things the spike must settle, because the docs do not: the exact `tools_json` shape the engine
accepts (the provider sends `[{name, description, parameters}]`), and the response field names
(the parser reads `function_calls`, `reasoning`, `confidence`).
