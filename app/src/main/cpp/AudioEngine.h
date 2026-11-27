#pragma once

#include <oboe/Oboe.h>
#include <android/asset_manager.h>

#include <array>
#include <atomic>
#include <cstddef>
#include <cstdint>
#include <memory>
#include <mutex>

struct tsf;

namespace pianova {

enum class AudioEngineState : int32_t {
    Stopped = 0,
    Running = 1,
    Recovering = 2,
    RecoveryFailed = 3
};

class AudioEngine final : public oboe::AudioStreamDataCallback,
                          public oboe::AudioStreamErrorCallback {
public:
    AudioEngine() = default;
    ~AudioEngine() override;

    AudioEngine(const AudioEngine&) = delete;
    AudioEngine& operator=(const AudioEngine&) = delete;

    bool loadSoundFont(AAssetManager* assets, const char* assetPath);
    bool start();
    void stop();
    AudioEngineState state() const noexcept { return state_.load(std::memory_order_acquire); }
    int32_t outputDeviceId() const noexcept;

    bool noteOn(int note, float velocity) noexcept;
    bool noteOff(int note) noexcept;
    bool setSustain(bool enabled) noexcept;
    bool setVolume(float volume) noexcept;
    bool stopAllNotes() noexcept;

    oboe::DataCallbackResult onAudioReady(
        oboe::AudioStream* stream,
        void* audioData,
        int32_t numFrames) override;

    void onErrorAfterClose(oboe::AudioStream* stream,
                           oboe::Result error) override;

private:
    enum class CommandType : uint8_t {
        NoteOn,
        NoteOff,
        Sustain,
        Volume,
        StopAll
    };

    struct Command {
        CommandType type{};
        int32_t note{};
        float value{};
    };

    // Bounded MPMC queue. Producers never wait for the audio thread and the
    // callback never takes a mutex or allocates memory.
    template <size_t Capacity>
    class CommandQueue {
        static_assert((Capacity & (Capacity - 1)) == 0,
                      "Queue capacity must be a power of two");
        static_assert(std::atomic<size_t>::is_always_lock_free,
                      "Audio command queue requires lock-free size_t atomics");

        struct Slot {
            std::atomic<size_t> sequence{0};
            Command command{};
        };

    public:
        CommandQueue() {
            for (size_t i = 0; i < Capacity; ++i) {
                slots_[i].sequence.store(i, std::memory_order_relaxed);
            }
        }

        bool push(const Command& command) noexcept {
            size_t position = enqueuePosition_.load(std::memory_order_relaxed);
            Slot* slot = nullptr;
            for (;;) {
                slot = &slots_[position & (Capacity - 1)];
                const size_t sequence =
                    slot->sequence.load(std::memory_order_acquire);
                const intptr_t difference =
                    static_cast<intptr_t>(sequence) -
                    static_cast<intptr_t>(position);
                if (difference == 0) {
                    if (enqueuePosition_.compare_exchange_weak(
                            position, position + 1,
                            std::memory_order_relaxed)) {
                        break;
                    }
                } else if (difference < 0) {
                    return false;
                } else {
                    position =
                        enqueuePosition_.load(std::memory_order_relaxed);
                }
            }
            slot->command = command;
            slot->sequence.store(position + 1, std::memory_order_release);
            return true;
        }

        bool pop(Command& command) noexcept {
            size_t position = dequeuePosition_.load(std::memory_order_relaxed);
            Slot* slot = nullptr;
            for (;;) {
                slot = &slots_[position & (Capacity - 1)];
                const size_t sequence =
                    slot->sequence.load(std::memory_order_acquire);
                const intptr_t difference =
                    static_cast<intptr_t>(sequence) -
                    static_cast<intptr_t>(position + 1);
                if (difference == 0) {
                    if (dequeuePosition_.compare_exchange_weak(
                            position, position + 1,
                            std::memory_order_relaxed)) {
                        break;
                    }
                } else if (difference < 0) {
                    return false;
                } else {
                    position =
                        dequeuePosition_.load(std::memory_order_relaxed);
                }
            }
            command = slot->command;
            slot->sequence.store(position + Capacity,
                                 std::memory_order_release);
            return true;
        }

    private:
        alignas(64) std::array<Slot, Capacity> slots_{};
        alignas(64) std::atomic<size_t> enqueuePosition_{0};
        alignas(64) std::atomic<size_t> dequeuePosition_{0};
    };

    bool startLocked();
    void applyPendingCommands() noexcept;
    mutable std::mutex streamMutex_;
    std::atomic<bool> recoveryEnabled_{false};
    std::atomic<float> currentVolume_{1.0f};

    static constexpr int kMaxVoices = 64;
    CommandQueue<256> commands_;
    std::unique_ptr<tsf, void (*)(tsf*)> synth_{nullptr, nullptr};
    std::shared_ptr<oboe::AudioStream> stream_;
    std::atomic<bool> started_{false};
    std::atomic<AudioEngineState> state_{AudioEngineState::Stopped};
};

} // namespace pianova
