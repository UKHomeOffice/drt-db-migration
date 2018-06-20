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

#### Run the tool

```bash
sbt run
```

