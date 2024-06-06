 -- add not null constraint 
update messungen set id_intervaltyp = 0 where id_intervaltyp is null;
alter table messungen alter column id_intervaltyp set not null;