package uk.gov.homeoffice.drt

import java.io.FileInputStream
import java.sql.ResultSet

import akka.persistence.serialization.streamToBytes
import org.slf4j.Logger

trait SnapshotsMigration {
  this: UsingPostgres with UsingDatabase =>
  val log: Logger

  lazy val dirName = config.getString("snapshotsDir")

  val snapshotColumnNames = List("persistence_id", "sequence_number", "created", "snapshot")

  lazy val allSnapshotPersistentIds = {
    val allFiles = new java.io.File(dirName).listFiles.filter(_.getName.startsWith("snapshot-"))
    allFiles.flatMap({
      file => extractMetadata(file.getName)
    }).map {
      case (persistenceId: String, _, _) => persistenceId
    }.distinct.sorted.toSeq
  }

  def saveSnapshots(persistentId: Option[String] = None, startSequence: Long = 0L, endSequence: Long = Long.MaxValue) = {

    val filter = persistentId.map(id => s"snapshot-$id-").getOrElse("snapshot-")
    val allFiles = new java.io.File(dirName).listFiles.filter(_.getName.startsWith(filter)).sortBy(f => f.getName).reverse
    withDatasource { implicit dataSource =>
      allFiles.foreach { file =>


        extractMetadata(file.getName).foreach { case (persistenceId: String, sequenceNumber: Long, created: Long) =>
          getSavedSequenceIds(persistenceId).foreach {
            savedSoFar =>
              if (!savedSoFar.contains(sequenceNumber)) {
                log.info(s" ${file.getName} - $persistenceId, $sequenceNumber, $created")
                val inputStream = new FileInputStream(file)
                val bytes = try streamToBytes(inputStream) finally inputStream.close()
                log.info(s"Saving $sequenceNumber.")
                dataToDatabase("snapshot", snapshotColumnNames, Seq(List(persistenceId, sequenceNumber, created, bytes)).toIterator)
              } else log.info(s"Skipping $sequenceNumber as we've already saved it.")

          }

        }
      }
    }
  }

  def resultSetToListOfSequenceNumbers(rs: ResultSet): List[Long] = {
    rs.next()
    if (rs.isLast)
      rs.getLong(1) :: Nil
    else {
      val sequenceNumber = rs.getLong(1)
      sequenceNumber :: resultSetToListOfSequenceNumbers(rs)
    }
  }

  def getSavedSequenceIds(persistenceId: String): Option[List[Long]] = {
    withDatasource[Option[List[Long]]](implicit dataSource => {
      val sql = s"select sequence_number from snapshot where persistence_id = '$persistenceId'"

      withPreparedStatement[List[Long]](sql, { implicit statement =>
        val rs: ResultSet = statement.executeQuery()
        resultSetToListOfSequenceNumbers(rs)
      })
    })
  }

  private def extractMetadata(filename: String): Option[(String, Long, Long)] = {
    val persistenceIdStartIdx = 9
    val sequenceNumberEndIdx = filename.lastIndexOf('-')
    val persistenceIdEndIdx = filename.lastIndexOf('-', sequenceNumberEndIdx - 1)
    val timestampString = filename.substring(sequenceNumberEndIdx + 1)
    if (persistenceIdStartIdx >= persistenceIdEndIdx || timestampString.exists(!_.isDigit)) None
    else {
      val persistenceId = filename.substring(persistenceIdStartIdx, persistenceIdEndIdx)
      val sequenceNumber = filename.substring(persistenceIdEndIdx + 1, sequenceNumberEndIdx).toLong
      val timestamp = filename.substring(sequenceNumberEndIdx + 1).toLong
      Some((persistenceId, sequenceNumber, timestamp))
    }
  }

}
