# BPC time sync over a sound card

[中文](README.md)

Sync a Casio radio-controlled watch to China BPC when it cannot hear the Shangqiu transmitter. A browser page plays the time code through a wired headphone; the watch reads the magnetic field from the speaker.

A browser cannot emit 68.5 kHz. The page plays a 13.7 kHz carrier (or 17.125 kHz). The headphone coil is nonlinear, so that tone produces the 68.5 kHz field the watch expects. Verified on a Casio 5610.

## Open

From this directory:

```bash
python -m http.server 8765
```

Open `http://127.0.0.1:8765/`. Do not open `index.html` as a file. Time requests are blocked on `file://`, and the page then falls back to the computer clock.

On load the page asks Suning, WorldTimeAPI, and TimeAPI in order, keeps the sample with the shortest round trip, and corrects the clock by half of that delay. Suning is read with a script tag because a normal request is blocked by CORS. If all three fail, the page uses the computer clock and marks the status in red.

## Set the watch

1. Plug in wired headphones. Bluetooth has no magnetic path to the watch.
2. Set the system volume to maximum and select those headphones as the output device.
3. Click Start. 13.7 kHz is near the top of human hearing, so the carrier can be hard to hear while it is still transmitting.
4. Hold the headphone speaker against the back of the watch. Do not move it.
5. Start a forced receive on the watch. After the receive icon appears, keep it there for about 3 to 8 minutes.
6. If it fails, try the other carrier frequency or waveform, turn on Invert, or move the delay trim by 20 ms and try again.

The default envelope matches real BPC: each data second starts with 0.1 to 0.4 seconds of silence, and frame markers stay at full amplitude for the whole second. Invert sounds at the start of each data second and silences the frame markers.

## Check

Self-check encodes the current frame and decodes it again. The result must equal Beijing time at the start of that frame. Encoding tests:

```bash
node --test
```
