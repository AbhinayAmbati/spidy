// JNI bridge to the vendor engine (engine/needle.h, linked from engine/libneedle.a; the model it runs is spidey.cact).
//
// The engine holds one process-global, non-thread-safe model per kind, so the Kotlin side
// serialises every call. Strings cross the boundary as UTF-8 byte arrays, never as jstring:
// GetStringUTFChars returns *modified* UTF-8, which encodes characters outside the BMP as
// surrogate pairs and would corrupt anything with an emoji in it.

#include <jni.h>

#include <fcntl.h>
#include <sys/mman.h>
#include <sys/stat.h>
#include <unistd.h>

#include <cstdlib>
#include <cstring>
#include <string>
#include <vector>

#include "needle.h"

namespace {

constexpr int kOutCapacity = 64 * 1024;

// A UTF-8 byte array copied into a NUL-terminated string. A null array stays null.
class Utf8Arg {
 public:
  Utf8Arg(JNIEnv* env, jbyteArray bytes) {
    if (bytes == nullptr) return;
    present_ = true;
    const jsize length = env->GetArrayLength(bytes);
    value_.resize(static_cast<size_t>(length));
    if (length > 0) {
      env->GetByteArrayRegion(bytes, 0, length, reinterpret_cast<jbyte*>(&value_[0]));
    }
  }

  const char* get() const { return present_ ? value_.c_str() : nullptr; }

 private:
  bool present_ = false;
  std::string value_;
};

jbyteArray toByteArray(JNIEnv* env, const char* text) {
  const jsize length = static_cast<jsize>(std::strlen(text));
  jbyteArray result = env->NewByteArray(length);
  if (result != nullptr && length > 0) {
    env->SetByteArrayRegion(result, 0, length, reinterpret_cast<const jbyte*>(text));
  }
  return result;
}

// The engine reads the weights in place, so the mapping must outlive the model.
struct Mapping {
  void* address;
  size_t length;
};
std::vector<Mapping> g_mappings;

}  // namespace

extern "C" {

JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM*, void*) {
  // Telemetry is on by default in the engine. This app is private by design.
  setenv("NEEDLE_TELEMETRY", "0", 1);
  setenv("DO_NOT_TRACK", "1", 1);
  return JNI_VERSION_1_6;
}

// Maps the .cact file and hands it to the engine. Returns the engine's result, or -1 when the
// file cannot be mapped (the reason is then not in needle_last_error).
JNIEXPORT jint JNICALL Java_com_example_agent_core_ai_local_SpideyNative_nativeLoad(
    JNIEnv* env, jclass, jbyteArray path) {
  Utf8Arg file(env, path);
  const int fd = open(file.get(), O_RDONLY);
  if (fd < 0) return -1;

  struct stat info;
  if (fstat(fd, &info) != 0 || info.st_size <= 0) {
    close(fd);
    return -1;
  }

  const size_t length = static_cast<size_t>(info.st_size);
  void* address = mmap(nullptr, length, PROT_READ, MAP_PRIVATE, fd, 0);
  close(fd);
  if (address == MAP_FAILED) return -1;

  const int result = needle_load(static_cast<const unsigned char*>(address),
                                 static_cast<unsigned long long>(length));
  if (result < 0) {
    munmap(address, length);
    return result;
  }

  g_mappings.push_back({address, length});
  return result;
}

JNIEXPORT jint JNICALL Java_com_example_agent_core_ai_local_SpideyNative_nativeModels(
    JNIEnv*, jclass) {
  return needle_models();
}

JNIEXPORT jbyteArray JNICALL Java_com_example_agent_core_ai_local_SpideyNative_nativeLastError(
    JNIEnv* env, jclass) {
  const char* error = needle_last_error();
  return toByteArray(env, error != nullptr ? error : "");
}

JNIEXPORT jint JNICALL Java_com_example_agent_core_ai_local_SpideyNative_nativeInit(
    JNIEnv* env, jclass, jbyteArray systemPrompt, jbyteArray toolsJson, jbyteArray toolIndexPath) {
  Utf8Arg system(env, systemPrompt);
  Utf8Arg tools(env, toolsJson);
  Utf8Arg index(env, toolIndexPath);
  return needle_init(system.get(), tools.get(), index.get());
}

// Returns the engine's JSON answer, or null on failure (see nativeLastError).
JNIEXPORT jbyteArray JNICALL Java_com_example_agent_core_ai_local_SpideyNative_nativeComplete(
    JNIEnv* env, jclass, jbyteArray input, jint maxNewTokens) {
  Utf8Arg text(env, input);
  std::vector<char> out(kOutCapacity, 0);
  const int result = needle_complete(text.get(), nullptr, 0, maxNewTokens, out.data(), kOutCapacity);
  if (result < 0) return nullptr;
  out[kOutCapacity - 1] = '\0';
  return toByteArray(env, out.data());
}

JNIEXPORT void JNICALL Java_com_example_agent_core_ai_local_SpideyNative_nativeReset(JNIEnv*, jclass) {
  needle_reset();
}

// Transcribes 16 kHz mono float PCM. Returns the engine's JSON, or null on failure.
JNIEXPORT jbyteArray JNICALL Java_com_example_agent_core_ai_local_SpideyNative_nativeTranscribe(
    JNIEnv* env, jclass, jfloatArray pcm, jbyteArray language, jbyteArray keywords) {
  const jsize samples = env->GetArrayLength(pcm);
  std::vector<float> audio(static_cast<size_t>(samples));
  if (samples > 0) env->GetFloatArrayRegion(pcm, 0, samples, audio.data());

  Utf8Arg lang(env, language);
  Utf8Arg words(env, keywords);
  std::vector<char> out(kOutCapacity, 0);
  const int result = needle_transcribe(audio.data(), static_cast<int>(samples), lang.get(),
                                       words.get(), 0, out.data(), kOutCapacity);
  if (result < 0) return nullptr;
  out[kOutCapacity - 1] = '\0';
  return toByteArray(env, out.data());
}

}  // extern "C"
