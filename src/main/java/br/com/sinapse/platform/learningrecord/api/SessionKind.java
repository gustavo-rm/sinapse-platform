package br.com.sinapse.platform.learningrecord.api;

/** Whether a session covered new ground or went back over it. */
public enum SessionKind {

    /** First pass over the topic. */
    STUDY,

    /** Going back over a topic already studied. */
    REVISION
}
