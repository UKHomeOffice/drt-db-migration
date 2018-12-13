package uk.gov.homeoffice.drt

trait RecreateTables {
  this: UsingPostgres with UsingDatabase =>

  val tables: Seq[String] = Seq("journal", "snapshot")

  val dropTables: Seq[String] = for (table <- tables) yield {
    s"DROP TABLE IF EXISTS public.$table;"
  }

  val journalSql: String =
    """
      |CREATE TABLE IF NOT EXISTS public.journal (
      |  ordering BIGSERIAL,
      |  persistence_id VARCHAR(255) NOT NULL,
      |  sequence_number BIGINT NOT NULL,
      |  deleted BOOLEAN DEFAULT FALSE,
      |  tags VARCHAR(255) DEFAULT NULL,
      |  message BYTEA NOT NULL,
      |  from_migration BOOLEAN default FALSE,
      |  PRIMARY KEY(persistence_id, sequence_number)
      |);
    """.stripMargin

  val journalFromMigrationIndex: String = "CREATE INDEX journal_from_migration_idx ON public.journal(from_migration);"
  val journalUniqueIndex: String = "CREATE UNIQUE INDEX journal_ordering_idx ON public.journal(ordering);"

  val snapshotSql: String =
    """
      |CREATE TABLE IF NOT EXISTS public.snapshot (
      |  persistence_id VARCHAR(255) NOT NULL,
      |  sequence_number BIGINT NOT NULL,
      |  created BIGINT NOT NULL,
      |  snapshot BYTEA NOT NULL,
      |  from_migration BOOLEAN default FALSE,
      |  PRIMARY KEY(persistence_id, sequence_number)
      |);
    """.stripMargin

  val snapshotFromMigrationIndex: String = "CREATE INDEX snapshot_from_migration_idx ON public.snapshot(from_migration);"

  val recreateTablesSql: Seq[String] = dropTables ++ Seq(journalSql, journalFromMigrationIndex, journalUniqueIndex, snapshotSql, snapshotFromMigrationIndex)

  def dropAndRecreateTables(): Unit = {
    withDatasource { implicit dataSource =>
      for (sql <- recreateTablesSql) {
        withPreparedStatement(sql, { implicit statement =>
          statement.execute
        })
      }
    }

  }

  val cleanseJournal: String = "DELETE FROM public.journal WHERE from_migration = FALSE;"
  val cleanseSnapshot: String = "DELETE FROM public.snapshot WHERE from_migration = FALSE;"

  val cleanseTablesSql: Seq[String] = Seq(cleanseJournal, cleanseSnapshot)

  def cleanseTables(): Unit = {
    withDatasource { implicit dataSource =>
      for (sql <- cleanseTablesSql) {
        withPreparedStatement(sql, { implicit statement =>
          statement.execute
        })
      }
    }
  }

}
