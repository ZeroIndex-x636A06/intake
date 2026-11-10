package com.zeroindex.intake.domain.backup

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SyncUrlPolicyTest {
    @Test
    fun `plain http is only for obviously local addresses`() {
        assertThat(SyncUrlPolicy.check("https://nas.example.net/dav")).isEqualTo(SyncUrlPolicy.Verdict.Ok)
        assertThat(SyncUrlPolicy.check("http://192.168.1.20:5005/dav")).isEqualTo(SyncUrlPolicy.Verdict.LocalPlainHttp)
        assertThat(SyncUrlPolicy.check("http://nas.local/dav")).isEqualTo(SyncUrlPolicy.Verdict.LocalPlainHttp)
        assertThat(SyncUrlPolicy.check("http://[fd00::5]/dav")).isEqualTo(SyncUrlPolicy.Verdict.LocalPlainHttp)
        assertThat(SyncUrlPolicy.check("http://nas.example.net/dav")).isInstanceOf(SyncUrlPolicy.Verdict.Rejected::class.java)
        assertThat(SyncUrlPolicy.check("http://8.8.8.8/")).isInstanceOf(SyncUrlPolicy.Verdict.Rejected::class.java)
        assertThat(SyncUrlPolicy.check("ftp://nas.local/")).isInstanceOf(SyncUrlPolicy.Verdict.Rejected::class.java)
        assertThat(SyncUrlPolicy.check("https://me:pw@nas.example.net/")).isInstanceOf(SyncUrlPolicy.Verdict.Rejected::class.java)
    }
}
