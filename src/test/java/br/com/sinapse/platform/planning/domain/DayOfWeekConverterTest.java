package br.com.sinapse.platform.planning.domain;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.sinapse.platform.planning.internal.domain.DayOfWeekConverter;
import java.time.DayOfWeek;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

/**
 * The weekday convention, stated once so that it cannot drift.
 *
 * <p>The column is {@code smallint} constrained to {@code 0..6} and the ISO enumeration runs
 * {@code 1..7} from Monday, so something has to give. What is asserted here is that the
 * mapping is PostgreSQL's own — {@code 0} is Sunday, as {@code extract(dow from ...)} reports
 * it — because that is what makes any future query comparing this column against the weekday
 * of a date correct without a translation nobody remembers to write.
 */
class DayOfWeekConverterTest {

    private final DayOfWeekConverter converter = new DayOfWeekConverter();

    @Test
    void sundayIsZeroAsPostgresCountsIt() {
        assertThat(converter.convertToDatabaseColumn(DayOfWeek.SUNDAY)).isZero();
        assertThat(converter.convertToDatabaseColumn(DayOfWeek.MONDAY)).isEqualTo((short) 1);
        assertThat(converter.convertToDatabaseColumn(DayOfWeek.SATURDAY)).isEqualTo((short) 6);
    }

    @Test
    void everyDayRoundTrips() {
        assertThat(Arrays.stream(DayOfWeek.values()))
                .allSatisfy(day -> assertThat(converter.convertToEntityAttribute(
                        converter.convertToDatabaseColumn(day))).isEqualTo(day));
    }

    @Test
    void everyStoredCodeIsWithinWhatTheConstraintAllows() {
        assertThat(Arrays.stream(DayOfWeek.values()))
                .allSatisfy(day -> assertThat(converter.convertToDatabaseColumn(day))
                        .isBetween((short) 0, (short) 6));
    }

    @Test
    void nullPassesThroughForAColumnThatIsNeverNull() {
        assertThat(converter.convertToDatabaseColumn(null)).isNull();
        assertThat(converter.convertToEntityAttribute(null)).isNull();
    }
}
