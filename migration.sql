-- 1. Add name column as nullable
ALTER TABLE form_configuration ADD COLUMN IF NOT EXISTS name VARCHAR(250);

-- 2. Populate name for existing records by concatenating portal, type, and subtype (e.g. portal_type_subtype)
UPDATE form_configuration SET name = CONCAT(portal, '_', type, '_', subtype) WHERE name IS NULL;

-- 3. Set name column as NOT NULL
ALTER TABLE form_configuration ALTER COLUMN name SET NOT NULL;

-- 4. Dedupe existing duplicate names before the uniqueness constraint below can be added.
-- Only rows whose name is actually shared by more than one row (name_count > 1) are touched.
-- Within such a group: volunteer/public-role rows are ALWAYS renamed — including the first
-- occurrence (rn = 1) — with a suffix identifying their role; any other role only gets renamed
-- when it's a later duplicate (rn > 1), keeping the old "_dup" fallback for the first occurrence.
-- rn is appended in every branch so rows sharing both a name and a role still end up unique.
WITH ranked AS (
    SELECT id, name, criteria,
           ROW_NUMBER() OVER (PARTITION BY name ORDER BY id) AS rn,
           COUNT(*)      OVER (PARTITION BY name)            AS name_count
    FROM form_configuration
)
UPDATE form_configuration fc
SET name = fc.name || CASE
                          WHEN LOWER(ranked.criteria ->> 'role') = 'volunteer' THEN '_volunteer' || ranked.rn
                          WHEN LOWER(ranked.criteria ->> 'role') = 'public'    THEN '_public'    || ranked.rn
                          ELSE '_dup' || ranked.rn
                       END
FROM ranked
WHERE fc.id = ranked.id
  AND ranked.name_count > 1
  AND (
        ranked.rn > 1
        OR LOWER(ranked.criteria ->> 'role') IN ('volunteer', 'public')
      );

-- 5. name is now the CRUD identity (replacing type/subtype/portal) — enforce uniqueness.
ALTER TABLE form_configuration ADD CONSTRAINT uq_form_configuration_name UNIQUE (name);
