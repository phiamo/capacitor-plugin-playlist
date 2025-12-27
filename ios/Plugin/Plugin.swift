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
        
        let tracks = createTracks(items)
        audioPlayerImpl.setPlaylistItems(tracks, options: options)
        
        call.resolve();
    }
    @objc func addItem(_ call: CAPPluginCall) {
        let trackInfo = call.getObject("item")
        
        let track = AudioTrack.initWithDictionary(trackInfo)
        audioPlayerImpl.addItem(track!)
        
        call.resolve();
    }
    @objc func addAllItems(_ call: CAPPluginCall) {
        let items = call.getArray("items", [String:Any].self)!
        
        let tracks = createTracks(items)
        audioPlayerImpl.addAllItems(tracks)
        call.resolve();
    }
    @objc func removeItem(_ call: CAPPluginCall) {
        do {
            if let id = call.getString("id"){
                try audioPlayerImpl.removeItem(id)
                return
            }
            guard let index = call.getString("index") else {
                call.reject("Cannot remove")
                return
            }
            try audioPlayerImpl.removeItem(index)
        } catch let message {
            call.reject(message as! String)
        }
        call.resolve();
    }
    @objc func removeItems(_ call: CAPPluginCall) {
        guard let items = call.getArray("items") else {
            call.reject("No Items")
            return
        }
        let count = audioPlayerImpl.removeItems(items)
        call.resolve([
            "removed": count
        ]);
    }
    @objc func clearAllItems(_ call: CAPPluginCall) {
        audioPlayerImpl.clearAllItems()
        call.resolve();
    }
    @objc func getPlaylist(_ call: CAPPluginCall) {
        let tracks = audioPlayerImpl.avQueuePlayer.queuedAudioTracks
        let items = tracks.map { $0.toDict() }
        call.resolve(["items": items]);
    }
    @objc func play(_ call: CAPPluginCall) {
        audioPlayerImpl.playCommand(false)
        call.resolve();
    }
    @objc func pause(_ call: CAPPluginCall) {
        audioPlayerImpl.pauseCommand(false)
        call.resolve();
    }
    @objc func skipForward(_ call: CAPPluginCall) {
        audioPlayerImpl.playNext(false)
        call.resolve();
    }
    @objc func skipBack(_ call: CAPPluginCall) {
        audioPlayerImpl.playPrevious(false)
        call.resolve();
    }
    @objc func seekTo(_ call: CAPPluginCall) {
        let to = call.getFloat("position", 0.0)
        audioPlayerImpl.seek(to: to, isCommand: false)
        call.resolve();
    }
    @objc func playTrackByIndex(_ call: CAPPluginCall) {
        guard let index = call.getInt("index") else {
            call.reject("Track index Invalid")
            return
        }
        
        do {
            try audioPlayerImpl.playTrack(index: index, positionTime: call.getFloat("position"))
            call.resolve();
        }
        catch let message {
            call.reject(message as! String)
        }
    }
    @objc func playTrackById(_ call: CAPPluginCall) {
        guard let id = call.getString("id") else {
            call.reject("Track Id Invalid")
            return
        }
        
        do {
            try audioPlayerImpl.playTrack(id, positionTime: call.getFloat("position"))
            call.resolve();
        }
        catch let message {
            call.reject(message as! String)
        }
        call.resolve();
    }
    @objc func selectTrackByIndex(_ call: CAPPluginCall) {
        guard let index = call.getInt("index") else {
            call.reject("Track index Invalid")
            return
        }
        
        do {
            try audioPlayerImpl.selectTrack(index: index)
            call.resolve();
        }
        catch let message {
            call.reject(message as! String)
        }
    }
    @objc func selectTrackById(_ call: CAPPluginCall) {
        guard let id = call.getString("id") else {
            call.reject("Track Id Invalid")
            return
        }
        
        do {
            try audioPlayerImpl.selectTrack(id: id)
            call.resolve();
        }
        catch let message {
            call.reject(message as! String)
        }
        call.resolve();
    }
    @objc func setPlaybackVolume(_ call: CAPPluginCall) {
        let volume = call.getFloat("volume", 1)
        audioPlayerImpl.setPlaybackVolume(volume)
        call.resolve();
    }
    @objc func setLoop(_ call: CAPPluginCall) {
        let loop = call.getBool("loop", true)
        audioPlayerImpl.setLoopAll(loop)
        call.resolve();
    }
    @objc func setPlaybackRate(_ call: CAPPluginCall) {
        let rate = call.getFloat("rate", 1)
        audioPlayerImpl.setPlaybackRate(rate)
        call.resolve();
    }
    
    // MARK: - StatusUpdater delegate
    // todo: calls to notifyListeners should be throttled
    func onStatus(_ data: [String: Any]) {
        notifyListeners("status", data: data)
    }
        
    // MARK: - Utility
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
