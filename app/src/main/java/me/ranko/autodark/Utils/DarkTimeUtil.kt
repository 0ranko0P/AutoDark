package me.ranko.autodark.Utils

import java.time.*
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
     * Return epochMilli of param time for set an alarm
     * <p>
     * If the param time has been passed, return time of next day
     * */
    @JvmStatic
    fun getTodayOrNextDay(time: LocalTime): Long {
        val now = ZonedDateTime.now().toLocalTime()
        return if (now.isAfter(time)) {
            toAlarmMillis(time).plus(DURATION_DAY_MILLIS)
        } else {
            toAlarmMillis(time)
        }
    }

    @JvmStatic
    fun toNextDayAlarmMillis(time: Long): Long {
        return time.plus(DURATION_DAY_MILLIS)
    }
}