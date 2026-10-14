# Contract: float sink chain (M2)

Order per player: decoder PCM → stream gain (normalization, 001) → [Night mode compressor, only when ON] → mixer →
session limiter (001, last). Night mode OFF removes the stage: output bit-identical to 001 (tested). Switching ON/OFF
crossfades 50 ms between bypass and compressed signal. Both A/B players use the same setting.
Silence-aware crossfade changes only the fade trigger time and the incoming start position; fade curve, duration and
the A/B handover are 001's.
