package br.com.sinapse.platform.planning.internal.config;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Configuration of the planning module, bound from {@code sinapse.planning}.
 *
 * @param maxScheduleWindow the widest window a schedule may be asked for. Section 1 of the API
 *                          contract requires the cap: planned sessions accumulate with every
 *                          plan and there is no generic pagination to fall back on
 * @param maxPlanHistory    ceiling on how many plans a history read returns. Re-planning is
 *                          manual, so this list grows in single figures; the ceiling is what
 *                          keeps "returned whole" from meaning "returned however large it got"
 */
@Validated
@ConfigurationProperties("sinapse.planning")
public record PlanningProperties(

        @NotNull @DefaultValue("90d") Duration maxScheduleWindow,

        @Positive @DefaultValue("100") int maxPlanHistory) {
}
