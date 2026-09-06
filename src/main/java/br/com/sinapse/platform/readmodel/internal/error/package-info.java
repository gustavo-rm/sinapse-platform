/**
 * The failures these reads can raise, each mapped to the shared error catalogue of ADR 0009.
 *
 * <p>Every one of them is a translation and never a text: the wording of a response body comes
 * from {@code ApiErrorType} and from nowhere else, so nothing here can leak an entity name, a
 * column, or anything about the student being asked about.
 */
package br.com.sinapse.platform.readmodel.internal.error;
