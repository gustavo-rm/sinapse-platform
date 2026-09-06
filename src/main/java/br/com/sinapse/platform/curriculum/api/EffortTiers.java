package br.com.sinapse.platform.curriculum.api;

import java.time.Duration;
import java.util.Map;

/**
 * The band-to-duration mapping.
 *
 * <p>Published from this module because the snapshot assembly needs it, and configuration
 * rather than code because that is the whole point of ADR 0012: the number belongs where it
 * can be calibrated against observed data, not where it is asserted.
 *
 * <p><strong>The initial values are an assumption, not a finding.</strong> They were chosen
 * to be plausible and evenly spaced, and nothing in the pilot has confirmed them yet.
 * Calibrating them against the durations actually observed in completed study sessions is
 * analysis work that belongs to the pilot, and until it happens these numbers should be read
 * as a starting point rather than as a claim about how long anything takes.
 *
 * <p>The per-student adjustment — the ratio between effective and planned duration in
 * completed sessions — is derived when the snapshot is assembled and is never persisted, so
 * it is not here either.
 */
public interface EffortTiers {

    /**
     * The planned duration of a band.
     *
     * @param tier band
     * @return the configured duration
     */
    Duration plannedDurationOf(EffortTier tier);

    /**
     * The whole mapping, so that it can travel into a snapshot as one value.
     *
     * @return every band and its configured duration
     */
    Map<EffortTier, Duration> mapping();
}
