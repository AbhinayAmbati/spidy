package com.example.agent.core.ai.local

/** One downloadable model file, pinned to an exact size and checksum. */
data class ModelSpec(
    val id: String,
    val displayName: String,
    val fileName: String,
    val url: String,
    val sizeBytes: Long,
    val sha256: String,
)

/**
 * The models Spidey runs on, both Apache-2.0 from Cactus Compute on Hugging Face.
 *
 * The hashes are pinned so a changed upstream file fails the download instead of entering the
 * app. To update a model, read the file's LFS size and SHA-256 on its Hugging Face page and
 * change the pins together.
 */
object OnDeviceModels {

    val NEEDLE = ModelSpec(
        id = "needle3",
        displayName = "Needle 3",
        fileName = "needle3.cact",
        url = "https://huggingface.co/Cactus-Compute/needle3/resolve/main/needle3.cact",
        sizeBytes = 35_335_380L,
        sha256 = "c9d915eca282ed42d1a09b143b592adb4cc6744ffe2d294adf5cfc5548170c38",
    )

    val WHISTLE = ModelSpec(
        id = "whistle",
        displayName = "Whistle",
        fileName = "whistle.cact",
        url = "https://huggingface.co/Cactus-Compute/whistle/resolve/main/whistle.cact",
        sizeBytes = 16_919_407L,
        sha256 = "b6e02f048568ac5d01a2042556c658061e699acbc0aa2a1439f52f3d461dffeb",
    )
}
