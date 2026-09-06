package br.com.sinapse.platform.coreclient.contract;

/** What a scheduled session is for: new ground, or going back over it. */
public enum SessionKind {

    /** New ground. */
    STUDY,

    /** Revision of something already studied. */
    REVISION
}
