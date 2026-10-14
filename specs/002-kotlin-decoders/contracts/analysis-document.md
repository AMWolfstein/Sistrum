# Contract: analysis document keys (M2, M4)

See data-model.md. Writers: the background analysis worker only. Readers: `SyncDatabase.moodRadioTracks` /
`moodRadioEligibleTrackIds` (mood keys), `TransitionController` via the coordinator (silence keys).
Algorithm versions (`moodVersion`, `silenceVersion`) change ⇒ the worker re-analyses those tracks once.
Absent keys are never an error.
