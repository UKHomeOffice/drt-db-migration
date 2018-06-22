package uk.gov.homeoffice.drt

import actors.serializers.ProtoBufSerializer
import akka.actor.ActorSystem
import akka.persistence.query.PersistenceQuery
import akka.persistence.query.journal.leveldb.scaladsl.LeveldbReadJournal
import akka.serialization.SerializationExtension
import akka.stream.ActorMaterializer
import org.slf4j.LoggerFactory
import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContextExecutor}

object Boot extends App with JournalMigration with SnapshotsMigration with ShowSummary with UsingPostgres with UsingDatabase with ActorConfig {
  val log = LoggerFactory.getLogger(getClass)

  log.info(s"Starting DB migration ${config.getString("portcode")} at persistenceBaseDir '${config.getString("persistenceBaseDir")}'.")

  implicit val system: ActorSystem = ActorSystem("default", actorConfig)
  implicit val mat: ActorMaterializer = ActorMaterializer()
  implicit val executionContext: ExecutionContextExecutor = system.dispatcher

  val protoBufSerializer = new ProtoBufSerializer
  lazy val serialization = SerializationExtension(system)

  val readJournal: LeveldbReadJournal = PersistenceQuery(system).readJournalFor[LeveldbReadJournal](
    LeveldbReadJournal.Identifier)

  val parser = new scopt.OptionParser[ParsedArguments]("drt-db-migration") {
    head("file-migration-tool", "1.0")

    cmd("journal")
      .action((_, c) => c.copy(command = Journal))
      .text("migrates leveldb data to the journal table")
      .children(
        opt[String]("persistenceId")
          .optional()
          .action((persistenceId, c) => c.copy(id = Some(persistenceId)))
          .text(s"persistenceId to migrate")
          .children(
            opt[Long](name = "startSequence")
              .optional()
              .text(s"start sequence number")
              .action((startSequence, c) => c.copy(startSequence = Some(startSequence)))
          )

      )

    cmd("snapshot")
      .action((_, c) => c.copy(command = Snapshots))
      .text("migrates file data to the snapshot table")
      .children(
        opt[String]("persistenceId")
          .optional()
          .action((persistenceId, c) => c.copy(id = Some(persistenceId)))
          .text(s"persistenceId to migrate")
          .children(
            opt[Long](name = "startSequence")
              .optional()
              .text(s"start sequence number")
              .action((startSequence, c) => c.copy(startSequence = Some(startSequence)))
          )
      )

    cmd("show")
      .action((_, c) => c.copy(command = Summary))
      .text("shows the state of play of the database and file system")


    override def showUsageOnError = true
  }

  parser.parse(args, ParsedArguments()) match {
    case Some(ParsedArguments(Journal, None, _)) => migrateAll.onComplete { f =>
      log.info(s"Migrated ${f.map(_.size).getOrElse(0L)} persistenceId's into journal")
      system.terminate()
    }
    case Some(ParsedArguments(Journal, Some(id), startSequence)) => migratePersistenceIdFrom(id, startSequence.getOrElse(0L)).onComplete { f =>
      log.info(s"Migrated ${f.map(_.size).getOrElse(0L)} $id into journal.")
      system.terminate()
    }
    case Some(ParsedArguments(Snapshots, id, startSequence)) =>
      saveSnapshots(id, startSequence.getOrElse(0L))
      system.terminate()
    case Some(ParsedArguments(Summary, _, _)) =>
      showSummary()
      system.terminate()
    case Some(_) =>
      parser.showUsage()
      system.terminate()
    case None =>
      log.info("terminate")
      sys.exit(0)
  }
}

sealed trait Command

case object ShowUsage extends Command

case object Journal extends Command

case object Snapshots extends Command

case object Summary extends Command

case class ParsedArguments(command: Command = ShowUsage, id: Option[String] = None, startSequence: Option[Long] = None)

