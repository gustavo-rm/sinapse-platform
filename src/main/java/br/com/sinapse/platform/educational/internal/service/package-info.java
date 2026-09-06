/**
 * Use cases of the educational context.
 *
 * <p>The two rules worth stating once, because everything here observes them.
 *
 * <p><strong>Authorisation is derived and checked now.</strong> There is no teacher-to-student
 * relation to read. A teacher may see a student when an active enrollment and a valid sharing
 * consent both hold at the moment of the question, and nothing caches either.
 *
 * <p><strong>Identity is asked, never read.</strong> Whether a student may share data with an
 * institution is one call to {@code AccountAccessPolicy}. No service here knows what a consent
 * record is, and {@code ModularityTests} fails the build if one starts to.
 */
package br.com.sinapse.platform.educational.internal.service;
