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
public class PlaylistPlugin: CAPPlugin, StatusUpdater, CAPBridgedPlugin {
    public let identifier = "PlaylistPlugin"
    public let jsName = "Playlist"
    public let pluginMethods: [CAPPluginMethod] = [
        CAPPluginMethod(name: "setOptions", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "initialize", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "release", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "setPlaylistItems", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "addItem", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "addAllItems", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "removeItem", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "removeItems", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "clearAllItems", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "getPlaylist", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "play", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "pause", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "skipForward", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "skipBack", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "seekTo", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "playTrackByIndex", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "playTrackById", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "selectTrackByIndex", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "selectTrackById", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "setPlaybackVolume", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "setLoop", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "setPlaybackRate", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "prepareForVideoHandoff", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "resumeAfterVideoHandoff", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "getLastKnownPosition", returnType: CAPPluginReturnPromise),
    ]
    let audioPlayerImpl = RmxAudioPlayer()
    
    // MARK: - Capacitor API
    @objc func initialize(_ call: CAPPluginCall) {
        // Ensure we don't drop the initial REGISTER status event.
        audioPlayerImpl.statusUpdater = self
        audioPlayerImpl.initialize()
        call.resolve()
    }
    @objc func setOptions(_ call: CAPPluginCall) {
        // setOptions is invoked with the full payload as the options object.
        audioPlayerImpl.setOptions(call.options as! [String : Any])
        call.resolve()
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
            // Prefer index if present.
            if let index = call.getInt("index") {
                try audioPlayerImpl.removeItem(index)
                call.resolve()
                return
            }
            if let id = call.getString("id") {
                try audioPlayerImpl.removeItem(id)
                call.resolve()
                return
            }
            call.reject("Cannot remove: missing id or index")
        } catch {
            call.reject(String(describing: error))
        }
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
        } catch {
            call.reject(error.localizedDescription)
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
        } catch {
            call.reject(error.localizedDescription)
        }
    }
    @objc func selectTrackByIndex(_ call: CAPPluginCall) {
        guard let index = call.getInt("index") else {
            call.reject("Track index Invalid")
            return
        }
        
        do {
            try audioPlayerImpl.selectTrack(index: index)
            call.resolve();
        } catch {
            call.reject(error.localizedDescription)
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
        } catch {
            call.reject(error.localizedDescription)
        }
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

    @objc func prepareForVideoHandoff(_ call: CAPPluginCall) {
        audioPlayerImpl.prepareForVideoHandoff()
        call.resolve()
    }

    @objc func resumeAfterVideoHandoff(_ call: CAPPluginCall) {
        let position = call.getFloat("position", 0)
        let prewarm = call.getBool("prewarm", false)
        audioPlayerImpl.resumeAfterVideoHandoff(position: position, prewarm: prewarm)
        // iOS only reactivates AVAudioSession — JS must still seekTo + play.
        call.resolve(["resumed": false])
    }

    @objc func getLastKnownPosition(_ call: CAPPluginCall) {
        let position = audioPlayerImpl.getLastKnownPosition()
        call.resolve(["position": position])
    }

    public override func load() {
        NotificationCenter.default.addObserver(
            self,
            selector: #selector(applicationWillResignActive),
            name: UIApplication.willResignActiveNotification,
            object: nil
        )
        NotificationCenter.default.addObserver(
            self,
            selector: #selector(applicationDidBecomeActive),
            name: UIApplication.didBecomeActiveNotification,
            object: nil
        )
    }

    @objc private func applicationWillResignActive() {
        audioPlayerImpl.setWebViewActive(false)
    }

    @objc private func applicationDidBecomeActive() {
        audioPlayerImpl.setWebViewActive(true)
        audioPlayerImpl.emitPlaybackSnapshot()
    }

    // MARK: - StatusUpdater delegate
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
