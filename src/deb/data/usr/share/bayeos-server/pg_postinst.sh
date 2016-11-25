#!/usr/bin/env bash
set -e

PRG="$0"
INSTALLDIR=`dirname "$PRG"`

if ! psql -c '\q' bayeos > /dev/null 2>&1
then 
 echo "Create database and user ..."
 createuser -r -S -D bayeos 2>&1
 psql -c "ALTER ROLE bayeos PASSWORD '4336bc9de7a6b11940e897ee22956d51'" 2>&1
 createdb -O bayeos -E UTF-8 bayeos 2>&1
else 
 echo "Drop liquibase and add flyway tables if not exist"
 psql -f $INSTALLDIR/sql/migrate-liquibase.sql bayeos
fi

psql -c "ALTER ROLE bayeos SUPERUSER" 2>&1
echo "Update database ..."
/usr/bin/java -Djava.security.egd=file:/dev/../dev/urandom -cp "$INSTALLDIR/lib/*" org.flywaydb.commandline.Main migrate
psql -c "ALTER ROLE bayeos NOSUPERUSER" 2>&1