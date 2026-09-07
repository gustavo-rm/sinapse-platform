package br.com.sinapse.platform.curation.internal.model;

/**
 * How a curator refers to a topic: {@code subject_code:topic_code}.
 *
 * <p>ADR 0014 makes the natural key mandatory, and both alternatives are worse in ways that
 * matter. A UUID in a spreadsheet is unusable by a human. A position is worse than unusable: it
 * is <em>plausible</em>, and reordering a subject would silently repoint every edge in the file.
 *
 * <p>The subject is part of the key because edges may cross subjects — a topic in physiology may
 * legitimately depend on one in anatomy — which is exactly what a two-file-per-subject layout
 * would otherwise make impossible to express.
 *
 * @param subjectCode subject the topic belongs to
 * @param topicCode   code of the topic within that subject
 */
public record TopicKey(String subjectCode, String topicCode) {

    /**
     * Parses {@code subject:topic}.
     *
     * @param reference text as written in the file
     * @return the key, or {@code null} when it is not two parts separated by one colon
     */
    public static TopicKey parse(String reference) {
        if (reference == null) {
            return null;
        }
        int separator = reference.indexOf(':');
        if (separator <= 0 || separator == reference.length() - 1
                || reference.indexOf(':', separator + 1) >= 0) {
            return null;
        }
        return new TopicKey(reference.substring(0, separator).trim(),
                reference.substring(separator + 1).trim());
    }

    @Override
    public String toString() {
        return subjectCode + ":" + topicCode;
    }
}
