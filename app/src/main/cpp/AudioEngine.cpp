#include "AudioEngine.h"

#define TSF_IMPLEMENTATION
#include "tsf.h"

#include <android/log.h>

#include <algorithm>
#include <cmath>
#include <climits>
#include <cstdint>
#include <vector>

namespace pianova {
namespace {
constexpr char kTag[] = "PianovaAudio";
constexpr float kInitialGainDb = -6.0f;
}

AudioEngine::~AudioEngine() {
    stop();
    if (synth_) {
        tsf_close(synth_.release());
    }
}

bool AudioEngine::loadSoundFont(AAssetManager* assets, const char* assetPath) {
    if (assets == nullptr || assetPath == nullptr) return false;

    AAsset* asset = AAssetManager_open(assets, assetPath, AASSET_MODE_BUFFER);
    if (asset == nullptr) {
        __android_log_print(ANDROID_LOG_ERROR, kTag,
                            "Unable to open SoundFont asset: %s", assetPath);
        return false;
    }

    const off64_t length = AAsset_getLength64(asset);
    if (length <= 0 || length > INT_MAX) {
        AAsset_close(asset);
        __android_log_print(ANDROID_LOG_ERROR, kTag,
                            "Invalid SoundFont size: %lld",
                            static_cast<long long>(length));
        return false;
    }

    std::vector<uint8_t> bytes(static_cast<size_t>(length));
    size_t offset = 0;
    while (offset < bytes.size()) {
        const int read = AAsset_read(
            asset, bytes.data() + offset, bytes.size() - offset);
        if (read <= 0) {
            AAsset_close(asset);
            __android_log_print(ANDROID_LOG_ERROR, kTag,
                                "Unable to read complete SoundFont asset");
            return false;
        }
        offset += static_cast<size_t>(read);
    }
    AAsset_close(asset);

    tsf* loaded = tsf_load_memory(bytes.data(), static_cast<int>(bytes.size()));
    if (loaded == nullptr) {
        __android_log_print(ANDROID_LOG_ERROR, kTag,
                            "TinySoundFont rejected the bundled SF2 file");
        return false;
    }

    if (!tsf_set_max_voices(loaded, kMaxVoices) ||
        !tsf_channel_set_presetindex(loaded, 0, 0)) {
        tsf_close(loaded);
        __android_log_print(ANDROID_LOG_ERROR, kTag,
                            "Unable to initialize TinySoundFont voices/preset");
        return false;
    }

    synth_ = std::unique_ptr<tsf, void (*)(tsf*)>(loaded, &tsf_close);
    return true;
}

bool AudioEngine::start() {
    std::lock_guard<std::mutex> lock(streamMutex_);
    return startLocked();
}

bool AudioEngine::startLocked() {
    if (!synth_) return false;
    if (started_.load(std::memory_order_acquire)) return true;

    oboe::AudioStreamBuilder builder;
    builder.setDirection(oboe::Direction::Output)
        ->setPerformanceMode(oboe::PerformanceMode::LowLatency)
        ->setSharingMode(oboe::SharingMode::Exclusive)
        ->setFormat(oboe::AudioFormat::Float)
        ->setChannelCount(oboe::ChannelCount::Stereo)
        ->setDataCallback(this)
        ->setErrorCallback(this);

    oboe::Result result = builder.openStream(stream_);
    if (result != oboe::Result::OK) {
        __android_log_print(ANDROID_LOG_WARN, kTag,
                            "Exclusive stream unavailable (%s), retrying shared",
                            oboe::convertToText(result));
        builder.setSharingMode(oboe::SharingMode::Shared);
        result = builder.openStream(stream_);
    }

    if (result != oboe::Result::OK || !stream_) {
        __android_log_print(ANDROID_LOG_ERROR, kTag,
                            "Unable to open Oboe output stream: %s",
                            oboe::convertToText(result));
        stream_.reset();
        return false;
    }

    tsf_set_output(synth_.get(), TSF_STEREO_INTERLEAVED,
                   stream_->getSampleRate(), kInitialGainDb);
    tsf_channel_set_volume(synth_.get(), 0,
                           currentVolume_.load(std::memory_order_relaxed));

    result = stream_->requestStart();
    if (result != oboe::Result::OK) {
        __android_log_print(ANDROID_LOG_ERROR, kTag,
                            "Unable to start Oboe stream: %s",
                            oboe::convertToText(result));
        stream_->close();
        stream_.reset();
        return false;
    }

    started_.store(true, std::memory_order_release);
    recoveryEnabled_.store(true, std::memory_order_release);
    __android_log_print(ANDROID_LOG_INFO, kTag,
                        "Audio started: %d Hz, %d channels, %s",
                        stream_->getSampleRate(), stream_->getChannelCount(),
                        stream_->getSharingMode() == oboe::SharingMode::Exclusive
                            ? "exclusive" : "shared");
    return true;
}

void AudioEngine::stop() {
    std::lock_guard<std::mutex> lock(streamMutex_);
    recoveryEnabled_.store(false, std::memory_order_release);
    if (stream_) {
        stream_->requestStop();
        stream_->close();
        stream_.reset();
    }
    started_.store(false, std::memory_order_release);
    if (synth_) {
        tsf_channel_set_sustain(synth_.get(), 0, 0);
        tsf_channel_sounds_off_all(synth_.get(), 0);
    }
    Command discarded;
    while (commands_.pop(discarded)) {}
}

bool AudioEngine::noteOn(int note, float velocity) noexcept {
    if (note < 0 || note > 127 || !std::isfinite(velocity)) return false;
    return commands_.push(
        {CommandType::NoteOn, note, std::clamp(velocity, 0.0f, 1.0f)});
}

bool AudioEngine::noteOff(int note) noexcept {
    if (note < 0 || note > 127) return false;
    return commands_.push({CommandType::NoteOff, note, 0.0f});
}

bool AudioEngine::setSustain(bool enabled) noexcept {
    return commands_.push(
        {CommandType::Sustain, 0, enabled ? 1.0f : 0.0f});
}

bool AudioEngine::setVolume(float volume) noexcept {
    if (!std::isfinite(volume)) return false;
    const float bounded = std::clamp(volume, 0.0f, 1.0f);
    currentVolume_.store(bounded, std::memory_order_relaxed);
    return commands_.push({CommandType::Volume, 0, bounded});
}

bool AudioEngine::stopAllNotes() noexcept {
    return commands_.push({CommandType::StopAll, 0, 0.0f});
}

void AudioEngine::applyPendingCommands() noexcept {
    Command command;
    while (commands_.pop(command)) {
        switch (command.type) {
            case CommandType::NoteOn:
                tsf_channel_note_on(synth_.get(), 0, command.note,
                                    command.value);
                break;
            case CommandType::NoteOff:
                tsf_channel_note_off(synth_.get(), 0, command.note);
                break;
            case CommandType::Sustain:
                tsf_channel_set_sustain(
                    synth_.get(), 0, command.value >= 0.5f ? 1 : 0);
                break;
            case CommandType::Volume:
                tsf_channel_set_volume(synth_.get(), 0, command.value);
                break;
            case CommandType::StopAll:
                tsf_channel_sounds_off_all(synth_.get(), 0);
                break;
        }
    }
}

oboe::DataCallbackResult AudioEngine::onAudioReady(
    oboe::AudioStream* /* stream */, void* audioData, int32_t numFrames) {
    auto* output = static_cast<float*>(audioData);
    if (synth_ == nullptr || output == nullptr || numFrames <= 0) {
        if (output != nullptr && numFrames > 0) {
            std::fill(output, output + (numFrames * 2), 0.0f);
        }
        return oboe::DataCallbackResult::Continue;
    }

    applyPendingCommands();
    tsf_render_float(synth_.get(), output, numFrames, 0);
    return oboe::DataCallbackResult::Continue;
}

void AudioEngine::onErrorAfterClose(oboe::AudioStream* /* stream */,
                                    oboe::Result error) {
    std::lock_guard<std::mutex> lock(streamMutex_);
    started_.store(false, std::memory_order_release);
    __android_log_print(ANDROID_LOG_ERROR, kTag,
                        "Oboe stream error after close: %s",
                        oboe::convertToText(error));

    // Oboe has already stopped and closed the disconnected stream. Reopen a
    // fresh stream on this error-callback thread only while playback is still
    // desired; an explicit lifecycle stop disables recovery before closing.
    stream_.reset();
    if (synth_) {
        tsf_channel_set_sustain(synth_.get(), 0, 0);
        tsf_channel_sounds_off_all(synth_.get(), 0);
    }
    Command discarded;
    while (commands_.pop(discarded)) {}

    if (recoveryEnabled_.load(std::memory_order_acquire)) {
        if (!startLocked()) {
            recoveryEnabled_.store(false, std::memory_order_release);
            __android_log_print(ANDROID_LOG_ERROR, kTag,
                                "Unable to recover Oboe stream after device change");
        }
    }
}

} // namespace pianova
