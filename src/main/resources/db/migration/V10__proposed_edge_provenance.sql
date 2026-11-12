-- PROPOSED: an edge asserted by a human and not yet reviewed by someone who
-- teaches the subject (ADR 0019).
--
-- Until now CURATED covered both an edge somebody wrote and an edge somebody
-- who teaches the subject checked, and the two must not share a label: the
-- curated graph is an input to the thesis ablation, and an unreviewed edge read
-- as curated would enter the experiment as reviewed data.
--
-- Only the check constraint changes. No row is rewritten here: an edge's
-- provenance is curated data, and the only path that changes curated data is
-- the catalogue importer, which records every change in catalog_import against
-- a revision of the files. A migration that relabelled rows would produce a
-- catalogue state that no import and no file describes. Existing CURATED rows
-- become PROPOSED when the importer applies files that say so.

alter table topic_prerequisite
    drop constraint ck_prerequisite_provenance;

alter table topic_prerequisite
    add constraint ck_prerequisite_provenance
        check (provenance in ('CURATED', 'PROPOSED', 'TEXTBOOK_ORDER', 'DERIVED'));
