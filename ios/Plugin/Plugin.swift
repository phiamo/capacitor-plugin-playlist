import Foundation
import Capacitor

/**
 * Please read the Capacitor iOS Plugin Development Guide
 * here: https://capacitorjs.com/docs/plugins/ios
 */
@objc(PlaylistPlugin)
public class PlaylistPlugin: CAPPlugin {

    let audioPlayerImpl = RmxAudioPlayer()
    
    @objc func setOptions(_ call: CAPPluginCall) {
        let options = call.getObject("options")!
        audioPlayerImpl.setOptions(options)
        call.resolve();
    }
    @objc func initialize(_ call: CAPPluginCall) {
        audioPlayerImpl.initialize()
        call.resolve();
    }
    @objc func release(_ call: CAPPluginCall) {
        call.resolve();
    }
    @objc func setPlaylistItems(_ call: CAPPluginCall) {
        let items: [[String:Any]] = call.getArray("items", [String:Any].self)!
        let options: [[String:Any]] = call.getArray("options", [String:Any].self)!
        
        audioPlayerImpl.setPlaylistItems(items, options: options)
        call.resolve();
    }
    @objc func addItem(_ call: CAPPluginCall) {
        let value = call.getString("value") ?? ""
        call.resolve();
    }
    @objc func addAllItems(_ call: CAPPluginCall) {
        let value = call.getString("value") ?? ""
        call.resolve();
    }
    @objc func removeItem(_ call: CAPPluginCall) {
        let value = call.getString("value") ?? ""
        call.resolve();
    }
    @objc func removeItems(_ call: CAPPluginCall) {
        let value = call.getString("value") ?? ""
        call.resolve();
    }
    @objc func clearAllItems(_ call: CAPPluginCall) {
        let value = call.getString("value") ?? ""
        call.resolve();
    }
    @objc func play(_ call: CAPPluginCall) {
        let value = call.getString("value") ?? ""
        call.resolve();
    }
    @objc func pause(_ call: CAPPluginCall) {
        let value = call.getString("value") ?? ""
        call.resolve();
    }
    @objc func skipForward(_ call: CAPPluginCall) {
        let value = call.getString("value") ?? ""
        call.resolve();
    }
    @objc func skipBack(_ call: CAPPluginCall) {
        let value = call.getString("value") ?? ""
        call.resolve();
    }
    @objc func seekTo(_ call: CAPPluginCall) {
        let value = call.getString("value") ?? ""
        call.resolve();
    }
    @objc func playTrackByIndex(_ call: CAPPluginCall) {
        let value = call.getString("value") ?? ""
        call.resolve();
    }
    @objc func playTrackById(_ call: CAPPluginCall) {
        let value = call.getString("value") ?? ""
        call.resolve();
    }
    @objc func selectTrackByIndex(_ call: CAPPluginCall) {
        let value = call.getString("value") ?? ""
        call.resolve();
    }
    @objc func selectTrackById(_ call: CAPPluginCall) {
        let value = call.getString("value") ?? ""
        call.resolve();
    }
    @objc func setPlaybackVolume(_ call: CAPPluginCall) {
        let value = call.getString("value") ?? ""
        call.resolve();
    }
    @objc func setLoop(_ call: CAPPluginCall) {
        let value = call.getString("value") ?? ""
        call.resolve();
    }
    @objc func setPlaybackRate(_ call: CAPPluginCall) {
        let value = call.getString("value") ?? ""
        call.resolve();
    }
}
