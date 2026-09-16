-- Pack crafter balance (duplicate cards melted into points, 15 pts = 1 pack).
-- No data is touched: new column defaults to 0.
ALTER TABLE IF EXISTS users
    ADD COLUMN IF NOT EXISTS craft_points INTEGER NOT NULL DEFAULT 0 CHECK (craft_points >= 0);
