package org.dwbn.plugins.playlist

import android.util.Log
import com.getcapacitor.JSObject
import org.json.JSONException
import org.json.JSONObject

class OnStatusCallback internal constructor(private val plugin: PlaylistPlugin) {
    fun onStatus(what: RmxAudioStatusMessage, trackId: String?, param: JSONObject?) {
        val data = JSObject()
        val detail = JSObject()
        detail.put("msgType", what.value)
        detail.put("trackId", trackId)
        detail.put("value", param)
        data.put("action", "status")
        data.put("status", detail)
        Log.v(TAG, "statusChanged:$data")
        plugin.emit("status", data)
    }

    companion object {
        private const val TAG = "PlaylistStatusCallback"
        fun createErrorWithCode(code: RmxAudioErrorType?, message: String?): JSONObject {
            val error = JSONObject()
            try {
                error.put("code", code)
                error.put("message", message ?: "")
            } catch (e: JSONException) {
                Log.e(TAG, "Exception while raising onStatus: ", e)
            }
            return error
        }
    }
}