package org.dwbn.plugins.playlist.manager

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSpec
import androidx.media3.exoplayer.source.LoadEventInfo
import androidx.media3.exoplayer.source.MediaLoadData
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
@UnstableApi
class DrmTerminalLoadErrorPolicyTest {

    private fun info(error: IOException) = LoadErrorHandlingPolicy.LoadErrorInfo(
        LoadEventInfo(1, DataSpec(Uri.parse("https://example.com/seg.ts")), 0),
        MediaLoadData(C.DATA_TYPE_MEDIA),
        error,
        1,
    )

    @Test
    fun plainNetworkError_keepsDefaultRetry() {
        val policy = DrmTerminalLoadErrorPolicy { false }
        assertNotEquals(C.TIME_UNSET, policy.getRetryDelayMsFor(info(IOException("timeout"))))
    }

    @Test
    fun terminalDrmErrorInCause_isNotRetried() {
        val policy = DrmTerminalLoadErrorPolicy { false }
        val error = IOException("load failed", IllegalStateException("notEntitled"))
        assertEquals(C.TIME_UNSET, policy.getRetryDelayMsFor(info(error)))
    }

    @Test
    fun anyErrorWhileDrmHalted_isNotRetried() {
        val policy = DrmTerminalLoadErrorPolicy { true }
        assertEquals(C.TIME_UNSET, policy.getRetryDelayMsFor(info(IOException("403"))))
    }
}
