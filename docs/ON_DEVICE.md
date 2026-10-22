# On-device inference (Spidey model and Whistle)

Spidey runs on its own fine-tuned model, `spidey.cact` (a Needle-architecture model tuned to turn
one sentence into one of five intents). It is downloaded on first launch (Wi-Fi) or from Settings.
Whistle (speech to text) is Cactus Compute's model, used as shipped.

## What is here

| Piece | Where |
|---|---|
| JNI bridge over the vendor `needle.h` | `app/src/main/cpp/spidey_jni.cpp`, `CMakeLists.txt` |
| Kotlin binding | `core/ai/local/SpideyNative.kt`, `NativeSpideyEngine.kt` |
| Provider (`AIProvider`) | `core/ai/local/SpideyProvider.kt` |
| Prompt and response mapping | `SpideyPrompt.kt`, `SpideyResponseParser.kt` |
| Engine fetch script | `tools/fetch_engine.py` |
| Accuracy run on a phone | `tools/spidey_eval/run_eval.py` |

## Building with the engine

The inference engine (`libneedle.a`, `needle.h`, android-arm64 only) is vendor code from Cactus
Compute that runs `.cact` models. It keeps its upstream file and function names (`needle_*`). It is
not committed.

```sh
python tools/fetch_engine.py
```

Both files are verified against pinned SHA-256 hashes. Once `app/src/main/cpp/engine/libneedle.a`
exists, Gradle builds the native bridge `libspidey_engine.so` (arm64-v8a, `c++_shared`); without it
the app builds as before and `SpideyNative.isAvailable` is false. CI and the release workflow fetch
the engine, so they compile the C++.

## Pinned artefacts

| File | Size | SHA-256 |
|---|---|---|
| `abhinayambati/spidey` `spidey.cact` | 63,437,076 B | `5d94343f3cdf9e9009926d9fe6ba688d7c1eaa9857720243b10316dc1ecc54ff` |
| `Cactus-Compute/whistle` `whistle.cact` | 16,919,407 B | `b6e02f048568ac5d01a2042556c658061e699acbc0aa2a1439f52f3d461dffeb` |

The models are downloaded by the app on first launch, not bundled. The licence of `spidey.cact`
must be stated on its Hugging Face page; it is derived from Needle 3 (Apache-2.0).

## How the engine behaves (from `needle.h`)

- One process-global, **non-thread-safe** model per kind (text and speech). Calls are serialised.
- `needle_load` takes the whole `.cact` in memory and reads it in place, so the file is mmapped
  and the mapping is kept for the life of the process.
- `needle_init(system, tools_json, tool_index_path)` fails when the system prompt plus tools do
  not fit the context. More than five tools makes the engine retrieve tools per turn.
- `needle_complete` takes one text input and returns one JSON object. The provider resets the
  conversation before every turn and sends a self-contained input.
- Telemetry is on by default in the engine; the bridge turns it off in `JNI_OnLoad`.
