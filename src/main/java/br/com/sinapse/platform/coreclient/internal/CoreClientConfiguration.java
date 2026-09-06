package br.com.sinapse.platform.coreclient.internal;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * The HTTP client the adapter calls the core with.
 *
 * <p>Built from the application's own {@code RestClient.Builder}, so that the mapper which
 * writes the request is the mapper the rest of the platform uses. That is not tidiness: the
 * job stores the snapshot by converting the same object with the same mapper, and if the two
 * differed the stored document would not be the document that was sent.
 *
 * <p>Both timeouts are configuration. The read timeout is generous by nature — the
 * optimisation is CPU-bound and runs for minutes — so it bounds a run that has hung rather
 * than one that is merely slow. Without it, a hung core holds a worker forever and the
 * account's partial index keeps the student from ever asking again.
 */
@Configuration(proxyBeanMethods = false)
public class CoreClientConfiguration {

    /**
     * @param builder    the application's configured builder, message converters and all
     * @param properties address and timeouts
     * @return the client used to reach the core, and nothing else
     */
    @Bean
    public RestClient sinapseCoreRestClient(RestClient.Builder builder, CoreProperties properties) {
        return builder.clone()
                .baseUrl(properties.baseUrl())
                .requestFactory(requestFactory(properties))
                .build();
    }

    private static ClientHttpRequestFactory requestFactory(CoreProperties properties) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(properties.connectTimeout());
        factory.setReadTimeout(properties.readTimeout());
        return factory;
    }
}
