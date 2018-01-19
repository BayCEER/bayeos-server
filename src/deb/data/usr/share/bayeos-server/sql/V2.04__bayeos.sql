CREATE OR REPLACE FUNCTION bayeos.create_user(
	_id_benutzer integer,
	_login character varying,
	_pw character varying,
	_name character varying,
	_authmethod character varying,
	_authname character varying)
    RETURNS integer
    LANGUAGE 'plpgsql'

    COST 100
    VOLATILE 
AS $BODY$
declare
rec record;
rec_auth record;
begin

if (_authMethod = 'DB') then
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
	 insert into benutzer(id,login,pw) values (rec.create_objekt,_login,crypt(_pw,gen_salt('des')));
	else 
	 insert into benutzer(id,login,pw,fk_auth_ldap) values (rec.create_objekt,_login,crypt(_pw,gen_salt('des')),rec_auth.id);
	end if;	
	-- Grant access on self -- 
	insert into zugriff(id_obj,id_benutzer,read,write,exec,inherit) values (rec.create_objekt,rec.create_objekt,true,true,true,false);		 
end if;

-- Try to grant default role to new user --
begin
	perform grant_role(_login, 'All Users');
	exception when raise_exception then
end;  

return rec.create_objekt;
end; 
$BODY$;