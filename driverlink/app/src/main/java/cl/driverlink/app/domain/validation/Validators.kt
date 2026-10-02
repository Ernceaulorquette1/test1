package cl.driverlink.app.domain.validation

/** Validaciones de formularios. Puras y probadas con tests unitarios. */
object Validators {

    private val EMAIL = Regex("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")
    private val NAME = Regex("^[\\p{L}][\\p{L} '.-]{0,49}$")
    /** Patentes chilenas: AA1234 (antigua), BBBB12 (nueva), motos AB123. */
    private val PLATE = Regex("^([A-Z]{2}[0-9]{4}|[B-DF-HJ-LPR-TV-Z]{4}[0-9]{2}|[A-Z]{2,3}[0-9]{2,3})$")
    private val INVITE_CODE = Regex("^DL-[A-Z]{3}-[0-9]{4}$")

    fun isValidEmail(value: String): Boolean = EMAIL.matches(value.trim())

    fun isValidName(value: String): Boolean = NAME.matches(value.trim())

    /** Contraseña: mínimo 8 caracteres, con al menos una letra y un número. */
    fun isValidPassword(value: String): Boolean =
        value.length >= 8 && value.any { it.isLetter() } && value.any { it.isDigit() }

    /** Normaliza teléfonos chilenos a formato E.164 (+569XXXXXXXX). */
    fun normalizeChileanPhone(value: String): String? {
        val digits = value.filter { it.isDigit() }
        val local = when {
            digits.length == 11 && digits.startsWith("56") -> digits.substring(2)
            digits.length == 9 -> digits
            digits.length == 8 -> "9$digits"
            else -> return null
        }
        if (local.length != 9 || !local.startsWith("9") && !local.startsWith("2")) return null
        return "+56$local"
    }

    fun isValidPhone(value: String): Boolean = normalizeChileanPhone(value) != null

    fun isValidPlate(value: String): Boolean = PLATE.matches(normalizePlate(value))

    fun normalizePlate(value: String): String =
        value.uppercase().filter { it.isLetterOrDigit() }

    fun isValidInviteCode(value: String): Boolean = INVITE_CODE.matches(value.trim().uppercase())

    fun isValidVehicleYear(year: Int, currentYear: Int): Boolean = year in 1980..(currentYear + 1)
}

/** RUT chileno con dígito verificador (módulo 11). */
object RutValidator {

    fun normalize(value: String): String? {
        val clean = value.uppercase().filter { it.isDigit() || it == 'K' }
        if (clean.length < 2) return null
        val body = clean.dropLast(1)
        if (!body.all { it.isDigit() } || body.length > 8) return null
        return "${body.trimStart('0')}-${clean.last()}"
    }

    fun isValid(value: String): Boolean {
        val normalized = normalize(value) ?: return false
        val (body, dv) = normalized.split("-")
        if (body.isEmpty()) return false
        return computeCheckDigit(body) == dv.first()
    }

    fun computeCheckDigit(body: String): Char {
        var sum = 0
        var factor = 2
        for (digit in body.reversed()) {
            sum += digit.digitToInt() * factor
            factor = if (factor == 7) 2 else factor + 1
        }
        return when (val result = 11 - (sum % 11)) {
            11 -> '0'
            10 -> 'K'
            else -> result.digitToChar()
        }
    }
}

/** Genera códigos de invitación con formato DL-ABC-8492. */
object InviteCodeGenerator {
    private const val LETTERS = "ABCDEFGHJKLMNPQRSTUVWXYZ"

    fun generate(random: kotlin.random.Random = kotlin.random.Random.Default): String {
        val letters = (1..3).map { LETTERS[random.nextInt(LETTERS.length)] }.joinToString("")
        val digits = random.nextInt(0, 10_000).toString().padStart(4, '0')
        return "DL-$letters-$digits"
    }
}
