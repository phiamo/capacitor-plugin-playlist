import Foundation
import Capacitor

protocol StatusUpdater {
    func onStatus(_ data: [String: Any])
}
/**
 * Please read the Capacitor iOS Plugin Development Guide
 * here: https://capacitorjs.com/docs/plugins/ios
 */
@objc(PlaylistPlugin)
public class PlaylistPlugin: CAPPlugin, StatusUpdater {
    let audioPlayerImpl = RmxAudioPlayer()
    // MARK: - StatusUpdater
    func onStatus(_ data: [String: Any]) {
        notifyListeners("status", data: data)
    }
    
    // MARK: - Capacitor API
    @objc func initialize(_ call: CAPPluginCall) {
        audioPlayerImpl.initialize()
        audioPlayerImpl.statusUpdater = self
        call.resolve();
    }
    @objc func setOptions(_ call: CAPPluginCall) {
        let options = call.getObject("options")!
        audioPlayerImpl.setOptions(options)
        call.resolve();
    }
    @objc func release(_ call: CAPPluginCall) {
        audioPlayerImpl.releaseResources()
        call.resolve();
    }
    @objc func setPlaylistItems(_ call: CAPPluginCall) {
        let items = call.getArray("items", [String:Any].self)!
        let options = call.getObject("options")!
        
        var tracks = createTracks(items)
        audioPlayerImpl.setPlaylistItems(&tracks, options: options)
        call.resolve();
    }
    @objc func addItem(_ call: CAPPluginCall) {
        let trackInfo = call.getObject("item")
        
        var track = AudioTrack.initWithDictionary(trackInfo)
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
        audioPlayerImpl.playCommand(false)
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
    
        
    // MARK: - Utility
    // ******
    // Utilities for the above functions
    func createTracks(_ items: [[String: Any]]?) -> [AudioTrack] {
        if items == nil || items?.count == 0 {
            return []
        }

        var newList: [AudioTrack] = []
        for item in items ?? [] {
            let track = AudioTrack.initWithDictionary(item)
            if let track = track {
                newList.append(track)
            }
        }

        return newList;
    }

}
