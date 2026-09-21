package org.dwbn.plugins.playlist

import androidx.media3.common.PlaybackException

/** Maps Media3 playback error codes onto the frozen JS `RmxAudioErrorType` values. */
object RmxPlaybackErrorMapper {
    /**
     * Classify a Media3 error for JS. The distinction that matters to consumers is retry-able
     * (`RMXERR_NETWORK` — the source is fine, the connection wasn't) versus not (`RMXERR_DECODE` /
     * `RMXERR_NONE_SUPPORTED` — retrying the same URL will fail the same way), see issue #143.
     */
    @JvmStatic
    fun fromMedia3ErrorCode(errorCode: Int): RmxAudioErrorType =
        when (errorCode) {
            PlaybackException.ERROR_CODE_IO_UNSPECIFIED,
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT,
            PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS -> RmxAudioErrorType.RMXERR_NETWORK

            PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
            PlaybackException.ERROR_CODE_DECODING_FAILED,
            PlaybackException.ERROR_CODE_DECODER_QUERY_FAILED -> RmxAudioErrorType.RMXERR_DECODE

            // The source was reached but isn't usable — a missing file, or a body that doesn't
            // parse as the container it was declared to be (the issue #144 failure mode).
            PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND,
            PlaybackException.ERROR_CODE_IO_INVALID_HTTP_CONTENT_TYPE,
            PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED,
            PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED,
            PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED,
            PlaybackException.ERROR_CODE_PARSING_MANIFEST_UNSUPPORTED -> RmxAudioErrorType.RMXERR_NONE_SUPPORTED

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
