package br.com.sinapse.platform.shared.web;

import java.time.Instant;

/**
 * Answer of the probe endpoints.
 *
 * @param status     fixed acknowledgement
 * @param observedAt instant taken from the application clock, serialised as ISO-8601
 *                   with offset
 */
public record ProbeResponse(String status, Instant observedAt) {
}
