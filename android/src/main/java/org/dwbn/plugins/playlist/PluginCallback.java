package org.dwbn.plugins.playlist;

import android.util.Log;

import com.getcapacitor.JSObject;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginResult;

import org.json.JSONException;

public class PluginCallback {
    protected PluginCall pluginCall;

    PluginCallback(PluginCall pluginCall) {
        this.pluginCall = pluginCall;
    }

    public void sendError(String message) {
      pluginCall.reject(message);
    }


    public void send(PluginResult result, boolean keepCallback) {
        if (pluginCall == null) {
          Log.e("PluginCallback", "send did not complete: callbackContext is null");
          return;
        }
        //result.
        // Log.i("PluginCallback", "Sending status: " + result.getMessage());
       //result.setKeepCallback(keepCallback);
        // callbackContext.success(dict);
        JSObject res = null;
        try {
            res = new JSObject(result.toString());
        } catch (JSONException e) {
            e.printStackTrace();
        }
        pluginCall.resolve(res);
    }
}
