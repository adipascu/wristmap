package be.pascu.wristmap.nav

object Distance {
    private val pattern =
        Regex(
            """^(\d+(?:[.,]\d+)?)\s*(m|km|ft|mi|yd|meters?|metres?|feet|miles?|yards?)\.?$""",
            RegexOption.IGNORE_CASE,
        )
    private val thousands = Regex("""^\d{1,3},\d{3}$""")
    private val metersPerUnit =
        mapOf(
            "m" to 1.0,
            "meter" to 1.0,
            "metre" to 1.0,
            "km" to 1000.0,
            "ft" to 0.3048,
            "feet" to 0.3048,
            "mi" to 1609.344,
            "mile" to 1609.344,
            "yd" to 0.9144,
            "yard" to 0.9144,
        )

    fun isDistance(text: String): Boolean = pattern.matches(text.trim())

    fun toMeters(text: String): Double? {
        val match = pattern.find(text.trim()) ?: return null
        val number = match.groupValues[1]
        val value = (if (thousands.matches(number)) number.replace(",", "") else number.replace(',', '.')).toDouble()
        return value * metersPerUnit.getValue(match.groupValues[2].lowercase().trimEnd('s'))
    }
}
