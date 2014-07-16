-- BayEOS Core Schema Version 1.7.0
-- Oliver Archner 


SET statement_timeout = 0;
SET client_encoding = 'UTF8';
SET standard_conforming_strings = off;
SET check_function_bodies = false;
SET client_min_messages = warning;
SET escape_string_warning = off;


CREATE SCHEMA bayeos;

ALTER SCHEMA bayeos OWNER TO bayeos;

set search_path to bayeos,public;

CREATE TYPE timeperiod AS (
	von timestamp with time zone,
	bis timestamp with time zone
);


ALTER TYPE bayeos.timeperiod OWNER TO bayeos;

--
-- Name: TYPE timeperiod; Type: COMMENT; Schema: bayeos; Owner: bayeos
--

COMMENT ON TYPE timeperiod IS 'Zeitspanne mit von und bis';


--
-- Name: aggr_all_childs(integer); Type: FUNCTION; Schema: bayeos; Owner: bayeos
--

CREATE FUNCTION aggr_all_childs(integer) RETURNS boolean
    LANGUAGE plpgsql
    AS $_$declare
bol boolean;
rec record;
begin
for rec in select m.id,m.aufloesung,m.id_intervaltyp from messungen m, (select id from get_child_objekte($1,117661)) o where o.id = m.id
loop 
select into bol aggr_full(rec.id,rec.aufloesung,rec.id_intervaltyp,true);
end loop;
return(true);
end;$_$;


ALTER FUNCTION bayeos.aggr_all_childs(integer) OWNER TO bayeos;

--
-- Name: aggr_calculate(); Type: FUNCTION; Schema: bayeos; Owner: bayeos
--

CREATE FUNCTION aggr_calculate() RETURNS boolean
    LANGUAGE plpgsql
    AS $$ declare
  val_last int4;
  val_cur  int4;
  bol boolean;
  begin 
--   set time zone 'GMT-1';
   select into val_last value::int from sys_variablen where name like 'aggr_last_his_massendaten_id' ;
   select into val_cur nextval('his_massendaten_id');
   if (not found) then 
    raise exception 'Variable definition not found in sys_variable.';
   end if;
   if (val_last is null) then
    -- Full Aggregation of all 
    select into bol aggr_full();
   else
    -- Delta Mode
     select into bol aggr_inkrement(val_last);   
   end if;
   update sys_variablen set value = CAST(val_cur as varchar) where name like 'aggr_last_his_massendaten_id';
  return(true);
  end;
$$;


ALTER FUNCTION bayeos.aggr_calculate() OWNER TO bayeos;

--
-- Name: aggr_full(); Type: FUNCTION; Schema: bayeos; Owner: bayeos
--

CREATE FUNCTION aggr_full() RETURNS boolean
    LANGUAGE plpgsql
    AS $$ declare
   rec record;
   bol boolean;
   i int4 := 0;
   curr_tz text := '';
 begin
raise notice 'Starting aggregation in mode full';
for rec in select m.*,tz.name as timezone from v_messung_massendaten m, timezone tz where m.id_super != 117846 and m.fk_timezone_id=tz.id order by fk_timezone_id
   loop
    if rec.timezone!=curr_tz then
curr_tz:=rec.timezone;
--raise notice 'Setting time zone to :%',rec.timezone;
        execute 'set time zone '''||rec.timezone||'''';
     end if;
    i := i+1;
    raise notice 'i:% Messung:%',i,rec.id;
    select into bol aggr_full(rec.id,rec.aufloesung,rec.id_intervaltyp,true);
   end loop;
   return(true);
  end;
$$;


ALTER FUNCTION bayeos.aggr_full() OWNER TO bayeos;

--
-- Name: aggr_full(integer); Type: FUNCTION; Schema: bayeos; Owner: bayeos
--

CREATE FUNCTION aggr_full(_id integer) RETURNS boolean
    LANGUAGE plpgsql
    AS $$ declare
   bol boolean;   
  begin
    select into bol aggr_full(id,aufloesung,id_intervaltyp,true) from messungen where id = _id;   
   return(bol);
  end;
$$;


ALTER FUNCTION bayeos.aggr_full(_id integer) OWNER TO bayeos;

--
-- Name: aggr_full(integer, integer, integer, boolean); Type: FUNCTION; Schema: bayeos; Owner: bayeos
--

CREATE FUNCTION aggr_full(integer, integer, integer, boolean) RETURNS boolean
    LANGUAGE plpgsql
    AS $_$ declare
  irec record;
  frec record;
  sql varchar;
  dest_tab varchar;
  datetr varchar;
  src_tab varchar;
  svon varchar;
  rows int;
  begin
    for irec in select id, name, intervall, get_min_intervall($2) as i_min from aggr_intervall where intervall>=get_min_intervall($2) order by intervall asc
    loop
        raise info ' Intervall:% MinIntervall:%',irec.intervall,irec.i_min;
        for frec in select id, name from aggr_funktion order by id asc
	--for frec in select id, name from aggr_funktion where id = 6 
        loop
        raise info '  Function:%',frec.name;
        select into dest_tab get_aggr_table(frec.id,irec.id);

        -- Lschen falls Flag gesetzt
        if $4 then
          sql := 'delete from ' || dest_tab || ' where id = ' || $1 || ';';
          execute sql;
        end if;

        -- Timeshift bei Massendaten mit Endzeitpunkt nur auf erster Stufe
        if (($3 = 2) and (irec.intervall = irec.i_min)) then
           svon := 'von - interval ' || quote_literal($2 || ' seconds') || '::interval';
        else
           svon := 'von';
        end if;
        datetr := 'date_trunc(' || quote_literal(irec.intervall) || '::interval,' || svon || ')';

sql := 'insert into ' || dest_tab || ' (id,von,wert,counts) ' ||  'select ' ||
$1 || ',' || datetr  || ',' || frec.name || '(wert), count(wert) from ' ;
         if irec.intervall = irec.i_min then
           src_tab := 'massendaten';
           sql:= sql || src_tab || ' where id = ' || $1 || ' and  status in (0,1,2)';
         else
           select into src_tab get_aggr_table(frec.id,get_aggr_src_id(irec.intervall));
           sql:= sql || src_tab || ' where id = ' || $1 ;
         end if;
         sql := sql || ' and von = von group by ' || datetr || ';';
         raise info '%', sql;
         execute sql;
         GET DIAGNOSTICS rows = ROW_COUNT;
         raise info 'Rows inserted:%',rows;
        end loop;
    end loop;
   return(true);
  end;
$_$;


ALTER FUNCTION bayeos.aggr_full(integer, integer, integer, boolean) OWNER TO bayeos;

--
-- Name: aggr_funktion(integer, timestamp with time zone, interval, interval, character varying); Type: FUNCTION; Schema: bayeos; Owner: bayeos
--

CREATE FUNCTION aggr_funktion(integer, timestamp with time zone, interval, interval, character varying) RETURNS boolean
    LANGUAGE plpgsql
    AS $_$ declare
  mesid alias for $1;
  von alias for $2;
  i_min alias for $3; 
  i_cur alias for $4;
  i_name alias for $5;
  rec record;
  dest_tab varchar(100);
  src_tab varchar(100);
  sql varchar(500);
  ts varchar(100);
  begin
  -- Loop durch alle Funktionen
  for rec in select id, name from aggr_funktion
  loop 
   raise debug '  Function:%',rec.name;
   raise debug 'rec.id:%,i_cur:%,i_min:%',rec.id,i_cur,i_min;
   ts := quote_literal(von) || '::timestamp with time zone';
   select into dest_tab get_aggr_table(rec.id,id) from aggr_intervall where intervall=i_cur;
   if dest_tab is null then 
       raise exception 'No destination table found';
   end if;
   sql := 'delete from ' || dest_tab || ' where id = ' || mesid || ' and von = ' || ts || ';'; 
   raise debug 'SQL:%',sql;	
   execute sql;
   sql := 'insert into ' || dest_tab || ' (id,von,wert,counts) ' || 
          'select ' || mesid || ',' || ts  || ',' || rec.name || '(wert), count(id) from ';
   if i_min = i_cur then 
     sql := sql || 'massendaten where status in (0,1,2) and id = ' || mesid;
   else
     select into src_tab get_aggr_table(rec.id,get_aggr_src_id(i_cur));
     if src_tab is null then 
       raise exception 'No source table found';
     end if;
     sql := sql || src_tab || ' where id = ' || mesid;     
   end if;
   sql := sql || ' and von >= ' || ts || ' and von < (' || ts || ' + ' || quote_literal(i_cur) || '::interval)';
   raise debug 'SQL:%',sql;	
   execute sql; 
  end loop;
  return(true);
  end;
$_$;


ALTER FUNCTION bayeos.aggr_funktion(integer, timestamp with time zone, interval, interval, character varying) OWNER TO bayeos;

--
-- Name: aggr_init(); Type: FUNCTION; Schema: bayeos; Owner: bayeos
--

CREATE FUNCTION aggr_init() RETURNS boolean
    LANGUAGE plpgsql
    AS $$ 
declare
bol boolean;
begin
select into bol drop_aggr_tables();
update sys_variablen set value = null where name = 'aggr_last_his_massendaten_id';
select into bol create_aggr_tables();
select into bol aggr_calculate();
return(bol);
end;
$$;


ALTER FUNCTION bayeos.aggr_init() OWNER TO bayeos;

--
-- Name: aggr_inkrement(integer); Type: FUNCTION; Schema: bayeos; Owner: bayeos
--

CREATE FUNCTION aggr_inkrement(his_id integer) RETURNS boolean
    LANGUAGE plpgsql
    AS $$ declare
  rec record;
  bol boolean;
  begin
  for rec in select id, name, intervall from aggr_intervall order by intervall asc
  loop
   raise debug 'Interval:%',rec.name;
   select into bol aggr_intervall(rec.intervall,rec.id,his_id);
  end loop;
  return(true);
  end;
$$;


ALTER FUNCTION bayeos.aggr_inkrement(his_id integer) OWNER TO bayeos;

--
-- Name: aggr_intervall(interval, integer, integer); Type: FUNCTION; Schema: bayeos; Owner: bayeos
--

CREATE FUNCTION aggr_intervall(_intervall interval, _id_intervall integer, _his_id integer) RETURNS boolean
    LANGUAGE plpgsql
    AS $$ declare
  mrec record;
  hrec record;
  rec record;
  bol boolean;
  curr_tz text:='';
  begin
  for hrec in select tz.name as timezone, his.id, 
  case when typ.bezeichnung like 'end' then date_trunc(_intervall,his.von - get_intervall(mes.aufloesung)) else date_trunc(_intervall,his.von) end as anchor  
  from his_massendaten his, messungen mes, intervaltyp typ, timezone tz where mes.fk_timezone_id=tz.id and mes.id = his.id and typ.id = mes.id_intervaltyp and his.his_id > _his_id group by 1,2,3 order by 1,2,3
    loop
     if hrec.timezone!=curr_tz then
curr_tz:=hrec.timezone;
--raise notice 'Setting time zone to :%',hrec.timezone;
        execute 'set time zone '''||hrec.timezone||'''';
     end if;
     
 select into mrec get_min_intervall(mes.aufloesung) as min_intervall, 
     case when typ.bezeichnung like 'end' then get_intervall(mes.aufloesung) 
     else '0 min'::interval end as shift 
     from messungen mes, intervaltyp typ where mes.id = hrec.id and typ.id = mes.id_intervaltyp;
 
     for rec in select id as fid, name as fname, get_aggr_table(id,_id_intervall) as dtable , get_aggr_table(id,get_aggr_src_id(_intervall)) as stable from aggr_funktion 
     loop
       if _intervall > mrec.min_intervall then
        select into bol aggr_row(rec.stable,rec.dtable,rec.fname,hrec.id,hrec.anchor,_intervall,mrec.shift);
       elsif _intervall = mrec.min_intervall then
select into bol aggr_row(null,rec.dtable,rec.fname,hrec.id,hrec.anchor,_intervall,mrec.shift);
       end if;
     end loop;
    
end loop;
  return(true);
  end;
$$;


ALTER FUNCTION bayeos.aggr_intervall(_intervall interval, _id_intervall integer, _his_id integer) OWNER TO bayeos;

--
-- Name: aggr_row(character varying, character varying, character varying, integer, timestamp with time zone, interval, interval); Type: FUNCTION; Schema: bayeos; Owner: bayeos
--

CREATE FUNCTION aggr_row(character varying, character varying, character varying, integer, timestamp with time zone, interval, interval) RETURNS boolean
    LANGUAGE plpgsql
    AS $_$ declare
  stable alias for $1;
  dtable alias for $2;
  fname alias for $3;
  id alias for $4;
  anchor alias for $5;
  intervall alias for $6;
  shift alias for $7;

  rec record;
  sql varchar(500);
  t varchar(100);
  ts varchar(100);
  begin
   -- raise info 'stable:% dtable:% fname:% id:% anchor:% intervall:% shift:% ',stable,dtable,fname,id,anchor,intervall,shift;
   t := quote_literal(anchor) || '::timestamp with time zone';
   sql := 'delete from ' || dtable || ' where id = ' || id || ' and von = ' || t || ';';
   execute sql;
   sql := 'insert into ' || dtable || ' (id,von,wert,counts) ' ||
          'select ' || id || ',' || t  || ',' || fname || '(wert), count(id) from ';
   if stable is null then
     ts := quote_literal(anchor+shift) || '::timestamp with time zone';
     sql := sql || 'massendaten where status in (0,1,2) and id = ' || id;
     sql := sql || ' and von >= ' || ts || ' and von < (' || ts || ' + ' || quote_literal(intervall) || '::interval)';
   else
     sql := sql || stable || ' where id = ' || id;
     sql := sql || ' and von >= ' || t || ' and von < (' || t || ' + ' || quote_literal(intervall) || '::interval)';
   end if;
   -- raise info 'sql:%',sql;
   execute sql;
   return(true);
  end;
$_$;


ALTER FUNCTION bayeos.aggr_row(character varying, character varying, character varying, integer, timestamp with time zone, interval, interval) OWNER TO bayeos;

--
-- Name: arch_his(); Type: FUNCTION; Schema: bayeos; Owner: bayeos
--

CREATE FUNCTION arch_his() RETURNS boolean
    LANGUAGE plpgsql
    AS $$declare
 rec record;
 bol bool;
 path text;
 begin  
    select into path value from messungen.sys_variablen where name like 'arch_path';
    if not found then
     raise exception 'Archivierungspfad nicht in sys_variablen gefunden.';
    end if;

    for rec in select tablename from pg_tables where tablename like 'his_%' 
    loop
      select into bol messungen.arch_his_tab(rec.tablename, path);
    end loop;
  return(true);
 end;
$$;


ALTER FUNCTION bayeos.arch_his() OWNER TO bayeos;

--
-- Name: arch_his_tab(text, text); Type: FUNCTION; Schema: bayeos; Owner: bayeos
--

CREATE FUNCTION arch_his_tab(text, text) RETURNS boolean
    LANGUAGE plpgsql
    AS $_$declare
 seq int4;
 filename text;
 filepath text;
 begin
    select into seq nextval('messungen.' || $1 || '_id');    
    filename :=  $1 || '_' || seq || '.cp';
    filepath := $2 || '/' || filename ;
    raise notice 'Archiviere % nach %.',$1, filepath;  
    execute 'copy messungen.' || $1 || ' to ' || quote_literal(filepath) ;
    execute 'insert into messungen.arch_his_log (name,min_id,max_id,min_datum,max_datum,counts)  ' ||
    'select ' || quote_literal(filename) || ', min(his_id) ,max(his_id), min(his_datum), max(his_datum), count(*) from messungen.' || $1;  
    execute 'delete from messungen.' || $1 || ' where his_id < ' || seq;
    return(true);
 end;
$_$;


ALTER FUNCTION bayeos.arch_his_tab(text, text) OWNER TO bayeos;

--
-- Name: check_exec(integer, integer); Type: FUNCTION; Schema: bayeos; Owner: bayeos
--

CREATE FUNCTION check_exec(integer, integer) RETURNS boolean
    LANGUAGE plpgsql
    AS $_$declare
rec record;
begin

select into rec check_perm($1,$2,false,false,true,false);
return rec.check_perm;

end;$_$;


ALTER FUNCTION bayeos.check_exec(integer, integer) OWNER TO bayeos;

CREATE FUNCTION check_perm(integer, integer, boolean, boolean, boolean, boolean) RETURNS boolean
    LANGUAGE plpgsql
    AS $_$declare
rec record; 
i_id_obj alias for $1;
i_id_benutzer alias for $2;
i_read alias for $3;
i_write alias for $4;
i_exec alias for $5;
i_inherit alias for $6;
begin

/* ungültige ids */ 
/* O. A. 4-2011  */
select into rec id from objekt where id =$1;
if not found then return false; end if;

/*Administratoren*/
select into rec id from benutzer 
where id=i_id_benutzer and admin;
        if found then return true; end if;

/*Zugriff Benutzer*/
        select into rec * from zugriff
where id_obj=$1 and id_benutzer=$2
and (not i_read or read) 
and (not i_write or write) 
and (not i_exec or exec) 
and (not i_inherit or inherit) ;
if found then return true; end if;

/*Zugriff Gruppen*/
for rec in select 
check_perm($1,id_gruppe,i_read,i_write,i_exec,i_inherit)
from benutzer_gr where id_benutzer=$2 loop
  if rec.check_perm then return true; end if;
end loop;

/*Parent-Objekt*/
select into rec check_perm(id_super,$2,i_read,i_write,i_exec,true)
from objekt where id=$1 and id_super>=0 and id_super!=$1 and inherit_perm;

if found
then 
  return rec.check_perm;
else
  return false;
end if;

end;$_$;


ALTER FUNCTION bayeos.check_perm(integer, integer, boolean, boolean, boolean, boolean) OWNER TO bayeos;

--
-- Name: check_read(integer, integer); Type: FUNCTION; Schema: bayeos; Owner: bayeos
--

CREATE FUNCTION check_read(integer, integer) RETURNS boolean
    LANGUAGE plpgsql
    AS $_$declare
rec record;
begin
select into rec check_perm($1,$2,true,false,false,false);
return rec.check_perm;
end;$_$;


ALTER FUNCTION bayeos.check_read(integer, integer) OWNER TO bayeos;

--
-- Name: check_rwx(integer, integer); Type: FUNCTION; Schema: bayeos; Owner: bayeos
--

CREATE FUNCTION check_rwx(integer, integer) RETURNS boolean
    LANGUAGE plpgsql
    AS $_$declare
rec record;
begin

select into rec check_perm($1,$2,true,true,true,false);
return rec.check_perm;

end;$_$;


ALTER FUNCTION bayeos.check_rwx(integer, integer) OWNER TO bayeos;

--
-- Name: check_write(integer, integer); Type: FUNCTION; Schema: bayeos; Owner: bayeos
--

CREATE FUNCTION check_write(integer, integer) RETURNS boolean
    LANGUAGE plpgsql
    AS $_$declare
rec record; 
begin
select into rec check_perm($1,$2,false,true,false,false);
return rec.check_perm;

end;$_$;


ALTER FUNCTION bayeos.check_write(integer, integer) OWNER TO bayeos;

--
-- Name: cluster_aggr_tables(); Type: FUNCTION; Schema: bayeos; Owner: bayeos
--

CREATE FUNCTION cluster_aggr_tables() RETURNS boolean
    LANGUAGE plpgsql
    AS $$ declare
  rec record;
  tab varchar(300);
  begin
  for rec in select f.name as fname,i.name as iname from aggr_funktion f, aggr_intervall i
  loop
   tab := 'aggr_'  || rec.fname || '_' || rec.iname ;
   execute 'cluster idx_' || tab || '_id_von on ' || tab || ';';
  end loop;
  return true;
  end;
$$;


ALTER FUNCTION bayeos.cluster_aggr_tables() OWNER TO bayeos;

--
-- Name: copy_child(integer, integer); Type: FUNCTION; Schema: bayeos; Owner: bayeos
--

CREATE FUNCTION copy_child(integer, integer) RETURNS void
    LANGUAGE plpgsql
    AS $_$
declare
 p_id alias for $1;
 p_id_super alias for $2;
 rec record;
begin
 raise notice 'copy_child p_id :% p_id_super:%',p_id,p_id_super;
 for rec in select id from objekt where id_super = p_id
 loop
  perform copy_child(rec.id,copy_objekt(rec.id,p_id_super));
 end loop;
 return;
end;$_$;


ALTER FUNCTION bayeos.copy_child(integer, integer) OWNER TO bayeos;

--
-- Name: copy_einbau(integer, integer); Type: FUNCTION; Schema: bayeos; Owner: bayeos
--

CREATE FUNCTION copy_einbau(integer, integer) RETURNS void
    LANGUAGE plpgsql
    AS $_$
declare
 p_id alias for $1;
 p_id_new alias for $2;
begin
 insert into einbau (id, bezeichnung,hoehe)
 select p_id_new,bezeichnung,hoehe from einbau where id = p_id;
 return;
end;$_$;


ALTER FUNCTION bayeos.copy_einbau(integer, integer) OWNER TO bayeos;

--
-- Name: copy_einheiten(integer, integer); Type: FUNCTION; Schema: bayeos; Owner: bayeos
--

CREATE FUNCTION copy_einheiten(integer, integer) RETURNS void
    LANGUAGE plpgsql
    AS $_$
declare
 p_id alias for $1;
 p_id_new alias for $2;
begin
 insert into einheiten (id,bezeichnung,beschreibung)
 select p_id_new,bezeichnung,beschreibung from einheiten where id = p_id;
 return;
end;$_$;


ALTER FUNCTION bayeos.copy_einheiten(integer, integer) OWNER TO bayeos;

--
-- Name: copy_geraete(integer, integer); Type: FUNCTION; Schema: bayeos; Owner: bayeos
--

CREATE FUNCTION copy_geraete(integer, integer) RETURNS void
    LANGUAGE plpgsql
    AS $_$
declare
 p_id alias for $1;
 p_id_new alias for $2;
begin
 insert into geraete (id, bezeichnung, beschreibung, seriennr)
 select p_id_new, bezeichnung, beschreibung, seriennr from geraete where id = p_id;
 return;
end;$_$;


ALTER FUNCTION bayeos.copy_geraete(integer, integer) OWNER TO bayeos;

--
-- Name: copy_kompartiment(integer, integer); Type: FUNCTION; Schema: bayeos; Owner: bayeos
--

CREATE FUNCTION copy_kompartiment(integer, integer) RETURNS void
    LANGUAGE plpgsql
    AS $_$
declare
 p_id alias for $1;
 p_id_new alias for $2;
begin
 insert into kompartimente (id, bezeichnung,beschreibung)
 select p_id_new, bezeichnung, beschreibung from kompartimente where id = p_id;
 return;
end;$_$;


ALTER FUNCTION bayeos.copy_kompartiment(integer, integer) OWNER TO bayeos;

--
-- Name: copy_messorte(integer, integer); Type: FUNCTION; Schema: bayeos; Owner: bayeos
--

CREATE FUNCTION copy_messorte(integer, integer) RETURNS void
    LANGUAGE plpgsql
    AS $_$
declare
 p_id alias for $1;
 p_id_new alias for $2;
begin
 insert into messorte (id, bezeichnung,beschreibung,x,y,z,fk_crs_id)
 select p_id_new, bezeichnung, beschreibung,x,y,z,fk_crs_id from messorte where id = p_id;
 return;
end;$_$;


ALTER FUNCTION bayeos.copy_messorte(integer, integer) OWNER TO bayeos;

--
-- Name: copy_messung(integer, integer); Type: FUNCTION; Schema: bayeos; Owner: bayeos
--

CREATE FUNCTION copy_messung(integer, integer) RETURNS integer
    LANGUAGE plpgsql
    AS $_$declare
   id_org alias for $1;
   id_super_dest alias for $2; -- Messungsordner
   rec record;
   det record;

 begin
  if id_org is null or id_super_dest is null then 
    raise exception 'Missing argument';
  end if;

  -- Copy Objekt  
  select into rec  nextval('objekt_id') as id,id_super_dest as id_super,id_art, de,en,public_read, public_write, public_exec, public_childs, inherit_perm from objekt where id = id_org;
  insert into objekt (id, id_super, id_art, de, en, public_read, public_write, public_exec, public_childs , inherit_perm) values 
  (rec.id, rec.id_super,rec.id_art,rec.de,rec.en,rec.public_read, rec.public_write,rec.public_exec, rec.public_childs, rec.inherit_perm);

  -- Copy Details
 insert into messungen (id, bezeichnung, beschreibung, aufloesung, tabelle, hasdata) select rec.id, bezeichnung, beschreibung, aufloesung, tabelle, hasdata from messungen where id = id_org;

  -- Copy Verweise
 insert into verweis (id_von, id_auf, von, bis, anteil) select id_von, rec.id , von, bis, anteil from verweis where id_auf = id_org;


 -- Copy Rechte
 insert into zugriff (id_obj, id_benutzer, read, write, exec, inherit) select rec.id , id_benutzer, read, write, exec, inherit from zugriff where id_obj = id_org;

 return(rec.id);
 end;
$_$;


ALTER FUNCTION bayeos.copy_messung(integer, integer) OWNER TO bayeos;

--
-- Name: copy_messungen(integer, integer); Type: FUNCTION; Schema: bayeos; Owner: bayeos
--

CREATE FUNCTION copy_messungen(integer, integer) RETURNS void
    LANGUAGE plpgsql
    AS $_$
declare
 p_id alias for $1;
 p_id_new alias for $2;
begin
 insert into messungen (id,id_ora,bezeichnung,beschreibung,aufloesung,tabelle,id_intervaltyp)
 select p_id_new,id_ora,bezeichnung, beschreibung, aufloesung,tabelle,id_intervaltyp from messungen where id = p_id;
 return;
end;$_$;


ALTER FUNCTION bayeos.copy_messungen(integer, integer) OWNER TO bayeos;

--
-- Name: copy_messziele(integer, integer); Type: FUNCTION; Schema: bayeos; Owner: bayeos
--

CREATE FUNCTION copy_messziele(integer, integer) RETURNS void
    LANGUAGE plpgsql
    AS $_$
declare
 p_id alias for $1;
 p_id_new alias for $2;
begin
 insert into messziele (id, bezeichnung,beschreibung,formel)
 select p_id_new, bezeichnung, beschreibung, formel from messziele where id = p_id;
 return;
end;$_$;


ALTER FUNCTION bayeos.copy_messziele(integer, integer) OWNER TO bayeos;

--
-- Name: copy_objekt(integer, integer); Type: FUNCTION; Schema: bayeos; Owner: bayeos
--

CREATE FUNCTION copy_objekt(integer, integer) RETURNS integer
    LANGUAGE plpgsql
    AS $_$
declare
 p_id alias for $1;
 p_id_super alias for $2;
 rec record;
 s varchar;
begin
 select into rec nextval('objekt_id')::int4 as id_seq, a.detail_table from objekt o, art_objekt a where o.id = p_id
and a.id = o.id_art;
-- objekt
 insert into objekt (id,id_super,id_art,id_cbenutzer,de, en, public_read,public_write,public_exec,inherit_perm) select
rec.id_seq,p_id_super,id_art,get_userid(),de,en,public_read,public_write,public_exec,inherit_perm
from objekt where id =p_id;
-- details
s := 'select copy_' || rec.detail_table || '(' || p_id || ',' ||
rec.id_seq ||  ');';
execute s;
 -- verweis extern
 insert into verweis_extern (id_von,id_auf,von,bis)
select id_von,rec.id_seq,von,bis from verweis_extern where id_auf =p_id;
 -- verweis
 insert into verweis (id_von,id_auf,von,bis,anteil) select id_von,rec.id_seq,von,bis,anteil from verweis where
id_auf = p_id;
 -- zugriff
 insert into zugriff (id_obj,id_benutzer,read,write,exec,inherit) values (rec.id_seq,get_userid(),true,true,true,true);
 -- datei
 insert into datei (name,beschreibung,modified,base64,id_obj) select name,beschreibung, modified, base64,rec.id_seq
from datei where id_obj = p_id;
 return rec.id_seq;
end;$_$;


ALTER FUNCTION bayeos.copy_objekt(integer, integer) OWNER TO bayeos;

--
-- Name: copy_with_child(integer, integer); Type: FUNCTION; Schema: bayeos; Owner: bayeos
--

CREATE FUNCTION copy_with_child(integer, integer) RETURNS integer
    LANGUAGE plpgsql
    AS $_$
declare
 src alias for $1;
 des alias for $2;
 id int4;
begin
 raise notice 'copy_with_child src :% des:%',src,des;
 select into id copy_objekt(src,des);
 perform copy_child(src,id);
 return(id);
end;$_$;


ALTER FUNCTION bayeos.copy_with_child(integer, integer) OWNER TO bayeos;

--
-- Name: create_aggr_table(character varying, character varying); Type: FUNCTION; Schema: bayeos; Owner: bayeos
--

CREATE FUNCTION create_aggr_table(character varying, character varying) RETURNS void
    LANGUAGE plpgsql
    AS $_$declare
 tab varchar(300);
 fname alias for $1;
 iname alias for $2;
begin
 tab := 'aggr_'  || fname|| '_' || iname ;
   execute 'CREATE TABLE ' || tab || ' (id int4 NOT NULL,von timestamptz NOT NULL,wert float8 ,counts int4 NOT NULL) WITHOUT OIDS;' ;
   execute 'create unique index idx_' || tab || '_id_von on ' || tab || ' (id,von);';
   execute 'cluster idx_' || tab || '_id_von on ' || tab || ';';
   return;
end;$_$;


ALTER FUNCTION bayeos.create_aggr_table(character varying, character varying) OWNER TO bayeos;

--
-- Name: FUNCTION create_aggr_table(character varying, character varying); Type: COMMENT; Schema: bayeos; Owner: bayeos
--

COMMENT ON FUNCTION create_aggr_table(character varying, character varying) IS 'Erzeugt eine Aggregationstabelle';


--
-- Name: create_aggr_tables(); Type: FUNCTION; Schema: bayeos; Owner: bayeos
--

CREATE FUNCTION create_aggr_tables() RETURNS boolean
    LANGUAGE plpgsql
    AS $$ declare
  rec record;
  tab varchar(300);
  begin
  for rec in select f.name as fname,i.name as iname from aggr_funktion f, aggr_intervall i
  loop
   tab := 'aggr_'  || rec.fname || '_' || rec.iname ;
   execute 'CREATE TABLE ' || tab || ' (id int4 NOT NULL,von timestamptz NOT NULL,wert float8 ,counts int4 NOT NULL) WITHOUT OIDS;' ;
   execute 'create unique index idx_' || tab || '_id_von on ' || tab || ' (id,von);';
   execute 'cluster idx_' || tab || '_id_von on ' || tab || ';';
  end loop;
  return true;
  end;
$$;


ALTER FUNCTION bayeos.create_aggr_tables() OWNER TO bayeos;

--
-- Name: create_check(text, interval, interval, text, integer[], integer[]); Type: FUNCTION; Schema: bayeos; Owner: bayeos
--

CREATE FUNCTION create_check(in_name text, in_cycle_interval interval, in_data_interval interval, in_valfunc text, cols integer[], arg integer[]) RETURNS boolean
    LANGUAGE plpgsql
    AS $_$
declare
rec record;
id_check int;
begin
select into rec * from valfunction where name=in_valfunc;
if not found then
  return false;
end if;

id_check:=nextval('checks_id_seq'::regclass);
insert into checks(id,fk_valfunction,name,cycle_interval,data_interval) values (id_check,rec.id,in_name,in_cycle_interval,in_data_interval);
return insert_check_function_parameter(id_check,$5,$6);

end;
$_$;


ALTER FUNCTION bayeos.create_check(in_name text, in_cycle_interval interval, in_data_interval interval, in_valfunc text, cols integer[], arg integer[]) OWNER TO bayeos;

--
-- Name: create_group(integer, character varying, character varying); Type: FUNCTION; Schema: bayeos; Owner: bayeos
--

CREATE FUNCTION create_group(integer, character varying, character varying) RETURNS integer
    LANGUAGE plpgsql
    AS $_$
declare
i_id_benutzer alias for $1;
i_login alias for $2;
i_name alias for $3;
rec record;
begin
select into rec create_objekt(i_id_benutzer,null,100003,i_name,i_name);
if rec.create_objekt>0
then
  insert into benutzer(id,login)
  values (rec.create_objekt,i_login);
end if;
return rec.create_objekt;
end;
$_$;


ALTER FUNCTION bayeos.create_group(integer, character varying, character varying) OWNER TO bayeos;

--
-- Name: create_his(text); Type: FUNCTION; Schema: bayeos; Owner: bayeos
--

CREATE FUNCTION create_his(tablename text) RETURNS boolean
    LANGUAGE plpgsql
    AS $$
DECLARE
columns text;
columns_new text;
columns_old text;
his_tablename varchar(100);
BEGIN
his_tablename := 'his_' || tableName;
execute 'create table ' || his_tablename || ' as select * from "' || tableName || '" where false;';
execute 'alter table  ' || his_tablename || ' add column his_id int;';
execute 'alter table  ' || his_tablename || ' alter column his_id set not null;';
execute 'alter table '  || his_tablename || ' add column his_datum timestamp with time zone;';
execute 'alter table '  || his_tablename || ' alter column his_datum set not null;';
execute 'alter table '  || his_tablename || ' alter column his_datum set default current_timestamp;';
execute 'alter table '  || his_tablename || ' add column his_benutzer_id int;';
execute 'alter table '  || his_tablename || ' alter column his_benutzer_id set default get_userid();';
execute 'alter table '  || his_tablename || ' add column his_aktion int2;';
execute 'alter table '  || his_tablename || ' alter column his_aktion set default 1;';
execute 'create sequence ' || his_tablename || '_id;';
execute 'alter table ' || his_tablename || ' alter column his_id set default nextval(''' || his_tablename ||  '_id'');';
execute 'create unique index idx_' || his_tablename || '_id on ' || his_tablename || ' (his_id);';

