package br.com.sinapse.platform.identity.internal.web;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.time.ZoneId;

/**
 * Checks a zone identifier by parsing it, which is the only honest check: the set of valid
 * identifiers is the tz database this runtime carries, not a pattern.
 *
 * <p>A blank value passes, so that {@code @NotBlank} is the constraint that reports it. Two
 * violations for one empty field would say the same thing twice.
 */
public class IanaZoneIdValidator implements ConstraintValidator<IanaZoneId, String> {

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null || value.isBlank()) {
            return true;
        }
        try {
            ZoneId.of(value);
            return true;
        } catch (RuntimeException notAZone) {
            return false;
        }
    }
}
