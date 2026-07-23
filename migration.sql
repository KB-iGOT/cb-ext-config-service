-- 1. Add name column as nullable
ALTER TABLE form_configuration ADD COLUMN IF NOT EXISTS name VARCHAR(250);

-- 2. Populate name for existing records by concatenating portal, type, and subtype (e.g. portal_type_subtype)
UPDATE form_configuration SET name = CONCAT(portal, '_', type, '_', subtype) WHERE name IS NULL;

-- 3. Set name column as NOT NULL
ALTER TABLE form_configuration ALTER COLUMN name SET NOT NULL;
