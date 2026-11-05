package app.aesthory.progress
import org.junit.Test
import org.junit.Assert.*
import app.aesthory.routines.CompletionStatus.*
import java.time.LocalDate
class WeeklyReviewTest {
 private val start = LocalDate.of(2026,11,2)
 private fun log(id:Long, status:app.aesthory.routines.CompletionStatus, value:Double?=null, seconds:Long?=null, day:Long=start.toEpochDay(), habit:String="a") = WeeklyLog(id,habit,day,status,value,seconds,"DURATION")
 @Test fun latestResultAndUndoReplaceEarlierResults() {
  val result=reviewWeek(listOf(log(1,DONE,60.0),log(2,UNDONE),log(3,PARTIAL,30.0,30,habit="b")),start,start)
  assertEquals(0,result.completed);assertEquals(1,result.partial);assertEquals(30.0,result.recordedSeconds!!,0.0)
 }
 @Test fun missingZeroAndWeekBoundariesStayDistinct() {
  assertNull(reviewWeek(emptyList(),start,start).recordedSeconds)
  assertEquals(0.0,reviewWeek(listOf(log(1,DONE,0.0)),start,start).recordedSeconds!!,0.0)
  assertEquals(1,reviewWeek(listOf(log(1,DONE,60.0),log(2,DONE,60.0,day=start.plusDays(7).toEpochDay()),log(3,DONE,60.0,day=start.minusDays(1).toEpochDay())),start,start.plusDays(6)).completed)
  assertEquals(LocalDate.of(2025,12,29),weekStart(LocalDate.of(2026,1,1)))
 }
 @Test fun futureDatesAndSkippedTimeDoNotCount() {
  val result=reviewWeek(listOf(log(1,SKIPPED),log(2,DONE,60.0,day=start.plusDays(1).toEpochDay())),start,start)
  assertEquals(1,result.skipped);assertEquals(0,result.completed);assertNull(result.recordedSeconds)
 }
}
