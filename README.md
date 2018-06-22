# drt-db-migration
This is a standalone tool to migrate leveldb data from akka persistence into postgres


#### Background
Currently the DRT v2 app store journal data via leveldb serialised via protobuf. The snapshots are stored in a file system also serialised with protobuf.
After this tool is run, the data will be in postgres.

#### Configure the tool
To use this tool you need to provide the following configuration
```
portcode = ${?PORT_CODE}

persistenceBaseDir = ${?PERSISTENCE_BASE_DIR}

snapshotsDir = ""${persistenceBaseDir}"/snapshots"

db {
  host = ${?POSTGRES_HOST}
  url = "jdbc:postgresql://"${db.host}":5432/"${portcode}"?reWriteBatchedInserts=true"
  user = ${?POSTGRES_USER}
  password = ${?POSTGRES_PASSWORD}
}

```
This tool is used by DRT v2 to transfer port data which is stored in a filesystem to a database.  
By default if you provide the portcode it assumes the user/password and database name is also the same.

#### Usage 

```bash
SBT_OPTS="-Xms8G -Xmx8G" sbt run 
Usage: drt-db-migration [journal|snapshot|show]
 
 Command: journal [options]
 migrates leveldb data to the journal table
   --persistenceId <value>  persistenceId to migrate
   --startSequence <value>  start sequence number
 Command: snapshot [options]
 migrates file data to the snapshot table
   --persistenceId <value>  persistenceId to migrate
   --startSequence <value>  start sequence number
 Command: show
 shows the state of play of the database and file system

```

```bash
SBT_OPTS="-Xms8G -Xmx8G" sbt run journal
SBT_OPTS="-Xms8G -Xmx8G" sbt run snapshot
SBT_OPTS="-Xms8G -Xmx8G" sbt run show
```

