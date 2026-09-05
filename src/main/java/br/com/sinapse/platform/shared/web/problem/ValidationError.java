package br.com.sinapse.platform.shared.web.problem;

import java.util.Comparator;
import java.util.List;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;

/**
 * One entry of the {@code errors} array of a validation problem.
 *
 * <p>Carries the name of the offending field and the constraint message. It never
 * carries the rejected value: the value is exactly the piece of the request that may
 * be personal data.
 *
 * <p>Constraint messages must therefore be written as statements about the rule
 * ("must not be blank"), never interpolate {@code ${validatedValue}}, and never name
 * a table or a column.
 *
 * @param field   name of the field in the request contract
 * @param message message of the violated constraint
 */
public record ValidationError(String field, String message) {

    /** Field name used for errors that concern the request as a whole. */
    private static final String REQUEST_LEVEL = "request";

    private static final Comparator<ValidationError> ORDER =
            Comparator.comparing(ValidationError::field).thenComparing(ValidationError::message);

    /**
     * Extracts the errors of a binding result.
     *
     * <p>Object-level errors are reported against {@link #REQUEST_LEVEL} rather than
     * against the object name, because the object name is the name of an internal
     * class.
     *
     * @param bindingResult result of the failed binding
     * @return the errors, ordered so that the response is deterministic
     */
    public static List<ValidationError> from(BindingResult bindingResult) {
        return bindingResult.getAllErrors().stream()
                .map(ValidationError::toEntry)
                .sorted(ORDER)
                .toList();
    }

    private static ValidationError toEntry(ObjectError error) {
        String field = error instanceof FieldError fieldError ? fieldError.getField() : REQUEST_LEVEL;
        String message = error.getDefaultMessage() == null ? "invalid" : error.getDefaultMessage();
        return new ValidationError(field, message);
    }
}
