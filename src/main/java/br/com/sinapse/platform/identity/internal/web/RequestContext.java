package br.com.sinapse.platform.identity.internal.web;

import br.com.sinapse.platform.identity.internal.domain.ConsentEvidence;
import br.com.sinapse.platform.shared.web.ClientAddressResolver;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;

/**
 * What the controllers need to know about the request itself.
 *
 * <p>One component, so that the address a consent is attributed to is resolved the same way
 * everywhere. The resolution is the shared one: the address reported by the infrastructure,
 * with {@code X-Forwarded-For} believed only behind a configured trusted proxy. A
 * forged-header bypass of exactly this kind was already found in the Core repository, and
 * evidence attributed to an address the client chose would be evidence of nothing.
 */
@Component
public class RequestContext {

    private final ClientAddressResolver clientAddressResolver;

    /**
     * @param clientAddressResolver shared resolution of the client address
     */
    public RequestContext(ClientAddressResolver clientAddressResolver) {
        this.clientAddressResolver = clientAddressResolver;
    }

    /**
     * The address to attribute a request to.
     *
     * @param request current request
     * @return the client address
     */
    public String addressOf(HttpServletRequest request) {
        return clientAddressResolver.resolve(request);
    }

    /**
     * The agent string a request declared.
     *
     * @param request current request
     * @return the header, or {@code null}
     */
    public String userAgentOf(HttpServletRequest request) {
        return request.getHeader(HttpHeaders.USER_AGENT);
    }

    /**
     * The evidence document for an act of consent performed by this request.
     *
     * @param request current request
     * @return address, agent and the methods by which consent and age were established
     */
    public ConsentEvidence evidenceOf(HttpServletRequest request) {
        return ConsentEvidence.ofApiForm(addressOf(request), userAgentOf(request));
    }
}
