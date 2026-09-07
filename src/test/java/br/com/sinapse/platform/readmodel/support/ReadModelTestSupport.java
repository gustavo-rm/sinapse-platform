package br.com.sinapse.platform.readmodel.support;

import jakarta.persistence.EntityManagerFactory;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

/**
 * Beans every read model test needs.
 *
 * <p>Nothing is replaced. The read models under test are the real ones, composing the real
 * modules over a real PostgreSQL; the only addition is the statement counter, which observes
 * and changes nothing.
 */
@TestConfiguration
public class ReadModelTestSupport {

    /**
     * @param factory the persistence unit whose statistics are read
     * @return the statement counter
     */
    @Bean
    public QueryCounter queryCounter(EntityManagerFactory factory) {
        return new QueryCounter(factory);
    }
}
