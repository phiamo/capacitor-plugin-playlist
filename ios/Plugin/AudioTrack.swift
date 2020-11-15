//  Converted to Swift 5.3 by Swiftify v5.3.19197 - https://swiftify.com/
//
//  AudioTrack.swift
//  BackgroundAudioObjc
//
//  Created by Patrick Sears on 3/29/18.
//

//
//  AudioTrack.swift
//  RmxAudioPlayer
//
//  Created by codinronan on 3/29/18.
//

import AVFoundation

class AudioTrack: AVPlayerItem {
    var isStream = false
    var trackId: String?
    var assetUrl: URL?
    var albumArt: URL?
    var artist: String?
    var album: String?
    var title: String?

    class func initWithDictionary(_ trackInfo: [AnyHashable : Any]?) -> AudioTrack? {
        let trackId = trackInfo?["trackId"] as? String
        let assetUrl = trackInfo?["assetUrl"] as? String
        let isStreamStr = trackInfo?["isStream"] as? String
        let albumArt = trackInfo?["albumArt"] as? String

        if trackId == nil || (trackId == "") {
            return nil
        }
        if assetUrl == nil {
            return nil
        }

        let assetUrlObj = self.getUrlForAsset(assetUrl)
        var track: AudioTrack? = nil
        if let assetUrlObj = assetUrlObj {
            track = AudioTrack(url: assetUrlObj)
        }

        var isStream = false
        if isStreamStr != nil && (isStreamStr as NSString?)?.boolValue ?? false {
            isStream = true
        }

        track?.isStream = isStream
        track?.trackId = trackId
        track?.assetUrl = assetUrlObj
        track?.albumArt = albumArt != nil ? self.getUrlForAsset(albumArt) : nil
        track?.artist = trackInfo?["artist"] as? String
        track?.album = trackInfo?["album"] as? String
        track?.title = trackInfo?["title"] as? String

        if isStream && track?.responds(to: #selector(setter: AVPlayerItem.canUseNetworkResourcesForLiveStreamingWhilePaused)) ?? false {
            track?.canUseNetworkResourcesForLiveStreamingWhilePaused = true
        }

        return track
    }

    func toDict() -> [AnyHashable : Any]? {
        let info = [
            "isStream": NSNumber(value: isStream),
            "trackId": trackId ?? "",
            "assetUrl": assetUrl?.absoluteString ?? "",
            "albumArt": (albumArt != nil ? albumArt?.absoluteString : "") ?? "",
            "artist": artist ?? "",
            "album": album ?? "",
            "title": title ?? ""
        ] as [String : Any]

        return info
    }

    // We create a wrapper function for this so that we can properly handle web, file, cdv, and document urls.
    class func getUrlForAsset(_ assetUrl: String?) -> URL? {
        return URL(string: assetUrl ?? "")
    }

    deinit {
    }
}
