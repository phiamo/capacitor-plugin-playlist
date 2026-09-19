package org.dwbn.plugins.playlist

import androidx.media3.common.PlaybackException

/** Maps Media3 playback error codes onto the frozen JS `RmxAudioErrorType` values. */
object RmxPlaybackErrorMapper {
    @JvmStatic
    fun fromMedia3ErrorCode(errorCode: Int): RmxAudioErrorType =
        when (errorCode) {
            PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
            PlaybackException.ERROR_CODE_DECODING_FAILED,
            PlaybackException.ERROR_CODE_DECODER_QUERY_FAILED -> RmxAudioErrorType.RMXERR_DECODE
            else -> RmxAudioErrorType.RMXERR_NONE_SUPPORTED
        }

    @JvmStatic
    fun isSourceError(errorCode: Int): Boolean =
        when (errorCode) {
            PlaybackException.ERROR_CODE_IO_UNSPECIFIED,
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT,
            PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS,
            PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND -> true
            else -> false
        }
}
