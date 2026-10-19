package br.com.sinapse.platform.planning.orchestration.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * A core over HTTP that chooses its engine the way the real one does, and remembers every body
 * it was sent.
 *
 * <p>For the tests that need the real adapter and the real wire, not the in-process
 * {@link StubSinapseCore}. It reproduces the real core's engine selection as its README and
 * {@code PlanEngineSelector} describe it (read at exam-optimizer-application@3b54ba4, not run):
 * no {@code algorithmParams.engine} runs the default, {@code greedy-baseline}; a registered name
 * runs that engine; anything else is refused with {@code 422} and the
 * {@code .../errors/unknown-engine} problem, naming the value. Either way the answer declares
 * the engine that ran under {@code fitness.engine}, as the real one does, and its build under
 * {@code fitness.build}. The plan itself is one session at the start of the first window, which
 * passes every check the adapter makes.
 *
 * <p>The list of engines lives here, in a test stand-in, and nowhere in {@code src/main}: which
 * engines exist is the core's to say.
 */
public final class EngineAwareCore {

    /** The engine the real core runs when the request names none. */
    public static final String DEFAULT_ENGINE = "greedy-baseline";

    /** What this stand-in reports as its build, under {@code fitness.build}. */
    public static final String BUILD = "0123456789abcdef0123456789abcdef01234567";

    private static final Set<String> ENGINES = Set.of(DEFAULT_ENGINE, "ga", "ga-timeline");

    private static final ObjectMapper JSON = new ObjectMapper();

    private final HttpServer server;

    private final List<JsonNode> received = new CopyOnWriteArrayList<>();

    private EngineAwareCore(HttpServer server) {
        this.server = server;
    }

    /** Starts one on a free loopback port. */
    public static EngineAwareCore start() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            EngineAwareCore core = new EngineAwareCore(server);
            server.createContext("/plans", core::answer);
            server.start();
            return core;
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }

    /** Where it answers, for {@code sinapse.core.base-url}. */
    public String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    /** Every body received, in order. */
    public List<JsonNode> received() {
        return List.copyOf(received);
    }

    /** The last body received. */
    public JsonNode lastReceived() {
        if (received.isEmpty()) {
            throw new AssertionError("the core was never called");
        }
        return received.get(received.size() - 1);
    }

    /** Stops it. */
    public void stop() {
        server.stop(0);
    }

    private void answer(HttpExchange exchange) throws IOException {
        JsonNode request = JSON.readTree(exchange.getRequestBody());
        received.add(request);
        JsonNode engine = request.path("algorithmParams").get("engine");
        String effective = engine == null ? DEFAULT_ENGINE : engine.asText();
        if (engine != null && (!engine.isTextual() || !ENGINES.contains(effective))) {
            String problem = """
                    {"type":"https://api.dynamicstudyplanner.com/errors/unknown-engine",
                     "title":"Unknown engine","status":422,
                     "detail":"algorithmParams.engine names no registered engine; registered: %s",
                     "offending":[%s]}
                    """.formatted(ENGINES, JSON.writeValueAsString(engine.asText()));
            send(exchange, 422, "application/problem+json", problem);
            return;
        }
        JsonNode window = request.get("availability").get(0);
        String plan = """
                {"contractVersion":"1.0",
                 "sessions":[{"topicId":"%s","kind":"STUDY","scheduledStart":"%s",
                              "durationMinutes":30,"sequenceIndex":0}],
                 "fitness":{"engine":%s,"build":"%s"},
                 "metadata":{"coreVersion":"engine-aware-stub","randomSeed":%d,
                             "generations":0,"elapsedMillis":0}}
                """.formatted(request.get("topics").get(0).get("id").asText(),
                window.get("start").asText(), JSON.writeValueAsString(effective), BUILD,
                request.get("randomSeed").asLong());
        send(exchange, 200, "application/json", plan);
    }

    private static void send(HttpExchange exchange, int status, String type, String body)
            throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", type);
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }
}
