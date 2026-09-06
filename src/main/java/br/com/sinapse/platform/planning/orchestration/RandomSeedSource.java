package br.com.sinapse.platform.planning.orchestration;

import java.security.SecureRandom;
import org.springframework.stereotype.Component;

/**
 * Draws a seed.
 *
 * <p>{@link SecureRandom} not because a seed needs to be unguessable — it does not, and it is
 * stored in clear on the job — but because it is the source that does not need seeding itself.
 * A generator seeded from the clock would repeat across a restart, and two runs that shared a
 * seed by accident would be indistinguishable from two runs that shared one on purpose.
 */
@Component
public class RandomSeedSource implements SeedSource {

    private final SecureRandom random = new SecureRandom();

    @Override
    public long next() {
        return random.nextLong();
    }
}
