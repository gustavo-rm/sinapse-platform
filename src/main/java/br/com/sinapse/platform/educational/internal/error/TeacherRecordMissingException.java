package br.com.sinapse.platform.educational.internal.error;

import br.com.sinapse.platform.shared.web.problem.ApiErrorType;
import br.com.sinapse.platform.shared.web.problem.ApiException;

/**
 * Raised when an account holding the teacher role has no teacher record.
 *
 * <p>The role authorises the login; this record holds the display name a student sees on an
 * invite preview. How an account comes to hold the role is unresolved (P2), and creating the
 * record is the administrative operation that answer will define.
 */
public class TeacherRecordMissingException extends ApiException {

    /** Creates the failure. */
    public TeacherRecordMissingException() {
        super(ApiErrorType.ACCESS_DENIED);
    }
}
