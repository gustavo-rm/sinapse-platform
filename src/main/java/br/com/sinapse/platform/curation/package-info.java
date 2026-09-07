/**
 * The command-line importer for the curated catalogue.
 *
 * <p><strong>Not a bounded context, and not part of the running product.</strong> It is an
 * administrative tool that happens to live in this repository, behind its own profile, with no
 * HTTP surface at all. ADR 0014 decided that: authoring happens in a spreadsheet, the source of
 * truth is CSV committed to git, and this applies it.
 *
 * <p>The argument for that arrangement is provenance, not convenience. The curated graph and the
 * effort bands are inputs to the thesis ablation, and curation done through a screen that writes
 * straight to the database produces no history, no diff, and no way to say which state of the
 * catalogue produced which result. In git, all three are free.
 *
 * <p><strong>It consumes {@code curriculum :: api} and nothing else.</strong> The declaration
 * below is what makes that true rather than intended: it cannot reach a repository, a table, or
 * any other module — so every rule the curriculum module enforces about its own data holds here
 * too, including the acyclicity trigger that stands as the last line of defence behind this
 * tool's own validation.
 */
@org.springframework.modulith.ApplicationModule(
        displayName = "Catalogue Curation",
        allowedDependencies = {"curriculum :: api"})
package br.com.sinapse.platform.curation;
