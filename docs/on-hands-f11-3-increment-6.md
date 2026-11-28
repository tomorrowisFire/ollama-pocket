# F11-3 r006 — unified Whisper session deadline and late playback admission

Keep Whisper's existing 180-second session budget and 150-second processing cap, but never extend the original session when processing starts late. Reject expired TTS initialization and playback start callbacks before starting speech or barge monitoring. Clear session-specific deadlines during cancellation. Pure boundary fixtures added. Design perspectives: voice UX, lifecycle safety, bounded resource use. Device latency, acoustic quality and full-duplex capabilities remain unverified.
