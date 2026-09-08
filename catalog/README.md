# `catalog/` — EXAMPLE DATA, NOT A CURATED CATALOGUE

Everything under this directory is **example data**. It exists so that the importer has
something real to run against, and so that a curator can see the shape of the files before
producing their own. **It has not been reviewed by anyone who teaches the subject, and no
result should be attributed to it.**

ADR 0014 recommends curating one pilot subject of around thirty edges before scaling. This is a
stand-in for that subject, not that subject.

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

## Commands

```
catalog validate [--subject=<code>]     parse and validate, touch nothing
catalog diff [--subject=<code>]         show what applying would change
catalog apply [--allow-topic-removal]   apply, in one transaction
catalog seed-order --subject=<code>     emit TEXTBOOK_ORDER edges into the CSV
```

Run them against a built jar with the `catalog` profile:

```
java -jar platform.jar --spring.profiles.active=catalog catalog diff
```
