/**
 * The wire contract of the Sinapse Core: what is sent to it and what comes back.
 *
 * <p><strong>Self-contained on purpose.</strong> Nothing here imports anything else in this
 * application — not another module's {@code api}, not {@code shared}, and no persistence
 * annotation. The optimiser lives in its own repository, and ADR 0002 makes this contract a
 * versioned artifact between the two; a package that referenced the backend's own types could
 * not be lifted out and published without dragging the backend with it.
 *
 * <p>That is why the enumerations here repeat values the curriculum and the learning record
 * also define. They are the same words for the same ideas, and they are declared twice because
 * a wire format that changed whenever an internal enum was renamed would not be a contract.
 *
 * <p>A change here is a breaking change between two repositories. {@code PlanRequest} carries
 * the version it was written against so that the core can refuse a payload it does not
 * understand rather than misread it.
 *
 * <p><strong>The shape on the wire is not only these records.</strong> It is also the
 * application's Jackson configuration: {@code spring.jackson.default-property-inclusion: non_null}
 * is why a null component is absent rather than written as {@code null}, and no record here says
 * so. A change to {@code spring.jackson.*} is a change of contract shape, with the same version
 * bump and re-sync as a change to a record (ADR 0016). The optimiser's mirror reproduces the
 * inclusion rule with {@code @JsonInclude(NON_NULL)} on its records, and aligning those records
 * with these must not remove it.
 */
@org.springframework.modulith.NamedInterface("contract")
package br.com.sinapse.platform.coreclient.contract;
