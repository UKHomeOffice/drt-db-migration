package uk.gov.homeoffice.drt

import actors.serializers.ProtoBufSerializer
import akka.actor.ActorSystem
import akka.persistence.query.journal.leveldb.scaladsl.LeveldbReadJournal
import akka.persistence.serialization.{MessageFormats => mf}
import akka.protobuf.ByteString
import akka.serialization.Serializers
import akka.stream.{ActorAttributes, ActorMaterializer, Supervision}
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

  def migratePersistenceIdFrom(persistenceId: String, startSequence: Long = 0L, endSequence: Long = Long.MaxValue) = {

    log.info(s"Migrating $persistenceId with start sequence $startSequence into journal.")
    readJournal.eventsByPersistenceId(persistenceId, fromSequenceNr = startSequence, toSequenceNr = endSequence)
      .map { event =>
        log.info(s"Reading a persistent-id $persistenceId into Journal ${event.sequenceNr}")
        val payload = event.event.asInstanceOf[AnyRef]
        val serializer = serialization.findSerializerFor(payload)
        val payloadBuilder = mf.PersistentPayload.newBuilder()
        val ms = Serializers.manifestFor(serializer, payload)
        if (ms.nonEmpty) payloadBuilder.setPayloadManifest(ByteString.copyFromUtf8(ms))
        payloadBuilder.setPayload(ByteString.copyFrom(serializer.toBinary(payload)))
        payloadBuilder.setSerializerId(serializer.identifier)
        val msgBuilder = mf.PersistentMessage.newBuilder
        msgBuilder.setPersistenceId(event.persistenceId)
        msgBuilder.setPayload(payloadBuilder)
        msgBuilder.setSequenceNr(event.sequenceNr)
        msgBuilder.setManifest(ms)

        withDatasource { implicit dataSource =>
          dataToDatabase("journal", columnNames, Seq(List(event.persistenceId, event.sequenceNr, false, null, msgBuilder.build().toByteArray)).toIterator)
        }
      }.runWith(Sink.seq)

  }

  def migrateAll = {

    val ids = Await.result(allJournalPersistentIds, Duration.Inf)

    Future.sequence {
      for {
        id <- ids
      } yield migratePersistenceIdFrom(id)
    }
  }


}
