package uk.gov.homeoffice.drt

import akka.actor.ActorSystem
import akka.persistence.query.EventEnvelope
import akka.persistence.query.journal.leveldb.scaladsl.LeveldbReadJournal
import akka.persistence.serialization.{MessageFormats => mf}
import akka.protobuf.ByteString
import akka.serialization.Serializers
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Sink
import javax.sql.DataSource
import org.slf4j.Logger
import uk.gov.homeoffice.drt.Boot.serialization

import scala.collection.immutable
import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContextExecutor, Future}

trait JournalMigration {
  this: UsingPostgres with UsingDatabase =>
  val log: Logger
  val readJournal: LeveldbReadJournal
  implicit val system: ActorSystem
  implicit val mat: ActorMaterializer
  implicit val executionContext: ExecutionContextExecutor

  val columnNames = List("persistence_id", "sequence_number", "deleted", "tags", "message", "from_migration")

  lazy val allJournalPersistentIds: Future[immutable.Seq[String]] = {
    readJournal.currentPersistenceIds().runWith(Sink.seq)
  }

  def migratePersistenceIdFrom(persistenceId: String, startSequence: Long = 0L, batchSize: Int)(implicit dataSource: DataSource): Long = {
    val endSequenceNumber = startSequence + batchSize - 1
    log.info(s"Migrating $persistenceId from $startSequence to $endSequenceNumber")

    val events = readJournal.currentEventsByPersistenceId(persistenceId, fromSequenceNr = startSequence, toSequenceNr = endSequenceNumber)
    val eventualRecordsToInsert: Future[Seq[List[Any]]] = events
      .map { eventEnvelope =>
        val payload = eventEnvelope.event.asInstanceOf[AnyRef]
        val serializer = serialization.findSerializerFor(payload)
        val payloadBuilder = mf.PersistentPayload.newBuilder()

        val ms = Serializers.manifestFor(serializer, payload)
        if (ms.nonEmpty) payloadBuilder.setPayloadManifest(ByteString.copyFromUtf8(ms))
        payloadBuilder.setPayload(ByteString.copyFrom(serializer.toBinary(payload)))
        payloadBuilder.setSerializerId(serializer.identifier)

        val msgBuilder = mf.PersistentMessage.newBuilder
        msgBuilder.setPersistenceId(eventEnvelope.persistenceId)
        msgBuilder.setPayload(payloadBuilder)
        msgBuilder.setSequenceNr(eventEnvelope.sequenceNr)
        msgBuilder.setManifest(ms)

        List(eventEnvelope.persistenceId, eventEnvelope.sequenceNr, false, null, msgBuilder.build().toByteArray, true)
      }
      .runWith(Sink.seq)
    val recordsToInsert = Await.result(eventualRecordsToInsert, 24 hours)
    dataToDatabase("journal", columnNames, recordsToInsert.toIterator)

    recordsToInsert.length

  }

  def getStartSequence(id: String): Long = {
    withDatasource(implicit dataSource => {
      val sql = s"select max(sequence_number) from journal where persistence_id = '$id'"
      withPreparedStatement[Long](sql, { implicit statement =>
        val rs = statement.executeQuery()
        if (rs.next()) {
          val maxSequenceId = rs.getInt(1).toLong
          if (maxSequenceId > 0) maxSequenceId + 1 else 1L
        } else 1L
      }).getOrElse(1L)
    })
  }

  def migrateAll: Long = {
    val ids = Await.result(allJournalPersistentIds, Duration.Inf)

    log.info(s"persistence ids to be migrated:\n${ids.sorted.mkString("\n")}")

    withDatasource { implicit dataSource =>
      val migratedById = for {
        id <- ids
        startSeq = getStartSequence(id)
      } yield {
        val batchSize = if (id.contains("forecast")) 10 else 5000
        migrateJournal(id, startSeq, batchSize)
      }
      migratedById.sum
    }

  }

  def migrateJournal(pId: String, startSequenceNumber: Long, batchSize: Int = 5000)(implicit dataSource: DataSource): Long = {
    var numProcessed = 0L
    var totalNumProcessed = 0L
    var sequenceNumber = startSequenceNumber

    do {
      numProcessed = migratePersistenceIdFrom(pId, sequenceNumber, batchSize)

      totalNumProcessed = totalNumProcessed + numProcessed
      sequenceNumber = sequenceNumber + numProcessed
    } while (numProcessed > 0)

    totalNumProcessed
  }
}
