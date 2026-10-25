package dev.campususage.app

import dev.campususage.app.data.remote.MainCampusPortal
import org.junit.Assert.*
import org.junit.Test

class MainCampusPortalTests {
    @Test fun officialHomeHistoryAndSchoolCasAreAllowed(){
        assertTrue(MainCampusPortal.allowed(MainCampusPortal.HOME))
        assertTrue(MainCampusPortal.allowed(MainCampusPortal.HISTORY))
        assertTrue(MainCampusPortal.allowed("http://sdhq.hust.edu.cn/ICBS/cas/neusoftcas.aspx"))
        assertTrue(MainCampusPortal.allowed("https://pass.hust.edu.cn/cas/login?service=http%3A%2F%2Fsdhq.hust.edu.cn"))
    }
    @Test fun similarlyNamedHostsAndEmbeddedCredentialsAreRejected(){
        assertFalse(MainCampusPortal.allowed("http://sdhq.hust.edu.cn.attacker.test/MobileUtilityPay1/"))
        assertFalse(MainCampusPortal.allowed("http://attacker@sdhq.hust.edu.cn/MobileUtilityPay1/"))
        assertFalse(MainCampusPortal.allowed("https://hkwxy.hust.edu.cn/tp_up/v2/"))
    }
    @Test fun authenticationCannotBeDowngradedAndNonWebSchemesAreRejected(){
        assertEquals("https://pass.hust.edu.cn/cas/login?service=http%3A%2F%2Fsdhq.hust.edu.cn%2FICBS%2Fcas%2Fneusoftcas.aspx",MainCampusPortal.secureAuthentication("http://pass.hust.edu.cn/cas/login?service=http%3A%2F%2Fsdhq.hust.edu.cn%2FICBS%2Fcas%2Fneusoftcas.aspx"))
        assertNull(MainCampusPortal.secureAuthentication("http://pass.hust.edu.cn.attacker.test/cas/login"))
        assertFalse(MainCampusPortal.allowed("http://pass.hust.edu.cn/cas/login"))
        assertFalse(MainCampusPortal.allowed("javascript:alert(1)"))
        assertFalse(MainCampusPortal.allowed("file:///sdcard/session.html"))
        assertFalse(MainCampusPortal.allowed("intent://sdhq.hust.edu.cn/MobileUtilityPay1/"))
    }
    @Test fun unrelatedPathsMalformedUrlsAndAlternatePortsAreRejected(){
        assertFalse(MainCampusPortal.allowed("http://sdhq.hust.edu.cn/other/"))
        assertFalse(MainCampusPortal.allowed("http://sdhq.hust.edu.cn:9999/MobileUtilityPay1/"))
        assertFalse(MainCampusPortal.allowed("https://pass.hust.edu.cn:444/cas/login"))
        assertFalse(MainCampusPortal.allowed("not a url"))
    }
}
