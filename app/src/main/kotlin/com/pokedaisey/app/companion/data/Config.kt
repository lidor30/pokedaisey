package com.pokedaisey.app.companion.data

/**
 * Default gQolTelemetry address, baked in from dist/qol-telemetry-address.txt
 * at the time this file was generated. Like tools/telemetry-viewer, this
 * address shifts on ROM rebuilds - regenerate this constant (or use the
 * in-app override in Settings) whenever the ROM changes. See
 * docs/telemetry.md's "Why the address isn't hardcoded" section.
 */
const val DEFAULT_TELEMETRY_ADDR = 0x03007490L

const val DEFAULT_RETROARCH_HOST = "127.0.0.1"
const val DEFAULT_RETROARCH_PORT = 55355
const val DEFAULT_POLL_INTERVAL_MS = 1000L
