package org.dwbn.plugins.playlist;

import org.json.*;
import android.util.Log;

import com.getcapacitor.JSObject;

public class OnStatusCallback{

  private static final String TAG = "OnStatusCallback";

  private PlaylistPlugin plugin;
  OnStatusCallback(PlaylistPlugin plugin) {
    this.plugin = plugin;
  }

  public static JSONObject createErrorWithCode(RmxAudioErrorType code, String message) {
    JSONObject error = new JSONObject();
    try {
        error.put("code", code);
        error.put("message", message != null ? message : "");
    } catch (JSONException e) {
        Log.e(TAG, "Exception while raising onStatus: ", e);
    }
    return error;
  }

  public void onStatus(RmxAudioStatusMessage what, String trackId, JSONObject param) {

    JSObject data = new JSObject();
    JSObject detail = new JSObject();

    detail.put("msgType", what.getValue());
    detail.put("trackId", trackId);
    detail.put("value", param);

    data.put("action", "status");
    data.put("status", detail);

    Log.v(TAG, "statusChanged:" + data.toString());

    this.plugin.emit("status", data);
  }

}
