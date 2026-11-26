# SQL Server init

Compose creates the `catalog` database with an **inline** `sqlserver-init` command
(see `docker-compose.yml`). That avoids Windows CRLF breaking a mounted shell script.

`create-catalog.sql` is kept as a readable reference of the same statement.
