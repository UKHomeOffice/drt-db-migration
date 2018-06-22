package uk.gov.homeoffice.drt

import akka.actor.ActorSystem
import akka.persistence.query.journal.leveldb.scaladsl.LeveldbReadJournal
import akka.stream.ActorMaterializer
import org.slf4j.Logger
import uk.gov.homeoffice.drt.Boot.allSnapshotPersistentIds

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContextExecutor}

trait ShowSummary {
  this: JournalMigration with SnapshotsMigration with UsingPostgres with UsingDatabase =>
  val log: Logger
  val readJournal: LeveldbReadJournal
  implicit val system: ActorSystem
  implicit val mat: ActorMaterializer
  implicit val executionContext: ExecutionContextExecutor

  def showSummary() : Unit = {
    //    val ids = Await.result(allJournalPersistentIds, Duration.Inf)
    //
    //    log.info("Journal File system:")
    //    Future.sequence{
    //      for {
    //        id <- ids
    //      } yield readJournal.currentEventsByPersistenceId(id).map { event => {
    //        if (event.sequenceNr % 1000 == 0) log.info(s" $id , ${event.sequenceNr}")
    //        (id, event.sequenceNr)
    //      }
    //      }.runWith(Sink.seq).map(s => log.info(s"persistenceId ${s.head._1} count ${s.size} with max sequenceNumber ${s.last._2}"))
    //    }

    log.info(s"Summary")


    log.info("=================")
    log.info("Database Summary")
    log.info("=================")
    withDatasource { implicit dataSource =>
      val tables = Seq("journal", "snapshot")
      for (table <- tables) {
        val sql = s"select persistence_id, count(sequence_number), max(sequence_number) from $table group by persistence_id order by persistence_id ASC"
        withPreparedStatement(sql, { implicit statement =>
          val rs = statement.executeQuery()
          val columnCnt: Int = rs.getMetaData.getColumnCount
          val columns: IndexedSeq[String] = 1 to columnCnt map rs.getMetaData.getColumnName
          val results: Iterator[IndexedSeq[String]] = Iterator.continually(rs).takeWhile(_.next()).map { rs =>
            columns map rs.getString
          }

          log.info(s"TABLE : $table")
          log.info("=================")
          log.info(columns.mkString(", "))
          results.foreach(f => log.info(f.mkString(", ")))
        })
      }
    }

    log.info("=================")
    log.info("File System Summary")
    log.info("=================")
    log.info(s"Here are all the snapshot persistentIds [${allSnapshotPersistentIds.mkString(", ")}]")
    log.info("=================")
    log.info(s"Here are all the journal persistentIds [${Await.result(allJournalPersistentIds.map(_.sorted.mkString(", ")), 10 seconds)}]")
  }

}
