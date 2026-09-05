package br.com.sinapse.platform.identity.internal.web;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * The annotated value is an IANA time zone identifier this runtime knows.
 *
 * <p>ADR 0009 makes {@code Account.timeZone} mandatory, because "study from 19:00 to 21:00"
 * has no determined meaning without it and the plan is generated in absolute instants. A
 * zone that does not parse has to be rejected at the edge: accepting it would put a value in
 * the database that nothing can read back, and the alternative — falling back to some
 * default — is exactly what the ADR forbids.
 */
@Documented
@Constraint(validatedBy = IanaZoneIdValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
public @interface IanaZoneId {

    /** Message of the violation. It names the rule and never the submitted value. */
    String message() default "must be an IANA time zone identifier";

    /** Validation groups. */
    Class<?>[] groups() default {};

    /** Payload. */
    Class<? extends Payload>[] payload() default {};
}
