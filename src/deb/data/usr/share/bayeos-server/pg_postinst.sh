#!/bin/bash
set -e
if ! psql -c '\q' bayeos > /dev/null 2>&1
then 
 echo "Create database and user bayeos"
 createuser -r -S -D bayeos 2>&1
 createdb -O bayeos bayeos 2>&1
 echo "Creating extensions.." 
 psql -c "CREATE EXTENSION IF NOT EXISTS tablefunc SCHEMA public;" bayeos 2>&1
 psql -c "CREATE EXTENSION IF NOT EXISTS plperl SCHEMA pg_catalog;" bayeos 2>&1
 psql -c "CREATE EXTENSION IF NOT EXISTS plperlu SCHEMA pg_catalog;" bayeos 2>&1
 psql -c "CREATE EXTENSION IF NOT EXISTS pgcrypto SCHEMA public;" bayeos 2>&1
 echo "Installing core system.." 
 psql -d bayeos -f /usr/share/bayeos-server/bayeos.sql 2>&1 
fi
echo "Alter user bayeos" 
psql -c "ALTER ROLE bayeos PASSWORD '4336bc9de7a6b11940e897ee22956d51'" 2>&1
