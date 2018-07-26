package uk.gov.homeoffice.drt

import com.typesafe.config.{Config, ConfigFactory}

import collection.JavaConverters._

trait ActorConfig extends HasConfig {

  private val bindings = Map(
    """"server.protobuf.messages.CrunchState.CrunchDiffMessage"""" -> "protobuf",
    """"server.protobuf.messages.FlightsMessage.FlightsDiffMessage"""" -> "protobuf",
    """"server.protobuf.messages.CrunchState.CrunchStateSnapshotMessage"""" -> "protobuf",
    """"server.protobuf.messages.ShiftMessage.ShiftStateSnapshotMessage"""" -> "protobuf",
    """"server.protobuf.messages.FixedPointMessage.FixedPointsStateSnapshotMessage"""" -> "protobuf",
    """"server.protobuf.messages.StaffMovementMessages.StaffMovementsStateSnapshotMessage"""" -> "protobuf",
    """"server.protobuf.messages.FlightsMessage.FlightStateSnapshotMessage"""" -> "protobuf",
    """"server.protobuf.messages.VoyageManifest.VoyageManifestStateSnapshotMessage"""" -> "protobuf",
    """"server.protobuf.messages.VoyageManifest.VoyageManifestLatestFileNameMessage"""" -> "protobuf",
    """"server.protobuf.messages.VoyageManifest.VoyageManifestsMessage"""" -> "protobuf",
    """"server.protobuf.messages.VoyageManifest.VoyageManifestMessage"""" -> "protobuf").asJava

  val actorConfig: Config = ConfigFactory.parseMap(Map(
    "max-buffer-size" -> "100m",
    "akka.persistence.journal.plugin"-> "akka.persistence.journal.leveldb",
    "akka.persistence.journal.leveldb.dir" -> s"${config.getString("persistenceBaseDir")}",
    "akka.persistence.snapshot-store.plugin" -> "akka.persistence.snapshot-store.local",
    "akka.persistence.snapshot-store.local.class" -> "akka.persistence.snapshot.local.LocalSnapshotStore",
    "akka.persistence.snapshot-store.local.dir" -> s"${config.getString("snapshotsDir")}",
    "akka.persistence.snapshot-store.loc§al.plugin-dispatcher" -> "akka.persistence.dispatchers.default-plugin-dispatcher",
    "akka.persistence.snapshot-store.local.stream-dispatcher" -> "akka.persistence.dispatchers.default-stream-dispatcher",
    "akka.actor.serializers.protobuf" -> "actors.serializers.ProtoBufSerializer",
    "akka.actor.serialization-bindings" -> bindings,
    "akka.persistence.query.journal.leveldb.max-buffer-size" -> 100,
    "akka.persistence.query.journal.leveldb.class" -> "akka.persistence.query.journal.leveldb.LeveldbReadJournalProvider",
    "akka.stream.materializer.initial-input-buffer-size" -> 1,
    "akka.stream.materializer.max-input-buffer-size" -> 1,
    "akka.persistence.query.journal.leveldb.class" -> "akka.persistence.query.journal.leveldb.LeveldbReadJournalProvider",
    "akka.persistence.query.journal.leveldb.write-plugin" -> "",
    "akka.persistence.query.journal.leveldb.refresh-interval" -> "3s",
    "akka.persistence.query.journal.leveldb.max-buffer-size" -> "100"

  ).asJava)

}