columns := get_columns(tableName,'');
columns_new :=  get_columns(tableName,'NEW.');
columns_old :=  get_columns(tableName,'OLD.');

execute 'create or replace function write_' || his_tablename || ' () RETURNS trigger AS ''  
    BEGIN
        if TG_OP = ''''INSERT'''' then
            insert into ' || his_tablename || '(his_aktion,' || columns || ')
            values (1,' || columns_new || ' );
    RETURN NEW;
        elsif TG_OP = ''''UPDATE'''' then
            insert into ' || his_tablename || '(his_aktion,' || columns || ')
            values (2,' || columns_new || ');
            RETURN NEW;
        elsif TG_OP = ''''DELETE'''' then
            insert into ' ||  his_tablename || '(his_aktion,' || columns || ')
            values (3,' ||  columns_old || ');
    RETURN OLD;
        end if;       

    END;'' LANGUAGE  ''plpgsql'';';

execute 'CREATE TRIGGER ' || his_tablename || '_id BEFORE INSERT OR UPDATE OR DELETE ON ' || tableName  || ' FOR EACH ROW EXECUTE PROCEDURE write_' || his_tablename || '();';
return true;
END; 
$$;


ALTER FUNCTION bayeos.create_his(tablename text) OWNER TO bayeos;

--
-- Name: create_objekt(integer, integer, integer, character varying, character varying); Type: FUNCTION; Schema: bayeos; Owner: bayeos
--

CREATE FUNCTION create_objekt(_id_benutzer integer, _id_super integer, _id_art integer, _de character varying, _en character varying) RETURNS integer
    LANGUAGE plpgsql
    AS $$declare
idobj int;
rec record;
begin

/* create-Rechte ueberpruefen... */
select into rec check_exec(_id_art,_id_benutzer);
if not rec.check_exec
then 
  raise exception 'User is not allowed to create objects of this type'; 
  return 0;
end if;

/*child-Objekt-Recht*/
if _id_super>0 then
  select into rec public_childs from objekt where id=_id_super;
  if not rec.public_childs
    then
    select into rec check_exec(_id_super,_id_benutzer);
    if not rec.check_exec
      then
raise exception 'User is not allowed to create child objects for this object';
    end if;
  end if;
end if; 

select into rec nextval('objekt_id');
idobj:=rec.nextval;

insert into objekt(id,id_art,id_super,de,en,id_cbenutzer,ctime)
values (idobj,_id_art,_id_super,_de,_en,_id_benutzer,now());

/*Zugriff sicherstellen...*/
select into rec check_rwx(idobj,_id_benutzer);
if not rec.check_rwx
then
insert into zugriff values(idobj,_id_benutzer,true,true,true);
end if;


return idobj;
end;$$;


ALTER FUNCTION bayeos.create_objekt(_id_benutzer integer, _id_super integer, _id_art integer, _de character varying, _en character varying) OWNER TO bayeos;

--
-- Name: create_objekt_by_name(integer, integer, character varying, character varying, character varying); Type: FUNCTION; Schema: bayeos; Owner: bayeos
--

CREATE FUNCTION create_objekt_by_name(_id_benutzer integer, _id_super integer, _uname character varying, _de character varying, _en character varying) RETURNS integer
    LANGUAGE plpgsql
    AS $$declare 
rec record;
begin
select into rec id from art_objekt where uname=_uname;
if not found then
  raise exception 'Unknown object type'; 
end if;

select into rec create_objekt(_id_benutzer,_id_super,rec.id,_de,_en);
return rec.create_objekt;
end;$$;


ALTER FUNCTION bayeos.create_objekt_by_name(_id_benutzer integer, _id_super integer, _uname character varying, _de character varying, _en character varying) OWNER TO bayeos;

--
-- Name: create_objekt_with_detail(integer, integer, character varying, character varying, character varying); Type: FUNCTION; Schema: bayeos; Owner: bayeos
--

CREATE FUNCTION create_objekt_with_detail(_id_benutzer integer, _id_super integer, _uname character varying, _de character varying, _en character varying) RETURNS integer
    LANGUAGE plpgsql
    AS $$declare
rec record;
s varchar(100);
tab art_objekt.detail_table%TYPE;
begin

if (_id_benutzer is null) or (_uname is null) then
 raise exception 'Illegal argument'; 
end if;

select into tab detail_table from art_objekt where uname=_uname;
if not found then
  raise exception 'Unknown object type'; 
end if;
if tab is null then
  raise exception 'Table name not found'; 
end if;

select into rec create_objekt_by_name(_id_benutzer,_id_super,_uname,_de,_en);
if rec.create_objekt_by_name>0 then
 s := 'insert into ' || tab || ' (id,bezeichnung) values (' || rec.create_objekt_by_name || ',' || quote_literal(_de) || ')';
 execute s;
else 
 raise exception 'No objekt created'; 
end if;
return rec.create_objekt_by_name;
end;$$;


ALTER FUNCTION bayeos.create_objekt_with_detail(_id_benutzer integer, _id_super integer, _uname character varying, _de character varying, _en character varying) OWNER TO bayeos;

--
-- Name: create_role(character varying, character varying); Type: FUNCTION; Schema: bayeos; Owner: bayeos
--

CREATE FUNCTION create_role(_login character varying, _name character varying) RETURNS integer
    LANGUAGE plpgsql
    AS $$
declare
 rec record;
begin
select into rec create_objekt_by_name(get_userid(),null,'gruppe',_name,_name);
if rec.create_objekt>0
then
  insert into benutzer(id,login)
  values (rec.create_objekt_by_name,_login);
end if;
return rec.create_objekt;
end;
$$;


ALTER FUNCTION bayeos.create_role(_login character varying, _name character varying) OWNER TO bayeos;

--
-- Name: create_session(integer); Type: FUNCTION; Schema: bayeos; Owner: bayeos
--

CREATE FUNCTION create_session(userid integer) RETURNS integer
    LANGUAGE plpgsql
    AS $$
declare 
 m_ben_id benutzer.id%TYPE;
 m_key session.key%TYPE;
begin
 select into m_key trunc(random() * 999999999) + 1;
 insert into session (id_benutzer, key) values (userid, m_key);
return m_key;
end;
$$;


ALTER FUNCTION bayeos.create_session(userid integer) OWNER TO bayeos;

--
-- Name: create_session(character varying, character); Type: FUNCTION; Schema: bayeos; Owner: bayeos
--

CREATE FUNCTION create_session(character varying, character) RETURNS integer
    LANGUAGE plpgsql
    AS $_$
declare 
 p_login alias for $1;
 p_pw alias for $2;
 m_ben_id benutzer.id%TYPE;
 m_key session.key%TYPE;
begin
 m_ben_id = get_user_id(p_login,p_pw);
 if m_ben_id is null then 
   return null;
 end if; 
 select into m_key trunc(random() * 999999999) + 1;
 insert into session (id_benutzer, key) values (m_ben_id, m_key);
return m_key;
end;
$_$;


ALTER FUNCTION bayeos.create_session(character varying, character) OWNER TO bayeos;

--
-- Name: create_user(character varying, character varying, character varying); Type: FUNCTION; Schema: bayeos; Owner: bayeos
--

CREATE FUNCTION create_user(_login character varying, _pw character varying, _name character varying) RETURNS integer
    LANGUAGE plpgsql
    AS $$declare
ret int;
begin
select into ret create_user(get_userid(),_login,_pw,_name,'DB','LOCAL');
return ret;
end; $$;


ALTER FUNCTION bayeos.create_user(_login character varying, _pw character varying, _name character varying) OWNER TO bayeos;

--
-- Name: FUNCTION create_user(_login character varying, _pw character varying, _name character varying); Type: COMMENT; Schema: bayeos; Owner: bayeos
--

COMMENT ON FUNCTION create_user(_login character varying, _pw character varying, _name character varying) IS 'Convienent method with database authentication';


--
-- Name: create_user(character varying, character varying, character varying, character varying, character varying); Type: FUNCTION; Schema: bayeos; Owner: bayeos
--

CREATE FUNCTION create_user(_login character varying, _pw character varying, _name character varying, _authmethod character varying, _authname character varying) RETURNS integer
    LANGUAGE plpgsql
    AS $$declare
ret int;
begin
select into ret create_user(get_userid(),_login,_pw,_name,_authMethod,_authName);
return ret;
end; $$;


ALTER FUNCTION bayeos.create_user(_login character varying, _pw character varying, _name character varying, _authmethod character varying, _authname character varying) OWNER TO bayeos;

--
-- Name: create_user(integer, character varying, character varying, character varying, character varying, character varying); Type: FUNCTION; Schema: bayeos; Owner: bayeos
--

CREATE FUNCTION create_user(_id_benutzer integer, _login character varying, _pw character varying, _name character varying, _authmethod character varying, _authname character varying) RETURNS integer
    LANGUAGE plpgsql
    AS $$declare
rec record;
rec_auth record;
begin

if (_authMethod = 'DB') then
 select into rec_auth id from auth_db where name like _authName;
 if (not FOUND) then 
	raise exception 'Database authentication method:% not found.',_authName;
 end if;
elsif (_authMethod = 'LDAP') then
 select into rec_auth id from auth_ldap where name like _authName;
 if (not FOUND) then 
	raise exception 'LDAP authentication method:% not found.',_authName;
 end if;
else 
 raise exception 'Authentication method:% not supported. Must be DB or LDAP',_authMethod;	
end if;

select into rec create_objekt(_id_benutzer,null,100002,_name,_name);
if rec.create_objekt>0
then
	if (_authMethod = 'DB') then
	 insert into benutzer(id,login,pw,fk_auth_db) values (rec.create_objekt,_login,crypt(_pw,gen_salt('des')),rec_auth.id);
	else 
	 insert into benutzer(id,login,pw,fk_auth_ldap) values (rec.create_objekt,_login,crypt(_pw,gen_salt('des')),rec_auth.id);
	end if;	 
end if;
return rec.create_objekt;

end; $$;


ALTER FUNCTION bayeos.create_user(_id_benutzer integer, _login character varying, _pw character varying, _name character varying, _authmethod character varying, _authname character varying) OWNER TO bayeos;

--
-- Name: create_verweis(integer, integer); Type: FUNCTION; Schema: bayeos; Owner: bayeos
--

CREATE FUNCTION create_verweis(integer, integer) RETURNS integer
    LANGUAGE plpgsql
    AS $_$declare
i_id_von alias for $1; -- not null
i_id_auf alias for $2;  -- not null   
rec record;
begin
if (i_id_von is null) or (i_id_auf is null) then
 raise exception 'Illegal argument'; 
end if;

select into rec nextval('verweis_id_seq');
if not found then
  raise exception 'Sequence verweis_id_seq not found'; 
end if;
insert into verweis (id,id_von,id_auf) values (rec.nextval,i_id_von,i_id_auf);
return rec.nextval;
end;$_$;


ALTER FUNCTION bayeos.create_verweis(integer, integer) OWNER TO bayeos;

--
-- Name: create_web_verweis(integer, integer); Type: FUNCTION; Schema: bayeos; Owner: bayeos
--

CREATE FUNCTION create_web_verweis(integer, integer) RETURNS integer
    LANGUAGE plpgsql
    AS $_$declare
i_id_von alias for $1; -- not null
i_id_auf alias for $2;  -- not null   
rec record;
begin
if (i_id_von is null) or (i_id_auf is null) then
 raise exception 'Illegal argument'; 
end if;

select into rec nextval('verweis_extern_id_seq');
if not found then
  raise exception 'Sequence verweis_extern_id_seq not found'; 
end if;
insert into verweis_extern (id,id_von,id_auf) values (rec.nextval,i_id_von,i_id_auf);
return rec.nextval;
end;$_$;


ALTER FUNCTION bayeos.create_web_verweis(integer, integer) OWNER TO bayeos;

--
-- Name: current_interval(integer); Type: FUNCTION; Schema: bayeos; Owner: bayeos
--

CREATE FUNCTION current_interval(integer) RETURNS timestamp with time zone
    LANGUAGE sql
    AS $_$
SELECT TIMESTAMP WITH TIME ZONE 'epoch' + (EXTRACT(EPOCH FROM current_timestamp(0)) - EXTRACT(EPOCH FROM current_timestamp(0))::bigint%$1) * INTERVAL '1 second';   
$_$;


ALTER FUNCTION bayeos.current_interval(integer) OWNER TO bayeos;

--
-- Name: d_objekt(); Type: FUNCTION; Schema: bayeos; Owner: bayeos
--

CREATE FUNCTION d_objekt() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
 update objekt set id = id where id_super = old.id_super;
RETURN OLD;
END;$$;


ALTER FUNCTION bayeos.d_objekt() OWNER TO bayeos;

--
-- Name: date_trunc(interval, timestamp with time zone); Type: FUNCTION; Schema: bayeos; Owner: bayeos
--

CREATE FUNCTION date_trunc(interval, timestamp with time zone) RETURNS timestamp with time zone
    LANGUAGE plpgsql
    AS $_$   declare
      t1 timestamptz;
      t0 timestamptz;
    begin
      if ($1 = interval '30 minutes')  then
t0 := date_trunc('hour',$2);
      t1 := t0 + $1;
      if $2 < t1 then
        return t0;
        else
        return t1;
        end if;
      elsif ($1 = interval '1 hour') then
      return date_trunc('hour',$2);
      elsif $1 = interval '1 day' then
      return date_trunc('day',$2);
      elsif $1 = interval '1 month' then
      return date_trunc('month',$2);
      elsif $1 = interval '1 year' then
      return date_trunc('year',$2);
      else
        raise exception 'Interval % not supported',$1;
      end if;
    end;$_$;


ALTER FUNCTION bayeos.date_trunc(interval, timestamp with time zone) OWNER TO bayeos;

--
-- Name: FUNCTION date_trunc(interval, timestamp with time zone); Type: COMMENT; Schema: bayeos; Owner: bayeos
--

COMMENT ON FUNCTION date_trunc(interval, timestamp with time zone) IS 'Abschneiden eines Timestamp auf Intervall';


--
-- Name: delete_child_messungen_values(integer); Type: FUNCTION; Schema: bayeos; Owner: bayeos
--

CREATE FUNCTION delete_child_messungen_values(_id integer) RETURNS void
    LANGUAGE plpgsql
    AS $$declare
 rec record;
  begin
  -- Delete massendaten
  for rec in select id, de from get_child_objekte(_id,'messung_massendaten')
   loop
    raise notice 'id:% name:%',rec.id,rec.de;
    delete from massendaten where id = rec.id;    
  end loop;

  -- Delete labordaten
  for rec in select id, de from get_child_objekte(_id,'messung_labordaten')
   loop
    raise notice 'id:% name:%',rec.id,rec.de;
    delete from labordaten where id = rec.id;    
  end loop;
 end;
$$;


ALTER FUNCTION bayeos.delete_child_messungen_values(_id integer) OWNER TO bayeos;

--
-- Name: delete_lab(); Type: FUNCTION; Schema: bayeos; Owner: bayeos
--

CREATE FUNCTION delete_lab() RETURNS integer
    LANGUAGE plpgsql
    AS $$declare
 rec record;
 i int4;
 begin
  for rec in select * from his_labordaten where his_id between 134929 and 142082 and his_aktion = 1
   loop
    delete from labordaten where labornummer = rec.labornummer and id = rec.id;
    i:= i+1;
  end loop;
 return i;   
 end;
$$;


ALTER FUNCTION bayeos.delete_lab() OWNER TO bayeos;

--
-- Name: drop_aggr_tables(); Type: FUNCTION; Schema: bayeos; Owner: bayeos
--

CREATE FUNCTION drop_aggr_tables() RETURNS boolean
    LANGUAGE plpgsql
    AS $$ declare
  rec record;
  tab varchar(300);
  begin
  for rec in select f.name as fname,i.name as iname from aggr_funktion f, aggr_intervall i
  loop 
   tab := 'aggr_'  || rec.fname || '_' || rec.iname ;
   execute 'drop table ' || tab || ';';
  end loop;
  return true;
  end;
$$;


ALTER FUNCTION bayeos.drop_aggr_tables() OWNER TO bayeos;

--
-- Name: drop_his(character varying); Type: FUNCTION; Schema: bayeos; Owner: bayeos
--

CREATE FUNCTION drop_his(tablename character varying) RETURNS boolean
    LANGUAGE plpgsql
    AS $$
declare
his_tablename varchar(2000);
begin
if tablename is null then
 raise exception 'Illegal argument';
end if;
his_tablename := 'his_' || tablename;
        begin 
	
	begin 	
	execute 'drop trigger  ' || his_tablename || '_id on ' || tablename || ';';
	exception 
	 when OTHERS then
	 	raise warning 'Exception:%', SQLERRM; 
	end;	
	
	begin 		
	execute 'drop table his_' || tablename || ';';
	exception 
	 when OTHERS then
	 	raise warning 'Exception:%', SQLERRM; 
	end;	

	
	begin 		
	execute 'drop function write_' || his_tablename || '();';
	exception 
	 when OTHERS then
	 	raise warning 'Exception:%', SQLERRM; 
	end;	
	
	execute 'drop sequence ' || his_tablename || '_id;';
	exception 
	 when OTHERS then
	 	raise warning 'Exception:%', SQLERRM; 
	end;	
	
	
return true;
end;
$$;


ALTER FUNCTION bayeos.drop_his(tablename character varying) OWNER TO bayeos;

--
-- Name: drop_user(text); Type: FUNCTION; Schema: bayeos; Owner: bayeos
--

CREATE FUNCTION drop_user(_login text) RETURNS void
    LANGUAGE plpgsql
    AS $$
declare
uid int;
b boolean;
begin
select into uid id from benutzer where login like _login;
if not found then
raise exception 'User not found';
end if;

delete from session where id_benutzer = uid;
delete from zugriff where id_benutzer = uid;
delete from benutzer_gr where id_benutzer = uid;
delete from benutzer where id = uid;
delete from objekt where id = uid;


end;
$$;


ALTER FUNCTION bayeos.drop_user(_login text) OWNER TO bayeos;


CREATE FUNCTION get_aggr_src_id(interval) RETURNS integer
    LANGUAGE plpgsql IMMUTABLE
    AS $_$ declare
  p int4;
 begin
  select into p id from aggr_intervall WHERE intervall < $1 order by intervall desc;
  return(p);
 end;
$_$;


ALTER FUNCTION bayeos.get_aggr_src_id(interval) OWNER TO bayeos;

--
-- Name: get_aggr_table(integer, integer); Type: FUNCTION; Schema: bayeos; Owner: bayeos
--

CREATE FUNCTION get_aggr_table(_id_funktion integer, _id_intervall integer) RETURNS character varying
    LANGUAGE plpgsql IMMUTABLE
    AS $_$ declare
  funktion alias for $1;
  intervall alias for $2;
  f varchar(100);
  i varchar(100);
 begin
  select into f lower(name) from aggr_funktion where id = _id_funktion;
  select into i lower(name) from aggr_intervall where id = _id_intervall;
  return('aggr_' || f || '_' || i);
 end;
$_$;


ALTER FUNCTION bayeos.get_aggr_table(_id_funktion integer, _id_intervall integer) OWNER TO bayeos;

--
-- Name: get_all_ref(integer, integer); Type: FUNCTION; Schema: bayeos; Owner: bayeos
--

CREATE FUNCTION get_all_ref(_id integer, _id_art integer) RETURNS SETOF integer
    LANGUAGE plpgsql
    AS $$
DECLARE
    r integer;
    i integer;    
BEGIN
    -- parent loop 
    FOR i IN SELECT * from get_objekt_super_ids(_id)
    LOOP
	FOR r in SELECT v.id_von from verweis v, objekt o where v.id_auf = i and v.id_von = o.id and o.id_art = _id_art 
	LOOP
        RETURN NEXT r;
        END LOOP;
    END LOOP;
    RETURN;
END
$$;


ALTER FUNCTION bayeos.get_all_ref(_id integer, _id_art integer) OWNER TO bayeos;

--
-- Name: get_check_funccall_sql(integer); Type: FUNCTION; Schema: bayeos; Owner: bayeos
--

CREATE FUNCTION get_check_funccall_sql(id_check integer) RETURNS text
    LANGUAGE plpgsql
    AS $$
declare
  rec record;
  sql text;
begin
  select into rec f.name from valfunction f, checks c where c.id=id_check and c.fk_valfunction=f.id;
  sql:=rec.name||'(get_check_sql('||id_check||')';
  for rec in select v.* from valfuncargvalue v,valfuncargument a where v.fk_checks=id_check and v.fk_valfuncargument=a.id order by a.index loop
    sql:=sql||','||rec.value;
  end loop;
  sql:=sql||')';
  return sql;
end;
$$;


ALTER FUNCTION bayeos.get_check_funccall_sql(id_check integer) OWNER TO bayeos;

--
-- Name: get_check_sql(integer); Type: FUNCTION; Schema: bayeos; Owner: bayeos
--

CREATE FUNCTION get_check_sql(id_check integer) RETURNS text
    LANGUAGE plpgsql
    AS $$
declare
  rec record;
  out text;
  crosstab_source text;
  crosstab_cols text;
