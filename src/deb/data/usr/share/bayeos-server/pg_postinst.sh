#!/usr/bin/env bash
set -e
PRG="$0"
INSTALLDIR=`dirname "$PRG"`

if ! psql -c '\q' bayeos > /dev/null 2>&1
then 
 echo "Create database and user ..."
 dropuser --if-exists bayeos 2>&1 
 createuser -r -S -D bayeos 2>&1
 psql -c "ALTER ROLE bayeos PASSWORD '4336bc9de7a6b11940e897ee22956d51'" 2>&1
 createdb -O bayeos -E UTF-8 bayeos 2>&1
fi

psql -c "ALTER ROLE bayeos SUPERUSER" 2>&1
java -Djava.security.egd=file:/dev/../dev/urandom -cp $INSTALLDIR/lib/*:$INSTALLDIR/drivers/* org.flywaydb.commandline.Main -url=${db.url} -driver=org.postgresql.Driver -user=${db.user} -password=${db.password} -locations=filesystem:$INSTALLDIR/sql migrate
psql -c "ALTER ROLE bayeos NOSUPERUSER" 2>&1