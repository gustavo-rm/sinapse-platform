/**
 * The aggregates of the educational context, mapped onto {@code V3__educational.sql}, which
 * is authoritative and untouched.
 *
 * <p>Four roots. {@code Invite} and {@code Enrollment} are separate from {@code Classroom}
 * because of who reads them: an invite is looked up by code by a student who has no access to
 * the classroom yet, and an enrollment is read on every single authorisation check. Loading a
 * classroom to answer either would be a design error rather than an inefficiency.
 */
package br.com.sinapse.platform.educational.internal.domain;
