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
        CAPPluginMethod(name: "moveItem", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "replaceItem", returnType: CAPPluginReturnPromise),
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
        CAPPluginMethod(name: "startDownload", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "cancelDownload", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "deleteDownload", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "renewDownload", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "listDownloads", returnType: CAPPluginReturnPromise),
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
        audioPlayerImpl.setOptions(call.jsObjectRepresentation)
        call.resolve()
    }
    @objc func release(_ call: CAPPluginCall) {
        audioPlayerImpl.releaseResources()
        call.resolve();
    }
    @objc func setPlaylistItems(_ call: CAPPluginCall) {
        let items = call.getArray("items", [String:Any].self)!
        let options = call.getObject("options")!

        if let refusal = drmNoProviderRejection(for: items) {
            call.reject(refusal.message, refusal.code)
            return
        }

        let tracks = createTracks(items)
        audioPlayerImpl.setPlaylistItems(tracks, options: options)

        call.resolve();
    }
    @objc func addItem(_ call: CAPPluginCall) {
        let trackInfo = call.getObject("item")

        if let refusal = drmNoProviderRejection(for: [trackInfo]) {
            call.reject(refusal.message, refusal.code)
            return
        }

        guard let track = AudioTrack.initWithDictionary(trackInfo, onDrmError: { [weak self] trackId, error in
            self?.audioPlayerImpl.reportDrmError(trackId: trackId, error: error)
        }) else {
            call.reject("Invalid track")
            return
        }

        if let index = call.getInt("index") {
            do {
                try audioPlayerImpl.addItem(track, at: index)
                call.resolve()
            } catch {
                call.reject(error.localizedDescription)
            }
            return
        }

        audioPlayerImpl.addItem(track)
        call.resolve()
    }

    @objc func moveItem(_ call: CAPPluginCall) {
        guard let from = call.getInt("from"), let to = call.getInt("to") else {
            call.reject("Missing from or to index")
            return
        }

        do {
            try audioPlayerImpl.moveItem(from: from, to: to)
            call.resolve()
        } catch {
            call.reject(error.localizedDescription)
        }
    }

    @objc func replaceItem(_ call: CAPPluginCall) {
        guard let trackInfo = call.getObject("item") else {
            call.reject("Missing item")
            return
        }

        if let refusal = drmNoProviderRejection(for: [trackInfo]) {
            call.reject(refusal.message, refusal.code)
            return
        }

        do {
            try audioPlayerImpl.replaceItem(
                at: call.getInt("index"),
                id: call.getString("id"),
                with: trackInfo
            )
            call.resolve()
        } catch {
            call.reject(error.localizedDescription)
        }
    }
    @objc func addAllItems(_ call: CAPPluginCall) {
        let items = call.getArray("items", [String:Any].self)!

        if let refusal = drmNoProviderRejection(for: items) {
            call.reject(refusal.message, refusal.code)
            return
        }

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
        audioPlayerImpl.seek(to: to, isCommand: true)
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
            try audioPlayerImpl.selectTrack(index: index, positionTime: call.getFloat("position"))
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
            try audioPlayerImpl.selectTrack(id: id, positionTime: call.getFloat("position"))
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
        let play = call.getBool("play", false)
        audioPlayerImpl.resumeAfterVideoHandoff(position: position, prewarm: prewarm, play: play) { resumed in
            NSLog("[Playlist] resumeAfterVideoHandoff resolved prewarm=%@ play=%@ resumed=%@",
                  prewarm ? "true" : "false", play ? "true" : "false", resumed ? "true" : "false")
            call.resolve(["resumed": resumed])
        }
    }

    @objc func getLastKnownPosition(_ call: CAPPluginCall) {
        let position = audioPlayerImpl.getLastKnownPosition()
        call.resolve(["position": position])
    }

    // MARK: - Offline downloads (Story 59.5)

    @objc func startDownload(_ call: CAPPluginCall) {
        let downloadId = call.getString("downloadId")?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        let url = call.getString("url")?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        let drm = call.getObject("drm")
        if downloadId.isEmpty || url.isEmpty || drm == nil {
            call.reject("downloadId, url and drm are required", "invalidArgument")
            return
        }
        let failure = OfflineDownloads.shared.start(downloadId: downloadId, url: url, drm: drm)
        if let failure {
            call.reject("Offline provider is not registered", failure)
            return
        }
        call.resolve(["downloadId": downloadId])
    }

    @objc func cancelDownload(_ call: CAPPluginCall) {
        let downloadId = call.getString("downloadId")?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        if downloadId.isEmpty {
            call.reject("downloadId is required", "invalidArgument")
            return
        }
        OfflineDownloads.shared.cancel(downloadId)
        call.resolve()
    }

    @objc func deleteDownload(_ call: CAPPluginCall) {
        let downloadId = call.getString("downloadId")?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        if downloadId.isEmpty {
            call.reject("downloadId is required", "invalidArgument")
            return
        }
        OfflineDownloads.shared.delete(downloadId)
        call.resolve()
    }

    @objc func renewDownload(_ call: CAPPluginCall) {
        let downloadId = call.getString("downloadId")?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        if downloadId.isEmpty {
            call.reject("downloadId is required", "invalidArgument")
            return
        }
        OfflineDownloads.shared.renew(downloadId, drm: call.getObject("drm")) { failure in
            DispatchQueue.main.async {
                if let failure {
                    call.reject("Offline licence renewal failed", failure)
                } else {
                    call.resolve()
                }
            }
        }
    }

    @objc func listDownloads(_ call: CAPPluginCall) {
        let items: [[String: Any]] = OfflineDownloads.shared.list().map { info in
            var entry: [String: Any] = [
                "downloadId": info.downloadId,
                "state": info.state,
                "progress": Double(info.progress),
                "needsRenewal": info.needsRenewal
            ]
            if let expiresAt = info.expiresAt {
                entry["expiresAt"] = expiresAt
            } else {
                entry["expiresAt"] = NSNull()
            }
            return entry
        }
        call.resolve(["downloads": items])
    }

    public override func load() {
        OfflineDownloads.shared.eventSink = { [weak self] event in
            self?.emitDownload(event)
        }
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

    func emitDownload(_ event: DownloadEvent) {
        DispatchQueue.main.async { [weak self] in
            guard let self else { return }
            var data: [String: Any] = [
                "downloadId": event.downloadId,
                "state": event.state,
                "progress": Double(event.progress)
            ]
            if let error = event.error {
                data["error"] = error
            }
            self.notifyListeners(
                "download",
                data: data,
                retainUntilConsumed: event.state != DownloadStates.downloading
            )
        }
    }
        
    // MARK: - Utility
    func createTracks(_ items: [[String: Any]]?) -> [AudioTrack] {
        if items == nil || items?.count == 0 {
            return []
        }

        var newList: [AudioTrack] = []
        for item in items ?? [] {
            let track = AudioTrack.initWithDictionary(item, onDrmError: { [weak self] trackId, error in
                self?.audioPlayerImpl.reportDrmError(trackId: trackId, error: error)
            })
            if let track = track {
                newList.append(track)
            }
        }

        return newList;
    }

    /// Reject `drm` items when no `AudioDrm` provider is registered (Story 58.5), and `downloadId`
    /// items when no `AudioOffline` provider is registered (Story 59.5). `drm` still uses
    /// `AudioDrm`; `downloadId` uses `AudioOffline`. Queue is left unchanged by the caller.
    func drmNoProviderRejection(for items: [[String: Any]]) -> (code: String, message: String)? {
        drmNoProviderRejection(for: items.map { Optional($0) })
    }

    func drmNoProviderRejection(for items: [[String: Any]?]) -> (code: String, message: String)? {
        func downloadId(of item: [String: Any]?) -> String? {
            guard let raw = item?["downloadId"], !(raw is NSNull) else { return nil }
            let id = (raw as? String)?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
            return id.isEmpty ? nil : id
        }
        if items.contains(where: { downloadId(of: $0) != nil }) && AudioOffline.getProvider() == nil {
            return (AudioDrm.codeNoProvider, "Offline provider is not registered")
        }
        let needsStreamingDrm = items.contains { item in
            item?["drm"] is [String: Any] && downloadId(of: item) == nil
        }
        if needsStreamingDrm && AudioDrm.getProvider() == nil {
            return (AudioDrm.codeNoProvider, "DRM provider is not registered")
        }
        return nil
    }

}
