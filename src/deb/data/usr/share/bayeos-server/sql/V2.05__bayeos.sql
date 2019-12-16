-- Fixed wrong schema in select 
CREATE OR REPLACE FUNCTION get_child_ids(integer)
  RETURNS text AS
$BODY$declare
rec record;
rec2 record;
ids text := '';
begin
if $1>0 then
        for rec in select id,get_child_ids(id)
from objekt where id_super=$1 loop
ids:=ids||','||rec.id||rec.get_child_ids;
end loop;
end if;

return ids;
end;$BODY$
  LANGUAGE plpgsql VOLATILE
  COST 100;
