delete from objekt where id_art in (select id from art_objekt where uname like 'mess_ort');
delete from objekt where de like 'Objektart: Messort';
delete from art_objekt where uname like 'mess_ort' ;

DROP VIEW v_mess_ort;
DROP FUNCTION copy_messorte(int4, int4);
drop function get_messort(int4);
drop function copy_messung(integer, integer);


DROP TABLE messorte;
drop TABLE coordinate_ref_sys;


ALTER TABLE messungen ADD lat float4 NULL;
ALTER TABLE messungen ADD lon float4 NULL;
ALTER TABLE messungen ADD alt float4 NULL;
alter table messungen drop column id_ora;


CREATE OR REPLACE FUNCTION bayeos.copy_messungen(integer, integer)
 RETURNS void
 LANGUAGE plpgsql
AS $function$
declare
 p_id alias for $1;
 p_id_new alias for $2;
begin
 insert into messungen (id,bezeichnung,beschreibung,aufloesung,tabelle,id_intervaltyp, lat, lon, alt)
 select p_id_new,bezeichnung, beschreibung, aufloesung,tabelle,id_intervaltyp, lat,lon, alt from messungen where id = p_id;
 return;
end;$function$;

drop view v_messung_ordner;

CREATE OR REPLACE VIEW bayeos.v_messung_ordner
AS SELECT vo.id,
    vo.id_super,
    vo.objektart,
    vo.id_art,
    vo.id_cbenutzer,
    vo.cbenutzer,
    vo.ctime,
    vo.id_ubenutzer,
    vo.ubenutzer,
    vo.utime,
    vo.dtime,
    vo.de,
    vo.en,
    vo.plan_start,
    vo.plan_end,
    vo.rec_start,
    vo.rec_end,
    vo.inherit_perm,
    d.bezeichnung,
    d.beschreibung,
    d.aufloesung,
    d.id_intervaltyp,
    d.fk_timezone_id,
    d.lat, 
    d.lon,
    d.alt
   FROM v_objekt vo,
    messungen d
  WHERE vo.id = d.id AND vo.id_art = 117660;
  
  
CREATE OR REPLACE VIEW bayeos.v_messung_massendaten
AS SELECT vo.id,
    vo.id_super,
    vo.objektart,
    vo.id_art,
    vo.id_cbenutzer,
    vo.cbenutzer,
    vo.ctime,
    vo.id_ubenutzer,
    vo.ubenutzer,
    vo.utime,
    vo.dtime,
    vo.de,
    vo.en,
    vo.plan_start,
    vo.plan_end,
    vo.rec_start,
    vo.rec_end,
    vo.inherit_perm,
    d.bezeichnung,
    d.beschreibung,
    d.aufloesung,
    d.id_intervaltyp,
    d.fk_timezone_id,
    d.lat, 
    d.lon,
    d.alt
   FROM v_objekt vo,
    messungen d
  WHERE vo.id = d.id AND vo.id_art = 117661;
  
  
  
DROP FUNCTION IF EXISTS bayeos.get_objekt_baum(int4, text, int4, bool, timestamptz, timestamptz);


create type objekt_with_location as (
	id integer,
	id_super integer,
	id_art integer,
	plan_start timestamp with time zone,
	plan_end timestamp with time zone,
	rec_start timestamp with time zone,
	rec_end timestamp with time zone,
	name text,
	path text,
	art text,
	lon float,
	lat float,
    alt float
);


create or replace function get_objekt_tree_with_location(
_id integer,
_id_benutzer integer,
_path text,
_lon float,
_lat float,
_alt float,
_max_depth integer,
_active boolean,
_von timestamp with time zone,
_bis timestamp with time zone) 
returns setof objekt_with_location
    language plpgsql
    as $$
declare
  rec1 record;
  rec2 record;
begin
for rec1 in
select
	o.id,
	o.id_super,
	o.id_art,
	o.plan_start,
	o.plan_end,
	o.rec_start,
	o.rec_end,
	case
		when o.de is null
		or o.de = '' then o.en
		else o.de
	end::text as name,
	_path::text as path,
	ao.uname::text as art,
    coalesce(m.lon,_lon) lon,
    coalesce(m.lat,_lat) lat,
	coalesce(m.alt,_alt) alt	
from
	objekt o left outer join messungen m on m.id = o.id,
	art_objekt ao
where
	check_read(o.id,_id_benutzer)
	and ao.id = o.id_art
	and o.id_super = _id
	and (not _active or isactive(o.plan_start, o.plan_end))
	and ((o.rec_start, o.rec_end) overlaps (coalesce(_von,o.rec_start),coalesce(_bis,o.rec_end)) 
	or (_von is null and _bis is null))
order by
	o.de loop
    return next rec1;

if rec1.art not in ('messung', 'messung_massendaten', 'messung_labordaten', 'data_column')
and _max_depth != 0 then
      for rec2 in
select
	*
from
	get_objekt_tree_with_location(rec1.id,_id_benutzer, _path || replace(rec1.name,'/',E'\\/')|| '/',	rec1.lon, rec1.lat ,rec1.alt,
	_max_depth-1,_active,_von,_bis) loop
	return next rec2;
end loop;
end if;
end loop;

return;
end
$$;
