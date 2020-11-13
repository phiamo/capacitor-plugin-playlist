package org.dwbn.plugins.playlist;
import org.json.JSONObject;

public interface OnStatusReportListener {
  void onError(RmxAudioErrorType errorCode, String trackId, String message);
  void onStatus(RmxAudioStatusMessage what, String trackId, JSONObject param);
}
