package br.com.sinapse.platform.planning.orchestration.support;

import br.com.sinapse.platform.planning.orchestration.SeedSource;

/**
 * A seed a test chooses.
 *
 * <p>The production source draws one at random, which is right and makes a test of "the same
 * seed produces the same plan" impossible to write. Pinning it is the point: what has to be
 * shown is that the plan follows from the recorded seed, not that two random runs happened to
 * agree.
 */
public class FixedSeedSource implements SeedSource {

    private volatile long seed = 20260906L;

    @Override
    public long next() {
        return seed;
    }

    /**
     * @param seed what the next run will use
     */
    public void setSeed(long seed) {
        this.seed = seed;
    }
}
