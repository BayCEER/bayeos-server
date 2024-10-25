#!/usr/bin/env bash
set -e
PRG="$0"
INSTALLDIR=`dirname "$PRG"`

if ! psql -c '\q' ${db.name} > /dev/null 2>&1
then 
 echo "Create database and user ..."
 dropuser --if-exists ${db.username} 2>&1 
 createuser -r -S -D ${db.username} 2>&1
 psql -c "ALTER ROLE ${db.username} PASSWORD '${db.password}'" 2>&1
 createdb -O ${db.username} -E UTF-8 ${db.name} 2>&1
fi

psql -c "ALTER ROLE ${db.username} SUPERUSER" 2>&1
java -Djava.security.egd=file:/dev/../dev/urandom -cp $INSTALLDIR/lib/*:$INSTALLDIR/drivers/* org.flywaydb.commandline.Main -url=jdbc:postgresql://${db.hostname}/${db.name} -driver=org.postgresql.Driver -user=${db.username} -password=${db.password} -locations=filesystem:$INSTALLDIR/sql migrate
psql -c "ALTER ROLE ${db.username} NOSUPERUSER" 2>&1