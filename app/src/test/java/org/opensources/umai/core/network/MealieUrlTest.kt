package org.opensources.umai.core.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MealieUrlTest {

    private fun canonical(raw: String): String? =
        (MealieUrl.parse(raw) as? MealieUrl.Result.Valid)?.let { MealieUrl.canonical(it.url) }

    @Test
    fun `bare custom domain defaults to https`() {
        assertEquals("https://mealie.ndd.custom/", canonical("mealie.ndd.custom"))
    }

    @Test
    fun `surrounding whitespace is ignored`() {
        assertEquals("https://mealie.ndd.custom/", canonical("  mealie.ndd.custom \n"))
    }

    @Test
    fun `explicit https is kept`() {
        assertEquals("https://mealie.example.org/", canonical("https://mealie.example.org"))
    }

    @Test
    fun `http is accepted and reported as cleartext`() {
        val result = MealieUrl.parse("http://192.168.1.10:9000")
        assertTrue(result is MealieUrl.Result.Valid)
        assertTrue((result as MealieUrl.Result.Valid).isCleartext)
        assertEquals("http://192.168.1.10:9000/", MealieUrl.canonical(result.url))
    }

    @Test
    fun `https is not cleartext`() {
        val result = MealieUrl.parse("mealie.ndd.custom") as MealieUrl.Result.Valid
        assertFalse(result.isCleartext)
    }

    @Test
    fun `sub path instances keep their prefix with a trailing slash`() {
        assertEquals("https://home.lan/mealie/", canonical("https://home.lan/mealie"))
        assertEquals("https://home.lan/mealie/", canonical("https://home.lan/mealie/"))
    }

    @Test
    fun `query and fragment are dropped`() {
        assertEquals("https://home.lan/", canonical("https://home.lan/?a=1#frag"))
    }

    @Test
    fun `ip address with port is accepted`() {
        assertEquals("https://10.0.0.5:9925/", canonical("10.0.0.5:9925"))
    }

    @Test
    fun `empty input is reported as empty`() {
        assertEquals(MealieUrl.Result.Empty, MealieUrl.parse("   "))
    }

    @Test
    fun `other schemes are rejected`() {
        assertEquals(MealieUrl.Result.UnsupportedScheme, MealieUrl.parse("ftp://mealie.lan"))
    }

    @Test
    fun `malformed host is rejected`() {
        assertEquals(MealieUrl.Result.Malformed, MealieUrl.parse("https://"))
        assertEquals(MealieUrl.Result.Malformed, MealieUrl.parse("://///"))
    }
}
