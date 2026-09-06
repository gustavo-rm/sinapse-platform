/**
 * HTTP surface of the planning module.
 *
 * <p>There is no route that creates or edits a plan. Plans come from the generation job and
 * from nowhere else: one written by hand would have no snapshot behind it and could never be
 * regenerated, which is the property ADR 0007 exists to protect.
 */
package br.com.sinapse.platform.planning.internal.web;
