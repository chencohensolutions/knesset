package com.chencohensolutions.knesset.core.odata

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.offsetAt
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlin.time.Instant

/**
 * Parses the API's ISO 8601 timestamps: an Israel offset that follows DST (+02:00 or +03:00) and 0 to 3
 * fractional digits, for example `2026-11-16T13:01:33.717+02:00` or `2026-07-28T00:00:00+03:00`.
 */
object ODataInstantSerializer : KSerializer<Instant> {
    override val descriptor = PrimitiveSerialDescriptor("ODataInstant", PrimitiveKind.STRING)
    override fun deserialize(decoder: Decoder): Instant = parseODataInstant(decoder.decodeString())
    override fun serialize(encoder: Encoder, value: Instant) = encoder.encodeString(IsraelTime.isoWithOffset(value))
}

typealias ODataInstant = @Serializable(with = ODataInstantSerializer::class) Instant

fun parseODataInstant(text: String): Instant {
    val s = text.trim()
    // A value without an offset is local Israel time.
    val hasOffset = s.endsWith("Z") || Regex("[+-]\\d{2}:\\d{2}$").containsMatchIn(s)
    return if (hasOffset) Instant.parse(s)
    else LocalDateTime.parse(s).toInstant(IsraelTime.zone)
}

/** All dates are shown in Israel time regardless of the device zone. */
object IsraelTime {
    val zone: TimeZone by lazy { TimeZone.of("Asia/Jerusalem") }

    fun local(instant: Instant): LocalDateTime = instant.toLocalDateTime(zone)

    fun isoWithOffset(instant: Instant): String {
        val ldt = local(instant)
        val offset = zone.offsetAt(instant).toString().let { if (it == "Z") "+00:00" else it }
        return "${ldt.date}T${two(ldt.hour)}:${two(ldt.minute)}:${two(ldt.second)}$offset"
    }

    /** `dd/MM/yyyy` */
    fun date(instant: Instant): String = date(local(instant).date)
    fun date(d: LocalDate): String = "${two(d.day)}/${two(d.month.ordinal + 1)}/${d.year}"

    /** `HH:mm` */
    fun time(instant: Instant): String = local(instant).let { "${two(it.hour)}:${two(it.minute)}" }

    /** `dd/MM/yyyy HH:mm`, or the date alone when the time is midnight (date-only values). */
    fun dateTime(instant: Instant): String {
        val l = local(instant)
        return if (l.hour == 0 && l.minute == 0) date(l.date) else "${date(l.date)} ${time(instant)}"
    }

    fun startOfDay(date: LocalDate): Instant = date.atStartOfDayIn(zone)

    private fun two(n: Int) = n.toString().padStart(2, '0')
}
