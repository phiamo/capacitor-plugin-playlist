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
    var startTime: Double = 0.0
    var endTime: Double?

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
        
        // Handle excerpt timing
        if let startTime = trackInfo["startTime"] as? NSNumber {
            track.startTime = startTime.doubleValue
        }
        if let endTime = trackInfo["endTime"] as? NSNumber {
            track.endTime = endTime.doubleValue
        }
        
        return track
    }

    func toDict() -> [String : Any]? {
        var dict: [String : Any] = [
            "isStream": NSNumber(value: isStream),
            "trackId": trackId ?? "",
            "assetUrl": assetUrl?.absoluteString ?? "",
            "albumArt": albumArt?.absoluteString ?? "",
            "artist": artist ?? "",
            "album": album ?? "",
            "title": title ?? ""
        ]
        
        if startTime > 0 {
            dict["startTime"] = NSNumber(value: startTime)
        }
        if let endTime = endTime {
            dict["endTime"] = NSNumber(value: endTime)
        }
        
        return dict
    }
}
