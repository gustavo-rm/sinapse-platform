-- Makes consent_record.evidence nullable, so that it can actually be cleared.
--
-- ADR 0011 says the consent record survives an Article 18 erasure with `evidence`
-- sanitised, and V6 taught the trigger to permit exactly that change. The column
-- itself was still `not null`, which left the permission unusable: the trigger
-- would allow the update and the constraint would refuse it.
--
-- Only this column is relaxed. The account shell keeps its `not null` columns and
-- is emptied with values that identify nobody, because dropping `not null` from
-- `email` or `password_hash` to serve a terminal state would weaken every live row
-- in the table to describe the last thing that ever happens to one.
--
-- Nothing else changes. `evidence` remains unwritable outside the erasure
-- transaction, and the record itself remains undeletable in every circumstance:
-- it is the proof of the legal basis for treatment that already happened, and the
-- burden of that proof is the controller's.

alter table consent_record
    alter column evidence drop not null;
