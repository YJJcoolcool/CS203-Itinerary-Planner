ALTER TABLE location ADD COLUMN country_code varchar(2);
UPDATE location SET country_code = 'sg' WHERE country_code IS NULL;
CREATE INDEX idx_location_country_code ON location (country_code);
