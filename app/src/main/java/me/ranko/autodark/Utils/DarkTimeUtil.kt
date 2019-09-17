package me.ranko.autodark.Utils

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Time formatter for dark settings
 *
 * @author  0ranko0p
 * */
object DarkTimeUtil {

    /**
     * Display in 12 hour format to user
     * */
    private val mTimeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("hh:mm a")

    private val mPersistFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

    private const val DURATION_DAY_MILLIS = 0x5265C00L

    @JvmStatic
    fun getPersistFormattedString(time: LocalTime): String {
        return time.format(mPersistFormatter)
    }

    @JvmStatic
    fun getPersistLocalTime(formattedStr: String): LocalTime {
        return LocalTime.parse(formattedStr, mPersistFormatter)
    }

    @JvmStatic
    fun getDisplayFormattedString(time: LocalTime): String {
        return time.format(mTimeFormatter)
    }

    @JvmStatic
    fun toAlarmMillis(time: LocalTime): Long {
        val zoneTime = LocalDateTime.of(LocalDate.now(), time).atZone(ZoneId.systemDefault())
        return zoneTime.toInstant().toEpochMilli()
    }

    /**
     * @return  epochMilli of param time for set an alarm
     *          If the param time has been passed, return
     *          time of next day.
     * */
    @JvmStatic
    fun getTodayOrNextDay(time: LocalTime): Long {
        val now = LocalTime.now()
        return if (isNextDay(now, time)) {
            toAlarmMillis(time).plus(DURATION_DAY_MILLIS)
        } else {
            toAlarmMillis(time)
        }
    }

    @JvmStatic
    fun toNextDayAlarmMillis(time: Long): Long {
        return time.plus(DURATION_DAY_MILLIS)
    }

    /**
     * Check current time is in start to end range include the wee hour condition.
     * */
    @JvmStatic
    fun isInTime(start: LocalTime, end: LocalTime, now: LocalTime): Boolean {
        val sDate = LocalDateTime.of(LocalDate.now(), start).atZone(ZoneId.systemDefault())
        var eDate = LocalDateTime.of(LocalDate.now(), end).atZone(ZoneId.systemDefault())
        var nDate = LocalDateTime.of(LocalDate.now(), now).atZone(ZoneId.systemDefault())

        val endAtNextDay = isNextDay(start, end)

        if (endAtNextDay) {
            eDate = eDate.plusDays(1)
        }

        // Check is in yesterday endTime
        if (endAtNextDay && isNextDay(start, now)) {
            nDate = nDate.plusDays(1)
        }

        return nDate.isAfter(sDate) && nDate.isBefore(eDate)
    }


    /**
     * @return  true
     *          if endTime at next day or not include wee hour.
     *          E.g start at 10pm end at 2am, it is true.
     * */
    fun isNextDay(start: LocalTime, end: LocalTime): Boolean {
        if (start.isAfter(end)) {
            return true
        }
        return !start.isBefore(end)
    }
}
