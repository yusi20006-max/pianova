# Bluetooth MIDI and Audio Verification

## Implemented paths

- BLE MIDI input uses Android's Bluetooth LE scanner filtered to the standard MIDI service UUID and opens the discovered device with `MidiManager.openBluetoothDevice`.
- BLE MIDI events are normalized through `PianoEvent` and merged with touchscreen and USB MIDI input through `MergedPianoInput`.
- BLE MIDI device selection is independent from USB MIDI selection. Device identity is stored separately.
- Disconnect handling closes ports/device, releases notes held by the BLE connection, and clears its sustain contribution. A selected device can reconnect when it is rediscovered during a scan.
- Pianova's Oboe output reports the active Android output device. The Settings page displays the route and opens Android Bluetooth settings so the user can select the piano's Bluetooth audio output. Audio routing is system-managed and independent from BLE MIDI.

## Kinix PJ-88D physical test checklist

The following checks require the actual piano and an Android device. CI cannot prove hardware/profile compatibility.

### BLE MIDI
- [ ] Grant Bluetooth scan/connect permission when prompted.
- [ ] Put the Kinix PJ-88D in BLE MIDI discovery mode and scan in Pianova.
- [ ] Select the piano and verify the status changes to connected.
- [ ] Play keys and confirm lesson/practice input receives correct NoteOn/NoteOff and Sustain.
- [ ] Hold one or more notes, disconnect Bluetooth, and confirm Pianova releases them.
- [ ] Reconnect and verify new events work, with no stale note or sustain state.
- [ ] Verify touchscreen and USB MIDI still work while Bluetooth MIDI is selected/connected.

### Bluetooth Audio
- [ ] Pair/connect the piano's audio profile separately in Android Bluetooth settings (the historical device label was "Music-Piano").
- [ ] In Pianova Settings, open Bluetooth settings and select the piano as media output if Android presents the route selector.
- [ ] Start Pianova's piano audio and confirm sound is heard from the piano speakers.
- [ ] Confirm Settings reports Bluetooth Audio as the active output route.
- [ ] Disconnect Bluetooth audio and confirm audio returns to the phone output.
- [ ] Confirm BLE MIDI remains connected/disconnected independently of Bluetooth audio.

## Notes

- Bluetooth audio output is an Android media route, not a MIDI connection. Pianova does not create or tear down the A2DP profile connection.
- The output-route display reports the device ID used by the Oboe stream; Android system routing is the control surface for choosing Bluetooth media output.
- No audio recording or network transfer is introduced by this Bluetooth feature.
