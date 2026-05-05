-- Lock the class hierarchy at plan time so that file-level generation
-- cannot drift on constructor signatures or extends/implements relations.
-- Stored as a JSON array of class declarations
-- ([{name, extends?, implements[], constructorParams[]}, ...]) and forwarded
-- verbatim to the implementer prompt as ground truth. Default '[]' keeps
-- pre-existing plans valid; the implementer treats an empty list as
-- "no upfront class contract" and falls back to old behavior.
ALTER TABLE plan_documents ADD COLUMN classes TEXT NOT NULL DEFAULT '[]';
