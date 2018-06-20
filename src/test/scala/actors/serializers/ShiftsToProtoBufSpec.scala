package actors.serializers

import org.joda.time.DateTime
import org.joda.time.format.DateTimeFormat
import org.specs2.mutable.Specification
import server.protobuf.messages.ShiftMessage.{ShiftMessage, ShiftsMessage}
import scala.util.Try

class ShiftsToProtoBufSpec extends Specification {

  private val createdAt = DateTime.now

  "shiftStringToShiftMessage" should {
    "take a single shift string and return a case class representing it" in {
      val shiftString = "shift name, T1, 20/01/2017, 10:00, 20:00, 9"
      val shiftMessage = shiftStringToShiftMessage(shiftString, createdAt)

      val expected = Some(ShiftMessage(
        name = Some("shift name"),
        terminalName = Some("T1"),
        startDayOLD = None,
        startTimeOLD = None,
        endTimeOLD = None,
        startTimestamp = Some(1484906400000L),
        endTimestamp = Some(1484942400000L),
        numberOfStaff = Some("9"),
        createdAt = Some(createdAt.getMillis)
      ))

      shiftMessage === expected
    }
  }

  "shiftsStringToShiftsMessage" should {
    "take some lines of shift strings and return a case class representing all of the shifts" in {
      val shiftsString =
        """
          |shift name, T1, 20/01/2017, 10:00, 20:00, 5
          |shift name, T1, 20/01/2017, 10:00, 20:00, 9
        """.stripMargin.trim

      val shiftsMessage = shiftsStringToShiftsMessage(shiftsString, createdAt)

      val expected = ShiftsMessage(List(
        ShiftMessage(
          name = Some("shift name"),
          terminalName = Some("T1"),
          startTimestamp = Some(1484906400000L),
          endTimestamp = Some(1484942400000L),
          numberOfStaff = Some("5"),
          createdAt = Some(createdAt.getMillis)
        ), ShiftMessage(
          name = Some("shift name"),
          terminalName = Some("T1"),
          startTimestamp = Some(1484906400000L),
          endTimestamp = Some(1484942400000L),
          numberOfStaff = Some("9"),
          createdAt = Some(createdAt.getMillis)
        )))

      shiftsMessage === expected
    }
  }



  def shiftStringToShiftMessage(shift: String, createdAt: DateTime): Option[ShiftMessage] = {
    shift.replaceAll("([^\\\\]),", "$1\",\"").split("\",\"").toList.map(_.trim) match {
      case List(description, terminalName, startDay, startTime, endTime, staffNumberDelta) =>
        val (startTimestamp, endTimestamp) = startAndEndTimestamps(startDay, startTime, endTime)
        Some(ShiftMessage(
          name = Some(description),
          terminalName = Some(terminalName),
          startTimestamp = startTimestamp,
          endTimestamp = endTimestamp,
          numberOfStaff = Some(staffNumberDelta),
          createdAt = Option(createdAt.getMillis)
        ))
      case _ =>

        None
    }
  }

  def shiftsStringToShiftsMessage(shifts: String, createdAt: DateTime): ShiftsMessage = {
    ShiftsMessage(shiftsStringToShiftMessages(shifts, createdAt))
  }


  def shiftsStringToShiftMessages(shifts: String, createdAt: DateTime): List[ShiftMessage] = {
    shifts.split("\n").map((shift: String) => shiftStringToShiftMessage(shift, createdAt)).collect { case Some(x) => x }.toList
  }

  def startAndEndTimestamps(startDate: String, startTime: String, endTime: String): (Option[Long], Option[Long]) = {
    val startMillis = dateAndTimeToMillis(startDate, startTime)
    val endMillis = dateAndTimeToMillis(startDate, endTime)

    val oneDay = 60 * 60 * 24 * 1000L

    (startMillis, endMillis) match {
      case (Some(start), Some(end)) =>
        if (start <= end)
          (Some(start), Some(end))
        else
          (Some(start), Some(end + oneDay))
      case _ => (None, None)
    }
  }
  def dateAndTimeToMillis(date: String, time: String): Option[Long] = {

    val formatter = DateTimeFormat.forPattern("dd/MM/yy HH:mm")
    Try {
      formatter.parseMillis(date + " " + time)
    }.toOption
  }
}
