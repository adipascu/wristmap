package be.pascu.wristmap.nav

object Distance {
    private val pattern = Regex("""^(\d+(?:[.,]\d+)?)\s*(m|km|ft|mi|yd|meters?|metres?|feet|miles?|yards?)\.?$""", RegexOption.IGNORE_CASE)
    private val thousands = Regex("""^\d{1,3},\d{3}$""")

    fun isDistance(text: String): Boolean = pattern.matches(text.trim())

    fun toMeters(text: String): Double? {
        val match = pattern.find(text.trim()) ?: return null
        val number = match.groupValues[1]
        val value = (if (thousands.matches(number)) number.replace(",", "") else number.replace(',', '.')).toDoubleOrNull()
            ?: return null
        val factor = when (match.groupValues[2].lowercase().trimEnd('s')) {
            "m", "meter", "metre" -> 1.0
            "km" -> 1000.0
            "ft", "feet" -> 0.3048
            "mi", "mile" -> 1609.344
            "yd", "yard" -> 0.9144
            else -> return null
        }
        return value * factor
    }
}
