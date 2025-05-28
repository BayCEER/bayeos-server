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
 
