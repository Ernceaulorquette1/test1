package cl.driverlink.app.domain

import cl.driverlink.app.domain.validation.InviteCodeGenerator
import cl.driverlink.app.domain.validation.RutValidator
import cl.driverlink.app.domain.validation.Validators
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class ValidatorsTest {

    @Test fun `email valido e invalido`() {
        assertTrue(Validators.isValidEmail("conductor@driverlink.cl"))
        assertTrue(Validators.isValidEmail("  a.b+c@dominio.com "))
        assertFalse(Validators.isValidEmail("sin-arroba.cl"))
        assertFalse(Validators.isValidEmail("a@b"))
    }

    @Test fun `password requiere largo letras y numeros`() {
        assertTrue(Validators.isValidPassword("demo1234"))
        assertFalse(Validators.isValidPassword("corta1"))
        assertFalse(Validators.isValidPassword("soloLetras"))
        assertFalse(Validators.isValidPassword("12345678"))
    }

    @Test fun `nombres con tildes y espacios`() {
        assertTrue(Validators.isValidName("María José"))
        assertTrue(Validators.isValidName("O'Higgins"))
        assertFalse(Validators.isValidName(""))
        assertFalse(Validators.isValidName("123"))
    }

    @Test fun `telefono chileno se normaliza a E164`() {
        assertEquals("+56912345678", Validators.normalizeChileanPhone("+56 9 1234 5678"))
        assertEquals("+56912345678", Validators.normalizeChileanPhone("912345678"))
        assertEquals("+56912345678", Validators.normalizeChileanPhone("12345678"))
        assertEquals("+56222222222", Validators.normalizeChileanPhone("222222222"))
        assertNull(Validators.normalizeChileanPhone("12345"))
        assertNull(Validators.normalizeChileanPhone("512345678"))
    }

    @Test fun `patentes chilenas`() {
        assertTrue(Validators.isValidPlate("KXTR21"))
        assertTrue(Validators.isValidPlate("kx-tr-21"))
        assertTrue(Validators.isValidPlate("AB1234"))
        assertFalse(Validators.isValidPlate("1234AB"))
        assertFalse(Validators.isValidPlate(""))
    }

    @Test fun `codigo de invitacion`() {
        assertTrue(Validators.isValidInviteCode("DL-ABC-8492"))
        assertTrue(Validators.isValidInviteCode("dl-abc-8492"))
        assertFalse(Validators.isValidInviteCode("DL-AB-8492"))
        assertFalse(Validators.isValidInviteCode("XX-ABC-8492"))
    }

    @Test fun `generador produce codigos validos`() {
        val random = Random(42)
        repeat(200) { assertTrue(Validators.isValidInviteCode(InviteCodeGenerator.generate(random))) }
    }

    @Test fun `rut con digito verificador`() {
        assertTrue(RutValidator.isValid("76.123.456-0"))
        assertTrue(RutValidator.isValid("11.111.111-1"))
        assertTrue(RutValidator.isValid("12.345.678-5"))
        assertFalse(RutValidator.isValid("12.345.678-9"))
        assertFalse(RutValidator.isValid("abc"))
        assertEquals("12345678-5", RutValidator.normalize("12.345.678-5"))
    }

    @Test fun `rut con K`() {
        val dv = RutValidator.computeCheckDigit("10000013")
        assertEquals('K', dv)
        assertTrue(RutValidator.isValid("10.000.013-K"))
        assertTrue(RutValidator.isValid("10000013k"))
    }

    @Test fun `anio de vehiculo`() {
        assertTrue(Validators.isValidVehicleYear(2024, 2026))
        assertTrue(Validators.isValidVehicleYear(2027, 2026))
        assertFalse(Validators.isValidVehicleYear(1970, 2026))
        assertFalse(Validators.isValidVehicleYear(2030, 2026))
    }
}
