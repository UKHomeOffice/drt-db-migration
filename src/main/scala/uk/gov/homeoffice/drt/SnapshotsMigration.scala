package uk.gov.homeoffice.drt

import java.io.FileInputStream

import akka.persistence.serialization.streamToBytes
import org.slf4j.Logger

trait SnapshotsMigration {
  this: UsingPostgres with UsingDatabase =>
  val log: Logger
  val snapshotColumnNames = List("persistence_id", "sequence_number", "created", "snapshot")

  def saveSnaphots(dirName: String) = {

    val allFiles = new java.io.File(dirName).listFiles.filter(_.getName.startsWith("snapshot-"))
    withDatasource { implicit dataSource =>
      allFiles.foreach { file =>
        log.info(file.getAbsolutePath)

        extractMetadata(file.getName).foreach { case (persistenceId: String, sequenceNumber: Long, created: Long) =>

          log.info((persistenceId, sequenceNumber, created).toString())
          val inputStream = new FileInputStream(file)
          val bytes = streamToBytes(inputStream)
          inputStream.close()

          dataToDatabase("snapshot", snapshotColumnNames, Seq(List(persistenceId, sequenceNumber, created, bytes)).toIterator)
        }
      }
    }
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
