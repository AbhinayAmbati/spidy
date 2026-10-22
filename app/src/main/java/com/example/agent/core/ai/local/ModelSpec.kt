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
 * The models Spidey runs on: its own fine-tuned intent model, and Cactus Compute's Whistle for
 * speech to text.
 *
 * The hashes are pinned so a changed file fails the download instead of entering the app. To
 * update a model, read the file's LFS size and SHA-256 on its Hugging Face page and change the
 * pins together.
 */
object OnDeviceModels {

    val SPIDEY = ModelSpec(
        id = "spidey",
        displayName = "Spidey",
        fileName = "spidey.cact",
        url = "https://huggingface.co/abhinayambati/spidey/resolve/main/spidey.cact",
        sizeBytes = 63_437_076L,
        sha256 = "5d94343f3cdf9e9009926d9fe6ba688d7c1eaa9857720243b10316dc1ecc54ff",
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
