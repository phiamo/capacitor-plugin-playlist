//  AudioTrack.swift
//  RmxAudioPlayer
//
//  Created by codinronan on 3/29/18.
//

import AVFoundation

final class AudioTrack: AVPlayerItem {
    var isStream = false
    var trackId: String?
    var assetUrl: URL?
    var albumArt: URL?
    var artist: String?
    var album: String?
    var title: String?

    class func initWithDictionary(_ trackInfo: [String : Any]?) -> AudioTrack? {
        guard
            let trackInfo = trackInfo,
            let trackId = trackInfo["trackId"] as? String,
            !trackId.isEmpty,
            let assetUrlString = trackInfo["assetUrl"] as? String,
            let assetUrl = URL(string: assetUrlString)
        else {
            return nil
        }
        let track = AudioTrack(url: assetUrl)
        track.canUseNetworkResourcesForLiveStreamingWhilePaused = true

        if let isStreamStr = trackInfo["isStream"] as? NSString {
            track.isStream = isStreamStr.boolValue
        }
        
        let albumArt = trackInfo["albumArt"] as? String
        track.albumArt = albumArt != nil ? URL(string: albumArt!) : nil
        
        track.trackId = trackId
        track.assetUrl = assetUrl
        track.artist = trackInfo["artist"] as? String
        track.album = trackInfo["album"] as? String
        track.title = trackInfo["title"] as? String
        
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
            "title": title ?? ""
        ]
    }
}
