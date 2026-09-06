package br.com.sinapse.platform.datarights.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.core.annotation.Order;

/**
 * The one thing an erasure test cannot get from the real system: a module that fails.
 *
 * <p>Nothing else is replaced. The transaction, the triggers, the partial indexes and every real
 * module's erasure are the ones that run in production.
 */
@TestConfiguration
public class DataRightsTestSupport {

    /**
     * @return a module that erases nothing and can be told to throw
     */
    @Bean
    @Order(ControllableModuleDataRights.ORDER)
    public ControllableModuleDataRights controllableModuleDataRights() {
        return new ControllableModuleDataRights();
    }
}
