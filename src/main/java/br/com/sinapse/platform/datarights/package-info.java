/**
 * The data subject rights coordinator: erasure, export and disclosure of past access.
 *
 * <p><strong>Not a bounded context.</strong> It is a coordinator, and the <em>second</em>
 * component in the system that knows more than one module, alongside
 * {@code planning.orchestration}. Section 10 of the architecture document and ADR 0011 both
 * put it here rather than inside identity, because none of the three rights is about one
 * context: erasure crosses every module that holds personal data, an export is the union of
 * what each holds, and the disclosure of past access is a fact about enrollments read on behalf
 * of a student.
 *
 * <p><strong>It never touches another module's tables.</strong> Each module implements
 * {@code ModuleDataRights} for its own data and this package sequences the calls inside one
 * transaction. The declaration below is what keeps that honest: the only modules named are
 * identity, whose account state the lifecycle moves, and educational, whose enrollments the
 * disclosure is derived from. The erasure itself names none of them — it asks Spring for every
 * implementation of the shared contract and runs them in order.
 */
@org.springframework.modulith.ApplicationModule(
        displayName = "Data Subject Rights",
        allowedDependencies = {
                "identity :: api", "educational :: api",
                "shared :: datarights", "shared :: web", "shared :: problem",
                "shared :: security"})
package br.com.sinapse.platform.datarights;