begin
  out:='select ';
  crosstab_source:='select 0 as von,''dummy'' as col,1 as wert where false';
  crosstab_cols:='von float';

  select into rec count(*) from valfunccolumn c,valfunction f,checks ch where ch.id=id_check and ch.fk_valfunction=f.id and f.id=c.fk_valfunction; 

  if rec.count=1 then
    select into rec ccm.*,vfc.name,c.data_interval,af.name as af, ai.name as ai from checkcolmessung ccm left outer join aggr_intervall ai on ai.id=ccm.fk_interval left outer join aggr_funktion af on af.id=ccm.fk_function, valfunccolumn vfc,checks c where c.id=id_check and c.fk_valfunction=vfc.fk_valfunction and ccm.fk_checks=id_check and ccm.fk_valfunccolumn=vfc.id;
    out:=out||'extract(epoch from von)/3600 as von,wert as '||rec.name||' from '|| case when rec.af is not null and rec.ai is not null then 'aggr_'||rec.af||'_'||rec.ai else 'massendaten' end||' where id='||rec.fk_messungen||' and von<=now() and von>=now()-interval '''||rec.data_interval||''' order by 1';
  else
    for rec in select  ccm.*,vfc.name,c.data_interval,af.name as af, ai.name as ai from checkcolmessung ccm left outer join aggr_intervall ai on ai.id=ccm.fk_interval left outer join aggr_funktion af on af.id=ccm.fk_function, valfunccolumn vfc,checks c where c.id=id_check and c.fk_valfunction=vfc.fk_valfunction and ccm.fk_checks=id_check and ccm.fk_valfunccolumn=vfc.id loop
    crosstab_source:=crosstab_source||' union select extract(epoch from von)/3600 as von,'''||rec.name||''' as col, wert from '|| case when rec.af is not null and rec.ai is not null then 'aggr_'||rec.af||'_'||rec.ai else 'massendaten' end||' where id='||rec.fk_messungen||' and von<=now() and von>=now()-interval '''||rec.data_interval||''' ';
    crosstab_cols:=crosstab_cols||','||rec.name||' float';
    end loop;
    crosstab_source:=crosstab_source||' order by 1,2';
    out:='select * from crosstab('||quote_literal(crosstab_source)||',''select vfc.name from checkcolmessung ccm, valfunccolumn vfc where ccm.fk_checks='||id_check||' and ccm.fk_valfunccolumn=vfc.id'') as c('||crosstab_cols||')';
  end if;
  return out;
end;
$$;


ALTER FUNCTION bayeos.get_check_sql(id_check integer) OWNER TO bayeos;

--
-- Name: get_child_id(character varying, integer); Type: FUNCTION; Schema: bayeos; Owner: bayeos
--

CREATE FUNCTION get_child_id(character varying, integer) RETURNS integer
    LANGUAGE plpgsql
    AS $_$declare
rec record;
i int4 := 0;
begin
for rec in select o.id from objekt o, 
 (select * FROM connectby('messungen.objekt'::text, 'id'::text, 
  'id_super'::text, $2::text, 0)  t(id integer, id_super integer, "level" integer)) as c
 where o.id = c.id and level > 0 and o.de like ($1)
loop 
 i:=i+1;
 if i > 1 then 
   raise exception 'Name not unique';
 end if;
end loop; 
 return rec.id;
end

$_$;


ALTER FUNCTION bayeos.get_child_id(character varying, integer) OWNER TO bayeos;

--
-- Name: get_child_ids(integer); Type: FUNCTION; Schema: bayeos; Owner: bayeos
--

CREATE FUNCTION get_child_ids(integer) RETURNS text
    LANGUAGE plpgsql
    AS $_$declare
rec record;
rec2 record;
ids text := '';
begin
if $1>0 then
        for rec in select id,get_child_ids(id)
from messungen.objekt where id_super=$1 loop
ids:=ids||','||rec.id||rec.get_child_ids;
end loop;
end if;

return ids;
end;$_$;


ALTER FUNCTION bayeos.get_child_ids(integer) OWNER TO bayeos;

SET default_tablespace = '';

SET default_with_oids = false;

--
-- Name: objekt; Type: TABLE; Schema: bayeos; Owner: bayeos; Tablespace: 
--

CREATE TABLE objekt (
    id integer NOT NULL,
    id_super integer,
    id_art integer NOT NULL,
    id_cbenutzer integer,
    ctime timestamp with time zone,
    id_ubenutzer integer,
    utime timestamp with time zone,
    dtime timestamp with time zone,
    de character varying(255),
    en character varying(255),
    public_read boolean DEFAULT false NOT NULL,
    public_write boolean DEFAULT false NOT NULL,
    public_exec boolean DEFAULT false NOT NULL,
    public_childs boolean DEFAULT false NOT NULL,
    inherit_perm boolean DEFAULT true NOT NULL,
    plan_start timestamp with time zone,
    plan_end timestamp with time zone,
    rec_start timestamp with time zone,
    rec_end timestamp with time zone,
    CONSTRAINT objekt_mrec CHECK ((rec_end >= rec_start)),
    CONSTRAINT objekt_plan CHECK ((plan_end >= plan_start))
);


ALTER TABLE bayeos.objekt OWNER TO bayeos;

--
-- Name: COLUMN objekt.plan_start; Type: COMMENT; Schema: bayeos; Owner: bayeos
--

COMMENT ON COLUMN objekt.plan_start IS 'Planned start date of object.';


--
-- Name: COLUMN objekt.plan_end; Type: COMMENT; Schema: bayeos; Owner: bayeos
--

COMMENT ON COLUMN objekt.plan_end IS 'Planned end date of object.';


--
-- Name: COLUMN objekt.rec_start; Type: COMMENT; Schema: bayeos; Owner: bayeos
--

COMMENT ON COLUMN objekt.rec_start IS 'Min(date) of measurement records';


--
-- Name: COLUMN objekt.rec_end; Type: COMMENT; Schema: bayeos; Owner: bayeos
--

COMMENT ON COLUMN objekt.rec_end IS 'Max(date) of measurement records';


--
-- Name: get_child_objekte(integer, integer); Type: FUNCTION; Schema: bayeos; Owner: bayeos
--

CREATE FUNCTION get_child_objekte(_id integer, _id_art integer) RETURNS SETOF objekt
    LANGUAGE sql
    AS $_$select o.* from objekt o, (select * FROM connectby('messungen.objekt'::text, 'id'::text, 
  'id_super'::text, $1::text, 0)  t(id integer, id_super integer, "level" integer)) as c
 where o.id = c.id and level > 0 and o.id_art = $2 order by 2;$_$;


ALTER FUNCTION bayeos.get_child_objekte(_id integer, _id_art integer) OWNER TO bayeos;

--
-- Name: get_child_objekte(integer, text); Type: FUNCTION; Schema: bayeos; Owner: bayeos
--

CREATE FUNCTION get_child_objekte(_parent_id integer, _uname text) RETURNS SETOF objekt
    LANGUAGE plpgsql
    AS $$
declare
rec record;
ret record;
begin
select into rec id from art_objekt where uname like _uname;
if not found then
raise exception 'Unknown uname';
end if;
RETURN QUERY select * from get_child_objekte(_parent_id,rec.id);
RETURN;
end;
$$;


ALTER FUNCTION bayeos.get_child_objekte(_parent_id integer, _uname text) OWNER TO bayeos;

--
-- Name: get_childs(integer); Type: FUNCTION; Schema: bayeos; Owner: bayeos
--

CREATE FUNCTION get_childs(integer) RETURNS text
    LANGUAGE plpgsql
    AS $_$declare
rec record;
rec2 record;
ids text;
begin

ids:='';
if $1>0 then
        for rec in select id,get_childs(id)
from objekt where id_super=$1 loop
ids:=ids||','||rec.id||rec.get_childs;
end loop;
end if;        

return ids;
end;$_$;


ALTER FUNCTION bayeos.get_childs(integer) OWNER TO bayeos;

--
-- Name: get_childs(integer, integer); Type: FUNCTION; Schema: bayeos; Owner: bayeos
--

CREATE FUNCTION get_childs(integer, integer) RETURNS text
    LANGUAGE plpgsql
    AS $_$declare
rec record;
rec2 record;
ids text;
begin

ids:='';
if $1>0 and $2>0  then
  for rec in select id,get_childs(id,$2-1) 
from objekt where id_super=$1 loop
  ids:=ids||','||rec.id||rec.get_childs;
  end loop;
        end if;
return ids;
end;$_$;


ALTER FUNCTION bayeos.get_childs(integer, integer) OWNER TO bayeos;

--
-- Name: get_columns(text, text); Type: FUNCTION; Schema: bayeos; Owner: bayeos
--

CREATE FUNCTION get_columns(tablename text, prefix text) RETURNS text
    LANGUAGE plpgsql
    AS $$
DECLARE
rec record;
s text;
rmeta record;
BEGIN
if tableName is null then
 raise exception 'Please specify a table name.';
end if;

select into rec table_name from information_schema.tables where table_name like tableName and table_schema like current_schema();
if not found then
 raise exception 'Table does not exist.';
end if;

for rmeta in select column_name from information_schema.columns where table_name like tableName and table_schema like current_schema()
loop
 if s is null then
  s := prefix || rmeta.column_name;
 else
  s := s || ',' || prefix || rmeta.column_name;
 end if;
end loop;    
return s;
END;
$$;


ALTER FUNCTION bayeos.get_columns(tablename text, prefix text) OWNER TO bayeos;

--
-- Name: get_description(integer, character varying); Type: FUNCTION; Schema: bayeos; Owner: bayeos
--

CREATE FUNCTION get_description(integer, character varying) RETURNS character varying
    LANGUAGE plpgsql STABLE
    AS $_$
declare
 p_id alias for $1;
 p_uname alias for $2;
 t varchar;
 d varchar;
begin
select into t en from art_objekt where uname = p_uname;
if not found then 
 raise exception 'Objekt type not found.';
end if;
select into d z.de from (select de from objekt where id = get_lowest_ref(p_id ,p_uname)) as z;
if found then 
 return t || ':' || d;
else 
 return t || ':null';
end if;
end;
$_$;


ALTER FUNCTION bayeos.get_description(integer, character varying) OWNER TO bayeos;

--
-- Name: get_inherited_parent_ids(integer); Type: FUNCTION; Schema: bayeos; Owner: bayeos
--

CREATE FUNCTION get_inherited_parent_ids(integer) RETURNS text
    LANGUAGE plpgsql
    AS $_$declare
rec record;
ids text;
begin
ids:='null';
        select into rec id_super from objekt where id=$1 and inherit_perm;
        if found then
if rec.id_super>0
then
  ids:=rec.id_super;
  select into rec get_inherited_parent_ids(rec.id_super);
  if rec.get_inherited_parent_ids!='null'
  then
    ids:=ids||','||rec.get_inherited_parent_ids;
  end if;
end if;
end if;
return ids;
end;$_$;


ALTER FUNCTION bayeos.get_inherited_parent_ids(integer) OWNER TO bayeos;

--
-- Name: get_intervall(integer); Type: FUNCTION; Schema: bayeos; Owner: bayeos
--

CREATE FUNCTION get_intervall(integer) RETURNS interval
    LANGUAGE plpgsql IMMUTABLE
    AS $_$ declare
  rec record;
 begin
   return quote_literal($1 || ' ' || 'seconds')::interval;
 end;
$_$;


ALTER FUNCTION bayeos.get_intervall(integer) OWNER TO bayeos;

--
-- Name: get_lab_bis(text, text); Type: FUNCTION; Schema: bayeos; Owner: bayeos
--

CREATE FUNCTION get_lab_bis(text, text) RETURNS timestamp with time zone
    LANGUAGE plpgsql
    AS $_$declare
   i_kw alias for $1;
   i_year alias for $2;
   t timestamp with time zone;
 begin
  if i_kw is null or i_year is null then 
   	raise exception 'Missing argument';
  end if;
  select into t ( to_date(i_kw || '.' || i_year,'IW.YYYY'))::timestamp with time zone;
  return(t);
 end;
$_$;


ALTER FUNCTION bayeos.get_lab_bis(text, text) OWNER TO bayeos;

--
-- Name: get_lab_von(text, text); Type: FUNCTION; Schema: bayeos; Owner: bayeos
--

CREATE FUNCTION get_lab_von(text, text) RETURNS timestamp with time zone
    LANGUAGE plpgsql
    AS $_$declare
   i_kw alias for $1;
   i_year alias for $2;
   t timestamp with time zone;
 begin
  if i_kw is null or i_year is null then 
   	raise exception 'Missing argument';
  end if;
  select into t ( to_date(i_kw || '.' || i_year,'IW.YYYY') -13)::timestamp with time zone;
  return(t);
 end;
$_$;


ALTER FUNCTION bayeos.get_lab_von(text, text) OWNER TO bayeos;

--
-- Name: get_lowest_ref(integer, integer); Type: FUNCTION; Schema: bayeos; Owner: bayeos
--

CREATE FUNCTION get_lowest_ref(integer, integer) RETURNS integer
    LANGUAGE plpgsql STABLE
    AS $_$
declare
 p_id alias for $1;
 p_id_art alias for $2;
 rec record;
 rec_s record;
 rec_a record;
begin
-- verweis über tabelle
select into rec objekt.id from objekt, verweis where objekt.id = verweis.id_von and objekt.id_art = p_id_art and verweis.id_auf = p_id;
if found then
   return rec.id;
end if;
-- id_super bestimmen
select into rec_s id_super from objekt where objekt.id = p_id;
if rec_s.id_super is null then
   return null;
end if;
-- kontrolle ob super id richtige id art hat
select into rec_a id_art from objekt where id = rec_s.id_super;
if rec_a.id_art = p_id_art then
 return rec_s.id_super;
else
 return get_lowest_ref(rec_s.id_super,p_id_art);
end if;
return null;
end;
$_$;


ALTER FUNCTION bayeos.get_lowest_ref(integer, integer) OWNER TO bayeos;

--
-- Name: get_lowest_ref(integer, character varying); Type: FUNCTION; Schema: bayeos; Owner: bayeos
--

CREATE FUNCTION get_lowest_ref(integer, character varying) RETURNS integer
    LANGUAGE plpgsql STABLE
    AS $_$
declare
i_id alias for $1; -- not null
i_art alias for $2;  -- not null
rec record;
begin
if (i_id is null) or (i_art is null) then
 raise exception 'Illegal argument';
end if;
select into rec id from art_objekt where uname = i_art;
if not found then
  raise exception 'Object art unknown.';
end if;
return get_lowest_ref(i_id, rec.id);
end;

$_$;


ALTER FUNCTION bayeos.get_lowest_ref(integer, character varying) OWNER TO bayeos;

--
-- Name: get_mas_period(integer); Type: FUNCTION; Schema: bayeos; Owner: bayeos
--

CREATE FUNCTION get_mas_period(p_id integer) RETURNS timeperiod
    LANGUAGE plpgsql
    AS $$ 
  declare  
	ret timeperiod;
  begin    
   select into ret.von von from massendaten m where m.id = p_id order by m.von asc limit 1;
   select into ret.bis von from massendaten m where m.id = p_id order by m.von desc limit 1;   
   return ret;
  end;
$$;


ALTER FUNCTION bayeos.get_mas_period(p_id integer) OWNER TO bayeos;

--
-- Name: get_measurement_description(integer); Type: FUNCTION; Schema: bayeos; Owner: bayeos
--

CREATE FUNCTION get_measurement_description(integer) RETURNS character varying
    LANGUAGE plpgsql STABLE
    AS $_$
declare
 p_id alias for $1;
 d varchar;
begin
select into d de from objekt where id = p_id;
return 'Measurement:' || d || ';' || 'Id:'|| p_id || ';' || get_description(p_id,'mess_ziel') || ';' ||
get_description(p_id,'mess_einheit') || ';' || get_description(p_id,'mess_geraet') || ';' || get_description(p_id,'mess_ort');
end;
$_$;


ALTER FUNCTION bayeos.get_measurement_description(integer) OWNER TO bayeos;

--
-- Name: get_mes_id_ort_ziel(text, text); Type: FUNCTION; Schema: bayeos; Owner: bayeos
--

CREATE FUNCTION get_mes_id_ort_ziel(text, text) RETURNS integer
    LANGUAGE plpgsql
    AS $_$declare
   i_ort alias for $1;
   i_ziel alias for $2;
   b int4;
   id_ort int4;
   id_ziel int4;
 begin
  if i_ort is null or i_ziel is null then 
   	raise exception 'Missing argument';
  end if;

  select id into id_ort from messungen.messorte where lower(bezeichnung) like lower('%' || i_ort || '%') ;
  if not found then 
	raise exception ' Ort:% not found !',i_ort;
  end if;

  select id into id_ziel from messungen.messziele where lower(bezeichnung) like lower('%' || i_ziel || '%');
  if not found then 
	raise exception ' Ziel:% not found !',i_ziel;
  end if;
  select a.id_von into b from (
	select id_von from connectby('messungen.v_verweis','id_auf','id_von',id_ziel,0) 
	as t(id_von int4, id_auf int4,level int) where level > 0 
	intersect 
	select id_von from connectby('messungen.v_verweis','id_auf','id_von',id_ort,0) 
	as t(id_von int4, id_auf int4,level int) where level > 0
	) a; 

  if not found then 
	raise exception 'MESSUNGEN.ID for Ziel:% and Ort:% not found !',i_ziel,i_ort;
  end if;
  return(b);
 end;
$_$;


ALTER FUNCTION bayeos.get_mes_id_ort_ziel(text, text) OWNER TO bayeos;

--
-- Name: messorte; Type: TABLE; Schema: bayeos; Owner: bayeos; Tablespace: 
--

CREATE TABLE messorte (
    id integer NOT NULL,
    bezeichnung character varying(255),
    beschreibung text,
    x numeric,
    y numeric,
    z numeric,
    fk_crs_id integer
);


ALTER TABLE bayeos.messorte OWNER TO bayeos;

--
-- Name: get_messort(integer); Type: FUNCTION; Schema: bayeos; Owner: bayeos
--

CREATE FUNCTION get_messort(p_id integer) RETURNS SETOF messorte
    LANGUAGE plpgsql
    AS $$
declare
 _id integer;
begin
select into _id id from messorte where id = get_lowest_ref(p_id,'mess_ort');
RETURN QUERY select * from messorte where id = _id;
end;
$$;


ALTER FUNCTION bayeos.get_messort(p_id integer) OWNER TO bayeos;

--
-- Name: get_messziel(integer); Type: FUNCTION; Schema: bayeos; Owner: bayeos
--

CREATE FUNCTION get_messziel(p_id integer) RETURNS text
    LANGUAGE plpgsql
    AS $$
declare
 d text;
begin
select into d de from objekt where id = get_lowest_ref(p_id,'mess_ziel');
return d;
end;
$$;


ALTER FUNCTION bayeos.get_messziel(p_id integer) OWNER TO bayeos;

--
-- Name: get_min_intervall(integer); Type: FUNCTION; Schema: bayeos; Owner: bayeos
--

CREATE FUNCTION get_min_intervall(integer) RETURNS interval
    LANGUAGE plpgsql IMMUTABLE
    AS $_$ declare
  rec record;
 begin
  for rec in select id, extract(epoch from intervall) as s, intervall from aggr_intervall order by
intervall 
  loop
    if (rec.s > $1) then
     return(rec.intervall);
    end if;
  end loop; 
  return(null);
 end;
$_$;


ALTER FUNCTION bayeos.get_min_intervall(integer) OWNER TO bayeos;

--
-- Name: get_numberaxislabel(integer); Type: FUNCTION; Schema: bayeos; Owner: bayeos
--

CREATE FUNCTION get_numberaxislabel(integer) RETURNS character varying
    LANGUAGE plpgsql STABLE
    AS $_$declare
 p_id alias for $1;
 z varchar;
 e varchar;
begin
select into z de from objekt where id = get_lowest_ref(p_id,'mess_ziel');
select into e de from objekt where id = get_lowest_ref(p_id,'mess_einheit');
return coalesce(initcap(z),'none') || ' [' || coalesce(e,'none') || ']';
end;
$_$;


ALTER FUNCTION bayeos.get_numberaxislabel(integer) OWNER TO bayeos;


--
-- Name: get_objekt_super_ids(integer); Type: FUNCTION; Schema: bayeos; Owner: bayeos
--

CREATE FUNCTION get_objekt_super_ids(_id integer) RETURNS SETOF integer
    LANGUAGE sql
    AS $_$select id from connectby('messungen.objekt'::text, 'id_super'::text, 'id'::text, $1::text, 0) t(id integer, id_super integer, "level" integer) ;$_$;


ALTER FUNCTION bayeos.get_objekt_super_ids(_id integer) OWNER TO bayeos;

--
-- Name: get_parent_ids(integer); Type: FUNCTION; Schema: bayeos; Owner: bayeos
--

CREATE FUNCTION get_parent_ids(integer) RETURNS text
    LANGUAGE plpgsql
    AS $_$declare
rec record;
ids text;
begin
ids:='null';
        select into rec id_super from objekt where id=$1;
        if rec.id_super>0
then
  ids:=rec.id_super;
  select into rec get_parent_ids(rec.id_super);
  if rec.get_parent_ids!='null'
  then
    ids:=ids||','||rec.get_parent_ids;
  end if;
end if;
return ids;
end;$_$;


ALTER FUNCTION bayeos.get_parent_ids(integer) OWNER TO bayeos;

--
-- Name: get_unit(integer); Type: FUNCTION; Schema: bayeos; Owner: bayeos
--

CREATE FUNCTION get_unit(p_id integer) RETURNS text
    LANGUAGE plpgsql
    AS $$
declare
 d text;
begin
select into d de from objekt where id = get_lowest_ref(p_id,'mess_einheit');
return d;
end;
$$;


ALTER FUNCTION bayeos.get_unit(p_id integer) OWNER TO bayeos;

--
-- Name: get_user_id(character varying, character); Type: FUNCTION; Schema: bayeos; Owner: bayeos
--

CREATE FUNCTION get_user_id(character varying, character) RETURNS integer
    LANGUAGE plpgsql
    AS $_$
declare
 p_login alias for $1;
 p_pw alias for $2;
 p_id benutzer.id%TYPE;
begin
select into p_id id from benutzer where login= p_login  and crypt(p_pw,substr(pw,1,2)) like pw
and locked = false;
return p_id;
end;
$_$;


ALTER FUNCTION bayeos.get_user_id(character varying, character) OWNER TO bayeos;

--
-- Name: get_userid(); Type: FUNCTION; Schema: bayeos; Owner: bayeos
--

CREATE FUNCTION get_userid() RETURNS integer
    LANGUAGE plpgsql
    AS $$declare
rec record;
begin
select into rec id from tmp_userid;
return rec.id;
end;$$;


ALTER FUNCTION bayeos.get_userid() OWNER TO bayeos;

--
-- Name: get_userlogin(integer); Type: FUNCTION; Schema: bayeos; Owner: bayeos
--

CREATE FUNCTION get_userlogin(integer) RETURNS character varying
    LANGUAGE plpgsql
    AS $_$declare
    i_id alias for $1; -- not null
    l benutzer.login%type;
    begin
    select into l login from benutzer where id = i_id; 
    return l;
end;$_$;


ALTER FUNCTION bayeos.get_userlogin(integer) OWNER TO bayeos;



--
-- Name: get_web_parent_ids(integer); Type: FUNCTION; Schema: bayeos; Owner: bayeos
--

CREATE FUNCTION get_web_parent_ids(integer) RETURNS text
    LANGUAGE plpgsql
    AS $_$declare
rec record;
ids text;
begin
ids:='null';
        select into rec id_super from v_web_objekt where id=$1;
        if rec.id_super>0
then
  ids:=rec.id_super;
  select into rec get_web_parent_ids(rec.id_super);
  if rec.get_web_parent_ids!='null'
  then
    ids:=ids||','||rec.get_web_parent_ids;
  end if;
end if;
return ids;
end;$_$;


ALTER FUNCTION bayeos.get_web_parent_ids(integer) OWNER TO bayeos;

--
-- Name: grant_role(text, text); Type: FUNCTION; Schema: bayeos; Owner: bayeos
--

CREATE FUNCTION grant_role(_login text, _role text) RETURNS void
    LANGUAGE plpgsql
    AS $$
declare
uid int;
rid int;
b boolean;
begin
select into uid id from benutzer where login like _login;
if not found then
raise exception 'User not found';
end if;

select into rid id from benutzer where login like _role;
if not found then
raise exception 'Role not found';
end if;

select true into b from benutzer_gr where id_benutzer = uid and id_gruppe = rid;
if found then
raise warning 'Role already granted';
else 
insert into benutzer_gr (id_benutzer,id_gruppe) values (uid,rid);
end if;
end;
$$;


ALTER FUNCTION bayeos.grant_role(_login text, _role text) OWNER TO bayeos;

--
-- Name: has_child(integer); Type: FUNCTION; Schema: bayeos; Owner: bayeos
--

CREATE FUNCTION has_child(integer) RETURNS boolean
    LANGUAGE plpgsql
    AS $_$declare
    rec record;
begin
select id into rec from objekt where id_super=$1;
if found then return true;end if;
select id into rec from verweis where id_von = $1;
if found then return true;end if;
select id into rec from verweis_extern where id_von = $1;
if found then return true;end if;
return false;
end;$_$;


ALTER FUNCTION bayeos.has_child(integer) OWNER TO bayeos;

--
-- Name: htmldecode(text); Type: FUNCTION; Schema: bayeos; Owner: bayeos
--

CREATE FUNCTION htmldecode(text) RETURNS text
    LANGUAGE plperl
    AS $_X$
my %trans=("&nbsp;"=>" ",
	   "&iexcl;"=>"¡",
	   "&cent;"=>"¢",
	   "&pound;"=>"£",
	   "&curren;"=>"€",
	   "&yen;"=>"¥",
	   "&brvbar;"=>"Š",
	   "&sect;"=>"§",
	   "&uml;"=>"š",
	   "&copy;"=>"©",
	   "&ordf;"=>"ª",
	   "&laquo;"=>"«",
	   "&not;"=>"¬",
	   "&shy;"=>"­",
	   "&reg;"=>"®",
	   "&macr;"=>"¯",
	   "&deg;"=>"°",
	   "&plusmn;"=>"±",
	   "&sup2;"=>"²",
	   "&sup3;"=>"³",
	   "&acute;"=>"Ž",
	   "&micro;"=>"µ",
	   "&para;"=>"¶",
	   "&middot;"=>"·",
	   "&cedil;"=>"ž",
	   "&sup1;"=>"¹",
	   "&ordm;"=>"º",
	   "&raquo;"=>"»",
	   "&frac14;"=>"Œ",
	   "&frac12;"=>"œ",
	   "&frac34;"=>"Ÿ",
	   "&iquest;"=>"¿",
	   "&Agrave;"=>"À",
	   "&Aacute;"=>"Á",
	   "&Acirc;"=>"Â",
	   "&Atilde;"=>"Ã",
	   "&Auml;"=>"Ä",
	   "&Aring;"=>"Å",
	   "&AElig;"=>"Æ",
	   "&Ccedil;"=>"Ç",
	   "&Egrave;"=>"È",
	   "&Eacute;"=>"É",
	   "&Ecirc;"=>"Ê",
	   "&Euml;"=>"Ë",
	   "&Igrave;"=>"Ì",
	   "&Iacute;"=>"Í",
	   "&Icirc;"=>"Î",
	   "&Iuml;"=>"Ï",
	   "&ETH;"=>"Ð",
	   "&Ntilde;"=>"Ñ",
	   "&Ograve;"=>"Ò",
	   "&Oacute;"=>"Ó",
	   "&Ocirc;"=>"Ô",
	   "&Otilde;"=>"Õ",
	   "&Ouml;"=>"Ö",
	   "&times;"=>"×",
	   "&Oslash;"=>"Ø",
	   "&Ugrave;"=>"Ù",
	   "&Uacute;"=>"Ú",
	   "&Ucirc;"=>"Û",
	   "&Uuml;"=>"Ü",
	   "&Yacute;"=>"Ý",
	   "&THORN;"=>"Þ",
	   "&szlig;"=>"ß",
	   "&agrave;"=>"à",
	   "&aacute;"=>"á",
	   "&acirc;"=>"â",
	   "&atilde;"=>"ã",
	   "&auml;"=>"ä",
	   "&aring;"=>"å",
	   "&aelig;"=>"æ",
	   "&ccedil;"=>"ç",
	   "&egrave;"=>"è",
	   "&eacute;"=>"é",
	   "&ecirc;"=>"ê",
	   "&euml;"=>"ë",
	   "&igrave;"=>"ì",
	   "&iacute;"=>"í",
	   "&icirc;"=>"î",
	   "&iuml;"=>"ï",
	   "&eth;"=>"ð",
	   "&ntilde;"=>"ñ",
	   "&ograve;"=>"ò",
	   "&oacute;"=>"ó",
	   "&ocirc;"=>"ô",
	   "&otilde;"=>"õ",
	   "&ouml;"=>"ö",
	   "&divide;"=>"÷",
	   "&oslash;"=>"ø",
	   "&ugrave;"=>"ù",
	   "&uacute;"=>"ú",
	   "&ucirc;"=>"û",
	   "&uuml;"=>"ü",
	   "&yacute;"=>"ý",
	   "&thorn;"=>"þ",
	   "&amp;"=>"&",
	   "&quot;"=>"\"",
	   "&lt;"=>"<",
	   "&gt;"=>">"
	  );

while(($key,$value)=each(%trans)){
  $_[0]=~s/$key/$value/g;
}
return $_[0];

$_X$;


ALTER FUNCTION bayeos.htmldecode(text) OWNER TO bayeos;

--
-- Name: insert_check_function_parameter(integer, integer[], double precision[]); Type: FUNCTION; Schema: bayeos; Owner: bayeos
--

CREATE FUNCTION insert_check_function_parameter(id_check integer, integer[], double precision[]) RETURNS boolean
    LANGUAGE plpgsql
    AS $_$
declare
 i int;
 m int;
 rec record;
begin
 -- Inserting Mess-IDs
 i:=0;
 for rec in select vfc.id from valfunccolumn vfc, checks c where c.id=id_check and c.fk_valfunction=vfc.fk_valfunction order by vfc.name loop
   i:=i+1;
   insert into checkcolmessung(fk_messungen,fk_valfunccolumn,fk_checks) values ($2[i],rec.id,id_check);
 end loop;

 -- Inserting Argument-Values
 i:=0;
 for rec in select vfa.id from valfuncargument vfa, checks c where c.id=id_check and c.fk_valfunction=vfa.fk_valfunction order by vfa.index loop
   i:=i+1;
   insert into valfuncargvalue(fk_checks,fk_valfuncargument,value) values (id_check,rec.id,$3[i]);
 end loop;

 return true;

end;

$_$;


ALTER FUNCTION bayeos.insert_check_function_parameter(id_check integer, integer[], double precision[]) OWNER TO bayeos;

--
-- Name: insert_labor_bod(); Type: FUNCTION; Schema: bayeos; Owner: bayeos
--

CREATE FUNCTION insert_labor_bod() RETURNS void
    LANGUAGE plpgsql
    AS $$
 begin  
	select set_user('oliver');
	set time zone 'GMT-1';
	update labordaten_bod set von = get_lab_von(kw,year), bis = get_lab_bis(kw,year);
	insert into labordaten (id,status,von,bis,wert,labornummer,genauigkeit,bestimmungsgrenze,bemerkung)
	select id,status,von,bis,wert,labornummer,genauigkeit,bestimmungsgrenze,bemerkung from labordaten_bod;
	select update_child_min_max(118123);
	delete from labordaten_bod;
 end;
$$;


ALTER FUNCTION bayeos.insert_labor_bod() OWNER TO bayeos;

--
-- Name: insert_labordaten_export(integer); Type: FUNCTION; Schema: bayeos; Owner: bayeos
--

CREATE FUNCTION insert_labordaten_export(user_id integer) RETURNS boolean
    LANGUAGE plpgsql
    AS $$declare
rec record;
i int := 0;
begin
for rec in select m.id, m.bezeichnung from messungen m, (select id from get_child_objekte(83,117662)) o where o.id = m.id and 
check_read(o.id,user_id) 
loop 
 i := i+1;
 raise notice 'i:% id:% Messung:%',i,rec.id, rec.bezeichnung;
 insert into labordaten_export (id, status, von, bis, wert, labornummer, genauigkeit, bestimmungsgrenze, bemerkung) 
 select id, status, von, bis, wert, labornummer, genauigkeit, bestimmungsgrenze, bemerkung from labordaten where id = rec.id;
end loop;
return(true);
end;
$$;


ALTER FUNCTION bayeos.insert_labordaten_export(user_id integer) OWNER TO bayeos;

--
-- Name: insert_massendaten_export(integer); Type: FUNCTION; Schema: bayeos; Owner: bayeos
--

CREATE FUNCTION insert_massendaten_export(user_id integer) RETURNS boolean
    LANGUAGE plpgsql
    AS $$declare
rec record;
i int := 0;
begin
for rec in select m.id, m.bezeichnung from messungen m, (select id from get_child_objekte(83,117661)) o where o.id = m.id and 
check_read(o.id,user_id) 
loop 
 i := i+1;
 raise notice 'i:% id:% Messung:%',i,rec.id, rec.bezeichnung;
 insert into massendaten_export (id, von, wert, status) select id, von, wert,status from massendaten where id = rec.id;
end loop;
return(true);
end;
$$;


ALTER FUNCTION bayeos.insert_massendaten_export(user_id integer) OWNER TO bayeos;

--
-- Name: insert_pivot(integer, interval); Type: FUNCTION; Schema: bayeos; Owner: bayeos
--

CREATE FUNCTION insert_pivot(integer, interval) RETURNS boolean
    LANGUAGE plpgsql
    AS $_$
declare
 p_id alias for $1; /* massendaten.id */
 p_interval alias for $2; 
 lvon constant timestamp with time zone := '1970-01-01 00:00:00';
 lbis constant timestamp with time zone := '2010-01-01 00:00:00';
 t timestamp with time zone;
 l integer := 0;
begin
 t := lvon;
 while t <= lbis loop
  insert into massendaten (id,von,wert) values (p_id,t,0);
  t := t + p_interval;
  l := l +1;
  if l%10000 = 0  then
   raise notice '% values inserted.',l;
  end if;
 end loop;
 return true;
end;
$_$;


ALTER FUNCTION bayeos.insert_pivot(integer, interval) OWNER TO bayeos;

--
-- Name: isactive(timestamp with time zone, timestamp with time zone); Type: FUNCTION; Schema: bayeos; Owner: bayeos
--

CREATE FUNCTION isactive(timestamp with time zone, timestamp with time zone) RETURNS boolean
    LANGUAGE plpgsql
    AS $_$
declare
plan_start alias for $1;
plan_end alias for $2 ;
begin
 if (plan_start is null or plan_end is null) then
  return false;
 end if;
 return (current_timestamp between plan_start and plan_end);
 end;$_$;


ALTER FUNCTION bayeos.isactive(timestamp with time zone, timestamp with time zone) OWNER TO bayeos;

--
-- Name: isimage(text); Type: FUNCTION; Schema: bayeos; Owner: bayeos
--

CREATE FUNCTION isimage(text) RETURNS boolean
    LANGUAGE plpgsql
    AS $_$ DECLARE 
     t text;
     BEGIN
     t := substring(upper($1) from '.+.((PNG)|(BMP)|(JPG)|(GIF)|(JPEG))$');
     return (t is not null);
    end;
 $_$;


ALTER FUNCTION bayeos.isimage(text) OWNER TO bayeos;

--
-- Name: ismissing(timestamp with time zone, character varying); Type: FUNCTION; Schema: bayeos; Owner: bayeos
--

CREATE FUNCTION ismissing(timestamp with time zone, character varying) RETURNS boolean
    LANGUAGE plpgsql
    AS $_$
begin
 if ($1 is null or $2 is null or $2 like 'null') then
  return false;
 end if;
 return ( (current_timestamp - quote_literal('1 ' ||$2)::interval) > $1);
 end;$_$;


ALTER FUNCTION bayeos.ismissing(timestamp with time zone, character varying) OWNER TO bayeos;

--
-- Name: iu_objekt(); Type: FUNCTION; Schema: bayeos; Owner: bayeos
--

CREATE FUNCTION iu_objekt() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
 DECLARE
 rec RECORD;
 recv RECORD;
 BEGIN
  if (NEW.id_super is null) then
     RETURN NEW;
  end if;
  -- raise notice 'iu_objekt: %',NEW.id_super;
  -- Direkt im Baum
  select into rec min(rec_start) as rmin ,max(rec_end) as rmax,min(plan_start) as pmin, max(plan_end) as pmax from
  objekt where id_super = NEW.id_super;
  update objekt set rec_start = rec.rmin, rec_end =rec.rmax, plan_start = rec.pmin, plan_end = rec.pmax
  where id = NEW.id_super;
  RETURN NEW;
 END;$$;


ALTER FUNCTION bayeos.iu_objekt() OWNER TO bayeos;

--
-- Name: iu_objekt_extern(); Type: FUNCTION; Schema: bayeos; Owner: bayeos
--

CREATE FUNCTION iu_objekt_extern() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
 DECLARE
 rec RECORD;
 BEGIN
for rec in select v.id_von, min(plan_start,NEW.plan_start) as
pmin,max(plan_end,NEW.plan_end) as pmax,min(rec_start,NEW.rec_start)
as rmin,max(rec_end,NEW.rec_end) as rmax from verweis v,objekt o
where v.id_auf=NEW.id and v.id_auf=o.id
loop
update objekt set plan_start =rec.pmin, plan_end =rec.pmax, rec_start=
rec.rmin, rec_end = rec.rmax where id= rec.id_von;
end loop;
RETURN NEW;
END;
$$;


ALTER FUNCTION bayeos.iu_objekt_extern() OWNER TO bayeos;

--
-- Name: iud_verweis(); Type: FUNCTION; Schema: bayeos; Owner: bayeos
--

CREATE FUNCTION iud_verweis() RETURNS trigger
    LANGUAGE plpgsql
    AS $$    DECLARE
     rec RECORD;
     BEGIN
       if TG_OP = 'INSERT' then
            -- raise info 'iud_verweis NEW.id_auf:%',NEW.id_auf;
            update objekt set id = id where id = NEW.id_auf;
            RETURN NEW;
        elsif TG_OP = 'DELETE' then
	    -- raise info 'iud_verweis old.id_von:%',OLD.id_von;
            update objekt set plan_start = null, plan_end = null, rec_start = null, rec_end = null where id =
OLD.id_von;            RETURN OLD;
        end if;
    END;$$;


ALTER FUNCTION bayeos.iud_verweis() OWNER TO bayeos;

--
-- Name: max(timestamp with time zone, timestamp with time zone); Type: FUNCTION; Schema: bayeos; Owner: bayeos
--

CREATE FUNCTION max(timestamp with time zone, timestamp with time zone) RETURNS timestamp with time zone
    LANGUAGE plpgsql
    AS $_$
begin
 if ($1 is null and $2 is null) then
  return null;
 end if;
 if ($1 is null) then
   return $2;
 end if;
 if ($2 is null) then
  return $1;
 end if;
 if ($1 < $2) then
  return $2;
 else
  return $1;
 end if;
end;$_$;


ALTER FUNCTION bayeos.max(timestamp with time zone, timestamp with time zone) OWNER TO bayeos;

--
-- Name: min(timestamp with time zone, timestamp with time zone); Type: FUNCTION; Schema: bayeos; Owner: bayeos
--

CREATE FUNCTION min(timestamp with time zone, timestamp with time zone) RETURNS timestamp with time zone
    LANGUAGE plpgsql
    AS $_$
begin
 if ($1 is null and $2 is null) then
  return null;
 end if;
 if ($1 is null) then
   return $2;
 end if;
 if ($2 is null) then
  return $1;
 end if;
 if ($1 > $2) then
  return $2;
 else
  return $1;
 end if;
end;$_$;


ALTER FUNCTION bayeos.min(timestamp with time zone, timestamp with time zone) OWNER TO bayeos;

--
-- Name: move_objekt(integer, integer); Type: FUNCTION; Schema: bayeos; Owner: bayeos
--

CREATE FUNCTION move_objekt(integer, integer) RETURNS boolean
    LANGUAGE plpgsql
    AS $_$
declare
 begin
 update objekt set id_super = $2 where id = $1;
 return true;
end;$_$;


ALTER FUNCTION bayeos.move_objekt(integer, integer) OWNER TO bayeos;

--
-- Name: non_empty(character varying); Type: FUNCTION; Schema: bayeos; Owner: bayeos
--

CREATE FUNCTION non_empty(character varying) RETURNS character varying
    LANGUAGE plpgsql IMMUTABLE
    AS $_$
 begin
  if $1 is null then
    return ('');
  else 
    return ($1);
  end if;
 end;
$_$;


ALTER FUNCTION bayeos.non_empty(character varying) OWNER TO bayeos;

--
-- Name: really_del_objekt(integer, integer); Type: FUNCTION; Schema: bayeos; Owner: bayeos
--

CREATE FUNCTION really_del_objekt(integer, integer) RETURNS integer
    LANGUAGE plpgsql
    AS $_$declare
anz int;
rec record;
begin
anz:=0;
/*Rechte...*/
select into rec check_write($1,$2);
if not rec.check_write then
  raise exception 'User is not allowed to delete this object'; 
end if;

/* Kind-Objekte... */
for rec in select 
really_del_objekt(id,$2) from objekt where id_super=$1 loop
  anz:=anz+rec.really_del_objekt;
end loop;

/* Objekt */
delete from objekt where id=$1;
return anz+1;

end;$_$;


ALTER FUNCTION bayeos.really_del_objekt(integer, integer) OWNER TO bayeos;

--
-- Name: recreate_his(text); Type: FUNCTION; Schema: bayeos; Owner: bayeos
--

CREATE FUNCTION recreate_his(tablename text) RETURNS boolean
    LANGUAGE plpgsql
    AS $$
declare
bol boolean;
begin
select into bol drop_his(tablename);
select into bol create_his(tablename);
return bol;
end;
$$;


ALTER FUNCTION bayeos.recreate_his(tablename text) OWNER TO bayeos;

--
-- Name: register_val_function(text, text[], text[]); Type: FUNCTION; Schema: bayeos; Owner: bayeos
--

CREATE FUNCTION register_val_function(in_name text, cols text[], arg text[]) RETURNS boolean
    LANGUAGE plpgsql
    AS $_$
declare
 i int;
 m int;
 in_id int;
begin
 in_id:=nextval('valfunction_id_seq'::regclass);
 insert into valfunction(id,name) values (in_id,in_name);
 m:=array_upper($2,1);
 for i in 1..m loop
   insert into valfunccolumn(fk_valfunction,name) values (in_id,$2[i]);
 end loop;
 m:=array_upper($3,1);
 for i in 1..m loop
   insert into valfuncargument(fk_valfunction,alias,index) values (in_id,$3[i],i);
 end loop;
 return true;
end;     
$_$;


ALTER FUNCTION bayeos.register_val_function(in_name text, cols text[], arg text[]) OWNER TO bayeos;

--
-- Name: rename_objekt_and_detail(integer, character varying, character varying, character varying); Type: FUNCTION; Schema: bayeos; Owner: bayeos
--

CREATE FUNCTION rename_objekt_and_detail(integer, character varying, character varying, character varying) RETURNS boolean
    LANGUAGE plpgsql
    AS $_$declare
i_id alias for $1;                 -- not null
i_uname alias for $2;         -- not null
i_de alias for $3;

i_en alias for $4;
s varchar(100);
tab art_objekt.detail_table%TYPE;
begin
if (i_id is null) or (i_uname is null) then
 raise exception 'Illegal argument'; 
end if;

select into tab detail_table from art_objekt where uname=i_uname;
if not found then
  raise exception 'Unknown object type'; 
end if;
if tab is null then
  raise exception 'Table name not found'; 
end if;
update objekt set de = i_de,  en = i_en where id = i_id;
s := 'update ' || tab || ' set bezeichnung = ' || quote_literal(i_de) || ' where id = ' || i_id;
execute s;
return true;
end;$_$;


ALTER FUNCTION bayeos.rename_objekt_and_detail(integer, character varying, character varying, character varying) OWNER TO bayeos;

--
-- Name: revoke_role(text, text); Type: FUNCTION; Schema: bayeos; Owner: bayeos
--

CREATE FUNCTION revoke_role(_login text, _role text) RETURNS void
    LANGUAGE plpgsql
    AS $$
declare
uid int;
rid int;
b boolean;
begin
select into uid id from benutzer where login like _login;
if not found then
raise exception 'User not found';
end if;

select into rid id from benutzer where login like _role;
if not found then
raise exception 'Role not found';
end if;

select true into b from benutzer_gr where id_benutzer = uid and id_gruppe = rid;
if not found then
raise warning 'Role not granted';
else 
delete from benutzer_gr where id_benutzer = uid and id_gruppe = rid;
end if;
end;
$$;


ALTER FUNCTION bayeos.revoke_role(_login text, _role text) OWNER TO bayeos;

--
-- Name: set_massendaten_meta(integer); Type: FUNCTION; Schema: bayeos; Owner: bayeos
--

CREATE FUNCTION set_massendaten_meta(integer) RETURNS boolean
    LANGUAGE plpgsql
    AS $_$
declare
rec record;
ivon timestamp;
ibis timestamp;
iaufloesung float;
begin
select into rec tabelle from messungen where id=$1;
if not found then return false; end if;

raise notice 'Processing mes_id %',$1;
if rec.tabelle!='MASSENDATEN' then
    raise notice 'Not massendaten';
  return false;
        end if;
        
/*von*/
select into rec von from massendaten 
where id=$1 order by von limit 1;
if not found then 
      raise notice 'No data';
   update messungen set hasdata=false where id=$1;
   return false;
end if;

ivon:=rec.von;

select into rec von from massendaten 
where id=$1 order by von desc limit 1;
ibis=rec.von;

select into rec von from massendaten 
where id=$1 order by von limit 1 offset 1;
if found then
   iaufloesung:=extract(epoch from ivon)-extract(epoch from rec.von);
else
   iaufloesung:=null;
end if;

raise notice 'Setting von to %, bis to %, aufloesung to %',ivon,ibis,iaufloesung;

update messungen set von=ivon,bis=ibis,aufloesung=iaufloesung where id=$1;
return true;
end;
$_$;


ALTER FUNCTION bayeos.set_massendaten_meta(integer) OWNER TO bayeos;

--
-- Name: set_passwd(text, text); Type: FUNCTION; Schema: bayeos; Owner: bayeos
--

CREATE FUNCTION set_passwd(_user text, _pw text) RETURNS void
    LANGUAGE plpgsql
    AS $$
begin
update benutzer set pw = crypt(_pw,gen_salt('des')) where login = _user;
end; $$;


ALTER FUNCTION bayeos.set_passwd(_user text, _pw text) OWNER TO bayeos;

--
-- Name: set_time_zone(); Type: FUNCTION; Schema: bayeos; Owner: bayeos
--

CREATE FUNCTION set_time_zone() RETURNS boolean
    LANGUAGE plpgsql
    AS $$
begin
set time zone 'GMT+1';
return true;
end;$$;


ALTER FUNCTION bayeos.set_time_zone() OWNER TO bayeos;

--
-- Name: set_user(text); Type: FUNCTION; Schema: bayeos; Owner: bayeos
--

CREATE FUNCTION set_user(_login text) RETURNS integer
    LANGUAGE plpgsql
    AS $$declare
ret int;
begin
select into ret set_userid(id) from benutzer where login = _login and locked = false;
if not found then 
	raise exception 'User not found.';
end if;
return ret;
end;$$;


ALTER FUNCTION bayeos.set_user(_login text) OWNER TO bayeos;

--
-- Name: set_userid(integer); Type: FUNCTION; Schema: bayeos; Owner: bayeos
--

CREATE FUNCTION set_userid(integer) RETURNS integer
    LANGUAGE plpgsql
    AS $_$declare
rec record;
begin
select into rec relname from pg_class 
where relname='tmp_userid' and pg_table_is_visible(oid);
if found then
  update tmp_userid set id=$1;
else
  create temp table tmp_userid(id int4);
  insert into tmp_userid values($1);
end if;
return $1;
end;$_$;


ALTER FUNCTION bayeos.set_userid(integer) OWNER TO bayeos;

--
-- Name: terminate_session(integer); Type: FUNCTION; Schema: bayeos; Owner: bayeos
--

CREATE FUNCTION terminate_session(sessionid integer) RETURNS boolean
    LANGUAGE plpgsql
    AS $$
declare 
begin
 delete from session where key = sessionid;
 return true;
end;
$$;


ALTER FUNCTION bayeos.terminate_session(sessionid integer) OWNER TO bayeos;

--
-- Name: terminate_session(integer, integer); Type: FUNCTION; Schema: bayeos; Owner: bayeos
--

CREATE FUNCTION terminate_session(sessionid integer, userid integer) RETURNS boolean
    LANGUAGE plpgsql
    AS $$
declare 
begin
 delete from session where key = sessionid and id_benutzer = userid;
 return true;
end;
$$;


ALTER FUNCTION bayeos.terminate_session(sessionid integer, userid integer) OWNER TO bayeos;

--
-- Name: terminate_session(character varying, character, integer); Type: FUNCTION; Schema: bayeos; Owner: bayeos
--

CREATE FUNCTION terminate_session(character varying, character, integer) RETURNS boolean
    LANGUAGE plpgsql
    AS $_$
declare 
 p_login alias for $1;
 p_pw alias for $2;
 p_key alias for $3;
 m_ben_id benutzer.id%TYPE;
begin
 m_ben_id = get_user_id(p_login,p_pw);
 if m_ben_id is null then 
   return false;
 end if; 
 delete from session where key = p_key and id_benutzer = m_ben_id;
 return true;
end;
$_$;


ALTER FUNCTION bayeos.terminate_session(character varying, character, integer) OWNER TO bayeos;

--
-- Name: trunc_aggr_tables(); Type: FUNCTION; Schema: bayeos; Owner: bayeos
--

CREATE FUNCTION trunc_aggr_tables() RETURNS boolean
    LANGUAGE plpgsql
    AS $$ declare
  rec record;
  tab varchar(300);
  begin
  for rec in select f.name as fname,i.name as iname from aggr_funktion f, aggr_intervall i
  loop 
   tab := 'aggr_'  || rec.fname || '_' || rec.iname ;
   raise notice 'Processing:%',tab;
   execute 'truncate table ' || tab || ';';   
  end loop;
  return true;
  end;
$$;


ALTER FUNCTION bayeos.trunc_aggr_tables() OWNER TO bayeos;

--
-- Name: u_check(); Type: FUNCTION; Schema: bayeos; Owner: bayeos
--

CREATE FUNCTION u_check() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
 DECLARE
 _status text;
 _rec record;
 BEGIN
  if (NEW.last_value = OLD.last_value OR (NEW.last_value is NULL and OLD.last_value is NULL)) then
  -- do not resend messages 
  RETURN NEW;
  else  
	if (NEW.last_value = true) then
		_status := 'OK';   
	elsif (NEW.last_value = false) then
		_status := 'CRITICAL';   
	else 
		_status := 'UNKNOWN';   
	end if;	
	-- insert alerts 
	FOR _rec IN SELECT fk_listener as lid FROM checklistener WHERE fk_checks = NEW.id
	LOOP
	 insert into alert (fk_listener,fk_checks,status) values (_rec.lid, NEW.id,_status);
        END LOOP;	
	RETURN NEW;
  end if;            
 END
 $$;


ALTER FUNCTION bayeos.u_check() OWNER TO bayeos;

--
-- Name: update_allminmax(); Type: FUNCTION; Schema: bayeos; Owner: bayeos
--

CREATE FUNCTION update_allminmax() RETURNS integer
    LANGUAGE plpgsql
    AS $$
declare 
rec record;
r record;
t boolean;
begin

-- Massendaten --
-- raise notice 'Processing Massendaten';
-- for rec in select messungen.id from messungen, objekt where messungen.id = objekt.id and objekt.id_art = 117661 loop
-- raise notice 'Processing id:%',rec.id;
-- select into r max(von),min(von) from massendaten where id = rec.id;
-- update messungen set von = r.min, bis = r.max where id = rec.id;
-- end loop;


-- Labordaten --
raise notice 'Processing Labordaten';
for rec in select messungen.id from messungen, objekt where messungen.id = objekt.id and objekt.id_art = 117662 loop
raise notice 'Processing id:%',rec.id;
select into r CASE WHEN min(von) <  min(bis) THEN min(von) ELSE min(bis) END  as min ,
CASE WHEN max(von) > max(bis) THEN max(von) ELSE max(bis) END as max from labordaten where id = rec.id;
update messungen set von = r.min, bis = r.max where id = rec.id;
end loop;

return 1;
end;$$;


ALTER FUNCTION bayeos.update_allminmax() OWNER TO bayeos;

--
-- Name: update_aufloesung(); Type: FUNCTION; Schema: bayeos; Owner: bayeos
--

CREATE FUNCTION update_aufloesung() RETURNS boolean
    LANGUAGE plpgsql
    AS $$
-- Berechnung der Aufloesung  alle Messungen
-- Naeherung ueber haeufigste Differenz in der ersten 1000 Datensaetzen
-- Oliver Archner
-- 11.09.2003
-- Benutzt plr Funktion
declare 
rec record;
r int4;
begin
-- Massendaten --
for rec in select messungen.id from messungen, objekt where messungen.id = objekt.id and objekt.id_art = 117661 loop
raise notice 'Processing id:%',rec.id;
select into r round(modediff(s.von)) from (select CAST( (extract(epoch from von)) as float8) as von from massendaten where id = rec.id order by von limit 1000) as s;
update messungen set aufloesung = round(r) where id = rec.id;
raise notice 'Aufloesung:%',r;
end loop;
return(true);
end;
$$;


ALTER FUNCTION bayeos.update_aufloesung() OWNER TO bayeos;

--
-- Name: update_child_min_max(integer); Type: FUNCTION; Schema: bayeos; Owner: bayeos
--

CREATE FUNCTION update_child_min_max(integer) RETURNS boolean
    LANGUAGE plpgsql
    AS $_$declare
  rec record;
  b boolean;
 begin  
  for rec in SELECT id, id_art from objekt where id in (select t.id FROM connectby('messungen.objekt'::text, 'id'::text, 'id_super'::text, $1::text, 0) t(id integer, id_super integer, "level" integer)) and objekt.id_art in (117661,117662)
  loop
      raise notice 'Processing id:%',rec.id;
      if (rec.id_art = 117661) then
        select into b update_massendaten_min_max(rec.id);
      elsif (rec.id_art = 117662) then
        select into b update_labordaten_min_max(rec.id);
      end if;      
  end loop;
  return(true);
 end;
$_$;


ALTER FUNCTION bayeos.update_child_min_max(integer) OWNER TO bayeos;

--
-- Name: update_lab(); Type: FUNCTION; Schema: bayeos; Owner: bayeos
--

CREATE FUNCTION update_lab() RETURNS boolean
    LANGUAGE plpgsql
    AS $$declare
 rec record;
 begin
  for rec in select l.id, l.von, l.bis from labordaten l , his_labordaten his where l.id = his.id and l.bis = his.bis and his_datum > now()- interval '1 day' and his.his_aktion = 1 and his_benutzer_id = 10005 order by his.id
loop
  update labordaten set bis = bis + interval '1 day' where id = rec.id and bis = rec.bis;
end loop;
 return true;   
 end;
$$;


ALTER FUNCTION bayeos.update_lab() OWNER TO bayeos;

--
-- Name: update_labordaten_min_max(integer); Type: FUNCTION; Schema: bayeos; Owner: bayeos
--

CREATE FUNCTION update_labordaten_min_max(integer) RETURNS boolean
    LANGUAGE plpgsql
    AS $_$declare
  r record; 
 begin
 select into r CASE WHEN min(von) <  min(bis) THEN min(von) ELSE min(bis) END  as min ,
 CASE WHEN max(von) > max(bis) THEN max(von) ELSE max(bis) END as max from labordaten where id = $1;
 raise notice 'update_labordaten: %', $1; 
update OBJEKT set rec_start = r.min, rec_end = r.max where id = $1;
 return(true);
end;
$_$;


ALTER FUNCTION bayeos.update_labordaten_min_max(integer) OWNER TO bayeos;

--
-- Name: update_massendaten_min_max(integer); Type: FUNCTION; Schema: bayeos; Owner: bayeos
--

CREATE FUNCTION update_massendaten_min_max(integer) RETURNS boolean
    LANGUAGE plpgsql
    AS $_$ declare
   r record;
  begin
  select into r max(von),min(von) from massendaten where id = $1;
  update objekt set rec_start = r.min, rec_end = r.max where id = $1;
  return(true);
end;
$_$;


ALTER FUNCTION bayeos.update_massendaten_min_max(integer) OWNER TO bayeos;

--
-- Name: update_massendaten_min_max(integer, timestamp with time zone, timestamp with time zone); Type: FUNCTION; Schema: bayeos; Owner: bayeos
--

CREATE FUNCTION update_massendaten_min_max(_id integer, _min timestamp with time zone, _max timestamp with time zone) RETURNS boolean
    LANGUAGE plpgsql
    AS $$ declare
  r record;
  min timestamptz;
  max timestamptz;
  _t timestamptz[4];  
  begin  
  select into r * from objekt where id = _id;       
   _t[1] =  r.rec_start;
   _t[2] =  r.rec_end;
   _t[3] = _min;
   _t[4] = _max; 
  min = _t[1];
  max = _t[1];
  for i in 2..4 loop
	if (_t[i] < min) then
		min = _t[i];
	end if; 
	if (_t[i] > max) then
		max = _t[i];
	end if;
  end loop; 
  update objekt set rec_start = min, rec_end = max where id = _id;
  return(true);
end;
$$;


ALTER FUNCTION bayeos.update_massendaten_min_max(_id integer, _min timestamp with time zone, _max timestamp with time zone) OWNER TO bayeos;

--
-- Name: update_objekt_utime(); Type: FUNCTION; Schema: bayeos; Owner: bayeos
--

CREATE FUNCTION update_objekt_utime() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
declare 
begin
update objekt set uTime = timenow(), id_ubenutzer = get_userid() where id = NEW.id;
return NEW;
end; $$;


ALTER FUNCTION bayeos.update_objekt_utime() OWNER TO bayeos;

--
-- Name: user(); Type: FUNCTION; Schema: bayeos; Owner: bayeos
--

CREATE FUNCTION "user"() RETURNS name
    LANGUAGE sql
    AS $$select current_user$$;


ALTER FUNCTION bayeos."user"() OWNER TO bayeos;

--
-- Name: write_his_labordaten(); Type: FUNCTION; Schema: bayeos; Owner: bayeos
--

CREATE FUNCTION write_his_labordaten() RETURNS trigger
    LANGUAGE plpgsql
    AS $$  
    BEGIN
        if TG_OP = 'INSERT' then
            insert into his_labordaten(his_aktion,id,status,von,bis,wert,labornummer,genauigkeit,bestimmungsgrenze,bemerkung)
            values (1,NEW.id,NEW.status,NEW.von,NEW.bis,NEW.wert,NEW.labornummer,NEW.genauigkeit,NEW.bestimmungsgrenze,NEW.bemerkung );
    RETURN NEW;
        elsif TG_OP = 'UPDATE' then
            insert into his_labordaten(his_aktion,id,status,von,bis,wert,labornummer,genauigkeit,bestimmungsgrenze,bemerkung)
            values (2,NEW.id,NEW.status,NEW.von,NEW.bis,NEW.wert,NEW.labornummer,NEW.genauigkeit,NEW.bestimmungsgrenze,NEW.bemerkung);
            RETURN NEW;
        elsif TG_OP = 'DELETE' then
            insert into his_labordaten(his_aktion,id,status,von,bis,wert,labornummer,genauigkeit,bestimmungsgrenze,bemerkung)
            values (3,OLD.id,OLD.status,OLD.von,OLD.bis,OLD.wert,OLD.labornummer,OLD.genauigkeit,OLD.bestimmungsgrenze,OLD.bemerkung);
    RETURN OLD;
        end if;       

    END;$$;


ALTER FUNCTION bayeos.write_his_labordaten() OWNER TO bayeos;

--
-- Name: write_his_massendaten(); Type: FUNCTION; Schema: bayeos; Owner: bayeos
--

CREATE FUNCTION write_his_massendaten() RETURNS trigger
    LANGUAGE plpgsql
    AS $$  
    BEGIN
        if TG_OP = 'INSERT' then
            insert into his_massendaten(his_aktion,id,status,von,wert,tstatus)
            values (1,NEW.id,NEW.status,NEW.von,NEW.wert,NEW.tstatus );
    RETURN NEW;
        elsif TG_OP = 'UPDATE' then
            insert into his_massendaten(his_aktion,id,status,von,wert,tstatus)
            values (2,NEW.id,NEW.status,NEW.von,NEW.wert,NEW.tstatus);
            RETURN NEW;
        elsif TG_OP = 'DELETE' then
            insert into his_massendaten(his_aktion,id,status,von,wert,tstatus)
            values (3,OLD.id,OLD.status,OLD.von,OLD.wert,OLD.tstatus);
    RETURN OLD;
        end if;       

    END;$$;


ALTER FUNCTION bayeos.write_his_massendaten() OWNER TO bayeos;

--
-- Name: aggr_avg_30min; Type: TABLE; Schema: bayeos; Owner: bayeos; Tablespace: 
--

CREATE TABLE aggr_avg_30min (
    id integer NOT NULL,
    von timestamp with time zone NOT NULL,
    wert double precision,
    counts integer NOT NULL
);


ALTER TABLE bayeos.aggr_avg_30min OWNER TO bayeos;

--
-- Name: aggr_avg_day; Type: TABLE; Schema: bayeos; Owner: bayeos; Tablespace: 
--

CREATE TABLE aggr_avg_day (
    id integer NOT NULL,
    von timestamp with time zone NOT NULL,
    wert double precision,
    counts integer NOT NULL
);


ALTER TABLE bayeos.aggr_avg_day OWNER TO bayeos;

--
-- Name: aggr_avg_hour; Type: TABLE; Schema: bayeos; Owner: bayeos; Tablespace: 
--

CREATE TABLE aggr_avg_hour (
    id integer NOT NULL,
    von timestamp with time zone NOT NULL,
    wert double precision,
    counts integer NOT NULL
);


ALTER TABLE bayeos.aggr_avg_hour OWNER TO bayeos;

--
-- Name: aggr_avg_month; Type: TABLE; Schema: bayeos; Owner: bayeos; Tablespace: 
--

CREATE TABLE aggr_avg_month (
    id integer NOT NULL,
    von timestamp with time zone NOT NULL,
    wert double precision,
    counts integer NOT NULL
);


ALTER TABLE bayeos.aggr_avg_month OWNER TO bayeos;

--
-- Name: aggr_avg_year; Type: TABLE; Schema: bayeos; Owner: bayeos; Tablespace: 
--

CREATE TABLE aggr_avg_year (
    id integer NOT NULL,
    von timestamp with time zone NOT NULL,
    wert double precision,
    counts integer NOT NULL
);


ALTER TABLE bayeos.aggr_avg_year OWNER TO bayeos;

SET default_with_oids = true;

--
-- Name: aggr_funktion; Type: TABLE; Schema: bayeos; Owner: bayeos; Tablespace: 
--

CREATE TABLE aggr_funktion (
    id integer NOT NULL,
    name character varying(30)
);


ALTER TABLE bayeos.aggr_funktion OWNER TO bayeos;

--
-- Name: aggr_funktion_id_seq; Type: SEQUENCE; Schema: bayeos; Owner: bayeos
--

CREATE SEQUENCE aggr_funktion_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER TABLE bayeos.aggr_funktion_id_seq OWNER TO bayeos;

--
-- Name: aggr_funktion_id_seq; Type: SEQUENCE OWNED BY; Schema: bayeos; Owner: bayeos
--

ALTER SEQUENCE aggr_funktion_id_seq OWNED BY aggr_funktion.id;


--
-- Name: aggr_funktion_id_seq; Type: SEQUENCE SET; Schema: bayeos; Owner: bayeos
--

SELECT pg_catalog.setval('aggr_funktion_id_seq', 1, false);


--
-- Name: aggr_intervall; Type: TABLE; Schema: bayeos; Owner: bayeos; Tablespace: 
--

CREATE TABLE aggr_intervall (
    id integer NOT NULL,
    name character varying(30),
    intervall interval NOT NULL
);


ALTER TABLE bayeos.aggr_intervall OWNER TO bayeos;

--
-- Name: aggr_intervall_id_seq; Type: SEQUENCE; Schema: bayeos; Owner: bayeos
--

CREATE SEQUENCE aggr_intervall_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER TABLE bayeos.aggr_intervall_id_seq OWNER TO bayeos;

--
-- Name: aggr_intervall_id_seq; Type: SEQUENCE OWNED BY; Schema: bayeos; Owner: bayeos
--

ALTER SEQUENCE aggr_intervall_id_seq OWNED BY aggr_intervall.id;


--
-- Name: aggr_intervall_id_seq; Type: SEQUENCE SET; Schema: bayeos; Owner: bayeos
--

SELECT pg_catalog.setval('aggr_intervall_id_seq', 1, false);


SET default_with_oids = false;

--
-- Name: aggr_max_30min; Type: TABLE; Schema: bayeos; Owner: bayeos; Tablespace: 
--

CREATE TABLE aggr_max_30min (
    id integer NOT NULL,
    von timestamp with time zone NOT NULL,
    wert double precision,
    counts integer NOT NULL
);


ALTER TABLE bayeos.aggr_max_30min OWNER TO bayeos;

--
-- Name: aggr_max_day; Type: TABLE; Schema: bayeos; Owner: bayeos; Tablespace: 
--

CREATE TABLE aggr_max_day (
    id integer NOT NULL,
    von timestamp with time zone NOT NULL,
    wert double precision,
    counts integer NOT NULL
);


ALTER TABLE bayeos.aggr_max_day OWNER TO bayeos;

--
-- Name: aggr_max_hour; Type: TABLE; Schema: bayeos; Owner: bayeos; Tablespace: 
--

CREATE TABLE aggr_max_hour (
    id integer NOT NULL,
    von timestamp with time zone NOT NULL,
    wert double precision,
    counts integer NOT NULL
);


ALTER TABLE bayeos.aggr_max_hour OWNER TO bayeos;

--
-- Name: aggr_max_month; Type: TABLE; Schema: bayeos; Owner: bayeos; Tablespace: 
--

CREATE TABLE aggr_max_month (
    id integer NOT NULL,
    von timestamp with time zone NOT NULL,
    wert double precision,
    counts integer NOT NULL
);


ALTER TABLE bayeos.aggr_max_month OWNER TO bayeos;

--
-- Name: aggr_max_year; Type: TABLE; Schema: bayeos; Owner: bayeos; Tablespace: 
--

CREATE TABLE aggr_max_year (
    id integer NOT NULL,
    von timestamp with time zone NOT NULL,
    wert double precision,
    counts integer NOT NULL
);


ALTER TABLE bayeos.aggr_max_year OWNER TO bayeos;

--
-- Name: aggr_min_30min; Type: TABLE; Schema: bayeos; Owner: bayeos; Tablespace: 
--

CREATE TABLE aggr_min_30min (
    id integer NOT NULL,
    von timestamp with time zone NOT NULL,
    wert double precision,
    counts integer NOT NULL
);


ALTER TABLE bayeos.aggr_min_30min OWNER TO bayeos;

--
-- Name: aggr_min_day; Type: TABLE; Schema: bayeos; Owner: bayeos; Tablespace: 
--

CREATE TABLE aggr_min_day (
    id integer NOT NULL,
    von timestamp with time zone NOT NULL,
    wert double precision,
    counts integer NOT NULL
);


ALTER TABLE bayeos.aggr_min_day OWNER TO bayeos;

--
-- Name: aggr_min_hour; Type: TABLE; Schema: bayeos; Owner: bayeos; Tablespace: 
--

CREATE TABLE aggr_min_hour (
    id integer NOT NULL,
    von timestamp with time zone NOT NULL,
    wert double precision,
    counts integer NOT NULL
);


ALTER TABLE bayeos.aggr_min_hour OWNER TO bayeos;

--
-- Name: aggr_min_month; Type: TABLE; Schema: bayeos; Owner: bayeos; Tablespace: 
--

CREATE TABLE aggr_min_month (
    id integer NOT NULL,
    von timestamp with time zone NOT NULL,
    wert double precision,
    counts integer NOT NULL
);


ALTER TABLE bayeos.aggr_min_month OWNER TO bayeos;

--
-- Name: aggr_min_year; Type: TABLE; Schema: bayeos; Owner: bayeos; Tablespace: 
--

CREATE TABLE aggr_min_year (
    id integer NOT NULL,
    von timestamp with time zone NOT NULL,
    wert double precision,
    counts integer NOT NULL
);


ALTER TABLE bayeos.aggr_min_year OWNER TO bayeos;

--
-- Name: aggr_sum_30min; Type: TABLE; Schema: bayeos; Owner: bayeos; Tablespace: 
--

CREATE TABLE aggr_sum_30min (
    id integer NOT NULL,
    von timestamp with time zone NOT NULL,
    wert double precision,
    counts integer NOT NULL
);


ALTER TABLE bayeos.aggr_sum_30min OWNER TO bayeos;

--
-- Name: aggr_sum_day; Type: TABLE; Schema: bayeos; Owner: bayeos; Tablespace: 
--

CREATE TABLE aggr_sum_day (
    id integer NOT NULL,
    von timestamp with time zone NOT NULL,
    wert double precision,
    counts integer NOT NULL
);


ALTER TABLE bayeos.aggr_sum_day OWNER TO bayeos;

--
-- Name: aggr_sum_hour; Type: TABLE; Schema: bayeos; Owner: bayeos; Tablespace: 
--

CREATE TABLE aggr_sum_hour (
    id integer NOT NULL,
    von timestamp with time zone NOT NULL,
    wert double precision,
    counts integer NOT NULL
);


ALTER TABLE bayeos.aggr_sum_hour OWNER TO bayeos;

--
-- Name: aggr_sum_month; Type: TABLE; Schema: bayeos; Owner: bayeos; Tablespace: 
--

CREATE TABLE aggr_sum_month (
    id integer NOT NULL,
    von timestamp with time zone NOT NULL,
    wert double precision,
    counts integer NOT NULL
);


ALTER TABLE bayeos.aggr_sum_month OWNER TO bayeos;

--
-- Name: aggr_sum_year; Type: TABLE; Schema: bayeos; Owner: bayeos; Tablespace: 
--

CREATE TABLE aggr_sum_year (
    id integer NOT NULL,
    von timestamp with time zone NOT NULL,
    wert double precision,
    counts integer NOT NULL
);


ALTER TABLE bayeos.aggr_sum_year OWNER TO bayeos;

--
-- Name: alert; Type: TABLE; Schema: bayeos; Owner: bayeos; Tablespace: 
--

CREATE TABLE alert (
    id integer NOT NULL,
    fk_listener integer NOT NULL,
    fk_checks integer NOT NULL,
    status text
);


ALTER TABLE bayeos.alert OWNER TO bayeos;

--
-- Name: alert_id_seq; Type: SEQUENCE; Schema: bayeos; Owner: bayeos
--

CREATE SEQUENCE alert_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER TABLE bayeos.alert_id_seq OWNER TO bayeos;

--
-- Name: alert_id_seq; Type: SEQUENCE OWNED BY; Schema: bayeos; Owner: bayeos
--

ALTER SEQUENCE alert_id_seq OWNED BY alert.id;


--
-- Name: alert_id_seq; Type: SEQUENCE SET; Schema: bayeos; Owner: bayeos
--

SELECT pg_catalog.setval('alert_id_seq', 1, false);


SET default_with_oids = true;

--
-- Name: arch_his_log; Type: TABLE; Schema: bayeos; Owner: bayeos; Tablespace: 
--

CREATE TABLE arch_his_log (
    id integer NOT NULL,
    name text NOT NULL,
    min_id integer,
    max_id integer,
    min_datum timestamp with time zone,
    max_datum timestamp with time zone,
    counts integer,
    von timestamp with time zone DEFAULT now() NOT NULL
);


ALTER TABLE bayeos.arch_his_log OWNER TO bayeos;

--
-- Name: arch_his_log_id_seq; Type: SEQUENCE; Schema: bayeos; Owner: bayeos
--

CREATE SEQUENCE arch_his_log_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER TABLE bayeos.arch_his_log_id_seq OWNER TO bayeos;

--
-- Name: arch_his_log_id_seq; Type: SEQUENCE OWNED BY; Schema: bayeos; Owner: bayeos
--

ALTER SEQUENCE arch_his_log_id_seq OWNED BY arch_his_log.id;


--
-- Name: arch_his_log_id_seq; Type: SEQUENCE SET; Schema: bayeos; Owner: bayeos
--

SELECT pg_catalog.setval('arch_his_log_id_seq', 1, false);


SET default_with_oids = false;

--
-- Name: art_objekt; Type: TABLE; Schema: bayeos; Owner: bayeos; Tablespace: 
--

CREATE TABLE art_objekt (
    id integer NOT NULL,
    uname character varying(40) NOT NULL,
    de character varying(255),
    en character varying(255),
    kurz text,
    detail_table character varying(30)
);


ALTER TABLE bayeos.art_objekt OWNER TO bayeos;

--
-- Name: auth_db; Type: TABLE; Schema: bayeos; Owner: bayeos; Tablespace: 
--

CREATE TABLE auth_db (
    id integer NOT NULL,
    url text NOT NULL,
    name text NOT NULL,
    query_stmt text NOT NULL,
    username text NOT NULL,
    password text,
    update_stmt text
);


ALTER TABLE bayeos.auth_db OWNER TO bayeos;

--
-- Name: auth_db_id_seq; Type: SEQUENCE; Schema: bayeos; Owner: bayeos
--

CREATE SEQUENCE auth_db_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER TABLE bayeos.auth_db_id_seq OWNER TO bayeos;

--
-- Name: auth_db_id_seq; Type: SEQUENCE OWNED BY; Schema: bayeos; Owner: bayeos
--

ALTER SEQUENCE auth_db_id_seq OWNED BY auth_db.id;


--
-- Name: auth_db_id_seq; Type: SEQUENCE SET; Schema: bayeos; Owner: bayeos
--

SELECT pg_catalog.setval('auth_db_id_seq', 2, true);


--
-- Name: auth_ldap; Type: TABLE; Schema: bayeos; Owner: bayeos; Tablespace: 
--

CREATE TABLE auth_ldap (
    id integer NOT NULL,
    name text NOT NULL,
    host text,
    dn text,
    ssl boolean DEFAULT false NOT NULL,
    keystore_path text
);


ALTER TABLE bayeos.auth_ldap OWNER TO bayeos;

--
-- Name: auth_ldap_id_seq; Type: SEQUENCE; Schema: bayeos; Owner: bayeos
--

CREATE SEQUENCE auth_ldap_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER TABLE bayeos.auth_ldap_id_seq OWNER TO bayeos;

--
-- Name: auth_ldap_id_seq; Type: SEQUENCE OWNED BY; Schema: bayeos; Owner: bayeos
--

ALTER SEQUENCE auth_ldap_id_seq OWNED BY auth_ldap.id;


--
-- Name: auth_ldap_id_seq; Type: SEQUENCE SET; Schema: bayeos; Owner: bayeos
--

SELECT pg_catalog.setval('auth_ldap_id_seq', 1, false);


--
-- Name: benutzer; Type: TABLE; Schema: bayeos; Owner: bayeos; Tablespace: 
--

CREATE TABLE benutzer (
    id integer NOT NULL,
    login character varying(40) NOT NULL,
    pw character(13),
    von date,
    bis date,
    locked boolean DEFAULT false,
    admin boolean DEFAULT false,
    fk_auth_ldap integer,
    fk_auth_db integer
);


ALTER TABLE bayeos.benutzer OWNER TO bayeos;

--
-- Name: benutzer_gr; Type: TABLE; Schema: bayeos; Owner: bayeos; Tablespace: 
--

CREATE TABLE benutzer_gr (
    id_benutzer integer NOT NULL,
    id_gruppe integer NOT NULL
);


ALTER TABLE bayeos.benutzer_gr OWNER TO bayeos;

--
-- Name: calibration; Type: TABLE; Schema: bayeos; Owner: bayeos; Tablespace: 
--

CREATE TABLE calibration (
    id integer NOT NULL,
    id_device integer NOT NULL,
    wann timestamp without time zone NOT NULL
);


ALTER TABLE bayeos.calibration OWNER TO bayeos;

--
-- Name: calibration_data; Type: TABLE; Schema: bayeos; Owner: bayeos; Tablespace: 
--

CREATE TABLE calibration_data (
    id_cal integer NOT NULL,
    x double precision NOT NULL,
    y double precision NOT NULL
);


ALTER TABLE bayeos.calibration_data OWNER TO bayeos;

--
-- Name: calibration_id_seq; Type: SEQUENCE; Schema: bayeos; Owner: bayeos
--

CREATE SEQUENCE calibration_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER TABLE bayeos.calibration_id_seq OWNER TO bayeos;

--
-- Name: calibration_id_seq; Type: SEQUENCE OWNED BY; Schema: bayeos; Owner: bayeos
--

ALTER SEQUENCE calibration_id_seq OWNED BY calibration.id;


--
-- Name: calibration_id_seq; Type: SEQUENCE SET; Schema: bayeos; Owner: bayeos
--

SELECT pg_catalog.setval('calibration_id_seq', 1, false);


--
-- Name: checkcolmessung; Type: TABLE; Schema: bayeos; Owner: bayeos; Tablespace: 
--

CREATE TABLE checkcolmessung (
    fk_messungen integer NOT NULL,
    fk_valfunccolumn integer NOT NULL,
    fk_checks integer NOT NULL,
    fk_interval integer,
    fk_function integer
);


ALTER TABLE bayeos.checkcolmessung OWNER TO bayeos;

--
-- Name: checklistener; Type: TABLE; Schema: bayeos; Owner: bayeos; Tablespace: 
--

CREATE TABLE checklistener (
    fk_listener integer NOT NULL,
    fk_checks integer NOT NULL
);


ALTER TABLE bayeos.checklistener OWNER TO bayeos;

--
-- Name: checks; Type: TABLE; Schema: bayeos; Owner: bayeos; Tablespace: 
--

CREATE TABLE checks (
    id integer NOT NULL,
    fk_valfunction integer NOT NULL,
    name text NOT NULL,
    cycle_interval interval NOT NULL,
    last_run timestamp with time zone,
    last_value boolean,
    data_interval interval NOT NULL
);


ALTER TABLE bayeos.checks OWNER TO bayeos;

--
-- Name: checks_id_seq; Type: SEQUENCE; Schema: bayeos; Owner: bayeos
--

CREATE SEQUENCE checks_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER TABLE bayeos.checks_id_seq OWNER TO bayeos;

--
-- Name: checks_id_seq; Type: SEQUENCE OWNED BY; Schema: bayeos; Owner: bayeos
--

ALTER SEQUENCE checks_id_seq OWNED BY checks.id;


--
-- Name: checks_id_seq; Type: SEQUENCE SET; Schema: bayeos; Owner: bayeos
--

SELECT pg_catalog.setval('checks_id_seq', 1, false);


--
-- Name: coordinate_ref_sys; Type: TABLE; Schema: bayeos; Owner: bayeos; Tablespace: 
--

CREATE TABLE coordinate_ref_sys (
    id integer NOT NULL,
    short_name text NOT NULL,
    fk_srid_id integer NOT NULL
);


ALTER TABLE bayeos.coordinate_ref_sys OWNER TO bayeos;

--
-- Name: coordinate_ref_sys_id_seq; Type: SEQUENCE; Schema: bayeos; Owner: bayeos
--

CREATE SEQUENCE coordinate_ref_sys_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER TABLE bayeos.coordinate_ref_sys_id_seq OWNER TO bayeos;

--
-- Name: coordinate_ref_sys_id_seq; Type: SEQUENCE OWNED BY; Schema: bayeos; Owner: bayeos
--

ALTER SEQUENCE coordinate_ref_sys_id_seq OWNED BY coordinate_ref_sys.id;


--
-- Name: coordinate_ref_sys_id_seq; Type: SEQUENCE SET; Schema: bayeos; Owner: bayeos
--

SELECT pg_catalog.setval('coordinate_ref_sys_id_seq', 5, true);


--
-- Name: einheiten; Type: TABLE; Schema: bayeos; Owner: bayeos; Tablespace: 
--

CREATE TABLE einheiten (
    id integer NOT NULL,
    bezeichnung character varying(255),
    beschreibung text,
    symbol text
);


ALTER TABLE bayeos.einheiten OWNER TO bayeos;

--
-- Name: geraete; Type: TABLE; Schema: bayeos; Owner: bayeos; Tablespace: 
--

CREATE TABLE geraete (
    id integer NOT NULL,
    bezeichnung character varying(255),
    beschreibung text,
    seriennr character varying(255)
);


ALTER TABLE bayeos.geraete OWNER TO bayeos;

--
-- Name: his_labordaten_id; Type: SEQUENCE; Schema: bayeos; Owner: bayeos
--

CREATE SEQUENCE his_labordaten_id
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER TABLE bayeos.his_labordaten_id OWNER TO bayeos;

--
-- Name: his_labordaten_id; Type: SEQUENCE SET; Schema: bayeos; Owner: bayeos
--

SELECT pg_catalog.setval('his_labordaten_id', 1, false);


--
-- Name: his_labordaten; Type: TABLE; Schema: bayeos; Owner: bayeos; Tablespace: 
--

CREATE TABLE his_labordaten (
    id integer,
    status smallint,
    von timestamp with time zone,
    bis timestamp with time zone,
    wert real,
    labornummer character varying(32),
    genauigkeit real,
    bestimmungsgrenze real,
    bemerkung character varying(64),
    his_id integer DEFAULT nextval('his_labordaten_id'::regclass) NOT NULL,
    his_datum timestamp with time zone DEFAULT now() NOT NULL,
    his_benutzer_id integer DEFAULT get_userid(),
    his_aktion smallint DEFAULT 1
);


ALTER TABLE bayeos.his_labordaten OWNER TO bayeos;

--
-- Name: his_massendaten_id; Type: SEQUENCE; Schema: bayeos; Owner: bayeos
--

CREATE SEQUENCE his_massendaten_id
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER TABLE bayeos.his_massendaten_id OWNER TO bayeos;

--
-- Name: his_massendaten_id; Type: SEQUENCE SET; Schema: bayeos; Owner: bayeos
--

SELECT pg_catalog.setval('his_massendaten_id', 1, false);


--
-- Name: his_massendaten; Type: TABLE; Schema: bayeos; Owner: bayeos; Tablespace: 
--

CREATE TABLE his_massendaten (
    id integer,
    status smallint,
    von timestamp with time zone,
    wert real,
    tstatus smallint,
    his_id integer DEFAULT nextval('his_massendaten_id'::regclass) NOT NULL,
    his_datum timestamp with time zone DEFAULT now() NOT NULL,
    his_benutzer_id integer DEFAULT get_userid(),
    his_aktion smallint DEFAULT 1
);


ALTER TABLE bayeos.his_massendaten OWNER TO bayeos;

SET default_with_oids = true;

--
-- Name: intervaltyp; Type: TABLE; Schema: bayeos; Owner: bayeos; Tablespace: 
--

CREATE TABLE intervaltyp (
    id integer NOT NULL,
    bezeichnung character varying(200) NOT NULL
);


ALTER TABLE bayeos.intervaltyp OWNER TO bayeos;

--
-- Name: intervaltyp_id_seq; Type: SEQUENCE; Schema: bayeos; Owner: bayeos
--

CREATE SEQUENCE intervaltyp_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER TABLE bayeos.intervaltyp_id_seq OWNER TO bayeos;

--
-- Name: intervaltyp_id_seq; Type: SEQUENCE OWNED BY; Schema: bayeos; Owner: bayeos
--

ALTER SEQUENCE intervaltyp_id_seq OWNED BY intervaltyp.id;


--
-- Name: intervaltyp_id_seq; Type: SEQUENCE SET; Schema: bayeos; Owner: bayeos
--

SELECT pg_catalog.setval('intervaltyp_id_seq', 1, false);


SET default_with_oids = false;

--
-- Name: kompartimente; Type: TABLE; Schema: bayeos; Owner: bayeos; Tablespace: 
--

CREATE TABLE kompartimente (
    id integer NOT NULL,
    bezeichnung character varying(255),
    beschreibung text
);


ALTER TABLE bayeos.kompartimente OWNER TO bayeos;

--
-- Name: labordaten; Type: TABLE; Schema: bayeos; Owner: bayeos; Tablespace: 
--

CREATE TABLE labordaten (
    id integer NOT NULL,
    status smallint DEFAULT 0 NOT NULL,
    von timestamp with time zone,
    bis timestamp with time zone NOT NULL,
    wert real,
    labornummer character varying(32),
    genauigkeit real,
    bestimmungsgrenze real,
    bemerkung character varying(64),
    CONSTRAINT labordaten_von_bis CHECK ((von <= bis))
);


ALTER TABLE bayeos.labordaten OWNER TO bayeos;

--
-- Name: listener; Type: TABLE; Schema: bayeos; Owner: bayeos; Tablespace: 
--

CREATE TABLE listener (
    id integer NOT NULL,
    adress text
);


ALTER TABLE bayeos.listener OWNER TO bayeos;

--
-- Name: listener_id_seq; Type: SEQUENCE; Schema: bayeos; Owner: bayeos
--

CREATE SEQUENCE listener_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER TABLE bayeos.listener_id_seq OWNER TO bayeos;

--
-- Name: listener_id_seq; Type: SEQUENCE OWNED BY; Schema: bayeos; Owner: bayeos
--

ALTER SEQUENCE listener_id_seq OWNED BY listener.id;


--
-- Name: listener_id_seq; Type: SEQUENCE SET; Schema: bayeos; Owner: bayeos
--

SELECT pg_catalog.setval('listener_id_seq', 1, false);


--
-- Name: massendaten; Type: TABLE; Schema: bayeos; Owner: bayeos; Tablespace: 
--

CREATE TABLE massendaten (
    id integer NOT NULL,
    status smallint DEFAULT 0 NOT NULL,
    von timestamp with time zone NOT NULL,
    wert real NOT NULL,
    tstatus smallint DEFAULT 0
);


ALTER TABLE bayeos.massendaten OWNER TO bayeos;

--
-- Name: messungen; Type: TABLE; Schema: bayeos; Owner: bayeos; Tablespace: 
--

CREATE TABLE messungen (
    id integer NOT NULL,
    id_ora integer,
    bezeichnung character varying(255),
    beschreibung text,
    aufloesung integer DEFAULT 600,
    tabelle character varying(255),
    id_intervaltyp integer DEFAULT 0,
    fk_timezone_id integer DEFAULT 2 NOT NULL
);


ALTER TABLE bayeos.messungen OWNER TO bayeos;

--
-- Name: COLUMN messungen.id_intervaltyp; Type: COMMENT; Schema: bayeos; Owner: bayeos
--

COMMENT ON COLUMN messungen.id_intervaltyp IS 'Intervalltyp bei Massendaten zur Kennzeichung des Startzeitpunkts';


--
-- Name: messziele; Type: TABLE; Schema: bayeos; Owner: bayeos; Tablespace: 
--

CREATE TABLE messziele (
    id integer NOT NULL,
    bezeichnung character varying(255),
    beschreibung text,
    formel character varying(255)
);


ALTER TABLE bayeos.messziele OWNER TO bayeos;

--
-- Name: objekt_extern; Type: TABLE; Schema: bayeos; Owner: bayeos; Tablespace: 
--

--
-- Name: objekt_id; Type: SEQUENCE; Schema: bayeos; Owner: bayeos
--

CREATE SEQUENCE objekt_id
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    MAXVALUE 2147483647
    CACHE 1;


ALTER TABLE bayeos.objekt_id OWNER TO bayeos;

--
-- Name: objekt_id; Type: SEQUENCE SET; Schema: bayeos; Owner: bayeos
--

SELECT pg_catalog.setval('objekt_id', 200100, true);


--
-- Name: seq_ses_id; Type: SEQUENCE; Schema: bayeos; Owner: bayeos
--

CREATE SEQUENCE seq_ses_id
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    MAXVALUE 2147483647
    CACHE 10;


ALTER TABLE bayeos.seq_ses_id OWNER TO bayeos;

--
-- Name: seq_ses_id; Type: SEQUENCE SET; Schema: bayeos; Owner: bayeos
--

SELECT pg_catalog.setval('seq_ses_id', 90, true);


--
-- Name: session; Type: TABLE; Schema: bayeos; Owner: bayeos; Tablespace: 
--

CREATE TABLE session (
    id integer DEFAULT nextval(('seq_ses_id'::text)::regclass) NOT NULL,
    id_benutzer integer NOT NULL,
    von timestamp with time zone DEFAULT ('now'::text)::timestamp without time zone,
    key integer NOT NULL
);


ALTER TABLE bayeos.session OWNER TO bayeos;

--
-- Name: stati; Type: TABLE; Schema: bayeos; Owner: bayeos; Tablespace: 
--

CREATE TABLE stati (
    id integer NOT NULL,
    bezeichnung character varying(254) NOT NULL,
    beschreibung text
);


ALTER TABLE bayeos.stati OWNER TO bayeos;

--
-- Name: TABLE stati; Type: COMMENT; Schema: bayeos; Owner: bayeos
--

COMMENT ON TABLE stati IS 'Referenztabelle Status';


SET default_with_oids = true;

--
-- Name: sys_variablen; Type: TABLE; Schema: bayeos; Owner: bayeos; Tablespace: 
--

CREATE TABLE sys_variablen (
    name character varying(30) NOT NULL,
    value character varying(400),
    description character varying(400)
);


ALTER TABLE bayeos.sys_variablen OWNER TO bayeos;

SET default_with_oids = false;

--
-- Name: timezone; Type: TABLE; Schema: bayeos; Owner: bayeos; Tablespace: 
--

CREATE TABLE timezone (
    id integer NOT NULL,
    name text NOT NULL
);


ALTER TABLE bayeos.timezone OWNER TO bayeos;

--
-- Name: timezone_id_seq; Type: SEQUENCE; Schema: bayeos; Owner: bayeos
--

CREATE SEQUENCE timezone_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER TABLE bayeos.timezone_id_seq OWNER TO bayeos;

--
-- Name: timezone_id_seq; Type: SEQUENCE OWNED BY; Schema: bayeos; Owner: bayeos
--

ALTER SEQUENCE timezone_id_seq OWNED BY timezone.id;


--
-- Name: timezone_id_seq; Type: SEQUENCE SET; Schema: bayeos; Owner: bayeos
--

SELECT pg_catalog.setval('timezone_id_seq', 1, false);


--
-- Name: tstati; Type: TABLE; Schema: bayeos; Owner: bayeos; Tablespace: 
--

CREATE TABLE tstati (
    id smallint NOT NULL,
    name text NOT NULL
);


ALTER TABLE bayeos.tstati OWNER TO bayeos;

--
-- Name: TABLE tstati; Type: COMMENT; Schema: bayeos; Owner: bayeos
--

COMMENT ON TABLE tstati IS 'Lookup Tabelle für Zeitstatus ';


--
-- Name: umrechnungen; Type: TABLE; Schema: bayeos; Owner: bayeos; Tablespace: 
--

CREATE TABLE umrechnungen (
    id_von integer NOT NULL,
    id_nach integer NOT NULL,
    funktion text,
    bezeichnung character varying(255),
    beschreibung text
);


ALTER TABLE bayeos.umrechnungen OWNER TO bayeos;

--
-- Name: v_objekt; Type: VIEW; Schema: bayeos; Owner: bayeos
--

CREATE VIEW v_objekt AS
    SELECT obj.id, obj.id_super, art_objekt.uname AS objektart, obj.id_art, obj.id_cbenutzer, get_userlogin(obj.id_cbenutzer) AS cbenutzer, obj.ctime, obj.id_ubenutzer, get_userlogin(obj.id_ubenutzer) AS ubenutzer, obj.utime, obj.dtime, obj.de, obj.en, obj.plan_start, obj.plan_end, obj.rec_start, obj.rec_end, obj.inherit_perm FROM objekt obj, art_objekt WHERE (obj.id_art = art_objekt.id);


ALTER TABLE bayeos.v_objekt OWNER TO bayeos;

--
-- Name: v_benutzer; Type: VIEW; Schema: bayeos; Owner: bayeos
--

CREATE VIEW v_benutzer AS
    SELECT vo.id, vo.id_super, vo.objektart, vo.id_art, vo.id_cbenutzer, vo.cbenutzer, vo.ctime, vo.id_ubenutzer, vo.ubenutzer, vo.utime, vo.dtime, vo.de, vo.en, vo.plan_start, vo.plan_end, vo.rec_start, vo.rec_end, vo.inherit_perm, d.login, d.von, d.bis, d.locked FROM v_objekt vo, benutzer d WHERE ((vo.id = d.id) AND (vo.id_art = 100002));


ALTER TABLE bayeos.v_benutzer OWNER TO bayeos;

--
-- Name: v_benutzer_gruppe; Type: VIEW; Schema: bayeos; Owner: bayeos
--

CREATE VIEW v_benutzer_gruppe AS
    SELECT gr.id_benutzer, a.login AS benutzer, gr.id_gruppe, b.login AS gruppe FROM benutzer_gr gr, benutzer a, benutzer b WHERE ((gr.id_benutzer = a.id) AND (gr.id_gruppe = b.id)) ORDER BY b.login, a.login;


ALTER TABLE bayeos.v_benutzer_gruppe OWNER TO bayeos;

--
-- Name: zugriff; Type: TABLE; Schema: bayeos; Owner: bayeos; Tablespace: 
--

CREATE TABLE zugriff (
    id_obj integer NOT NULL,
    id_benutzer integer NOT NULL,
    read boolean DEFAULT true NOT NULL,
    write boolean DEFAULT true NOT NULL,
    exec boolean DEFAULT true NOT NULL,
    inherit boolean DEFAULT true
);


ALTER TABLE bayeos.zugriff OWNER TO bayeos;

--
-- Name: v_benutzer_objekt_zugriff; Type: VIEW; Schema: bayeos; Owner: bayeos
--

CREATE VIEW v_benutzer_objekt_zugriff AS
    SELECT b.login AS benutzer, b.id AS benutzer_id, o.de AS objekt, o.id AS objekt_id, a.de AS objekt_art, z.read, z.write, z.exec, z.inherit FROM zugriff z, objekt o, benutzer b, art_objekt a WHERE (((b.id = z.id_benutzer) AND (o.id = z.id_obj)) AND (o.id_art = a.id));


ALTER TABLE bayeos.v_benutzer_objekt_zugriff OWNER TO bayeos;

--
-- Name: v_gruppe; Type: VIEW; Schema: bayeos; Owner: bayeos
--

CREATE VIEW v_gruppe AS
    SELECT vo.id, vo.id_super, vo.objektart, vo.id_art, vo.id_cbenutzer, vo.cbenutzer, vo.ctime, vo.id_ubenutzer, vo.ubenutzer, vo.utime, vo.dtime, vo.de, vo.en, vo.plan_start, vo.plan_end, vo.rec_start, vo.rec_end, vo.inherit_perm, d.login, d.von, d.bis, d.locked FROM v_objekt vo, benutzer d WHERE ((vo.id = d.id) AND (vo.id_art = 100003));


ALTER TABLE bayeos.v_gruppe OWNER TO bayeos;

--
-- Name: v_mess_einheit; Type: VIEW; Schema: bayeos; Owner: bayeos
--

CREATE VIEW v_mess_einheit AS
    SELECT vo.id, vo.id_super, vo.objektart, vo.id_art, vo.id_cbenutzer, vo.cbenutzer, vo.ctime, vo.id_ubenutzer, vo.ubenutzer, vo.utime, vo.dtime, vo.de, vo.en, vo.plan_start, vo.plan_end, vo.rec_start, vo.rec_end, vo.inherit_perm, d.bezeichnung, d.beschreibung, d.symbol FROM v_objekt vo, einheiten d WHERE ((vo.id = d.id) AND (vo.id_art = 117665));


ALTER TABLE bayeos.v_mess_einheit OWNER TO bayeos;

--
-- Name: v_mess_geraet; Type: VIEW; Schema: bayeos; Owner: bayeos
--

CREATE VIEW v_mess_geraet AS
    SELECT vo.id, vo.id_super, vo.objektart, vo.id_art, vo.id_cbenutzer, vo.cbenutzer, vo.ctime, vo.id_ubenutzer, vo.ubenutzer, vo.utime, vo.dtime, vo.de, vo.en, vo.plan_start, vo.plan_end, vo.rec_start, vo.rec_end, vo.inherit_perm, d.bezeichnung, d.beschreibung, d.seriennr FROM v_objekt vo, geraete d WHERE ((vo.id = d.id) AND (vo.id_art = 100007));


ALTER TABLE bayeos.v_mess_geraet OWNER TO bayeos;

--
-- Name: v_mess_kompartiment; Type: VIEW; Schema: bayeos; Owner: bayeos
--

CREATE VIEW v_mess_kompartiment AS
    SELECT vo.id, vo.id_super, vo.objektart, vo.id_art, vo.id_cbenutzer, vo.cbenutzer, vo.ctime, vo.id_ubenutzer, vo.ubenutzer, vo.utime, vo.dtime, vo.de, vo.en, vo.plan_start, vo.plan_end, vo.rec_start, vo.rec_end, vo.inherit_perm, d.bezeichnung, d.beschreibung FROM v_objekt vo, kompartimente d WHERE ((vo.id = d.id) AND (vo.id_art = 100008));


ALTER TABLE bayeos.v_mess_kompartiment OWNER TO bayeos;

--
-- Name: v_mess_ort; Type: VIEW; Schema: bayeos; Owner: bayeos
--

CREATE VIEW v_mess_ort AS
    SELECT vo.id, vo.id_super, vo.objektart, vo.id_art, vo.id_cbenutzer, vo.cbenutzer, vo.ctime, vo.id_ubenutzer, vo.ubenutzer, vo.utime, vo.dtime, vo.de, vo.en, vo.plan_start, vo.plan_end, vo.rec_start, vo.rec_end, vo.inherit_perm, d.bezeichnung, d.beschreibung, d.x, d.y, d.z, d.fk_crs_id FROM v_objekt vo, messorte d WHERE ((vo.id = d.id) AND (vo.id_art = 100009));


ALTER TABLE bayeos.v_mess_ort OWNER TO bayeos;

--
-- Name: v_mess_ziel; Type: VIEW; Schema: bayeos; Owner: bayeos
--

CREATE VIEW v_mess_ziel AS
    SELECT vo.id, vo.id_super, vo.objektart, vo.id_art, vo.id_cbenutzer, vo.cbenutzer, vo.ctime, vo.id_ubenutzer, vo.ubenutzer, vo.utime, vo.dtime, vo.de, vo.en, vo.plan_start, vo.plan_end, vo.rec_start, vo.rec_end, vo.inherit_perm, d.bezeichnung, d.beschreibung, d.formel FROM v_objekt vo, messziele d WHERE ((vo.id = d.id) AND (vo.id_art = 100010));


ALTER TABLE bayeos.v_mess_ziel OWNER TO bayeos;

--
-- Name: v_messung; Type: VIEW; Schema: bayeos; Owner: bayeos
--

CREATE VIEW v_messung AS
    SELECT m.id, o.id_super, m.bezeichnung, m.beschreibung, CASE WHEN (o.id_art = 117661) THEN 0 WHEN (o.id_art = 117662) THEN 1 ELSE NULL::integer END AS type FROM messungen m, objekt o WHERE ((m.id = o.id) AND ((o.id_art = 117661) OR (o.id_art = 117662)));


ALTER TABLE bayeos.v_messung OWNER TO bayeos;

--
-- Name: v_messung_labordaten; Type: VIEW; Schema: bayeos; Owner: bayeos
--

CREATE VIEW v_messung_labordaten AS
    SELECT vo.id, vo.id_super, vo.objektart, vo.id_art, vo.id_cbenutzer, vo.cbenutzer, vo.ctime, vo.id_ubenutzer, vo.ubenutzer, vo.utime, vo.dtime, vo.de, vo.en, vo.plan_start, vo.plan_end, vo.rec_start, vo.rec_end, vo.inherit_perm, d.bezeichnung, d.beschreibung, d.aufloesung, d.id_intervaltyp, d.fk_timezone_id FROM v_objekt vo, messungen d WHERE ((vo.id = d.id) AND (vo.id_art = 117662));


ALTER TABLE bayeos.v_messung_labordaten OWNER TO bayeos;

--
-- Name: v_messung_massendaten; Type: VIEW; Schema: bayeos; Owner: bayeos
--

CREATE VIEW v_messung_massendaten AS
    SELECT vo.id, vo.id_super, vo.objektart, vo.id_art, vo.id_cbenutzer, vo.cbenutzer, vo.ctime, vo.id_ubenutzer, vo.ubenutzer, vo.utime, vo.dtime, vo.de, vo.en, vo.plan_start, vo.plan_end, vo.rec_start, vo.rec_end, vo.inherit_perm, d.bezeichnung, d.beschreibung, d.aufloesung, d.id_intervaltyp, d.fk_timezone_id FROM v_objekt vo, messungen d WHERE ((vo.id = d.id) AND (vo.id_art = 117661));


ALTER TABLE bayeos.v_messung_massendaten OWNER TO bayeos;

--
-- Name: v_messung_ordner; Type: VIEW; Schema: bayeos; Owner: bayeos
--

CREATE VIEW v_messung_ordner AS
    SELECT vo.id, vo.id_super, vo.objektart, vo.id_art, vo.id_cbenutzer, vo.cbenutzer, vo.ctime, vo.id_ubenutzer, vo.ubenutzer, vo.utime, vo.dtime, vo.de, vo.en, vo.plan_start, vo.plan_end, vo.rec_start, vo.rec_end, vo.inherit_perm, d.bezeichnung, d.beschreibung, d.aufloesung, d.id_intervaltyp, d.fk_timezone_id FROM v_objekt vo, messungen d WHERE ((vo.id = d.id) AND (vo.id_art = 117660));


ALTER TABLE bayeos.v_messung_ordner OWNER TO bayeos;

--
-- Name: v_messungen_all; Type: VIEW; Schema: bayeos; Owner: bayeos
--

CREATE VIEW v_messungen_all AS
    SELECT massendaten.id, massendaten.status, massendaten.von, massendaten.wert FROM massendaten UNION SELECT labordaten.id, labordaten.status, labordaten.bis AS von, labordaten.wert FROM labordaten;


ALTER TABLE bayeos.v_messungen_all OWNER TO bayeos;

--
-- Name: v_messungen_id_super; Type: VIEW; Schema: bayeos; Owner: bayeos
--

CREATE VIEW v_messungen_id_super AS
    SELECT t.id, t.id_super, t.level FROM public.connectby('messungen.objekt'::text, 'id'::text, 'id_super'::text, (83)::text, 0) t(id integer, id_super integer, level integer);


ALTER TABLE bayeos.v_messungen_id_super OWNER TO bayeos;

--
-- Name: v_messungen_unit; Type: VIEW; Schema: bayeos; Owner: bayeos
--

CREATE VIEW v_messungen_unit AS
    SELECT messungen.id, messungen.bezeichnung, get_lowest_ref(messungen.id, 'mess_einheit'::character varying) AS einheit, get_lowest_ref(messungen.id, 'mess_ziel'::character varying) AS messziel FROM messungen;


ALTER TABLE bayeos.v_messungen_unit OWNER TO bayeos;

--
-- Name: v_messungen_unit_ziel; Type: VIEW; Schema: bayeos; Owner: bayeos
--

CREATE VIEW v_messungen_unit_ziel AS
    SELECT messungen.id, messungen.bezeichnung, get_unit(messungen.id) AS einheit, get_messziel(messungen.id) AS messziel FROM messungen;


ALTER TABLE bayeos.v_messungen_unit_ziel OWNER TO bayeos;

--
-- Name: v_messungen_unit_ziel_ort; Type: VIEW; Schema: bayeos; Owner: bayeos
--

CREATE VIEW v_messungen_unit_ziel_ort AS
    SELECT messungen.id, messungen.bezeichnung, get_unit(messungen.id) AS einheit, get_messziel(messungen.id) AS messziel, get_lowest_ref(messungen.id, 'mess_ort'::character varying) AS messort_id FROM messungen;


ALTER TABLE bayeos.v_messungen_unit_ziel_ort OWNER TO bayeos;

--
-- Name: verweis; Type: TABLE; Schema: bayeos; Owner: bayeos; Tablespace: 
--

CREATE TABLE verweis (
    id integer DEFAULT nextval(('"verweis_id_seq"'::text)::regclass) NOT NULL,
    id_von integer NOT NULL,
    id_auf integer NOT NULL,
    von timestamp with time zone DEFAULT ('now'::text)::timestamp(6) with time zone,
    bis timestamp with time zone,
    anteil real,
    CONSTRAINT chk_verweis_id_von_auf CHECK ((id_von <> id_auf)),
    CONSTRAINT chk_verweis_von_bis CHECK ((von < bis))
);


ALTER TABLE bayeos.verweis OWNER TO bayeos;

--
-- Name: v_messungen_verweis; Type: VIEW; Schema: bayeos; Owner: bayeos
--

CREATE VIEW v_messungen_verweis AS
    SELECT m.id AS messungen_id, o.id_art, o.id AS objekt_id FROM (SELECT messungen.id, get_objekt_super_ids(messungen.id) AS id_super FROM messungen) m, verweis v, objekt o WHERE ((v.id_auf = m.id_super) AND (v.id_von = o.id));


ALTER TABLE bayeos.v_messungen_verweis OWNER TO bayeos;

SET default_with_oids = true;

--
-- Name: verweis_extern; Type: TABLE; Schema: bayeos; Owner: bayeos; Tablespace: 
--

CREATE TABLE verweis_extern (
    id integer NOT NULL,
    id_von integer NOT NULL,
    id_auf integer NOT NULL,
    von timestamp with time zone DEFAULT ('now'::text)::timestamp(6) with time zone,
    bis timestamp with time zone,
    CONSTRAINT chk_verweis_ex_id_von_auf CHECK ((id_von <> id_auf)),
    CONSTRAINT chk_verweis_ex_von_bis CHECK ((von < bis))
);


ALTER TABLE bayeos.verweis_extern OWNER TO bayeos;

--
-- Name: COLUMN verweis_extern.id_von; Type: COMMENT; Schema: bayeos; Owner: bayeos
--

COMMENT ON COLUMN verweis_extern.id_von IS 'public.objekt.id';


--
-- Name: COLUMN verweis_extern.id_auf; Type: COMMENT; Schema: bayeos; Owner: bayeos
--

COMMENT ON COLUMN verweis_extern.id_auf IS 'messungen.objekt.id';





--
-- Name: v_objekt_super_id; Type: VIEW; Schema: bayeos; Owner: bayeos
--

CREATE VIEW v_objekt_super_id AS
    SELECT objekt.id, get_objekt_super_ids(objekt.id) AS id_super, objekt.id_art FROM objekt;


ALTER TABLE bayeos.v_objekt_super_id OWNER TO bayeos;

--
-- Name: v_verweis; Type: VIEW; Schema: bayeos; Owner: bayeos
--

CREATE VIEW v_verweis AS
    SELECT verweis.id_auf, verweis.id_von FROM verweis UNION SELECT objekt.id AS id_auf, objekt.id_super AS id_von FROM objekt;


ALTER TABLE bayeos.v_verweis OWNER TO bayeos;

--
-- Name: v_web_mit; Type: VIEW; Schema: bayeos; Owner: bayeos
--

SET default_with_oids = false;

--
-- Name: valfuncargument; Type: TABLE; Schema: bayeos; Owner: bayeos; Tablespace: 
--

CREATE TABLE valfuncargument (
    id integer NOT NULL,
    fk_valfunction integer,
    index integer NOT NULL,
    alias text NOT NULL
);


ALTER TABLE bayeos.valfuncargument OWNER TO bayeos;

--
-- Name: valfuncargument_id_seq; Type: SEQUENCE; Schema: bayeos; Owner: bayeos
--

CREATE SEQUENCE valfuncargument_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER TABLE bayeos.valfuncargument_id_seq OWNER TO bayeos;

--
-- Name: valfuncargument_id_seq; Type: SEQUENCE OWNED BY; Schema: bayeos; Owner: bayeos
--

ALTER SEQUENCE valfuncargument_id_seq OWNED BY valfuncargument.id;


--
-- Name: valfuncargument_id_seq; Type: SEQUENCE SET; Schema: bayeos; Owner: bayeos
--

SELECT pg_catalog.setval('valfuncargument_id_seq', 1, false);


--
-- Name: valfuncargvalue; Type: TABLE; Schema: bayeos; Owner: bayeos; Tablespace: 
--

CREATE TABLE valfuncargvalue (
    fk_checks integer NOT NULL,
    fk_valfuncargument integer NOT NULL,
    value double precision
);


ALTER TABLE bayeos.valfuncargvalue OWNER TO bayeos;

--
-- Name: valfunccolumn; Type: TABLE; Schema: bayeos; Owner: bayeos; Tablespace: 
--

CREATE TABLE valfunccolumn (
    id integer NOT NULL,
    fk_valfunction integer,
    name text NOT NULL
);


ALTER TABLE bayeos.valfunccolumn OWNER TO bayeos;

--
-- Name: valfunccolumn_id_seq; Type: SEQUENCE; Schema: bayeos; Owner: bayeos
--

CREATE SEQUENCE valfunccolumn_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER TABLE bayeos.valfunccolumn_id_seq OWNER TO bayeos;

--
-- Name: valfunccolumn_id_seq; Type: SEQUENCE OWNED BY; Schema: bayeos; Owner: bayeos
--

ALTER SEQUENCE valfunccolumn_id_seq OWNED BY valfunccolumn.id;


--
-- Name: valfunccolumn_id_seq; Type: SEQUENCE SET; Schema: bayeos; Owner: bayeos
--

SELECT pg_catalog.setval('valfunccolumn_id_seq', 1, false);


--
-- Name: valfunction; Type: TABLE; Schema: bayeos; Owner: bayeos; Tablespace: 
--

CREATE TABLE valfunction (
    id integer NOT NULL,
    name text
);


ALTER TABLE bayeos.valfunction OWNER TO bayeos;

--
-- Name: valfunction_id_seq; Type: SEQUENCE; Schema: bayeos; Owner: bayeos
--

CREATE SEQUENCE valfunction_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER TABLE bayeos.valfunction_id_seq OWNER TO bayeos;

--
-- Name: valfunction_id_seq; Type: SEQUENCE OWNED BY; Schema: bayeos; Owner: bayeos
--

ALTER SEQUENCE valfunction_id_seq OWNED BY valfunction.id;


--
-- Name: valfunction_id_seq; Type: SEQUENCE SET; Schema: bayeos; Owner: bayeos
--

SELECT pg_catalog.setval('valfunction_id_seq', 1, false);


--
-- Name: verweis_extern_id_seq; Type: SEQUENCE; Schema: bayeos; Owner: bayeos
--

CREATE SEQUENCE verweis_extern_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER TABLE bayeos.verweis_extern_id_seq OWNER TO bayeos;

--
-- Name: verweis_extern_id_seq; Type: SEQUENCE OWNED BY; Schema: bayeos; Owner: bayeos
--

ALTER SEQUENCE verweis_extern_id_seq OWNED BY verweis_extern.id;


--
-- Name: verweis_extern_id_seq; Type: SEQUENCE SET; Schema: bayeos; Owner: bayeos
--

SELECT pg_catalog.setval('verweis_extern_id_seq', 1, false);


--
-- Name: verweis_id_seq; Type: SEQUENCE; Schema: bayeos; Owner: bayeos
--

CREATE SEQUENCE verweis_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    MAXVALUE 2147483647
    CACHE 1;


ALTER TABLE bayeos.verweis_id_seq OWNER TO bayeos;

--
-- Name: verweis_id_seq; Type: SEQUENCE SET; Schema: bayeos; Owner: bayeos
--

SELECT pg_catalog.setval('verweis_id_seq', 1, false);


--
-- Name: id; Type: DEFAULT; Schema: bayeos; Owner: bayeos
--

ALTER TABLE aggr_funktion ALTER COLUMN id SET DEFAULT nextval('aggr_funktion_id_seq'::regclass);


--
-- Name: id; Type: DEFAULT; Schema: bayeos; Owner: bayeos
--

ALTER TABLE aggr_intervall ALTER COLUMN id SET DEFAULT nextval('aggr_intervall_id_seq'::regclass);


--
-- Name: id; Type: DEFAULT; Schema: bayeos; Owner: bayeos
--

ALTER TABLE alert ALTER COLUMN id SET DEFAULT nextval('alert_id_seq'::regclass);


--
-- Name: id; Type: DEFAULT; Schema: bayeos; Owner: bayeos
--

ALTER TABLE arch_his_log ALTER COLUMN id SET DEFAULT nextval('arch_his_log_id_seq'::regclass);


--
-- Name: id; Type: DEFAULT; Schema: bayeos; Owner: bayeos
--

ALTER TABLE auth_db ALTER COLUMN id SET DEFAULT nextval('auth_db_id_seq'::regclass);


--
-- Name: id; Type: DEFAULT; Schema: bayeos; Owner: bayeos
--

ALTER TABLE auth_ldap ALTER COLUMN id SET DEFAULT nextval('auth_ldap_id_seq'::regclass);


--
-- Name: id; Type: DEFAULT; Schema: bayeos; Owner: bayeos
--

ALTER TABLE calibration ALTER COLUMN id SET DEFAULT nextval('calibration_id_seq'::regclass);


--
-- Name: id; Type: DEFAULT; Schema: bayeos; Owner: bayeos
--

ALTER TABLE checks ALTER COLUMN id SET DEFAULT nextval('checks_id_seq'::regclass);


--
-- Name: id; Type: DEFAULT; Schema: bayeos; Owner: bayeos
--

ALTER TABLE coordinate_ref_sys ALTER COLUMN id SET DEFAULT nextval('coordinate_ref_sys_id_seq'::regclass);


--
-- Name: id; Type: DEFAULT; Schema: bayeos; Owner: bayeos
--

ALTER TABLE intervaltyp ALTER COLUMN id SET DEFAULT nextval('intervaltyp_id_seq'::regclass);


--
-- Name: id; Type: DEFAULT; Schema: bayeos; Owner: bayeos
--

ALTER TABLE listener ALTER COLUMN id SET DEFAULT nextval('listener_id_seq'::regclass);


--
-- Name: id; Type: DEFAULT; Schema: bayeos; Owner: bayeos
--

ALTER TABLE timezone ALTER COLUMN id SET DEFAULT nextval('timezone_id_seq'::regclass);


--
-- Name: id; Type: DEFAULT; Schema: bayeos; Owner: bayeos
--

ALTER TABLE valfuncargument ALTER COLUMN id SET DEFAULT nextval('valfuncargument_id_seq'::regclass);


--
-- Name: id; Type: DEFAULT; Schema: bayeos; Owner: bayeos
--

ALTER TABLE valfunccolumn ALTER COLUMN id SET DEFAULT nextval('valfunccolumn_id_seq'::regclass);


--
-- Name: id; Type: DEFAULT; Schema: bayeos; Owner: bayeos
--

ALTER TABLE valfunction ALTER COLUMN id SET DEFAULT nextval('valfunction_id_seq'::regclass);


--
-- Name: id; Type: DEFAULT; Schema: bayeos; Owner: bayeos
--

ALTER TABLE verweis_extern ALTER COLUMN id SET DEFAULT nextval('verweis_extern_id_seq'::regclass);


--
-- Data for Name: aggr_avg_30min; Type: TABLE DATA; Schema: bayeos; Owner: bayeos
--

COPY aggr_avg_30min (id, von, wert, counts) FROM stdin;
\.


--
-- Data for Name: aggr_avg_day; Type: TABLE DATA; Schema: bayeos; Owner: bayeos
--

COPY aggr_avg_day (id, von, wert, counts) FROM stdin;
\.


--
-- Data for Name: aggr_avg_hour; Type: TABLE DATA; Schema: bayeos; Owner: bayeos
--

COPY aggr_avg_hour (id, von, wert, counts) FROM stdin;
\.


--
-- Data for Name: aggr_avg_month; Type: TABLE DATA; Schema: bayeos; Owner: bayeos
--

COPY aggr_avg_month (id, von, wert, counts) FROM stdin;
\.


--
-- Data for Name: aggr_avg_year; Type: TABLE DATA; Schema: bayeos; Owner: bayeos
--

COPY aggr_avg_year (id, von, wert, counts) FROM stdin;
\.


--
-- Data for Name: aggr_funktion; Type: TABLE DATA; Schema: bayeos; Owner: bayeos
--

COPY aggr_funktion (id, name) FROM stdin;
1	Avg
2	Min
3	Max
4	Sum
\.


--
-- Data for Name: aggr_intervall; Type: TABLE DATA; Schema: bayeos; Owner: bayeos
--

COPY aggr_intervall (id, name, intervall) FROM stdin;
1	30min	00:30:00
2	hour	01:00:00
3	day	1 day
4	month	1 mon
5	year	1 year
\.


--
-- Data for Name: aggr_max_30min; Type: TABLE DATA; Schema: bayeos; Owner: bayeos
--

COPY aggr_max_30min (id, von, wert, counts) FROM stdin;
\.


--
-- Data for Name: aggr_max_day; Type: TABLE DATA; Schema: bayeos; Owner: bayeos
--

COPY aggr_max_day (id, von, wert, counts) FROM stdin;
\.


--
-- Data for Name: aggr_max_hour; Type: TABLE DATA; Schema: bayeos; Owner: bayeos
--

COPY aggr_max_hour (id, von, wert, counts) FROM stdin;
\.


--
-- Data for Name: aggr_max_month; Type: TABLE DATA; Schema: bayeos; Owner: bayeos
--

COPY aggr_max_month (id, von, wert, counts) FROM stdin;
\.


--
-- Data for Name: aggr_max_year; Type: TABLE DATA; Schema: bayeos; Owner: bayeos
--

COPY aggr_max_year (id, von, wert, counts) FROM stdin;
\.


--
-- Data for Name: aggr_min_30min; Type: TABLE DATA; Schema: bayeos; Owner: bayeos
--

COPY aggr_min_30min (id, von, wert, counts) FROM stdin;
\.


--
-- Data for Name: aggr_min_day; Type: TABLE DATA; Schema: bayeos; Owner: bayeos
--

COPY aggr_min_day (id, von, wert, counts) FROM stdin;
\.


--
-- Data for Name: aggr_min_hour; Type: TABLE DATA; Schema: bayeos; Owner: bayeos
--

COPY aggr_min_hour (id, von, wert, counts) FROM stdin;
\.


--
-- Data for Name: aggr_min_month; Type: TABLE DATA; Schema: bayeos; Owner: bayeos
--

COPY aggr_min_month (id, von, wert, counts) FROM stdin;
\.


--
-- Data for Name: aggr_min_year; Type: TABLE DATA; Schema: bayeos; Owner: bayeos
--

COPY aggr_min_year (id, von, wert, counts) FROM stdin;
\.


--
-- Data for Name: aggr_sum_30min; Type: TABLE DATA; Schema: bayeos; Owner: bayeos
--

COPY aggr_sum_30min (id, von, wert, counts) FROM stdin;
\.


--
-- Data for Name: aggr_sum_day; Type: TABLE DATA; Schema: bayeos; Owner: bayeos
--

COPY aggr_sum_day (id, von, wert, counts) FROM stdin;
\.


--
-- Data for Name: aggr_sum_hour; Type: TABLE DATA; Schema: bayeos; Owner: bayeos
--

COPY aggr_sum_hour (id, von, wert, counts) FROM stdin;
\.


--
-- Data for Name: aggr_sum_month; Type: TABLE DATA; Schema: bayeos; Owner: bayeos
--

COPY aggr_sum_month (id, von, wert, counts) FROM stdin;
\.


--
-- Data for Name: aggr_sum_year; Type: TABLE DATA; Schema: bayeos; Owner: bayeos
--

COPY aggr_sum_year (id, von, wert, counts) FROM stdin;
\.


--
-- Data for Name: alert; Type: TABLE DATA; Schema: bayeos; Owner: bayeos
--

COPY alert (id, fk_listener, fk_checks, status) FROM stdin;
\.


--
-- Data for Name: arch_his_log; Type: TABLE DATA; Schema: bayeos; Owner: bayeos
--

COPY arch_his_log (id, name, min_id, max_id, min_datum, max_datum, counts, von) FROM stdin;
\.


--
-- Data for Name: art_objekt; Type: TABLE DATA; Schema: bayeos; Owner: bayeos
--

COPY art_objekt (id, uname, de, en, kurz, detail_table) FROM stdin;
100001	art_objekt	Objektart	Object type	Type	\N
100010	mess_ziel	Messziel	Measuring target	Target	messziele
100011	messung	Messung	Measurement	Measurement	messungen
117660	messung_ordner	Messung Ordner	Measurement Folder	Folder	messungen
117661	messung_massendaten	Messung Massendaten	Measurement Massdata	Massdata	messungen
117662	messung_labordaten	Messung Labordaten	Measurment Labdata	Labdata	messungen
100002	benutzer	Benutzer	User	User	benutzer
100003	gruppe	Benutzergruppe	Usergroup	Group	benutzer_gr
117665	mess_einheit	Einheit	Unit	Unit	einheiten
100007	mess_geraet	MessgerÃ¤t	Measuring device	Device	geraete
100008	mess_kompartiment	Kompartiment	Compartiment	Compartiment	kompartimente
100009	mess_ort	Messort	Measuring location	Location	messorte
\.


--
-- Data for Name: auth_db; Type: TABLE DATA; Schema: bayeos; Owner: bayeos
--

COPY auth_db (id, url, name, query_stmt, username, password, update_stmt) FROM stdin;
1	jdbc:postgresql://localhost:5432/bayeos	LOCAL	select crypt(?,substr(pw,1,2)) = pw from benutzer where login like ? and locked = false;	bayeos	bayeos	update benutzer set pw = crypt(?,gen_salt('des')) where login like ?;
\.


--
-- Data for Name: auth_ldap; Type: TABLE DATA; Schema: bayeos; Owner: bayeos
--

COPY auth_ldap (id, name, host, dn, ssl, keystore_path) FROM stdin;
\.


--
-- Data for Name: benutzer; Type: TABLE DATA; Schema: bayeos; Owner: bayeos
--

COPY benutzer (id, login, pw, von, bis, locked, admin, fk_auth_ldap, fk_auth_db) FROM stdin;
100004	root	ITk2faAI8oXzk	\N	\N	f	t	\N	1
200003	All Users	\N	\N	\N	f	f	\N	\N
\.


--
-- Data for Name: benutzer_gr; Type: TABLE DATA; Schema: bayeos; Owner: bayeos
--

COPY benutzer_gr (id_benutzer, id_gruppe) FROM stdin;
\.


--
-- Data for Name: calibration; Type: TABLE DATA; Schema: bayeos; Owner: bayeos
--

COPY calibration (id, id_device, wann) FROM stdin;
\.


--
-- Data for Name: calibration_data; Type: TABLE DATA; Schema: bayeos; Owner: bayeos
--

COPY calibration_data (id_cal, x, y) FROM stdin;
\.


--
-- Data for Name: checkcolmessung; Type: TABLE DATA; Schema: bayeos; Owner: bayeos
--

COPY checkcolmessung (fk_messungen, fk_valfunccolumn, fk_checks, fk_interval, fk_function) FROM stdin;
\.


--
-- Data for Name: checklistener; Type: TABLE DATA; Schema: bayeos; Owner: bayeos
--

COPY checklistener (fk_listener, fk_checks) FROM stdin;
\.


--
-- Data for Name: checks; Type: TABLE DATA; Schema: bayeos; Owner: bayeos
--

COPY checks (id, fk_valfunction, name, cycle_interval, last_run, last_value, data_interval) FROM stdin;
\.


--
-- Data for Name: coordinate_ref_sys; Type: TABLE DATA; Schema: bayeos; Owner: bayeos
--

COPY coordinate_ref_sys (id, short_name, fk_srid_id) FROM stdin;
2	Gauss-Kruger zone 4	31468
3	Gauss-Kruger zone 3	31467
4	WGS 1984	4326
5	Spherical Mercator (Google)	900913
\.


--
-- Data for Name: einheiten; Type: TABLE DATA; Schema: bayeos; Owner: bayeos
--

COPY einheiten (id, bezeichnung, beschreibung, symbol) FROM stdin;
200009	Alle Einheiten	\N	\N
200010	SI Base Units	\N	\N
200011	SI Derived Unit	\N	\N
200051	newton meter second		N*m*s
200052	newton meter		N*m
200012	meter		m
200015	ampere		A
200017	candela		cd
200016	kelvin		K
200013	kilogram		kg
200018	mole		mol
200014	second		s
200053	newton per second		1/m
200055	kilogram per square metre		kg/m²
200056	kilogram per cubic metre		kl/m³
200023	pascal		Pa
200024	joule		J
200025	watt		W
200026	coulomb		C
200027	volt		V
200028	farad		F
200029	ohm		Ω
200030	siemens		S
200031	weber		Wb
200032	tesla		T
200033	henry		H
200034	degree Celsius		°C
200035	lumen		lm
200036	lux		lx
200037	becquerel		Bq
200038	gray		Gy
200039	sievert		Sv
200040	katal		kat
200057	cubic metre per kilogram		m³/kg
200058	mole per cubic metre		mol/m³
200059	cubic metre per mole		m³/mol
200060	joule second		Js
200019	hertz		Hz
200020	radian		rad
200021	steradian		sr
200022	newton		N
200041	Compound Units derived from SI units	\N	\N
200061	joule per kelvin		J/K
200043	cubic metre		m³
200062	joule per kelvin mole		J/(K*mol)
200054	reciprocal metre	\N	\N
200042	square meter		m²
200044	metre per second		m/s
200045	cubic metre per second		m³/s
200063	joule per kilogram kelvin		J/(K*kg)
200064	joule per mole		J/mol
200046	meter per second squared		m/s²
200047	meter per second cubed		m/s³
200065	joule per kilogram		J/kg
200066	joule per cubic meter		J/m³
200049	radian per second		rad/s
200050	newton second		N*s
200067	newton per meter		N/m
200068	watt per square meter		W/m²
200069	watt per metre kelvin		W/(m*K)
200070	square metre per second		m²/s
200071	pascal second		Pa*s
200072	coulomb per square meter		C/m²
200073	coulomb per cubic meter		C/m³
200074	ampere per square meter		A/m²
200075	siemens per meter		S/m
200076	siemens square meter per mole		S*m²/mol
200077	farad per meter		F/m
200078	henry per meter		H/m
200079	volt per meter		V/m
200080	ampere per meter		A/m
200081	candela per square meter		cd/m²
200082	coulomb per kilogram		C/kg
200083	gray per second		Gy/s
200084	ohm meter		Ω·m
200085	Widely used	\N	\N
200086	minute		min
200087	hour		h
200088	day		d
200090	minute of arc	\N	\N
200089	degree of arc		'
200091	second of arc		''
200092	liter		l
200093	tonne		t
200094	decibel		dB
200095	hectar		ha
200096	electron-volt		eV
200097	millibar	\N	\N
200098	nautical mile		nm
200099	knot		knot
200100	bar		bar
\.


--
-- Data for Name: geraete; Type: TABLE DATA; Schema: bayeos; Owner: bayeos
--

COPY geraete (id, bezeichnung, beschreibung, seriennr) FROM stdin;
200005	Alle Geräte	\N	\N
\.


--
-- Data for Name: his_labordaten; Type: TABLE DATA; Schema: bayeos; Owner: bayeos
--

COPY his_labordaten (id, status, von, bis, wert, labornummer, genauigkeit, bestimmungsgrenze, bemerkung, his_id, his_datum, his_benutzer_id, his_aktion) FROM stdin;
\.


--
-- Data for Name: his_massendaten; Type: TABLE DATA; Schema: bayeos; Owner: bayeos
--

COPY his_massendaten (id, status, von, wert, tstatus, his_id, his_datum, his_benutzer_id, his_aktion) FROM stdin;
\.


--
-- Data for Name: intervaltyp; Type: TABLE DATA; Schema: bayeos; Owner: bayeos
--

COPY intervaltyp (id, bezeichnung) FROM stdin;
1	start
2	end
0	undefined
\.


--
-- Data for Name: kompartimente; Type: TABLE DATA; Schema: bayeos; Owner: bayeos
--

COPY kompartimente (id, bezeichnung, beschreibung) FROM stdin;
200006	Alle Kompartimente	\N
\.


--
-- Data for Name: labordaten; Type: TABLE DATA; Schema: bayeos; Owner: bayeos
--

COPY labordaten (id, status, von, bis, wert, labornummer, genauigkeit, bestimmungsgrenze, bemerkung) FROM stdin;
\.


--
-- Data for Name: listener; Type: TABLE DATA; Schema: bayeos; Owner: bayeos
--

COPY listener (id, adress) FROM stdin;
\.


--
-- Data for Name: massendaten; Type: TABLE DATA; Schema: bayeos; Owner: bayeos
--

COPY massendaten (id, status, von, wert, tstatus) FROM stdin;
\.


--
-- Data for Name: messorte; Type: TABLE DATA; Schema: bayeos; Owner: bayeos
--

COPY messorte (id, bezeichnung, beschreibung, x, y, z, fk_crs_id) FROM stdin;
200007	Alle Orte	\N	\N	\N	\N	\N
\.


--
-- Data for Name: messungen; Type: TABLE DATA; Schema: bayeos; Owner: bayeos
--

COPY messungen (id, id_ora, bezeichnung, beschreibung, aufloesung, tabelle, id_intervaltyp, fk_timezone_id) FROM stdin;
200004	\N	Alle Ordner	\N	\N	\N	0	2
\.


--
-- Data for Name: messziele; Type: TABLE DATA; Schema: bayeos; Owner: bayeos
--

COPY messziele (id, bezeichnung, beschreibung, formel) FROM stdin;
200008	Alle Messziele	\N	\N
\.


--
-- Data for Name: objekt; Type: TABLE DATA; Schema: bayeos; Owner: bayeos
--

COPY objekt (id, id_super, id_art, id_cbenutzer, ctime, id_ubenutzer, utime, dtime, de, en, public_read, public_write, public_exec, public_childs, inherit_perm, plan_start, plan_end, rec_start, rec_end) FROM stdin;
100011	\N	100001	100004	2002-08-06 14:28:36+02	\N	\N	\N	Objektart: Messung	Objekct type: Measurment	f	f	f	f	t	\N	\N	\N	\N
100010	\N	100001	100004	2002-08-06 14:28:36+02	\N	\N	\N	Objektart: Messziel	Objekct type: Measuring target	f	f	f	f	t	\N	\N	\N	\N
117662	\N	100001	100004	2002-11-22 18:26:10+01	\N	\N	\N	Objektart: Messung Labordaten	Objekct type: Measurment Labdata	f	f	f	f	t	\N	\N	\N	\N
117665	\N	100001	100004	2002-11-22 18:26:10+01	\N	\N	\N	Objektart: Einheit	Objekct type: Unit	f	f	f	f	t	\N	\N	\N	\N
100007	\N	100001	100004	2002-08-06 14:28:36+02	\N	\N	\N	Objektart: Messgerät	Objekct type: Messuring device	f	f	f	f	t	\N	\N	\N	\N
117660	\N	100001	100004	2002-11-22 18:26:10+01	\N	\N	\N	Objektart: Messung Ordner	Objekct type: Measurment Folder	f	f	f	f	t	\N	\N	\N	\N
100001	\N	100001	100004	2002-07-26 15:19:15+02	\N	\N	\N	Art Objekt	Object type	f	f	f	f	t	\N	\N	\N	\N
200052	200041	117665	100004	2011-06-06 20:12:18.20949+02	100004	2011-06-06 20:22:22+02	\N	newton meter	newton meter	f	f	f	f	t	\N	\N	\N	\N
100009	\N	100001	100004	2002-08-06 14:28:36+02	\N	\N	\N	Objektart: Messort	Objekct type: Measuring location	f	f	f	f	t	\N	\N	\N	\N
117661	\N	100001	100004	2002-11-22 18:26:10+01	\N	\N	\N	Objektart: Messung Massendaten	Objekct type: Measurment Massdata	f	f	f	f	t	\N	\N	\N	\N
100008	\N	100001	100004	2002-08-06 14:28:36+02	\N	\N	\N	Objektart: Kompartimente	Objekct type: Compartiments	f	f	f	f	t	\N	\N	\N	\N
117664	\N	100001	100004	2002-11-22 18:26:10+01	\N	\N	\N	Objektart: Einbau	Objekct type: Installation	f	f	f	f	t	\N	\N	\N	\N
100004	\N	100002	100004	\N	\N	\N	\N	root	root	f	f	f	f	t	\N	\N	\N	\N
200081	200041	117665	100004	2011-06-06 20:17:17.967485+02	100004	2011-06-06 20:28:00+02	\N	candela per square meter	candela per square meter	f	f	f	f	t	\N	\N	\N	\N
200018	200010	117665	100004	2011-06-06 18:44:20.137964+02	100004	2011-06-06 19:55:45+02	\N	mole	mole	f	f	f	f	t	\N	\N	\N	\N
200044	200041	117665	100004	2011-06-06 20:06:16.172271+02	100004	2011-06-06 20:18:07+02	\N	metre per second	metre per second	f	f	f	f	t	\N	\N	\N	\N
200034	200011	117665	100004	2011-06-06 20:01:31.198713+02	100004	2011-06-06 20:01:42+02	\N	degree Celsius	degree Celsius	f	f	f	f	t	\N	\N	\N	\N
200014	200010	117665	100004	2011-06-06 18:43:40.598349+02	100004	2011-06-06 19:55:51+02	\N	second	second	f	f	f	f	t	\N	\N	\N	\N
200010	200009	117665	100004	2011-06-06 18:38:52.000926+02	100004	2011-06-06 18:42:42+02	\N	SI Base Units	SI Base Units	f	f	f	f	t	\N	\N	\N	\N
100002	\N	100001	100004	2002-07-26 15:19:15+02	\N	\N	\N	User	User	f	f	f	f	t	\N	\N	\N	\N
100003	\N	100001	100004	2002-07-26 15:19:15+02	\N	\N	\N	Group	Group	f	f	f	f	t	\N	\N	\N	\N
200003	\N	100003	100004	2011-05-27 18:31:04.076053+02	\N	\N	\N	All Users	All Users	t	f	f	f	t	\N	\N	\N	\N
200004	\N	117660	100004	2011-05-27 18:41:49.31269+02	\N	\N	\N	All Folders	All Folders	t	f	f	f	t	\N	\N	\N	\N
200005	\N	100007	100004	2011-05-27 18:44:54.428549+02	\N	\N	\N	All Devices	All Devices	t	f	f	f	t	\N	\N	\N	\N
200006	\N	100008	100004	2011-05-27 18:45:26.244469+02	\N	\N	\N	All Compartiments	All Compartiments	t	f	f	f	t	\N	\N	\N	\N
200007	\N	100009	100004	2011-05-27 18:45:49.19513+02	\N	\N	\N	All Locations	All Locations	t	f	f	f	t	\N	\N	\N	\N
200088	200085	117665	100004	2011-06-06 20:31:48.259168+02	100004	2011-06-06 20:31:51+02	\N	day	day	f	f	f	f	t	\N	\N	\N	\N
200008	\N	100010	100004	2011-05-27 18:46:17.539556+02	\N	\N	\N	All Targets	All Targets	t	f	f	f	t	\N	\N	\N	\N
200027	200011	117665	100004	2011-06-06 19:59:12.447045+02	100004	2011-06-06 19:59:18+02	\N	volt	volt	f	f	f	f	t	\N	\N	\N	\N
200040	200011	117665	100004	2011-06-06 20:03:20.994899+02	100004	2011-06-06 20:03:28+02	\N	katal	katal	f	f	f	f	t	\N	\N	\N	\N
200028	200011	117665	100004	2011-06-06 19:59:33.798735+02	100004	2011-06-06 19:59:38+02	\N	farad	farad	f	f	f	f	t	\N	\N	\N	\N
200035	200011	117665	100004	2011-06-06 20:01:49.88054+02	100004	2011-06-06 20:01:57+02	\N	lumen	lumen	f	f	f	f	t	\N	\N	\N	\N
200090	200085	117665	100004	2011-06-06 20:32:22.382818+02	\N	\N	\N	minute of arc	minute of arc	f	f	f	f	t	\N	\N	\N	\N
200089	200085	117665	100004	2011-06-06 20:32:00.275084+02	100004	2011-06-06 20:32:27+02	\N	degree of arc	degree of arc	f	f	f	f	t	\N	\N	\N	\N
200029	200011	117665	100004	2011-06-06 19:59:51.506621+02	100004	2011-06-06 19:59:59+02	\N	ohm	ohm	f	f	f	f	t	\N	\N	\N	\N
200012	200010	117665	100004	2011-06-06 18:43:19.991226+02	100004	2011-06-06 19:54:25+02	\N	meter	meter	f	f	f	f	t	\N	\N	\N	\N
200085	200009	117665	100004	2011-06-06 20:31:01.193542+02	\N	\N	\N	Widely used	Widely used	f	f	f	f	t	\N	\N	\N	\N
200015	200010	117665	100004	2011-06-06 18:43:48.823265+02	100004	2011-06-06 19:55:07+02	\N	ampere	ampere	f	f	f	f	t	\N	\N	\N	\N
200017	200010	117665	100004	2011-06-06 18:44:10.38153+02	100004	2011-06-06 19:55:18+02	\N	candela	candela	f	f	f	f	t	\N	\N	\N	\N
200036	200011	117665	100004	2011-06-06 20:02:08.036551+02	100004	2011-06-06 20:02:16+02	\N	lux	lux	f	f	f	f	t	\N	\N	\N	\N
200016	200010	117665	100004	2011-06-06 18:43:58.448145+02	100004	2011-06-06 19:55:25+02	\N	kelvin	kelvin	f	f	f	f	t	\N	\N	\N	\N
200023	200011	117665	100004	2011-06-06 19:57:54.942878+02	100004	2011-06-06 19:58:01+02	\N	pascal	pascal	f	f	f	f	t	\N	\N	\N	\N
200013	200010	117665	100004	2011-06-06 18:43:33.887031+02	100004	2011-06-06 19:55:34+02	\N	kilogram	kilogram	f	f	f	f	t	\N	\N	\N	\N
200030	200011	117665	100004	2011-06-06 20:00:18.455975+02	100004	2011-06-06 20:00:23+02	\N	siemens	siemens	f	f	f	f	t	\N	\N	\N	\N
200024	200011	117665	100004	2011-06-06 19:58:14.468302+02	100004	2011-06-06 19:58:24+02	\N	joule	joule	f	f	f	f	t	\N	\N	\N	\N
200031	200011	117665	100004	2011-06-06 20:00:33.880167+02	100004	2011-06-06 20:00:41+02	\N	weber	weber	f	f	f	f	t	\N	\N	\N	\N
200037	200011	117665	100004	2011-06-06 20:02:28.556016+02	100004	2011-06-06 20:02:35+02	\N	becquerel	becquerel	f	f	f	f	t	\N	\N	\N	\N
200025	200011	117665	100004	2011-06-06 19:58:36.922129+02	100004	2011-06-06 19:58:44+02	\N	watt	watt	f	f	f	f	t	\N	\N	\N	\N
200032	200011	117665	100004	2011-06-06 20:00:48.391321+02	100004	2011-06-06 20:00:54+02	\N	tesla	tesla	f	f	f	f	t	\N	\N	\N	\N
200026	200011	117665	100004	2011-06-06 19:58:54.825531+02	100004	2011-06-06 19:59:00+02	\N	coulomb	coulomb	f	f	f	f	t	\N	\N	\N	\N
200038	200011	117665	100004	2011-06-06 20:02:48.638377+02	100004	2011-06-06 20:02:56+02	\N	gray	gray	f	f	f	f	t	\N	\N	\N	\N
200033	200011	117665	100004	2011-06-06 20:01:02.203821+02	100004	2011-06-06 20:01:11+02	\N	henry	henry	f	f	f	f	t	\N	\N	\N	\N
200019	200011	117665	100004	2011-06-06 19:56:21.42703+02	100004	2011-06-06 20:04:20+02	\N	hertz	hertz	f	f	f	f	t	\N	\N	\N	\N
200020	200011	117665	100004	2011-06-06 19:56:48.428606+02	100004	2011-06-06 20:04:25+02	\N	radian	radian	f	f	f	f	t	\N	\N	\N	\N
200039	200011	117665	100004	2011-06-06 20:03:04.417529+02	100004	2011-06-06 20:03:11+02	\N	sievert	sievert	f	f	f	f	t	\N	\N	\N	\N
200021	200011	117665	100004	2011-06-06 19:57:10.016571+02	100004	2011-06-06 20:04:30+02	\N	steradian	steradian	f	f	f	f	t	\N	\N	\N	\N
200022	200011	117665	100004	2011-06-06 19:57:36.043931+02	100004	2011-06-06 20:04:35+02	\N	newton	newton	f	f	f	f	t	\N	\N	\N	\N
200011	200009	117665	100004	2011-06-06 18:42:54.758602+02	\N	\N	\N	SI Derived Unit	SI Derived Unit	f	f	f	f	t	\N	\N	\N	\N
200051	200041	117665	100004	2011-06-06 20:12:10.085249+02	100004	2011-06-06 20:22:14+02	\N	newton meter second	newton meter second	f	f	f	f	t	\N	\N	\N	\N
200050	200041	117665	100004	2011-06-06 20:07:13.511186+02	100004	2011-06-06 20:21:50+02	\N	newton second	newton second	f	f	f	f	t	\N	\N	\N	\N
200075	200041	117665	100004	2011-06-06 20:15:36.460454+02	100004	2011-06-06 20:27:12+02	\N	siemens per meter	siemens per meter	f	f	f	f	t	\N	\N	\N	\N
200082	200041	117665	100004	2011-06-06 20:17:30.116757+02	100004	2011-06-06 20:28:06+02	\N	coulomb per kilogram	coulomb per kilogram	f	f	f	f	t	\N	\N	\N	\N
200053	200041	117665	100004	2011-06-06 20:12:25.233249+02	100004	2011-06-06 20:23:24+02	\N	newton per second	newton per second	f	f	f	f	t	\N	\N	\N	\N
200055	200041	117665	100004	2011-06-06 20:12:42.428203+02	100004	2011-06-06 20:23:38+02	\N	kilogram per square metre	kilogram per square metre	f	f	f	f	t	\N	\N	\N	\N
200056	200041	117665	100004	2011-06-06 20:12:49.731678+02	100004	2011-06-06 20:23:47+02	\N	kilogram per cubic metre	kilogram per cubic metre	f	f	f	f	t	\N	\N	\N	\N
200057	200041	117665	100004	2011-06-06 20:12:58.198714+02	100004	2011-06-06 20:24:00+02	\N	cubic metre per kilogram	cubic metre per kilogram	f	f	f	f	t	\N	\N	\N	\N
200058	200041	117665	100004	2011-06-06 20:13:06.632733+02	100004	2011-06-06 20:24:13+02	\N	mole per cubic metre	mole per cubic metre	f	f	f	f	t	\N	\N	\N	\N
200059	200041	117665	100004	2011-06-06 20:13:14.440974+02	100004	2011-06-06 20:24:30+02	\N	cubic metre per mole	cubic metre per mole	f	f	f	f	t	\N	\N	\N	\N
200060	200041	117665	100004	2011-06-06 20:13:27.273532+02	100004	2011-06-06 20:24:38+02	\N	joule second	joule second	f	f	f	f	t	\N	\N	\N	\N
200061	200041	117665	100004	2011-06-06 20:13:37.731548+02	100004	2011-06-06 20:24:45+02	\N	joule per kelvin	joule per kelvin	f	f	f	f	t	\N	\N	\N	\N
200062	200041	117665	100004	2011-06-06 20:13:43.706052+02	100004	2011-06-06 20:24:59+02	\N	joule per kelvin mole	joule per kelvin mole	f	f	f	f	t	\N	\N	\N	\N
200063	200041	117665	100004	2011-06-06 20:13:50.997677+02	100004	2011-06-06 20:25:23+02	\N	joule per kilogram kelvin	joule per kilogram kelvin	f	f	f	f	t	\N	\N	\N	\N
200064	200041	117665	100004	2011-06-06 20:13:57.438535+02	100004	2011-06-06 20:25:32+02	\N	joule per mole	joule per mole	f	f	f	f	t	\N	\N	\N	\N
200065	200041	117665	100004	2011-06-06 20:14:03.663763+02	100004	2011-06-06 20:25:40+02	\N	joule per kilogram	joule per kilogram	f	f	f	f	t	\N	\N	\N	\N
200066	200041	117665	100004	2011-06-06 20:14:13.330562+02	100004	2011-06-06 20:25:49+02	\N	joule per cubic meter	joule per cubic meter	f	f	f	f	t	\N	\N	\N	\N
200067	200041	117665	100004	2011-06-06 20:14:22.105266+02	100004	2011-06-06 20:25:56+02	\N	newton per meter	newton per meter	f	f	f	f	t	\N	\N	\N	\N
200068	200041	117665	100004	2011-06-06 20:14:41.245792+02	100004	2011-06-06 20:26:05+02	\N	watt per square meter	watt per square meter	f	f	f	f	t	\N	\N	\N	\N
200045	200041	117665	100004	2011-06-06 20:06:23.770534+02	100004	2011-06-06 20:18:19+02	\N	cubic metre per second	cubic metre per second	f	f	f	f	t	\N	\N	\N	\N
200047	200041	117665	100004	2011-06-06 20:06:42.130136+02	100004	2011-06-06 20:19:00+02	\N	meter per second cubed	meter per second cubed	f	f	f	f	t	\N	\N	\N	\N
200049	200041	117665	100004	2011-06-06 20:07:03.569942+02	100004	2011-06-06 20:20:45+02	\N	radian per second	radian per second	f	f	f	f	t	\N	\N	\N	\N
200069	200041	117665	100004	2011-06-06 20:14:52.256217+02	100004	2011-06-06 20:26:18+02	\N	watt per metre kelvin	watt per metre kelvin	f	f	f	f	t	\N	\N	\N	\N
200043	200041	117665	100004	2011-06-06 20:05:58.604862+02	100004	2011-06-06 20:06:06+02	\N	cubic metre	cubic metre	f	f	f	f	t	\N	\N	\N	\N
200070	200041	117665	100004	2011-06-06 20:14:59.587008+02	100004	2011-06-06 20:26:33+02	\N	square metre per second	square metre per second	f	f	f	f	t	\N	\N	\N	\N
200054	200041	117665	100004	2011-06-06 20:12:34.575459+02	\N	\N	\N	reciprocal metre	reciprocal metre	f	f	f	f	t	\N	\N	\N	\N
200071	200041	117665	100004	2011-06-06 20:15:05.247972+02	100004	2011-06-06 20:26:41+02	\N	pascal second	pascal second	f	f	f	f	t	\N	\N	\N	\N
200086	200085	117665	100004	2011-06-06 20:31:08.176971+02	100004	2011-06-06 20:31:37+02	\N	minute	minute	f	f	f	f	t	\N	\N	\N	\N
200073	200041	117665	100004	2011-06-06 20:15:21.961398+02	100004	2011-06-06 20:26:59+02	\N	coulomb per cubic meter	coulomb per cubic meter	f	f	f	f	t	\N	\N	\N	\N
200087	200085	117665	100004	2011-06-06 20:31:31.317245+02	100004	2011-06-06 20:31:41+02	\N	hour	hour	f	f	f	f	t	\N	\N	\N	\N
200091	200085	117665	100004	2011-06-06 20:32:36.249529+02	100004	2011-06-06 20:32:41+02	\N	second of arc	second of arc	f	f	f	f	t	\N	\N	\N	\N
200092	200085	117665	100004	2011-06-06 20:32:49.190251+02	100004	2011-06-06 20:33:00+02	\N	liter	liter	f	f	f	f	t	\N	\N	\N	\N
200083	200041	117665	100004	2011-06-06 20:17:37.991923+02	100004	2011-06-06 20:28:14+02	\N	gray per second	gray per second	f	f	f	f	t	\N	\N	\N	\N
200041	200009	117665	100004	2011-06-06 20:05:07.491879+02	\N	\N	\N	Compound Units derived from SI units	Compound Units derived from SI units	f	f	f	f	t	\N	\N	\N	\N
200093	200085	117665	100004	2011-06-06 20:33:11.648738+02	100004	2011-06-06 20:34:18+02	\N	tonne	tonne	f	f	f	f	t	\N	\N	\N	\N
200094	200085	117665	100004	2011-06-06 20:34:28.00409+02	100004	2011-06-06 20:34:37+02	\N	decibel	decibel	f	f	f	f	t	\N	\N	\N	\N
200095	200085	117665	100004	2011-06-06 20:34:47.236984+02	100004	2011-06-06 20:34:56+02	\N	hectar	hectar	f	f	f	f	t	\N	\N	\N	\N
200096	200085	117665	100004	2011-06-06 20:35:08.819676+02	100004	2011-06-06 20:35:15+02	\N	electron-volt	electron-volt	f	f	f	f	t	\N	\N	\N	\N
200097	200085	117665	100004	2011-06-06 20:35:36.876424+02	\N	\N	\N	millibar	millibar	f	f	f	f	t	\N	\N	\N	\N
200098	200085	117665	100004	2011-06-06 20:36:16.800303+02	100004	2011-06-06 20:36:22+02	\N	nautical mile	nautical mile	f	f	f	f	t	\N	\N	\N	\N
200099	200085	117665	100004	2011-06-06 20:36:29.206911+02	100004	2011-06-06 20:36:37+02	\N	knot	knot	f	f	f	f	t	\N	\N	\N	\N
200100	200085	117665	100004	2011-06-06 20:36:51.007572+02	100004	2011-06-06 20:36:59+02	\N	bar	bar	f	f	f	f	t	\N	\N	\N	\N
200009	\N	117665	100004	2011-05-27 18:46:44.370384+02	\N	\N	\N	All Units	All Units	t	f	f	f	t	\N	\N	\N	\N
200076	200041	117665	100004	2011-06-06 20:15:43.31889+02	100004	2011-06-06 20:27:24+02	\N	siemens square meter per mole	siemens square meter per mole	f	f	f	f	t	\N	\N	\N	\N
200084	200041	117665	100004	2011-06-06 20:17:45.189993+02	100004	2011-06-06 20:28:26+02	\N	ohm meter	ohm meter	f	f	f	f	t	\N	\N	\N	\N
200042	200041	117665	100004	2011-06-06 20:05:28.930233+02	100004	2011-06-06 20:17:54+02	\N	square meter	square meter	f	f	f	f	t	\N	\N	\N	\N
200077	200041	117665	100004	2011-06-06 20:15:58.618729+02	100004	2011-06-06 20:27:34+02	\N	farad per meter	farad per meter	f	f	f	f	t	\N	\N	\N	\N
200072	200041	117665	100004	2011-06-06 20:15:13.495019+02	100004	2011-06-06 20:26:51+02	\N	coulomb per square meter	coulomb per square meter	f	f	f	f	t	\N	\N	\N	\N
200078	200041	117665	100004	2011-06-06 20:16:52.444207+02	100004	2011-06-06 20:27:42+02	\N	henry per meter	henry per meter	f	f	f	f	t	\N	\N	\N	\N
200079	200041	117665	100004	2011-06-06 20:17:00.573399+02	100004	2011-06-06 20:27:48+02	\N	volt per meter	volt per meter	f	f	f	f	t	\N	\N	\N	\N
200046	200041	117665	100004	2011-06-06 20:06:32.638308+02	100004	2011-06-06 20:18:54+02	\N	meter per second squared	meter per second squared	f	f	f	f	t	\N	\N	\N	\N
200074	200041	117665	100004	2011-06-06 20:15:30.918863+02	100004	2011-06-06 20:27:06+02	\N	ampere per square meter	ampere per square meter	f	f	f	f	t	\N	\N	\N	\N
200080	200041	117665	100004	2011-06-06 20:17:07.707918+02	100004	2011-06-06 20:27:54+02	\N	ampere per meter	ampere per meter	f	f	f	f	t	\N	\N	\N	\N
\.


--
-- Data for Name: stati; Type: TABLE DATA; Schema: bayeos; Owner: bayeos
--

COPY stati (id, bezeichnung, beschreibung) FROM stdin;
8	manual changed value	\N
9	manual added value	\N
0	not set	\N
1	valid	\N
2	valid (with special restrictions)	\N
3	noisy	\N
4	unknown	\N
5	overrange	\N
6	outside limits	\N
7	invalid	\N
\.


--
-- Data for Name: sys_variablen; Type: TABLE DATA; Schema: bayeos; Owner: bayeos
--

COPY sys_variablen (name, value, description) FROM stdin;
arch_path	/local/backup/his	Pfad indem die Archive abgelegt werden.
aggr_last_his_massendaten_id	0	Wert der Sequence his_massendaten_id nach Abschluss der Aggregation.
\.


--
-- Data for Name: timezone; Type: TABLE DATA; Schema: bayeos; Owner: bayeos
--

COPY timezone (id, name) FROM stdin;
2	Etc/GMT-1
3	Etc/GMT-8
1	Etc/GMT
\.


--
-- Data for Name: tstati; Type: TABLE DATA; Schema: bayeos; Owner: bayeos
--

COPY tstati (id, name) FROM stdin;
0	ok
1	indifferent
2	corrected
\.


--
-- Data for Name: umrechnungen; Type: TABLE DATA; Schema: bayeos; Owner: bayeos
--

COPY umrechnungen (id_von, id_nach, funktion, bezeichnung, beschreibung) FROM stdin;
\.


--
-- Data for Name: valfuncargument; Type: TABLE DATA; Schema: bayeos; Owner: bayeos
--

COPY valfuncargument (id, fk_valfunction, index, alias) FROM stdin;
\.


--
-- Data for Name: valfuncargvalue; Type: TABLE DATA; Schema: bayeos; Owner: bayeos
--

COPY valfuncargvalue (fk_checks, fk_valfuncargument, value) FROM stdin;
\.


--
-- Data for Name: valfunccolumn; Type: TABLE DATA; Schema: bayeos; Owner: bayeos
--

COPY valfunccolumn (id, fk_valfunction, name) FROM stdin;
\.


--
-- Data for Name: valfunction; Type: TABLE DATA; Schema: bayeos; Owner: bayeos
--

COPY valfunction (id, name) FROM stdin;
\.


--
-- Data for Name: verweis; Type: TABLE DATA; Schema: bayeos; Owner: bayeos
--

COPY verweis (id, id_von, id_auf, von, bis, anteil) FROM stdin;
\.


--
-- Data for Name: verweis_extern; Type: TABLE DATA; Schema: bayeos; Owner: bayeos
--

COPY verweis_extern (id, id_von, id_auf, von, bis) FROM stdin;
\.


--
-- Data for Name: zugriff; Type: TABLE DATA; Schema: bayeos; Owner: bayeos
--

COPY zugriff (id_obj, id_benutzer, read, write, exec, inherit) FROM stdin;
\.


--
-- Name: aggr_funktion_pkey; Type: CONSTRAINT; Schema: bayeos; Owner: bayeos; Tablespace: 
--

ALTER TABLE ONLY aggr_funktion
    ADD CONSTRAINT aggr_funktion_pkey PRIMARY KEY (id);


--
-- Name: aggr_intervall_pkey; Type: CONSTRAINT; Schema: bayeos; Owner: bayeos; Tablespace: 
--

ALTER TABLE ONLY aggr_intervall
    ADD CONSTRAINT aggr_intervall_pkey PRIMARY KEY (id);


--
-- Name: arch_his_log_pkey; Type: CONSTRAINT; Schema: bayeos; Owner: bayeos; Tablespace: 
--

ALTER TABLE ONLY arch_his_log
    ADD CONSTRAINT arch_his_log_pkey PRIMARY KEY (id);


--
-- Name: art_objekt_pkey; Type: CONSTRAINT; Schema: bayeos; Owner: bayeos; Tablespace: 
--

ALTER TABLE ONLY art_objekt
    ADD CONSTRAINT art_objekt_pkey PRIMARY KEY (id);


--
-- Name: art_objekt_uname; Type: CONSTRAINT; Schema: bayeos; Owner: bayeos; Tablespace: 
--

ALTER TABLE ONLY art_objekt
    ADD CONSTRAINT art_objekt_uname UNIQUE (uname);


--
-- Name: benutzer_gr_id_benutzer_key; Type: CONSTRAINT; Schema: bayeos; Owner: bayeos; Tablespace: 
--

ALTER TABLE ONLY benutzer_gr
    ADD CONSTRAINT benutzer_gr_id_benutzer_key UNIQUE (id_benutzer, id_gruppe);


--
-- Name: benutzer_login_key; Type: CONSTRAINT; Schema: bayeos; Owner: bayeos; Tablespace: 
--

ALTER TABLE ONLY benutzer
    ADD CONSTRAINT benutzer_login_key UNIQUE (login);


--
-- Name: benutzer_pkey; Type: CONSTRAINT; Schema: bayeos; Owner: bayeos; Tablespace: 
--

ALTER TABLE ONLY benutzer
    ADD CONSTRAINT benutzer_pkey PRIMARY KEY (id);


--
-- Name: calibration_pkey; Type: CONSTRAINT; Schema: bayeos; Owner: bayeos; Tablespace: 
--

ALTER TABLE ONLY calibration
    ADD CONSTRAINT calibration_pkey PRIMARY KEY (id);


--
-- Name: einheiten_pkey; Type: CONSTRAINT; Schema: bayeos; Owner: bayeos; Tablespace: 
--

ALTER TABLE ONLY einheiten
    ADD CONSTRAINT einheiten_pkey PRIMARY KEY (id);


--
-- Name: geraete_pkey; Type: CONSTRAINT; Schema: bayeos; Owner: bayeos; Tablespace: 
--

ALTER TABLE ONLY geraete
    ADD CONSTRAINT geraete_pkey PRIMARY KEY (id);


--
-- Name: intervaltyp_pkey; Type: CONSTRAINT; Schema: bayeos; Owner: bayeos; Tablespace: 
--

ALTER TABLE ONLY intervaltyp
    ADD CONSTRAINT intervaltyp_pkey PRIMARY KEY (id);


--
-- Name: kompartimente_pkey; Type: CONSTRAINT; Schema: bayeos; Owner: bayeos; Tablespace: 
--

ALTER TABLE ONLY kompartimente
    ADD CONSTRAINT kompartimente_pkey PRIMARY KEY (id);


--
-- Name: labordaten_id_bis; Type: CONSTRAINT; Schema: bayeos; Owner: bayeos; Tablespace: 
--

ALTER TABLE ONLY labordaten
    ADD CONSTRAINT labordaten_id_bis UNIQUE (id, bis);


--
-- Name: labordaten_id_lbnr; Type: CONSTRAINT; Schema: bayeos; Owner: bayeos; Tablespace: 
--

ALTER TABLE ONLY labordaten
    ADD CONSTRAINT labordaten_id_lbnr UNIQUE (id, labornummer);


--
-- Name: messorte_pkey; Type: CONSTRAINT; Schema: bayeos; Owner: bayeos; Tablespace: 
--

ALTER TABLE ONLY messorte
    ADD CONSTRAINT messorte_pkey PRIMARY KEY (id);


--
-- Name: messungen_pkey; Type: CONSTRAINT; Schema: bayeos; Owner: bayeos; Tablespace: 
--

ALTER TABLE ONLY messungen
    ADD CONSTRAINT messungen_pkey PRIMARY KEY (id);


--
-- Name: messziele_pkey; Type: CONSTRAINT; Schema: bayeos; Owner: bayeos; Tablespace: 
--

ALTER TABLE ONLY messziele
    ADD CONSTRAINT messziele_pkey PRIMARY KEY (id);


--
-- Name: objekt_pkey; Type: CONSTRAINT; Schema: bayeos; Owner: bayeos; Tablespace: 
--

ALTER TABLE ONLY objekt
    ADD CONSTRAINT objekt_pkey PRIMARY KEY (id);


--
-- Name: pk_alert; Type: CONSTRAINT; Schema: bayeos; Owner: bayeos; Tablespace: 
--

ALTER TABLE ONLY alert
    ADD CONSTRAINT pk_alert PRIMARY KEY (id);


--
-- Name: pk_auth_db; Type: CONSTRAINT; Schema: bayeos; Owner: bayeos; Tablespace: 
--

ALTER TABLE ONLY auth_db
    ADD CONSTRAINT pk_auth_db PRIMARY KEY (id);


--
-- Name: pk_auth_ldap; Type: CONSTRAINT; Schema: bayeos; Owner: bayeos; Tablespace: 
--

ALTER TABLE ONLY auth_ldap
    ADD CONSTRAINT pk_auth_ldap PRIMARY KEY (id);


--
-- Name: pk_checkcolmessung; Type: CONSTRAINT; Schema: bayeos; Owner: bayeos; Tablespace: 
--

ALTER TABLE ONLY checkcolmessung
    ADD CONSTRAINT pk_checkcolmessung PRIMARY KEY (fk_checks, fk_valfunccolumn, fk_messungen);


--
-- Name: pk_checklistener; Type: CONSTRAINT; Schema: bayeos; Owner: bayeos; Tablespace: 
--

ALTER TABLE ONLY checklistener
    ADD CONSTRAINT pk_checklistener PRIMARY KEY (fk_listener, fk_checks);


--
-- Name: pk_checks; Type: CONSTRAINT; Schema: bayeos; Owner: bayeos; Tablespace: 
--

ALTER TABLE ONLY checks
    ADD CONSTRAINT pk_checks PRIMARY KEY (id);


--
-- Name: pk_coordinate_ref_sys; Type: CONSTRAINT; Schema: bayeos; Owner: bayeos; Tablespace: 
--

ALTER TABLE ONLY coordinate_ref_sys
    ADD CONSTRAINT pk_coordinate_ref_sys PRIMARY KEY (id);


--
-- Name: pk_listener; Type: CONSTRAINT; Schema: bayeos; Owner: bayeos; Tablespace: 
--

ALTER TABLE ONLY listener
    ADD CONSTRAINT pk_listener PRIMARY KEY (id);


--
-- Name: pk_timezone_ref_sys; Type: CONSTRAINT; Schema: bayeos; Owner: bayeos; Tablespace: 
--

ALTER TABLE ONLY timezone
    ADD CONSTRAINT pk_timezone_ref_sys PRIMARY KEY (id);


--
-- Name: pk_valfuncargument; Type: CONSTRAINT; Schema: bayeos; Owner: bayeos; Tablespace: 
--

ALTER TABLE ONLY valfuncargument
    ADD CONSTRAINT pk_valfuncargument PRIMARY KEY (id);


--
-- Name: pk_valfuncargvalue; Type: CONSTRAINT; Schema: bayeos; Owner: bayeos; Tablespace: 
--

ALTER TABLE ONLY valfuncargvalue
    ADD CONSTRAINT pk_valfuncargvalue PRIMARY KEY (fk_checks, fk_valfuncargument);


--
-- Name: pk_valfunccolumn; Type: CONSTRAINT; Schema: bayeos; Owner: bayeos; Tablespace: 
--

ALTER TABLE ONLY valfunccolumn
    ADD CONSTRAINT pk_valfunccolumn PRIMARY KEY (id);


--
-- Name: pk_valfunction; Type: CONSTRAINT; Schema: bayeos; Owner: bayeos; Tablespace: 
--

ALTER TABLE ONLY valfunction
    ADD CONSTRAINT pk_valfunction PRIMARY KEY (id);


--
-- Name: session_pkey; Type: CONSTRAINT; Schema: bayeos; Owner: bayeos; Tablespace: 
--

ALTER TABLE ONLY session
    ADD CONSTRAINT session_pkey PRIMARY KEY (id);


--
-- Name: stati_pkey; Type: CONSTRAINT; Schema: bayeos; Owner: bayeos; Tablespace: 
--

ALTER TABLE ONLY stati
    ADD CONSTRAINT stati_pkey PRIMARY KEY (id);


--
-- Name: sys_variablen_pkey; Type: CONSTRAINT; Schema: bayeos; Owner: bayeos; Tablespace: 
--

ALTER TABLE ONLY sys_variablen
    ADD CONSTRAINT sys_variablen_pkey PRIMARY KEY (name);


--
-- Name: tstati_pk; Type: CONSTRAINT; Schema: bayeos; Owner: bayeos; Tablespace: 
--

ALTER TABLE ONLY tstati
    ADD CONSTRAINT tstati_pk PRIMARY KEY (id);


--
-- Name: uk_auth_db_name; Type: CONSTRAINT; Schema: bayeos; Owner: bayeos; Tablespace: 
--

ALTER TABLE ONLY auth_db
    ADD CONSTRAINT uk_auth_db_name UNIQUE (name);


--
-- Name: uk_auth_ldap_name; Type: CONSTRAINT; Schema: bayeos; Owner: bayeos; Tablespace: 
--

ALTER TABLE ONLY auth_ldap
    ADD CONSTRAINT uk_auth_ldap_name UNIQUE (name);


--
-- Name: verweis_extern_pkey; Type: CONSTRAINT; Schema: bayeos; Owner: bayeos; Tablespace: 
--

ALTER TABLE ONLY verweis_extern
    ADD CONSTRAINT verweis_extern_pkey PRIMARY KEY (id);


--
-- Name: verweis_pkey; Type: CONSTRAINT; Schema: bayeos; Owner: bayeos; Tablespace: 
--

ALTER TABLE ONLY verweis
    ADD CONSTRAINT verweis_pkey PRIMARY KEY (id);


--
-- Name: zugriff_id_obj_key; Type: CONSTRAINT; Schema: bayeos; Owner: bayeos; Tablespace: 
--

ALTER TABLE ONLY zugriff
    ADD CONSTRAINT zugriff_id_obj_key UNIQUE (id_obj, id_benutzer);


--
-- Name: benutzer_gr_id_gruppe; Type: INDEX; Schema: bayeos; Owner: bayeos; Tablespace: 
--

CREATE INDEX benutzer_gr_id_gruppe ON benutzer_gr USING btree (id_gruppe);


--
-- Name: calibration_data_id_cal; Type: INDEX; Schema: bayeos; Owner: bayeos; Tablespace: 
--

CREATE INDEX calibration_data_id_cal ON calibration_data USING btree (id_cal);


--
-- Name: calibration_data_id_device_wann; Type: INDEX; Schema: bayeos; Owner: bayeos; Tablespace: 
--

CREATE INDEX calibration_data_id_device_wann ON calibration USING btree (id_device, wann);


--
-- Name: fki_auth_db; Type: INDEX; Schema: bayeos; Owner: bayeos; Tablespace: 
--

CREATE INDEX fki_auth_db ON benutzer USING btree (fk_auth_db);


--
-- Name: fki_benutzer_auth_ldap; Type: INDEX; Schema: bayeos; Owner: bayeos; Tablespace: 
--

CREATE INDEX fki_benutzer_auth_ldap ON benutzer USING btree (fk_auth_ldap);


--
-- Name: fki_coordinate_ref_sys; Type: INDEX; Schema: bayeos; Owner: bayeos; Tablespace: 
--

CREATE INDEX fki_coordinate_ref_sys ON coordinate_ref_sys USING btree (fk_srid_id);


--
-- Name: fki_tstati; Type: INDEX; Schema: bayeos; Owner: bayeos; Tablespace: 
--

CREATE INDEX fki_tstati ON massendaten USING btree (tstatus);


--
-- Name: fki_verweis_auf; Type: INDEX; Schema: bayeos; Owner: bayeos; Tablespace: 
--

CREATE INDEX fki_verweis_auf ON verweis USING btree (id_auf);


--
-- Name: fki_verweis_objekt_von; Type: INDEX; Schema: bayeos; Owner: bayeos; Tablespace: 
--

CREATE INDEX fki_verweis_objekt_von ON verweis USING btree (id_von);


--
-- Name: idx_aggr_avg_30min_id_von; Type: INDEX; Schema: bayeos; Owner: bayeos; Tablespace: 
--

CREATE UNIQUE INDEX idx_aggr_avg_30min_id_von ON aggr_avg_30min USING btree (id, von);

ALTER TABLE aggr_avg_30min CLUSTER ON idx_aggr_avg_30min_id_von;


--
-- Name: idx_aggr_avg_day_id_von; Type: INDEX; Schema: bayeos; Owner: bayeos; Tablespace: 
--

CREATE UNIQUE INDEX idx_aggr_avg_day_id_von ON aggr_avg_day USING btree (id, von);

ALTER TABLE aggr_avg_day CLUSTER ON idx_aggr_avg_day_id_von;


--
-- Name: idx_aggr_avg_hour_id_von; Type: INDEX; Schema: bayeos; Owner: bayeos; Tablespace: 
--

CREATE UNIQUE INDEX idx_aggr_avg_hour_id_von ON aggr_avg_hour USING btree (id, von);

ALTER TABLE aggr_avg_hour CLUSTER ON idx_aggr_avg_hour_id_von;


--
-- Name: idx_aggr_avg_month_id_von; Type: INDEX; Schema: bayeos; Owner: bayeos; Tablespace: 
--

CREATE UNIQUE INDEX idx_aggr_avg_month_id_von ON aggr_avg_month USING btree (id, von);

ALTER TABLE aggr_avg_month CLUSTER ON idx_aggr_avg_month_id_von;


--
-- Name: idx_aggr_avg_year_id_von; Type: INDEX; Schema: bayeos; Owner: bayeos; Tablespace: 
--

CREATE UNIQUE INDEX idx_aggr_avg_year_id_von ON aggr_avg_year USING btree (id, von);

ALTER TABLE aggr_avg_year CLUSTER ON idx_aggr_avg_year_id_von;


--
-- Name: idx_aggr_max_30min_id_von; Type: INDEX; Schema: bayeos; Owner: bayeos; Tablespace: 
--

CREATE UNIQUE INDEX idx_aggr_max_30min_id_von ON aggr_max_30min USING btree (id, von);

ALTER TABLE aggr_max_30min CLUSTER ON idx_aggr_max_30min_id_von;


--
-- Name: idx_aggr_max_day_id_von; Type: INDEX; Schema: bayeos; Owner: bayeos; Tablespace: 
--

CREATE UNIQUE INDEX idx_aggr_max_day_id_von ON aggr_max_day USING btree (id, von);

ALTER TABLE aggr_max_day CLUSTER ON idx_aggr_max_day_id_von;


--
-- Name: idx_aggr_max_hour_id_von; Type: INDEX; Schema: bayeos; Owner: bayeos; Tablespace: 
--

CREATE UNIQUE INDEX idx_aggr_max_hour_id_von ON aggr_max_hour USING btree (id, von);

ALTER TABLE aggr_max_hour CLUSTER ON idx_aggr_max_hour_id_von;


--
-- Name: idx_aggr_max_month_id_von; Type: INDEX; Schema: bayeos; Owner: bayeos; Tablespace: 
--

CREATE UNIQUE INDEX idx_aggr_max_month_id_von ON aggr_max_month USING btree (id, von);

ALTER TABLE aggr_max_month CLUSTER ON idx_aggr_max_month_id_von;


--
-- Name: idx_aggr_max_year_id_von; Type: INDEX; Schema: bayeos; Owner: bayeos; Tablespace: 
--

CREATE UNIQUE INDEX idx_aggr_max_year_id_von ON aggr_max_year USING btree (id, von);

ALTER TABLE aggr_max_year CLUSTER ON idx_aggr_max_year_id_von;


--
-- Name: idx_aggr_min_30min_id_von; Type: INDEX; Schema: bayeos; Owner: bayeos; Tablespace: 
--

CREATE UNIQUE INDEX idx_aggr_min_30min_id_von ON aggr_min_30min USING btree (id, von);

ALTER TABLE aggr_min_30min CLUSTER ON idx_aggr_min_30min_id_von;


--
-- Name: idx_aggr_min_day_id_von; Type: INDEX; Schema: bayeos; Owner: bayeos; Tablespace: 
--

CREATE UNIQUE INDEX idx_aggr_min_day_id_von ON aggr_min_day USING btree (id, von);

ALTER TABLE aggr_min_day CLUSTER ON idx_aggr_min_day_id_von;


--
-- Name: idx_aggr_min_hour_id_von; Type: INDEX; Schema: bayeos; Owner: bayeos; Tablespace: 
--

CREATE UNIQUE INDEX idx_aggr_min_hour_id_von ON aggr_min_hour USING btree (id, von);

ALTER TABLE aggr_min_hour CLUSTER ON idx_aggr_min_hour_id_von;


--
-- Name: idx_aggr_min_month_id_von; Type: INDEX; Schema: bayeos; Owner: bayeos; Tablespace: 
--

CREATE UNIQUE INDEX idx_aggr_min_month_id_von ON aggr_min_month USING btree (id, von);

ALTER TABLE aggr_min_month CLUSTER ON idx_aggr_min_month_id_von;


--
-- Name: idx_aggr_min_year_id_von; Type: INDEX; Schema: bayeos; Owner: bayeos; Tablespace: 
--

CREATE UNIQUE INDEX idx_aggr_min_year_id_von ON aggr_min_year USING btree (id, von);

ALTER TABLE aggr_min_year CLUSTER ON idx_aggr_min_year_id_von;


--
-- Name: idx_aggr_sum_30min_id_von; Type: INDEX; Schema: bayeos; Owner: bayeos; Tablespace: 
--

CREATE UNIQUE INDEX idx_aggr_sum_30min_id_von ON aggr_sum_30min USING btree (id, von);

ALTER TABLE aggr_sum_30min CLUSTER ON idx_aggr_sum_30min_id_von;


--
-- Name: idx_aggr_sum_day_id_von; Type: INDEX; Schema: bayeos; Owner: bayeos; Tablespace: 
--

CREATE UNIQUE INDEX idx_aggr_sum_day_id_von ON aggr_sum_day USING btree (id, von);

ALTER TABLE aggr_sum_day CLUSTER ON idx_aggr_sum_day_id_von;


--
-- Name: idx_aggr_sum_hour_id_von; Type: INDEX; Schema: bayeos; Owner: bayeos; Tablespace: 
--

CREATE UNIQUE INDEX idx_aggr_sum_hour_id_von ON aggr_sum_hour USING btree (id, von);

ALTER TABLE aggr_sum_hour CLUSTER ON idx_aggr_sum_hour_id_von;


--
-- Name: idx_aggr_sum_month_id_von; Type: INDEX; Schema: bayeos; Owner: bayeos; Tablespace: 
--

CREATE UNIQUE INDEX idx_aggr_sum_month_id_von ON aggr_sum_month USING btree (id, von);

ALTER TABLE aggr_sum_month CLUSTER ON idx_aggr_sum_month_id_von;


--
-- Name: idx_aggr_sum_year_id_von; Type: INDEX; Schema: bayeos; Owner: bayeos; Tablespace: 
--

CREATE UNIQUE INDEX idx_aggr_sum_year_id_von ON aggr_sum_year USING btree (id, von);

ALTER TABLE aggr_sum_year CLUSTER ON idx_aggr_sum_year_id_von;


--
-- Name: idx_his_labordaten_id; Type: INDEX; Schema: bayeos; Owner: bayeos; Tablespace: 
--

CREATE UNIQUE INDEX idx_his_labordaten_id ON his_labordaten USING btree (his_id);


--
-- Name: idx_his_massendaten_id; Type: INDEX; Schema: bayeos; Owner: bayeos; Tablespace: 
--

CREATE UNIQUE INDEX idx_his_massendaten_id ON his_massendaten USING btree (his_id);


--
-- Name: massendaten_uk; Type: INDEX; Schema: bayeos; Owner: bayeos; Tablespace: 
--

CREATE UNIQUE INDEX massendaten_uk ON massendaten USING btree (id, von);

ALTER TABLE massendaten CLUSTER ON massendaten_uk;


--
-- Name: objekt_de; Type: INDEX; Schema: bayeos; Owner: bayeos; Tablespace: 
--

CREATE INDEX objekt_de ON objekt USING btree (de);


--
-- Name: objekt_de_lo; Type: INDEX; Schema: bayeos; Owner: bayeos; Tablespace: 
--

CREATE INDEX objekt_de_lo ON objekt USING btree (lower((de)::text));



--
-- Name: objekt_id_art; Type: INDEX; Schema: bayeos; Owner: bayeos; Tablespace: 
--

CREATE INDEX objekt_id_art ON objekt USING btree (id_art);


--
-- Name: objekt_id_super; Type: INDEX; Schema: bayeos; Owner: bayeos; Tablespace: 
--

CREATE INDEX objekt_id_super ON objekt USING btree (id_super);


--
-- Name: zugriff_id_benutzer; Type: INDEX; Schema: bayeos; Owner: bayeos; Tablespace: 
--

CREATE INDEX zugriff_id_benutzer ON zugriff USING btree (id_benutzer);


--
-- Name: labordaten_inserts; Type: RULE; Schema: bayeos; Owner: bayeos
--

CREATE RULE labordaten_inserts AS ON INSERT TO labordaten WHERE (SELECT true AS bool FROM labordaten WHERE ((labordaten.id = new.id) AND (labordaten.bis = new.bis))) DO INSTEAD NOTHING;


--
-- Name: massendaten_inserts; Type: RULE; Schema: bayeos; Owner: bayeos
--

CREATE RULE massendaten_inserts AS ON INSERT TO massendaten WHERE (SELECT true AS bool FROM massendaten WHERE ((massendaten.id = new.id) AND (massendaten.von = new.von))) DO INSTEAD NOTHING;


--
-- Name: d_objekt; Type: TRIGGER; Schema: bayeos; Owner: bayeos
--

CREATE TRIGGER d_objekt AFTER DELETE ON objekt FOR EACH ROW EXECUTE PROCEDURE d_objekt();


--
-- Name: his_labordaten_id; Type: TRIGGER; Schema: bayeos; Owner: bayeos
--

CREATE TRIGGER his_labordaten_id BEFORE INSERT OR DELETE OR UPDATE ON labordaten FOR EACH ROW EXECUTE PROCEDURE write_his_labordaten();


--
-- Name: his_massendaten_id; Type: TRIGGER; Schema: bayeos; Owner: bayeos
--

CREATE TRIGGER his_massendaten_id BEFORE INSERT OR DELETE OR UPDATE ON massendaten FOR EACH ROW EXECUTE PROCEDURE write_his_massendaten();


--
-- Name: iu_objekt; Type: TRIGGER; Schema: bayeos; Owner: bayeos
--

CREATE TRIGGER iu_objekt AFTER INSERT OR UPDATE ON objekt FOR EACH ROW EXECUTE PROCEDURE iu_objekt();


--
-- Name: iu_objekt_extern; Type: TRIGGER; Schema: bayeos; Owner: bayeos
--

CREATE TRIGGER iu_objekt_extern AFTER INSERT OR UPDATE ON objekt FOR EACH ROW EXECUTE PROCEDURE iu_objekt_extern();


--
-- Name: trg_update_checks; Type: TRIGGER; Schema: bayeos; Owner: bayeos
--

CREATE TRIGGER trg_update_checks BEFORE UPDATE ON checks FOR EACH ROW EXECUTE PROCEDURE u_check();


--
-- Name: update_einheiten_utime; Type: TRIGGER; Schema: bayeos; Owner: bayeos
--

CREATE TRIGGER update_einheiten_utime AFTER UPDATE ON einheiten FOR EACH ROW EXECUTE PROCEDURE update_objekt_utime();


--
-- Name: update_geraete_utime; Type: TRIGGER; Schema: bayeos; Owner: bayeos
--

CREATE TRIGGER update_geraete_utime AFTER UPDATE ON geraete FOR EACH ROW EXECUTE PROCEDURE update_objekt_utime();


--
-- Name: update_kompartimente_utime; Type: TRIGGER; Schema: bayeos; Owner: bayeos
--

CREATE TRIGGER update_kompartimente_utime AFTER UPDATE ON kompartimente FOR EACH ROW EXECUTE PROCEDURE update_objekt_utime();


--
-- Name: update_messorte_utime; Type: TRIGGER; Schema: bayeos; Owner: bayeos
--

CREATE TRIGGER update_messorte_utime AFTER UPDATE ON messorte FOR EACH ROW EXECUTE PROCEDURE update_objekt_utime();


--
-- Name: update_messziele_utime; Type: TRIGGER; Schema: bayeos; Owner: bayeos
--

CREATE TRIGGER update_messziele_utime AFTER UPDATE ON messziele FOR EACH ROW EXECUTE PROCEDURE update_objekt_utime();


--
-- Name: benutzer_id_fkey; Type: FK CONSTRAINT; Schema: bayeos; Owner: bayeos
--

ALTER TABLE ONLY benutzer
    ADD CONSTRAINT benutzer_id_fkey FOREIGN KEY (id) REFERENCES objekt(id) ON DELETE CASCADE;


--
-- Name: calibration_data_id_cal_fkey; Type: FK CONSTRAINT; Schema: bayeos; Owner: bayeos
--

ALTER TABLE ONLY calibration_data
    ADD CONSTRAINT calibration_data_id_cal_fkey FOREIGN KEY (id_cal) REFERENCES calibration(id) ON DELETE CASCADE;


--
-- Name: calibration_id_device_fkey; Type: FK CONSTRAINT; Schema: bayeos; Owner: bayeos
--

ALTER TABLE ONLY calibration
    ADD CONSTRAINT calibration_id_device_fkey FOREIGN KEY (id_device) REFERENCES geraete(id) ON DELETE CASCADE;


--
-- Name: einheiten_id_fkey; Type: FK CONSTRAINT; Schema: bayeos; Owner: bayeos
--

ALTER TABLE ONLY einheiten
    ADD CONSTRAINT einheiten_id_fkey FOREIGN KEY (id) REFERENCES objekt(id) ON DELETE CASCADE;


--
-- Name: fk_alert_relations_checks; Type: FK CONSTRAINT; Schema: bayeos; Owner: bayeos
--

ALTER TABLE ONLY alert
    ADD CONSTRAINT fk_alert_relations_checks FOREIGN KEY (fk_checks) REFERENCES checks(id) ON UPDATE RESTRICT ON DELETE RESTRICT;


--
-- Name: fk_alert_relations_listener; Type: FK CONSTRAINT; Schema: bayeos; Owner: bayeos
--

ALTER TABLE ONLY alert
    ADD CONSTRAINT fk_alert_relations_listener FOREIGN KEY (fk_listener) REFERENCES listener(id) ON UPDATE RESTRICT ON DELETE RESTRICT;


--
-- Name: fk_auth_db; Type: FK CONSTRAINT; Schema: bayeos; Owner: bayeos
--

ALTER TABLE ONLY benutzer
    ADD CONSTRAINT fk_auth_db FOREIGN KEY (fk_auth_db) REFERENCES auth_db(id);


--
-- Name: fk_benutzer_auth_ldap; Type: FK CONSTRAINT; Schema: bayeos; Owner: bayeos
--

ALTER TABLE ONLY benutzer
    ADD CONSTRAINT fk_benutzer_auth_ldap FOREIGN KEY (fk_auth_ldap) REFERENCES auth_ldap(id);


--
-- Name: fk_checkcol_reference_aggr_fun; Type: FK CONSTRAINT; Schema: bayeos; Owner: bayeos
--

ALTER TABLE ONLY checkcolmessung
    ADD CONSTRAINT fk_checkcol_reference_aggr_fun FOREIGN KEY (fk_function) REFERENCES aggr_funktion(id) ON UPDATE RESTRICT ON DELETE RESTRICT;


--
-- Name: fk_checkcol_reference_aggr_int; Type: FK CONSTRAINT; Schema: bayeos; Owner: bayeos
--

ALTER TABLE ONLY checkcolmessung
    ADD CONSTRAINT fk_checkcol_reference_aggr_int FOREIGN KEY (fk_interval) REFERENCES aggr_intervall(id) ON UPDATE RESTRICT ON DELETE RESTRICT;


--
-- Name: fk_checkcol_reference_messunge; Type: FK CONSTRAINT; Schema: bayeos; Owner: bayeos
--

ALTER TABLE ONLY checkcolmessung
    ADD CONSTRAINT fk_checkcol_reference_messunge FOREIGN KEY (fk_messungen) REFERENCES messungen(id) ON UPDATE RESTRICT ON DELETE RESTRICT;


--
-- Name: fk_checkcol_relations_checks; Type: FK CONSTRAINT; Schema: bayeos; Owner: bayeos
--

ALTER TABLE ONLY checkcolmessung
    ADD CONSTRAINT fk_checkcol_relations_checks FOREIGN KEY (fk_checks) REFERENCES checks(id) ON UPDATE RESTRICT ON DELETE RESTRICT;


--
-- Name: fk_checkcol_relations_valfuncc; Type: FK CONSTRAINT; Schema: bayeos; Owner: bayeos
--

ALTER TABLE ONLY checkcolmessung
    ADD CONSTRAINT fk_checkcol_relations_valfuncc FOREIGN KEY (fk_valfunccolumn) REFERENCES valfunccolumn(id) ON UPDATE RESTRICT ON DELETE RESTRICT;


--
-- Name: fk_checklis_checklist_checks; Type: FK CONSTRAINT; Schema: bayeos; Owner: bayeos
--

ALTER TABLE ONLY checklistener
    ADD CONSTRAINT fk_checklis_checklist_checks FOREIGN KEY (fk_checks) REFERENCES checks(id) ON UPDATE RESTRICT ON DELETE RESTRICT;


--
-- Name: fk_checklis_checklist_listener; Type: FK CONSTRAINT; Schema: bayeos; Owner: bayeos
--

ALTER TABLE ONLY checklistener
    ADD CONSTRAINT fk_checklis_checklist_listener FOREIGN KEY (fk_listener) REFERENCES listener(id) ON UPDATE RESTRICT ON DELETE RESTRICT;


--
-- Name: fk_checks_relations_valfunct; Type: FK CONSTRAINT; Schema: bayeos; Owner: bayeos
--

ALTER TABLE ONLY checks
    ADD CONSTRAINT fk_checks_relations_valfunct FOREIGN KEY (fk_valfunction) REFERENCES valfunction(id) ON UPDATE RESTRICT ON DELETE RESTRICT;


--
-- Name: fk_coordinate_ref_sys; Type: FK CONSTRAINT; Schema: bayeos; Owner: bayeos
--

-- ALTER TABLE ONLY coordinate_ref_sys
--     ADD CONSTRAINT fk_coordinate_ref_sys FOREIGN KEY (fk_srid_id) REFERENCES public.spatial_ref_sys(srid);


--
-- Name: fk_labordaten_stati; Type: FK CONSTRAINT; Schema: bayeos; Owner: bayeos
--

ALTER TABLE ONLY labordaten
    ADD CONSTRAINT fk_labordaten_stati FOREIGN KEY (status) REFERENCES stati(id);


--
-- Name: fk_massendaten_messungen; Type: FK CONSTRAINT; Schema: bayeos; Owner: bayeos
--

ALTER TABLE ONLY massendaten
    ADD CONSTRAINT fk_massendaten_messungen FOREIGN KEY (id) REFERENCES messungen(id) ON DELETE CASCADE;


--
-- Name: fk_massendaten_stati; Type: FK CONSTRAINT; Schema: bayeos; Owner: bayeos
--

ALTER TABLE ONLY massendaten
    ADD CONSTRAINT fk_massendaten_stati FOREIGN KEY (status) REFERENCES stati(id);


--
-- Name: fk_messorte_crs_id; Type: FK CONSTRAINT; Schema: bayeos; Owner: bayeos
--

ALTER TABLE ONLY messorte
    ADD CONSTRAINT fk_messorte_crs_id FOREIGN KEY (fk_crs_id) REFERENCES coordinate_ref_sys(id);


--
-- Name: fk_messungen_intervaltyp_id; Type: FK CONSTRAINT; Schema: bayeos; Owner: bayeos
--

ALTER TABLE ONLY messungen
    ADD CONSTRAINT fk_messungen_intervaltyp_id FOREIGN KEY (id_intervaltyp) REFERENCES intervaltyp(id) ON UPDATE RESTRICT ON DELETE RESTRICT;


--
-- Name: fk_messungen_timezone; Type: FK CONSTRAINT; Schema: bayeos; Owner: bayeos
--

ALTER TABLE ONLY messungen
    ADD CONSTRAINT fk_messungen_timezone FOREIGN KEY (fk_timezone_id) REFERENCES timezone(id);


--
-- Name: fk_tstati; Type: FK CONSTRAINT; Schema: bayeos; Owner: bayeos
--

ALTER TABLE ONLY massendaten
    ADD CONSTRAINT fk_tstati FOREIGN KEY (tstatus) REFERENCES tstati(id);


--
-- Name: fk_valfunca_relations_checks; Type: FK CONSTRAINT; Schema: bayeos; Owner: bayeos
--

ALTER TABLE ONLY valfuncargvalue
    ADD CONSTRAINT fk_valfunca_relations_checks FOREIGN KEY (fk_checks) REFERENCES checks(id) ON UPDATE RESTRICT ON DELETE RESTRICT;


--
-- Name: fk_valfunca_relations_valfunca; Type: FK CONSTRAINT; Schema: bayeos; Owner: bayeos
--

ALTER TABLE ONLY valfuncargvalue
    ADD CONSTRAINT fk_valfunca_relations_valfunca FOREIGN KEY (fk_valfuncargument) REFERENCES valfuncargument(id) ON UPDATE RESTRICT ON DELETE RESTRICT;


--
-- Name: fk_valfunca_relations_valfunct; Type: FK CONSTRAINT; Schema: bayeos; Owner: bayeos
--

ALTER TABLE ONLY valfuncargument
    ADD CONSTRAINT fk_valfunca_relations_valfunct FOREIGN KEY (fk_valfunction) REFERENCES valfunction(id) ON UPDATE RESTRICT ON DELETE RESTRICT;


--
-- Name: fk_valfuncc_relations_valfunct; Type: FK CONSTRAINT; Schema: bayeos; Owner: bayeos
--

ALTER TABLE ONLY valfunccolumn
    ADD CONSTRAINT fk_valfuncc_relations_valfunct FOREIGN KEY (fk_valfunction) REFERENCES valfunction(id) ON UPDATE RESTRICT ON DELETE RESTRICT;


--
-- Name: fk_verweis_auf; Type: FK CONSTRAINT; Schema: bayeos; Owner: bayeos
--

ALTER TABLE ONLY verweis
    ADD CONSTRAINT fk_verweis_auf FOREIGN KEY (id_auf) REFERENCES objekt(id);


--
-- Name: fk_verweis_extern_obj_id; Type: FK CONSTRAINT; Schema: bayeos; Owner: bayeos
--

ALTER TABLE ONLY verweis_extern
    ADD CONSTRAINT fk_verweis_extern_obj_id FOREIGN KEY (id_auf) REFERENCES objekt(id);


--
-- Name: fk_verweis_objekt_von; Type: FK CONSTRAINT; Schema: bayeos; Owner: bayeos
--

ALTER TABLE ONLY verweis
    ADD CONSTRAINT fk_verweis_objekt_von FOREIGN KEY (id_von) REFERENCES objekt(id);


--
-- Name: geraete_id_fkey; Type: FK CONSTRAINT; Schema: bayeos; Owner: bayeos
--

ALTER TABLE ONLY geraete
    ADD CONSTRAINT geraete_id_fkey FOREIGN KEY (id) REFERENCES objekt(id) ON DELETE CASCADE;


--
-- Name: kompartimente_id_fkey; Type: FK CONSTRAINT; Schema: bayeos; Owner: bayeos
--

ALTER TABLE ONLY kompartimente
    ADD CONSTRAINT kompartimente_id_fkey FOREIGN KEY (id) REFERENCES objekt(id) ON DELETE CASCADE;


--
-- Name: messorte_id_fkey; Type: FK CONSTRAINT; Schema: bayeos; Owner: bayeos
--

ALTER TABLE ONLY messorte
    ADD CONSTRAINT messorte_id_fkey FOREIGN KEY (id) REFERENCES objekt(id) ON DELETE CASCADE;


--
-- Name: messungen_id_fkey; Type: FK CONSTRAINT; Schema: bayeos; Owner: bayeos
--

ALTER TABLE ONLY messungen
    ADD CONSTRAINT messungen_id_fkey FOREIGN KEY (id) REFERENCES objekt(id) ON DELETE CASCADE;


--
-- Name: messziele_id_fkey; Type: FK CONSTRAINT; Schema: bayeos; Owner: bayeos
--

ALTER TABLE ONLY messziele
    ADD CONSTRAINT messziele_id_fkey FOREIGN KEY (id) REFERENCES objekt(id) ON DELETE CASCADE;


--
-- Name: objekt_id_art_fkey; Type: FK CONSTRAINT; Schema: bayeos; Owner: bayeos
--

ALTER TABLE ONLY objekt
    ADD CONSTRAINT objekt_id_art_fkey FOREIGN KEY (id_art) REFERENCES art_objekt(id);


--
-- Name: objekt_id_super_fkey; Type: FK CONSTRAINT; Schema: bayeos; Owner: bayeos
--

ALTER TABLE ONLY objekt
    ADD CONSTRAINT objekt_id_super_fkey FOREIGN KEY (id_super) REFERENCES objekt(id) ON DELETE CASCADE;


--
-- Name: session_id_benutzer_fkey; Type: FK CONSTRAINT; Schema: bayeos; Owner: bayeos
--

ALTER TABLE ONLY session
    ADD CONSTRAINT session_id_benutzer_fkey FOREIGN KEY (id_benutzer) REFERENCES benutzer(id);


--
-- Name: umrechnungen_id_nach_fkey; Type: FK CONSTRAINT; Schema: bayeos; Owner: bayeos
--

ALTER TABLE ONLY umrechnungen
    ADD CONSTRAINT umrechnungen_id_nach_fkey FOREIGN KEY (id_nach) REFERENCES einheiten(id) ON DELETE CASCADE;


--
-- Name: umrechnungen_id_von_fkey; Type: FK CONSTRAINT; Schema: bayeos; Owner: bayeos
--

ALTER TABLE ONLY umrechnungen
    ADD CONSTRAINT umrechnungen_id_von_fkey FOREIGN KEY (id_von) REFERENCES einheiten(id) ON DELETE CASCADE;


--
-- Name: verweis_id_auf_fkey; Type: FK CONSTRAINT; Schema: bayeos; Owner: bayeos
--

ALTER TABLE ONLY verweis
    ADD CONSTRAINT verweis_id_auf_fkey FOREIGN KEY (id_auf) REFERENCES objekt(id) ON DELETE CASCADE;


--
-- Name: verweis_id_von_fkey; Type: FK CONSTRAINT; Schema: bayeos; Owner: bayeos
--

ALTER TABLE ONLY verweis
    ADD CONSTRAINT verweis_id_von_fkey FOREIGN KEY (id_von) REFERENCES objekt(id) ON DELETE CASCADE;


--
-- Name: zugriff_id_benutzer_fkey; Type: FK CONSTRAINT; Schema: bayeos; Owner: bayeos
--

ALTER TABLE ONLY zugriff
    ADD CONSTRAINT zugriff_id_benutzer_fkey FOREIGN KEY (id_benutzer) REFERENCES benutzer(id) ON DELETE CASCADE;


--
-- Name: zugriff_id_obj_fkey; Type: FK CONSTRAINT; Schema: bayeos; Owner: bayeos
--

ALTER TABLE ONLY zugriff
    ADD CONSTRAINT zugriff_id_obj_fkey FOREIGN KEY (id_obj) REFERENCES objekt(id) ON DELETE CASCADE;


--
-- Name: bayeos; Type: ACL; Schema: -; Owner: bayeos
--

REVOKE ALL ON SCHEMA bayeos FROM PUBLIC;
REVOKE ALL ON SCHEMA bayeos FROM bayeos;
GRANT ALL ON SCHEMA bayeos TO bayeos;


--
-- Name: objekt; Type: ACL; Schema: bayeos; Owner: bayeos
--

REVOKE ALL ON TABLE objekt FROM PUBLIC;
REVOKE ALL ON TABLE objekt FROM bayeos;
GRANT ALL ON TABLE objekt TO bayeos;


--
-- Name: get_child_objekte(integer, integer); Type: ACL; Schema: bayeos; Owner: bayeos
--

REVOKE ALL ON FUNCTION get_child_objekte(_id integer, _id_art integer) FROM PUBLIC;
REVOKE ALL ON FUNCTION get_child_objekte(_id integer, _id_art integer) FROM bayeos;
GRANT ALL ON FUNCTION get_child_objekte(_id integer, _id_art integer) TO bayeos;
GRANT ALL ON FUNCTION get_child_objekte(_id integer, _id_art integer) TO PUBLIC;


--
-- Name: get_user_id(character varying, character); Type: ACL; Schema: bayeos; Owner: bayeos
--

REVOKE ALL ON FUNCTION get_user_id(character varying, character) FROM PUBLIC;
REVOKE ALL ON FUNCTION get_user_id(character varying, character) FROM bayeos;
GRANT ALL ON FUNCTION get_user_id(character varying, character) TO bayeos;
GRANT ALL ON FUNCTION get_user_id(character varying, character) TO PUBLIC;


--
-- Name: benutzer; Type: ACL; Schema: bayeos; Owner: bayeos
--

REVOKE ALL ON TABLE benutzer FROM PUBLIC;
REVOKE ALL ON TABLE benutzer FROM bayeos;
GRANT ALL ON TABLE benutzer TO bayeos;


--
-- Name: benutzer_gr; Type: ACL; Schema: bayeos; Owner: bayeos
--

REVOKE ALL ON TABLE benutzer_gr FROM PUBLIC;
REVOKE ALL ON TABLE benutzer_gr FROM bayeos;
GRANT ALL ON TABLE benutzer_gr TO bayeos;


--
-- Name: massendaten; Type: ACL; Schema: bayeos; Owner: bayeos
--

REVOKE ALL ON TABLE massendaten FROM PUBLIC;
REVOKE ALL ON TABLE massendaten FROM bayeos;
GRANT ALL ON TABLE massendaten TO bayeos;


--
-- Name: objekt_id; Type: ACL; Schema: bayeos; Owner: bayeos
--

REVOKE ALL ON SEQUENCE objekt_id FROM PUBLIC;
REVOKE ALL ON SEQUENCE objekt_id FROM bayeos;
GRANT ALL ON SEQUENCE objekt_id TO bayeos;


--
-- Name: stati; Type: ACL; Schema: bayeos; Owner: bayeos
--

REVOKE ALL ON TABLE stati FROM PUBLIC;
REVOKE ALL ON TABLE stati FROM bayeos;
GRANT ALL ON TABLE stati TO bayeos;

-- Grant default role access to all root nodes
-- Folder without inherit 
insert into zugriff SELECT id,200003,true,false,false,false from objekt where de ilike 'All Folders';

-- All other metadata with inherit 
insert into zugriff SELECT id,200003,true,false,false,true from objekt where de in ('All Users','All Devices','All Compartiments','All Locations','All Targets','All Units');


--
-- PostgreSQL database dump complete
--

