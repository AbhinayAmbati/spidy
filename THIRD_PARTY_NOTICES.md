# Third-party notices

Spidey is licensed under Apache-2.0 (see [LICENSE](LICENSE) and [NOTICE](NOTICE)). It uses the
following third-party components. Check the linked sources for the authoritative licence text.

## On-device models and engine

| Component | Use | Source |
| --- | --- | --- |
| Needle 3 (`needle3.cact`) | Tool-calling model, downloaded on first launch | https://huggingface.co/Cactus-Compute/needle3 |
| Whistle (`whistle.cact`) | Speech-to-text model, downloaded on first launch | https://huggingface.co/Cactus-Compute/whistle |
| Needle engine (`libneedle.a`, `needle.h`) | Native inference engine linked into the app | https://github.com/cactus-compute/needle |

The Needle repository states that it is under the Apache-2.0 licence. The model weights are
not bundled in the APK. Their licence terms are on the model cards above and have not been
checked here; check them before redistributing the weights.

## Fonts

Inter is bundled under the SIL Open Font License 1.1; the licence text is in
[licenses/OFL-Inter.txt](licenses/OFL-Inter.txt). Instrument Serif
([licenses/OFL-InstrumentSerif.txt](licenses/OFL-InstrumentSerif.txt)) was removed from the UI,
so delete that licence file if the font is no longer in the project.

## Libraries

Declared in `app/build.gradle.kts`. Each is used under its own licence, which for these is
Apache-2.0.

- AndroidX (core, appcompat, lifecycle, activity, navigation, Room, DataStore, Hilt
  navigation, Security Crypto, splash screen) and Jetpack Compose (BOM 2024.06.00, Material 3)
- Dagger / Hilt 2.51.1
- OkHttp 4.12.0 and its logging interceptor
- kotlinx.serialization 1.6.3 and kotlinx.coroutines 1.8.1
