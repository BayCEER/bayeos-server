--
-- Migrate to flyway 
--
DO
$do$
BEGIN
	BEGIN	
	CREATE TABLE schema_version (
		installed_rank integer NOT NULL,
		version character varying(50),
		description character varying(200) NOT NULL,
		type character varying(20) NOT NULL,
		script character varying(1000) NOT NULL,
		checksum integer,
		installed_by character varying(100) NOT NULL,
		installed_on timestamp without time zone DEFAULT now() NOT NULL,
		execution_time integer NOT NULL,
		success boolean NOT NULL
	);
	ALTER TABLE schema_version OWNER TO bayeos;
	ALTER TABLE ONLY schema_version ADD CONSTRAINT schema_version_pk PRIMARY KEY (installed_rank);
	INSERT INTO schema_version VALUES (1, '1.99', 'bayeos', 'SQL', 'V1.99__bayeos.sql', -459484018, 'bayeos', now(), 1667, true);
	CREATE INDEX schema_version_s_idx ON schema_version USING btree (success);
	DROP TABLE IF EXISTS databasechangelog;
	DROP TABLE IF EXISTS databasechangeloglock;
	EXCEPTION
		WHEN duplicate_table THEN RAISE NOTICE 'Table schema_version already exists';
	END;
END
$do$;
