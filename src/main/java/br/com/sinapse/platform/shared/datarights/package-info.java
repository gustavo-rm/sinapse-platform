/**
 * The contract each module implements for the data subject rights coordinator.
 *
 * <p><strong>Why it is here and not in {@code datarights}.</strong> Every module has to
 * implement it and the coordinator has to consume it, so an interface living in the
 * coordinator would make identity, planning and the rest depend on the thing that depends on
 * them. Putting it in {@code shared} is the only placement without a cycle, and it keeps the
 * coordinator from ever naming a module's type: it asks Spring for every implementation and
 * sequences them.
 *
 * <p>It is a cross-cutting contract rather than a domain concept, which is what {@code shared}
 * is for. Nothing here knows what any module stores.
 */
@org.springframework.modulith.NamedInterface("datarights")
package br.com.sinapse.platform.shared.datarights;
