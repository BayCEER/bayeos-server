#!/bin/bash
echo "Running liquibase command .."
java -jar liquibase.jar \
--driver=org.postgresql.Driver \
--classpath=postgresql-9.0-801.jdbc4.jar \
--changeLogFile=changelog.xml \
--url="jdbc:postgresql://localhost/bayeos" \
--username="bayeos" \
--password="4336bc9de7a6b11940e897ee22956d51" \
$1
echo "Finished."