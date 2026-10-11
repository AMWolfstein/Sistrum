# Analysis validation

## Dynamics and loudness (commit 3)

All 368 analyzable corpus files pass. The full analysis suite passed 391 tests,
zero failures/errors/skips, including cancellation/resume, checkpoint independence,
zero feed allocations and the short-file metadata boundary. Go mood parity maximum
error is 2.220446049250313e-16 over 6,144 scalar comparisons.

| Feature | Maximum error | Tolerance |
| --- | ---: | ---: |
| loudness_lufs | 0.005 LU | 0.1 LU |
| loudness_range | 0 LU | 0.1 LU |
| true_peak | 3.5527136788e-15 dB | 0.1 dB |
| rms | 0 dB | 0.05 dB |
| crest | 0 % | 0.5 % |
| zcr | 0.00492295574263 % | 0.5 % |

Non-finite oracle values are matched by class (NaN, positive/negative infinity),
not used as percentage denominators. No files remain outside tolerance.

A focused short-DSD test initially found -70 LUFS versus the oracle's 0: without
100 ms of input, ebur128 emits no metadata. The port now preserves that absence
and the sample-peak fallback. The second full comparison found the floating-point
torture vector's peak becoming permanently NaN; astats actually updates extrema
with comparisons, retaining extrema through NaNs. That source behavior is now
preserved. Two accompanying crest failures were report arithmetic on matching
NaNs, corrected without changing oracle values or tolerances. The next full run
passed all 368 files. An earlier long-vector run was interrupted to replace
per-tap modulo divisions with equivalent power-of-two ring indexing.

## Laptop benchmark

OpenJDK 21.0.12.1, Linux amd64, eight logical CPUs. 44.1 kHz stereo,
1,048,576 PCM frames per measured group, reused 8192-frame blocks, with DSP and
measurement counters warmed first. Constructor, finish, file I/O and checkpoint
allocation are outside the feed measurement. RTF = wall seconds / audio seconds.

group	frames	RTF	feed_allocated_bytes	bytes_per_frame
dynamics	1048576	3.797170085906982E-4	0	0.0
loudness_true_peak	1048576	0.03805358368177414	0	0.0

The JVM allocation test independently asserts zero warmed feed allocations.
