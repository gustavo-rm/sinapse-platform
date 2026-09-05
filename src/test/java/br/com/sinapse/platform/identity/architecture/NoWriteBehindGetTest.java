package br.com.sinapse.platform.identity.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.domain.JavaMethodCall;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

/**
 * No state-changing operation is reachable by GET.
 *
 * <p>This is the condition the CSRF decision rests on. Prompt 00 disabled CSRF on the
 * premise that the API is only ever called with a header token; ADR 0010 then chose a
 * browser cookie, which is an ambient credential, and {@code SameSite=Strict} became what
 * stands in for a token. {@code Strict} keeps the cookie off cross-site requests — but only
 * because none of the operations a cross-site request could reach change anything. The day a
 * GET writes, that reasoning collapses silently and nothing about the code looks wrong.
 *
 * <p>So it is checked rather than remembered. Every handler mapped to GET is walked, through
 * the calls it makes and the calls those make, and the walk fails if it reaches a write: a
 * method that opens a read-write transaction, or a repository operation that persists or
 * deletes.
 *
 * <p>What the walk deliberately does not cover is the session filter, which updates
 * {@code last_seen_at} on every authenticated request, GET included. That is not an
 * operation the API offers: nothing a forged request could do to it changes anything a
 * person would notice, and it happens before any route is chosen.
 */
class NoWriteBehindGetTest {

    private static final String APPLICATION_PACKAGE = "br.com.sinapse.platform";

    /** Repository operations that write. Their owner is a Spring Data interface. */
    private static final Set<String> WRITING_REPOSITORY_METHODS = Set.of(
            "save", "saveAll", "saveAllAndFlush", "saveAndFlush",
            "delete", "deleteAll", "deleteAllInBatch", "deleteAllById",
            "deleteAllByIdInBatch", "deleteById", "deleteInBatch", "flush");

    /** Entity manager operations that write. */
    private static final Set<String> WRITING_ENTITY_MANAGER_METHODS = Set.of(
            "persist", "merge", "remove", "flush");

    private static final JavaClasses PRODUCTION_CLASSES = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages(APPLICATION_PACKAGE);

    @Test
    void noGetHandlerCanReachAWrite() {
        List<String> handlers = new ArrayList<>();
        List<String> violations = new ArrayList<>();

        for (JavaClass type : PRODUCTION_CLASSES) {
            for (JavaMethod method : type.getMethods()) {
                if (!isMappedToGet(method)) {
                    continue;
                }
                handlers.add(method.getFullName());
                pathToWrite(method).ifPresent(violations::add);
            }
        }

        assertThat(handlers)
                .as("a test that found no GET handler at all would pass for the wrong reason")
                .isNotEmpty();
        assertThat(violations)
                .as("CSRF protection is off. What replaces it is SameSite=Strict on the session "
                        + "cookie, and that only holds while GET is safe. A write reachable from a "
                        + "GET means CSRF tokens have to come back.")
                .isEmpty();
    }

    /**
     * Walks the calls a handler can make, breadth first, and reports the first path that
     * reaches a write.
     */
    private static Optional<String> pathToWrite(JavaMethod handler) {
        Set<String> seen = new HashSet<>();
        Deque<List<JavaMethod>> queue = new ArrayDeque<>();
        queue.add(List.of(handler));
        seen.add(handler.getFullName());

        while (!queue.isEmpty()) {
            List<JavaMethod> path = queue.poll();
            JavaMethod current = path.get(path.size() - 1);

            for (JavaMethodCall call : current.getMethodCallsFromSelf()) {
                String target = call.getTarget().getFullName();
                if (writes(call)) {
                    return Optional.of(describe(path) + " -> " + target);
                }
                if (!target.startsWith(APPLICATION_PACKAGE) || !seen.add(target)) {
                    continue;
                }
                call.getTarget().resolveMember().ifPresent(resolved -> {
                    List<JavaMethod> next = new ArrayList<>(path);
                    next.add(resolved);
                    queue.add(next);
                });
            }
        }
        return Optional.empty();
    }

    /**
     * Whether a call is a write.
     *
     * <p>Three shapes count: a method that opens a read-write transaction, a Spring Data
     * operation that persists or removes, and a JPA entity manager operation that does the
     * same. A read-only transaction is not a write, which is the point of declaring it.
     */
    private static boolean writes(JavaMethodCall call) {
        String owner = call.getTargetOwner().getName();
        String name = call.getTarget().getName();

        if (owner.startsWith("org.springframework.data") && WRITING_REPOSITORY_METHODS.contains(name)) {
            return true;
        }
        if (owner.startsWith("jakarta.persistence") && WRITING_ENTITY_MANAGER_METHODS.contains(name)) {
            return true;
        }
        return call.getTarget().resolveMember()
                .map(NoWriteBehindGetTest::opensAWriteTransaction)
                .orElse(false);
    }

    private static boolean opensAWriteTransaction(com.tngtech.archunit.core.domain.JavaMethod method) {
        if (method.isAnnotatedWith(Modifying.class)) {
            return true;
        }
        if (method.isAnnotatedWith(Transactional.class)) {
            return !method.getAnnotationOfType(Transactional.class).readOnly();
        }
        if (method.getOwner().isAnnotatedWith(Transactional.class)) {
            return !method.getOwner().getAnnotationOfType(Transactional.class).readOnly();
        }
        return false;
    }

    private static boolean isMappedToGet(JavaMethod method) {
        if (method.isAnnotatedWith(GetMapping.class)) {
            return true;
        }
        return method.isAnnotatedWith(RequestMapping.class)
                && List.of(method.getAnnotationOfType(RequestMapping.class).method())
                        .contains(RequestMethod.GET);
    }

    private static String describe(List<JavaMethod> path) {
        return String.join(" -> ", path.stream().map(JavaMethod::getFullName).toList());
    }
}
