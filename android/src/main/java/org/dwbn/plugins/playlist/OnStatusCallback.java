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

    JSObject status = new JSObject();

    status.put("type", what.getValue()); // not .ordinal()
    status.put("trackId", trackId);
    status.put("value", param);

    status.put("action", "status");

    Log.v(TAG, "statusChanged:" + status.toString());

    this.plugin.emit("status", status);
  }

}
