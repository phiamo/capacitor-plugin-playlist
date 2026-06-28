package org.dwbn.plugins.playlist

import org.json.JSONException
import org.json.JSONObject

class OnStatusCallback internal constructor(private val plugin: PlaylistPlugin) {
    fun onStatus(what: RmxAudioStatusMessage, trackId: String?, param: JSONObject?) {
        plugin.emitStatus(what, trackId, param)
    }

    companion object {
        private const val TAG = "PlaylistStatusCallback"
        fun createErrorWithCode(code: RmxAudioErrorType?, message: String?): JSONObject {
            val error = JSONObject()
            try {
                error.put("code", code)
                error.put("message", message ?: "")
            } catch (e: JSONException) {
                android.util.Log.e(TAG, "Exception while raising onStatus: ", e)
            }
            return error
        }
    }
}
