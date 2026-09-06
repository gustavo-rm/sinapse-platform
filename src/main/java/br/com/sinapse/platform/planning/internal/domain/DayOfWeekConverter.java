package br.com.sinapse.platform.planning.internal.domain;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.time.DayOfWeek;

/**
 * Maps {@link DayOfWeek} onto the {@code smallint} the schema uses.
 *
 * <p>The column is constrained to {@code 0..6} and the ISO enumeration runs {@code 1..7} from
 * Monday, so a convention has to be chosen. This one is PostgreSQL's: {@code 0} is Sunday,
 * exactly as {@code extract(dow from ...)} reports it. The reason is not taste — it means any
 * query that ever compares this column against the weekday of a date is right without a
 * translation, and a translation is what nobody remembers to write.
 */
@Converter(autoApply = true)
public class DayOfWeekConverter implements AttributeConverter<DayOfWeek, Short> {

    @Override
    public Short convertToDatabaseColumn(DayOfWeek dayOfWeek) {
        // MONDAY(1) through SATURDAY(6) keep their number; SUNDAY(7) folds onto 0.
        return dayOfWeek == null ? null : (short) (dayOfWeek.getValue() % 7);
    }

    @Override
    public DayOfWeek convertToEntityAttribute(Short code) {
        if (code == null) {
            return null;
        }
        return code == 0 ? DayOfWeek.SUNDAY : DayOfWeek.of(code);
    }
}
