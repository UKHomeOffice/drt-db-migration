package uk.gov.homeoffice.drt

import akka.actor.ActorSystem
import akka.persistence.query.journal.leveldb.scaladsl.LeveldbReadJournal
import akka.persistence.serialization.{MessageFormats => mf}
import akka.protobuf.ByteString
import akka.serialization.Serializers
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Sink
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

  val columnNames = List("persistence_id", "sequence_number", "deleted", "tags", "message")

  lazy val allJournalPersistentIds: Future[immutable.Seq[String]] = {
    readJournal.currentPersistenceIds().runWith(Sink.seq)
  }

  def migratePersistenceIdFrom(persistenceId: String, startSequence: Long = 0L, endSequence: Long = Long.MaxValue): Int = {
    log.info(s"Migrating $persistenceId from $startSequence")
    val events = readJournal.currentEventsByPersistenceId(persistenceId, fromSequenceNr = startSequence, toSequenceNr = endSequence)
    withDatasource { implicit dataSource =>
      val eventualMaxSeqNr = if (persistenceId == "forecast-crunch-state") Future(-1)
      else events.map(_.sequenceNr).runWith(Sink.seq).map {
        case empty if empty.isEmpty => 0
        case nonEmpty if nonEmpty.nonEmpty => nonEmpty.length
      }
      val maxSeqNr = Await.result(eventualMaxSeqNr, 10 minutes)
      log.info(s"$maxSeqNr entries to migrate")

      val groupSize = if (persistenceId.contains("forecast")) 10 else 5000

      val eventualInts: Future[Int] = events
        .grouped(groupSize)
        .fold(0) {
          case (counter, eventEnvelopes) =>
            val seq = eventEnvelopes.map(eventEnvelope => {
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

              List(eventEnvelope.persistenceId, eventEnvelope.sequenceNr, false, null, msgBuilder.build().toByteArray)
            })
            dataToDatabase("journal", columnNames, seq.toIterator)
            val newCounter = counter + seq.length
            val progress = (newCounter.toDouble / maxSeqNr) * 100
            val progressIndicator = if (persistenceId != "forecast-crunch-state") s"(${progress.toInt}%)" else "(n/a)"
            log.info(s"Written $newCounter / $maxSeqNr entries to $persistenceId $progressIndicator")
            newCounter
        }
        .runWith(Sink.seq)
        .map(_.sum)
      Await.result(eventualInts, 24 hours)
    }
  }

  def getStartSequence(id: String): Long = {
    withDatasource(implicit dataSource => {
      val sql = s"select max(sequence_number) from journal where persistence_id = '$id'"
      withPreparedStatement[Long](sql, { implicit statement =>
        val rs = statement.executeQuery()
        if (rs.next()) {
          val maxSequenceId = rs.getInt(1).toLong
          if (maxSequenceId > 0) maxSequenceId + 1 else 0L
        } else 0L
      }).getOrElse(0L)
    })
  }

  def migrateAll: Int = {
    val ids = Await.result(allJournalPersistentIds, Duration.Inf)

    log.info(s"persistence ids to be migrated:\n${ids.sorted.mkString("\n")}")

    val migratedById = for {
      id <- ids
      startSeq = getStartSequence(id)
    } yield migratePersistenceIdFrom(id, startSeq)

    migratedById.sum
  }


}
