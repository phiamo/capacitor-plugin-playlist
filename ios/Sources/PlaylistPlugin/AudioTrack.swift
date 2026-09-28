//  AudioTrack.swift
//  RmxAudioPlayer
//
//  Created by codinronan on 3/29/18.
//

import AVFoundation
import Capacitor

final class AudioTrack: AVPlayerItem {
    var isStream = false
    var trackId: String?
    var assetUrl: URL?
    var albumArt: URL?
    var artist: String?
    var album: String?
    var title: String?
    /// Last known playback position for this track, in seconds. Seeded from the JS-supplied
    /// `startPosition` at construction, and kept fresh by `AVBidirectionalQueuePlayer` whenever
    /// the player skips away from this track, so a later skip back within the same session
    /// resumes correctly instead of restarting at 0.
    var startPositionSeconds: Double = 0
    /// Story 58.5: host-registered FairPlay session, opened via the registered `AudioDrm`
    /// provider before this track's `AVURLAsset` is used to construct the track. `nil` for plain
    /// (non-DRM) playback. Released in `deinit` so any removal path (`removeItem`/`removeItems`/
    /// `clearAllItems`/`releaseResources`/`replaceItem`'s old-track path) releases exactly once
    /// via ARC.
    var drmSession: AudioDrmSession?

    class func initWithDictionary(
        _ trackInfo: [String: Any]?,
        onDrmError: ((_ trackId: String, _ error: String) -> Void)? = nil
    ) -> AudioTrack? {
        guard
            let trackInfo = trackInfo,
            let trackId = trackInfo["trackId"] as? String,
            !trackId.isEmpty,
            let assetUrlString = trackInfo["assetUrl"] as? String,
            let assetUrl = URL(string: assetUrlString)
        else {
            return nil
        }

        // Build an explicit AVURLAsset so a registered DRM session can be attached before any
        // AVPlayerItem is constructed from it (Story 58.5) -- AVPlayerItem(url:)'s implicit
        // asset can't be attached to ahead of time.
        let asset = AVURLAsset(url: assetUrl)
        var openedSession: AudioDrmSession?
        if let drm = trackInfo["drm"] as? JSObject {
            let attempt = AudioDrm.open(drm) { error in
                onDrmError?(trackId, error)
            }
            if let session = attempt.session {
                session.attach(to: asset)
                session.start()
                openedSession = session
            }
        }

        let track = AudioTrack(asset: asset)
        track.drmSession = openedSession
        track.canUseNetworkResourcesForLiveStreamingWhilePaused = true

        // Accept common JS representations.
        if let isStream = trackInfo["isStream"] as? Bool {
            track.isStream = isStream
        } else if let isStreamNum = trackInfo["isStream"] as? NSNumber {
            track.isStream = isStreamNum.boolValue
        } else if let isStreamStr = trackInfo["isStream"] as? NSString {
            track.isStream = isStreamStr.boolValue
        }
        
        let albumArt = trackInfo["albumArt"] as? String
        track.albumArt = albumArt != nil ? URL(string: albumArt!) : nil
        
        track.trackId = trackId
        track.assetUrl = assetUrl
        track.artist = trackInfo["artist"] as? String
        track.album = trackInfo["album"] as? String
        track.title = trackInfo["title"] as? String

        // Accept common JS representations, same as isStream above.
        if let startPosition = trackInfo["startPosition"] as? Double {
            track.startPositionSeconds = max(0, startPosition)
        } else if let startPositionNum = trackInfo["startPosition"] as? NSNumber {
            track.startPositionSeconds = max(0, startPositionNum.doubleValue)
        }

        return track
    }

    func toDict() -> [String : Any]? {
        [
            "isStream": NSNumber(value: isStream),
            "trackId": trackId ?? "",
            "assetUrl": assetUrl?.absoluteString ?? "",
            "albumArt": albumArt?.absoluteString ?? "",
            "artist": artist ?? "",
            "album": album ?? "",
            "title": title ?? "",
            "startPosition": NSNumber(value: startPositionSeconds)
        ]
    }

    deinit {
        drmSession?.release()
    }
}
