package br.com.sinapse.platform.shared.web;

/**
 * Single definition of the versioned API prefix.
 *
 * <p>Every route lives under {@link #V1}. The prefix is part of the contract from
 * the first endpoint (ADR 0009): introducing it later would break every client.
 */
public final class ApiPaths {

    /** Prefix of version 1 of the public API. */
    public static final String V1 = "/api/v1";

    private ApiPaths() {
    }
}
