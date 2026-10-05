/**
 * HTTP surface of the curriculum module: the catalogue, read-only.
 *
 * <p>Only reads. Writing the catalogue is the importer's job (ADR 0014), and an HTTP route that
 * edited it would need to know who may curate — decision P2, still open.
 */
package br.com.sinapse.platform.curriculum.internal.web;
