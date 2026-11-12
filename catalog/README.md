# `catalog/` — EXAMPLE DATA, NOT A CURATED CATALOGUE

Everything under this directory is **example data**. It exists so that the importer has
something real to run against, and so that a curator can see the shape of the files before
producing their own. **It has not been reviewed by anyone who teaches the subject, and no
result should be attributed to it.**

ADR 0014 recommends curating one pilot subject of around thirty edges before scaling. This is a
stand-in for that subject, not that subject.

**Every edge here is `PROPOSED`**, and there is no `reviews.csv`: that is what "nobody who teaches
the subject has reviewed it" looks like in the files (ADR 0019). Until 2026-11-12 the same 35 edges
were labelled `CURATED`; they were relabelled because they were never reviewed, and nothing else in
them changed. Of their `source_reference` cells, 13 cite a textbook chapter, 17 hold a one-line
rationale rather than a source, and 5 are empty.

## The files

Two per subject, both exported from a spreadsheet:

```
catalog/<subject_code>/topics.csv          code, name, position, effort_tier
catalog/<subject_code>/prerequisites.csv   prerequisite, dependent, strength, provenance, source_reference
```

`prerequisite` and `dependent` are written `subject_code:topic_code`, so an edge may cross
subjects. `effort_tier` is an ordinal band — `SHORT`, `STANDARD`, `LONG`, `EXTENDED` — and never
minutes: what a band is worth in minutes is configuration, calibrated against observation.

A third file, `subject.csv`, is optional and holds one cell: the subject's display name. Without
it the subject is named after its code.

A fourth, `reviews.csv`, is optional and records who reviewed the subject's graph, and when:

```
catalog/<subject_code>/reviews.csv         reviewer, reviewed_on
```

An empty authoring template, with instructions written for whoever teaches the subject, is in
[`docs/curadoria/modelo-de-autoria/`](../docs/curadoria/modelo-de-autoria/INSTRUCOES.md).

## What the provenance levels mean

| `provenance` | Meaning | What the importer requires |
|---|---|---|
| `PROPOSED` | A person asserted it; nobody who teaches the subject has reviewed it yet. Every hand-written edge starts here | nothing more |
| `CURATED` | A person asserted it **and** someone who teaches the subject reviewed it | a non-empty `source_reference`, and at least one row in `reviews.csv` of the subject whose file declares the edge |
| `TEXTBOOK_ORDER` | Emitted by `catalog seed-order` from the subjects' curricular order; a claim about sequence, not about dependency | not written by hand |
| `DERIVED` | Produced by automatic derivation, a later line of research | not written by hand |

Only `CURATED` may be read as "curated graph" in a result. An edge added or changed after the last
review goes back to `PROPOSED` until the next review row; the tool cannot check that, the person
editing has to.

**On the wire to the core** contract 1.0 has three values, and `PROPOSED` travels as `CURATED`,
whose contract meaning is "asserted by a human curator". The distinction survives on the
platform's side, through the catalogue import a generation job points at (`docs/ai/STATE.md` K35).

## Commands

```
catalog validate [--subject=<code>]     parse and validate, touch nothing
catalog diff [--subject=<code>]         show what applying would change
catalog apply [--allow-topic-removal]   apply, in one transaction
catalog seed-order --subject=<code>     emit TEXTBOOK_ORDER edges into the CSV
catalog report [--subject=<code>]       what a reviewer needs to see, from the files
catalog shape --availability=... --goals=... [--start=...]
                                        the instance a student would produce, in the core's terms
```

Run them against a built jar with the `catalog` profile:

```
java -jar platform.jar --spring.profiles.active=catalog catalog diff
```
