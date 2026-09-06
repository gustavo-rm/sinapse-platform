package br.com.sinapse.platform.readmodel.support;

import jakarta.persistence.EntityManagerFactory;
import java.util.function.Supplier;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;

/**
 * Counts the statements one call actually sends to the database.
 *
 * <p>The prompt for the read models asks for this and gives the reason: an N+1 introduced later
 * passes every functional test in this repository and appears only as latency in production.
 * Asserting a fixed count is the only thing that fails when a batch lookup is quietly replaced
 * by a loop.
 *
 * <p>Prepared statements rather than Hibernate's own query counter, because the counter counts
 * what it was asked to run and the statement count is what the database is actually made to do
 * — which is the number that grows when a lazy association is resolved per row.
 */
public class QueryCounter {

    private final Statistics statistics;

    /**
     * @param factory the persistence unit whose statistics are read
     */
    public QueryCounter(EntityManagerFactory factory) {
        this.statistics = factory.unwrap(SessionFactory.class).getStatistics();
    }

    /**
     * Runs the call and reports what it cost.
     *
     * @param call  what to measure
     * @param <T>   what it produces
     * @return the result and the number of statements it took
     */
    public <T> Measured<T> measure(Supplier<T> call) {
        statistics.clear();
        long before = statistics.getPrepareStatementCount();
        T result = call.get();
        return new Measured<>(result, statistics.getPrepareStatementCount() - before);
    }

    /**
     * What one measured call produced and cost.
     *
     * @param result  what the call returned
     * @param queries statements it sent to the database
     * @param <T>     type of the result
     */
    public record Measured<T>(T result, long queries) {
    }
}
