/**
 * The aggregates of the curriculum context, mapped onto {@code V2__curriculum.sql}, which is
 * authoritative and untouched.
 *
 * <p>Three roots and not one. {@code Topic} is not an internal entity of {@code Subject}:
 * three other contexts reference topics by identifier and a topic is the unit planning works
 * in, so loading a whole subject on every read would be a design error. {@code
 * TopicPrerequisite} is a root because the graph is queried as a set, not from one topic
 * outwards.
 */
package br.com.sinapse.platform.curriculum.internal.domain;
