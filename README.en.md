# BPC time sync over a sound card

[中文](README.md)

Sync a Casio radio-controlled watch to China BPC when it cannot hear the Shangqiu transmitter. A browser page plays the time code through a wired headphone; the watch reads the magnetic field from the speaker. Verified on a Casio 5610.

A browser cannot emit 68.5 kHz. The page plays a 13.7 kHz carrier (or 17.125 kHz). With a wired headphone speaker held against the case back, the coil's nonlinearity produces the 68.5 kHz field the watch expects.

## Open

Double-click `index.html` in a browser. Python and a local server are not required.

The page syncs to Suning time automatically and corrects the clock by half of the round trip. The status line should show a Suning time source. If Suning fails, the page falls back to the computer clock and marks the status in red. Click Resync to try again.

## Set the watch

1. Confirm the page has synced to network time and the status is not red.
2. Plug in wired headphones. Bluetooth has no magnetic path to the watch.
3. Set the system volume to maximum and select those headphones as the output device.
4. Click Start. 13.7 kHz is near the top of human hearing, so the carrier can be hard to hear while it is still transmitting.
5. Hold the headphone speaker against the back of the watch. Do not move it.
6. Start a forced receive on the watch. After the receive icon appears, keep it there for about 3 to 8 minutes. Button layout varies by model; follow the watch manual.
7. If it fails, try the other carrier frequency or waveform, turn on Invert, or move the delay trim by 20 ms and try again. Positive trim advances the emission.

The default envelope matches real BPC: each data second starts with 0.1 to 0.4 seconds of silence, and frame markers stay at full amplitude for the whole second. Invert sounds at the start of each data second and silences the frame markers.

## Check

Self-check encodes the current frame and decodes it again. The result must equal Beijing time at the start of that frame. Encoding tests:

```bash
node --test
```
