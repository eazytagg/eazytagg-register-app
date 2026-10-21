package com.eazytagg.register.data

import org.junit.Assert.*
import org.junit.Test

class ServerAddressTest {
    @Test fun acceptsWifiAddressAndTrimsOuterSpaces() {
        assertEquals("192.168.0.39", ServerAddress.ip(" 192.168.0.39 "))
        assertEquals("10.0.2.2", ServerAddress.ip("10.0.2.2"))
    }
    @Test fun rejectsUrlsFoldersDomainsPortsAndMalformedIps() {
        listOf("", "portal.populardynamics.org", "http://192.168.0.39/", "192.168.0.39/eazytagg_test", "192.168.0.39:80", "192.168.0", "256.1.2.3", "192.168.0.-1", "192.168.0.3 9", "0.0.0.0", "224.0.0.1").forEach {
            assertNull("Must reject $it", ServerAddress.ip(it))
            assertTrue(ServerAddress.candidates(it).isEmpty())
        }
    }
    @Test fun handlesOctetBoundariesWithoutAmbiguousLeadingZeroes() {
        assertEquals("192.168.0.255", ServerAddress.ip("192.168.000.255"))
        assertNull(ServerAddress.ip("192.168.0.256"))
    }
    @Test fun discoveryStaysOnTheEnteredComputerAndUsesOnlyKnownPaths() {
        assertEquals(listOf("http://192.168.0.39/eazytagg_test/", "http://192.168.0.39/", "http://192.168.0.39/PDynamics/"), ServerAddress.candidates("192.168.0.39"))
    }
    @Test fun reusesVerifiedRootPathButNeverADifferentHostOrUnknownPath() {
        assertEquals("http://192.168.0.39/", ServerAddress.candidates("192.168.0.39", "http://192.168.0.39/").first())
        assertFalse(ServerAddress.candidates("192.168.0.39", "http://10.0.0.2/").any { it.contains("10.0.0.2") })
        assertFalse(ServerAddress.candidates("192.168.0.39", "http://192.168.0.39/private/").any { it.contains("private") })
    }
    @Test fun savedConnectionShowsOnlyItsIpInTheSetupField() {
        assertEquals("192.168.0.39", ServerAddress.displayIp("http://192.168.0.39/eazytagg_test/"))
        assertEquals("", ServerAddress.displayIp("https://portal.populardynamics.org/"))
        assertEquals("", ServerAddress.displayIp("not a URL"))
    }
}
