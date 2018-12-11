package uk.gov.homeoffice.drt

import actors.serializers.ProtoBufSerializer
import akka.actor.ActorSystem
import akka.persistence.query.PersistenceQuery
import akka.persistence.query.journal.leveldb.scaladsl.LeveldbReadJournal
import akka.serialization.SerializationExtension
import akka.stream.ActorMaterializer
import org.slf4j.LoggerFactory
import scala.concurrent.ExecutionContextExecutor
import scala.collection.JavaConversions._


object Boot extends App with JournalMigration with SnapshotsMigration with ShowSummary with UsingPostgres with UsingDatabase with ActorConfig with RecreateTables {
  val log = LoggerFactory.getLogger(getClass)

  log.info(s"Starting DB migration ${config.getString("portcode")} at persistenceBaseDir '${config.getString("persistenceBaseDir")}'.")

  log.info("**************************")
  log.info("**************************")
  log.info("************************** Envs:")
  val environmentVars = System.getenv().toSeq.sortBy(_._1)
  for ((k,v) <- environmentVars) println(s"key: $k, value: $v")

  log.info("************************** Properties:")
  val properties = System.getProperties.toSeq.sortBy(_._1)
  for ((k,v) <- properties) println(s"key: $k, value: $v")

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
              .action((startSequence, c) => c.copy(startSequence = Some(startSequence))),
            opt[Long](name = "endSequence")
              .optional()
              .text(s"end sequence number")
              .action((endSequence, c) => c.copy(endSequence = Some(endSequence)))
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
              .action((startSequence, c) => c.copy(startSequence = Some(startSequence))),
            opt[Long](name = "endSequence")
              .optional()
              .text(s"end sequence number")
              .action((endSequence, c) => c.copy(endSequence = Some(endSequence)))
          )
      )

    cmd("recreate")
      .action((_, c) => c.copy(command = RecreateDB))
      .text(s"recreate the database tables")

    cmd("show")
      .action((_, c) => c.copy(command = Summary))
      .text("shows the state of play of the database and file system")


    override def showUsageOnError = true
  }

  parser.parse(args, ParsedArguments()) match {
    case Some(ParsedArguments(Journal, None, _, _)) =>
      val totalMigrated = migrateAll
      log.info(s"$totalMigrated migrated.")
      closeDatasource()
      system.terminate()
    case Some(ParsedArguments(Journal, Some(id), startSequence, endSequence)) =>
      val totalMigrated = migratePersistenceIdFrom(id, startSequence.getOrElse(0L), endSequence.getOrElse(Long.MaxValue))
      log.info(s"$totalMigrated migrated.")
      closeDatasource()
      system.terminate()
    case Some(ParsedArguments(Snapshots, id, startSequence, endSequence)) =>
      saveSnapshots(id, startSequence.getOrElse(0L), endSequence.getOrElse(Long.MaxValue))
      system.terminate()
    case Some(ParsedArguments(RecreateDB, _, _, _)) =>
      dropAndRecreateTables()
      closeDatasource()
      system.terminate()
    case Some(ParsedArguments(Summary, _, _, _)) =>
      showSummary()
      closeDatasource()
      system.terminate()
    case Some(_) =>
      parser.showUsage()
      closeDatasource()
      system.terminate()
    case None =>
      log.info("terminate")
      closeDatasource()
      sys.exit(0)
  }
}

sealed trait Command

case object ShowUsage extends Command

case object Journal extends Command

case object Snapshots extends Command

case object Summary extends Command

case object RecreateDB extends Command

case class ParsedArguments(command: Command = ShowUsage, id: Option[String] = None, startSequence: Option[Long] = None, endSequence: Option[Long] = None)

