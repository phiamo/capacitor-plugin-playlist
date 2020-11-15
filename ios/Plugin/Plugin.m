#import <Foundation/Foundation.h>
#import <Capacitor/Capacitor.h>

// Define the plugin using the CAP_PLUGIN Macro, and
// each method the plugin supports using the CAP_PLUGIN_METHOD macro.
CAP_PLUGIN(PlaylistPlugin, "PlaylistPlugin",
   CAP_PLUGIN_METHOD(setOptions, CAPPluginReturnPromise);
   CAP_PLUGIN_METHOD(initialize, CAPPluginReturnPromise);
   CAP_PLUGIN_METHOD(release, CAPPluginReturnPromise);
   CAP_PLUGIN_METHOD(setPlaylistItems, CAPPluginReturnPromise);
   CAP_PLUGIN_METHOD(addItem, CAPPluginReturnPromise);
   CAP_PLUGIN_METHOD(addAllItems, CAPPluginReturnPromise);
   CAP_PLUGIN_METHOD(removeItem, CAPPluginReturnPromise);
   CAP_PLUGIN_METHOD(removeItems, CAPPluginReturnPromise);
   CAP_PLUGIN_METHOD(clearAllItems, CAPPluginReturnPromise);
   CAP_PLUGIN_METHOD(play, CAPPluginReturnPromise);
   CAP_PLUGIN_METHOD(pause, CAPPluginReturnPromise);
   CAP_PLUGIN_METHOD(skipForward, CAPPluginReturnPromise);
   CAP_PLUGIN_METHOD(skipBack, CAPPluginReturnPromise);
   CAP_PLUGIN_METHOD(seekTo, CAPPluginReturnPromise);
   CAP_PLUGIN_METHOD(playTrackByIndex, CAPPluginReturnPromise);
   CAP_PLUGIN_METHOD(playTrackById, CAPPluginReturnPromise);
   CAP_PLUGIN_METHOD(playTrackByIndex, CAPPluginReturnPromise);
   CAP_PLUGIN_METHOD(selectTrackByIndex, CAPPluginReturnPromise);
   CAP_PLUGIN_METHOD(selectTrackById, CAPPluginReturnPromise);
   CAP_PLUGIN_METHOD(setPlaybackVolume, CAPPluginReturnPromise);
   CAP_PLUGIN_METHOD(setLoop, CAPPluginReturnPromise);
   CAP_PLUGIN_METHOD(setPlaybackRate, CAPPluginReturnPromise);
)
