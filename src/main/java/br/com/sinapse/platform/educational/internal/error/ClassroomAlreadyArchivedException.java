package br.com.sinapse.platform.educational.internal.error;

import br.com.sinapse.platform.shared.web.problem.ApiErrorType;
import br.com.sinapse.platform.shared.web.problem.ApiException;

/**
 * Raised when an archived classroom is archived again.
 *
 * <p>A second archiving would move the timestamp and lose when the teacher's access actually
 * ended.
 */
public class ClassroomAlreadyArchivedException extends ApiException {

    /** Creates the failure. */
    public ClassroomAlreadyArchivedException() {
        super(ApiErrorType.CONFLICT);
    }
}
