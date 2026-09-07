/**
 * The read models: one shape per screen, composed across modules.
 *
 * <p><strong>Not a bounded context.</strong> It holds no table, no entity and no rule of its
 * own, and it is the <em>third</em> component in this system authorised to know more than one
 * module, alongside {@code planning.orchestration} and {@code datarights}. ADR 0013 puts it
 * here for the reason section 3 of the API contract states: write follows the aggregate,
 * because the aggregate is the unit of consistency; read follows the screen, because the
 * screen is the unit of utility. The two shapes differ, and the contract says so rather than
 * pretending one endpoint per aggregate would do.
 *
 * <p>Three rules, and the declaration below is what keeps them honest. It composes by calling
 * each module's {@code api} — never a table belonging to another module, never an
 * {@code internal} package. It never writes: {@code ReadModelsNeverWriteTest} fails the build
 * when a class here reaches a repository method that saves or deletes. And it materialises
 * nothing — no cache, no view, no asynchronous projection — because materialisation buys
 * staleness and a refresh routine, and the volume of the pilot justifies neither.
 *
 * <p><strong>Why every dependency is named here.</strong> The list is long because a screen is
 * long: the day's agenda alone needs planning, curriculum and the learning record to say one
 * coherent thing. That is the cost ADR 0013 accepted, and it comes with the warning it
 * recorded — this package concentrates knowledge of the whole system and is the natural
 * candidate to become a service that knows everything. A seventh read model should require a
 * user flow that demands it, not the convenience of another endpoint.
 */
@org.springframework.modulith.ApplicationModule(
        displayName = "Read Models",
        allowedDependencies = {
                "identity :: api", "curriculum :: api", "educational :: api",
                "learningrecord :: api", "planning :: api",
                "shared :: web", "shared :: problem", "shared :: security"})
package br.com.sinapse.platform.readmodel;
