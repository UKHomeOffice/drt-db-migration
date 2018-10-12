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
      |  PRIMARY KEY(persistence_id, sequence_number)
      |);
    """.stripMargin

  val journalUniqueIndex: String = "CREATE UNIQUE INDEX journal_ordering_idx ON public.journal(ordering);"

  val snapshotSql: String =
    """
      |CREATE TABLE IF NOT EXISTS public.snapshot (
      |  persistence_id VARCHAR(255) NOT NULL,
      |  sequence_number BIGINT NOT NULL,
      |  created BIGINT NOT NULL,
      |  snapshot BYTEA NOT NULL,
      |  PRIMARY KEY(persistence_id, sequence_number)
      |);
    """.stripMargin

  val sqlList: Seq[String] = dropTables ++ Seq(journalSql, journalUniqueIndex, snapshotSql)

  def dropAndRecreateTables(): Unit = {
    withDatasource { implicit dataSource =>
      for (sql <- sqlList) {
        withPreparedStatement(sql, { implicit statement =>
          statement.execute
        })
      }
    }

  }

}
