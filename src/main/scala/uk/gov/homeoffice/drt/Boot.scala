package uk.gov.homeoffice.drt

import actors.serializers.ProtoBufSerializer
import akka.actor.ActorSystem
import akka.persistence.query.PersistenceQuery
import akka.persistence.query.journal.leveldb.scaladsl.LeveldbReadJournal
import akka.persistence.serialization.{MessageFormats => mf}
import akka.protobuf.ByteString
import akka.serialization.{SerializationExtension, Serializers}
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Sink
import org.slf4j.LoggerFactory
import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContextExecutor, Future}

object Boot extends App with SnapshotsMigration with UsingPostgres with UsingDatabase with ActorConfig {

  val log = LoggerFactory.getLogger(getClass)

  log.info("Starting DB migration")

  implicit val system: ActorSystem = ActorSystem("default", actorConfig)
  implicit val mat: ActorMaterializer = ActorMaterializer()
  implicit val executionContext: ExecutionContextExecutor = system.dispatcher

  val columnNames = List("persistence_id", "sequence_number", "deleted", "tags", "message")

  val protoBufSerializer = new ProtoBufSerializer
  lazy val serialization = SerializationExtension(system)

  val readJournal = PersistenceQuery(system).readJournalFor[LeveldbReadJournal](
    LeveldbReadJournal.Identifier)

  val savingToJournal = readJournal.currentPersistenceIds().map { persistenceId =>
    log.info(s"journal persistenceId = $persistenceId")
    val allEvents = readJournal.currentEventsByPersistenceId(persistenceId).map { event =>
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

      List(event.persistenceId, event.sequenceNr, false, null, msgBuilder.build().toByteArray)
    }.runWith(Sink.seq)

    val dbUpdates = allEvents.map(scheduleData=> withDatasource { implicit dataSource =>
      log.info(s"Inserting ${scheduleData.size} of persistent-id $persistenceId into Journal")
      dataToDatabase("journal", columnNames, scheduleData.toIterator)
    })
    Await.ready(dbUpdates, 20 seconds)
  }.runWith(Sink.seq)

  savingToJournal.onComplete { done =>

    saveSnaphots(config.getString("snapshotsDir"))

    log.info("Work complete.")
    system.terminate()
    done
  }

  Await.ready(savingToJournal, 5 minutes)

}

