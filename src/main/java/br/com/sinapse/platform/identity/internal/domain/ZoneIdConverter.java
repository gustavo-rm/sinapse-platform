package br.com.sinapse.platform.identity.internal.domain;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.time.ZoneId;

/**
 * Stores a {@link ZoneId} as the IANA identifier it already is.
 *
 * <p>Reading is deliberately strict: {@link ZoneId#of(String)} throws on a zone the
 * runtime does not know, so a row written by an older tz database surfaces as a failure
 * rather than as a silent fallback to some default zone, which is exactly what ADR 0009
 * forbids.
 */
@Converter
public class ZoneIdConverter implements AttributeConverter<ZoneId, String> {

    @Override
    public String convertToDatabaseColumn(ZoneId attribute) {
        return attribute == null ? null : attribute.getId();
    }

    @Override
    public ZoneId convertToEntityAttribute(String dbData) {
        return dbData == null ? null : ZoneId.of(dbData);
    }
}
