package br.com.sinapse.platform.planning.orchestration;

/**
 * Where the seed of a generation run comes from.
 *
 * <p><strong>The backend chooses it, not the core.</strong> A genetic algorithm is stochastic,
 * so the same snapshot and the same parameters produce a different plan unless the seed is
 * fixed; and the party that has to be able to replay a run is the one that keeps the record of
 * it. A seed chosen by the core and reported back would make replay depend on the core having
 * told the truth.
 *
 * <p>It is an interface so that a test can pin it. Nothing in production has a second
 * implementation.
 */
public interface SeedSource {

    /**
     * A seed for one run.
     *
     * @return the seed, which is recorded on the job before the run starts
     */
    long next();
}
