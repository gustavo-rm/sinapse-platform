# Prompt 09 — Catalogue curation importer

**Prerequisite:** prompt 02 complete and verified. Independent of prompts 03 to 08.
**Scope:** the command-line importer for the curated catalogue, the CSV format, validation,
dry-run diff and the textbook-order generator.
**Out of scope:** any administrative UI, any HTTP endpoint for curation, authentication for
curators, concurrent editing, and the optimisation core.

Read `CLAUDE.md` and `docs/adr/0014-curadoria-do-catalogo.md` before starting.

---

## Task

### 1. Files

```
catalog/<subject_code>/topics.csv
    code, name, position, effort_tier

catalog/<subject_code>/prerequisites.csv
    prerequisite, dependent, strength, provenance, source_reference
```

`prerequisite` and `dependent` use `subject_code:topic_code`, so edges may cross subjects.

`effort_tier` is `SHORT`, `STANDARD`, `LONG` or `EXTENDED`. It is an ordinal band, not
minutes.

The authoring surface is a spreadsheet; CSV is the exported source of truth. Do not invent a
richer format that a spreadsheet cannot produce.

### 2. Commands

```
catalog validate [--subject <code>]     parse and validate, touch nothing
catalog diff                            show what would change
catalog apply [--allow-topic-removal]   apply in one transaction
catalog seed-order --subject <code>     emit TEXTBOOK_ORDER edges into the CSV
```

Implement as a Spring Boot `ApplicationRunner` behind a dedicated profile, not as an HTTP
endpoint. It consumes `curriculum.api` and must not touch tables directly.

### 3. Validation — this is where the value is

Everything is validated **before** any write.

**Cycles.** Detect in memory and print the **full cycle path**, naming each topic:

```
Cycle detected:
  MED-ANAT:osteologia -> MED-ANAT:artrologia -> MED-ANAT:miologia -> MED-ANAT:osteologia
```

The database trigger raises on the first offending edge, which tells a curator nothing about
where to cut. That is precisely why in-memory validation exists here rather than relying on
the trigger — the trigger stays as the last line of defence.

Also validate: unknown topic references, duplicate codes, duplicate positions, self-edges,
invalid enum values, malformed rows. Report **all** errors in one pass with file and line
number. Failing on the first error makes curation of a large file miserable.

### 4. Apply semantics

Declarative and idempotent: the file is the desired state. Running twice changes nothing the
second time.

- **Topics are never deleted by default.** A topic missing from the file is reported, not
  removed: study sessions may reference it, and evidence must not disappear as a side effect
  of an import. `--allow-topic-removal` enables removal and must fail if any reference
  exists.
- **Edges may be removed.** They are curated, correctable data.
- **One transaction.** A partial import would leave the catalogue in a state no file
  describes.
- Record one `catalog_import` row with the git commit of the CSV files as `source_revision`
  and the counts of what changed.

### 5. seed-order writes to the file, not the database

The textbook-order generator emits `SOFT` edges with provenance `TEXTBOOK_ORDER` between
consecutive topics **into `prerequisites.csv`**.

It must never write those edges straight to the database. If it did, the file would stop
describing the real state and the whole versioning argument would collapse.

It must not overwrite an existing `CURATED` edge for the same pair, and running it twice
must produce no change.

### 6. Output

`diff` output is read by a curator, not a developer. Group by subject, name topics by code
and name, and state counts plainly: added, changed, removed, unchanged. Do not print stack
traces or entity class names.

---

## Definition of done

All items in `CLAUDE.md` §8, plus:

- A file with a three-edge cycle is rejected with the full path printed
- A file with five distinct errors reports all five in one run, with line numbers
- Applying the same file twice produces no change on the second run
- A topic missing from the file is reported and not removed without the flag
- Removal is refused when a study session references the topic
- `seed-order` run twice produces no change and never overwrites a `CURATED` edge
- A forced failure mid-apply rolls back completely
- A `catalog_import` row records the correct git revision and counts

Include a real example catalogue for one subject under `catalog/`, with at least thirty
edges, so the pipeline has something to exercise. Mark it clearly as example data.

## Report

In Portuguese: what was implemented, the observed output of the cycle detection and of the
multi-error report, and any point where ADR 0014 was ambiguous or wrong.

Work on `feature/1.0/catalog-curation`. Commit in Conventional Commits format, in English,
scoped to `curriculum`, following `CLAUDE.md` §4.
