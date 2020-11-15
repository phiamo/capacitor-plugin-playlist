//  Converted to Swift 5.3 by Swiftify v5.3.19197 - https://swiftify.com/
//
// RmxAudioPlayer.swift
// Music Controls Cordova Plugin
//
// Created by Juan Gonzalez on 12/16/16.
//


import AVFoundation
import Cordova
import MediaPlayer
import UIKit

#if !RmxAudioPlayer_h
//#define RmxAudioPlayer_h


private var kAvQueuePlayerContext = 0
private var kAvQueuePlayerRateContext = 0
private var kPlayerItemStatusContext = 0
private var kPlayerItemDurationContext = 0
private var kPlayerItemTimeRangesContext = 0

class RmxAudioPlayer {
    private var playbackTimeObserver: Any?
    private var wasPlayingInterrupted = false
    private var commandCenterRegistered = false
    private var resetStreamOnPause = false
    private var updatedNowPlayingInfo: [AnyHashable : Any]?
    private var isReplacingItems = false
    private var isWaitingToStartPlayback = false
    private var loop = false

    private var _avQueuePlayer: AVBidirectionalQueuePlayer?
    private var avQueuePlayer: AVBidirectionalQueuePlayer? {
        if _avQueuePlayer == nil {
            let queue: [AnyHashable]? = []

            if let queue = queue as? [AVPlayerItem] {
                _avQueuePlayer = AVBidirectionalQueuePlayer(items: queue)
            }
            _avQueuePlayer?.actionAtItemEnd = .advance
            _avQueuePlayer?.addObserver(self as! NSObject, forKeyPath: "currentItem", options: .new, context: UnsafeMutableRawPointer(mutating: &kAvQueuePlayerContext))
            _avQueuePlayer?.addObserver(self as! NSObject, forKeyPath: "rate", options: .new, context: UnsafeMutableRawPointer(mutating: &kAvQueuePlayerRateContext))

            let interval = CMTimeMakeWithSeconds(Float64(1.0), preferredTimescale: Int32(Double(NSEC_PER_SEC)))
            weak var weakSelf = self
            let mainQueue = DispatchQueue.main
            playbackTimeObserver = _avQueuePlayer?.addPeriodicTimeObserver(forInterval: interval, queue: mainQueue, using: { time in
                let strongSelf = weakSelf
                if let strongSelf = strongSelf {
                    strongSelf.executePeriodicUpdate(time)
                }
            })
            if ProcessInfo.processInfo.isOperatingSystemAtLeast(OperatingSystemVersion(majorVersion: 10, minorVersion: 0, patchVersion: 0)) {
                //_avQueuePlayer.automaticallyWaitsToMinimizeStalling = NO;
            }
            // Put this behind a flag.
        }

        return _avQueuePlayer
    }

    private var currentItems: [AnyHashable]? {
        return avQueuePlayer?.itemsForPlayer
    }

    private var _isAtEnd = false
    private var isAtEnd: Bool {
        return avQueuePlayer?.isAtEnd() ?? false
    }

    private var _isAtBeginning = false
    private var isAtBeginning: Bool {
        return avQueuePlayer?.isAtBeginning() ?? false
    }

    private var _isPlaying = false
    private var isPlaying: Bool {
        return avQueuePlayer?.isPlaying ?? false
    }

    private var queuePosition: Float {
        if let av = avQueuePlayer?.currentTimeOffsetInQueue() {
            return Float(CMTimeGetSeconds(av))
        }
        return 0.0
    }
    // structural methods
    init() {
        playbackTimeObserver = nil
        wasPlayingInterrupted = false
        commandCenterRegistered = false
        updatedNowPlayingInfo = nil
        resetStreamOnPause = true
        isReplacingItems = false
        isWaitingToStartPlayback = false
        loop = false

        activateAudioSession()
        observeLifeCycle()
    }

    func setOptions(_ options: [String:Any]) {
        print("RmxAudioPlayer.execute=setOptions, \(options)")
        resetStreamOnPause = (options["resetStreamOnPause"] as? NSNumber)?.boolValue ?? false
    }

    func initialize() {
        print("RmxAudioPlayer.execute=initialize")
        onStatus(.rmxstatus_REGISTER, trackId: "INIT", param: nil)
    }
    
    func release() {
        print("RmxAudioPlayer.execute=initialize")
        _avQueuePlayer = nil
    }

    // public API

    // Item managementString:Any
    func setPlaylistItems(_ items: [[String:Any]], options: [String:Any]) {
        print("RmxAudioPlayer.execute=setPlaylistItems, \(options), \(items)")

        var seekToPosition: Float = 0.0
        let retainPosition = Bool(options["retainPosition"] != nil ? (options["retainPosition"] as? NSNumber)?.boolValue ?? false : false)
        let playFromPosition = options["playFromPosition"] != nil ? (options["playFromPosition"] as? NSNumber)?.floatValue ?? 0.0 : 0.0

        let playFromId = ((options["playFromId"] != nil) ? options["playFromId"] : nil) as? String

        let startPaused = Bool(options["startPaused"] != nil ? (options["startPaused"] as? NSNumber)?.boolValue ?? false : true)

        if retainPosition {
            seekToPosition = getTrackCurrentTime(nil)
            if playFromPosition > 0.0 {
                seekToPosition = playFromPosition
            }
        }

        let result = findTrack(byId: playFromId)
        let idx = (result?["index"] as? NSNumber)?.intValue ?? 0
        // AudioTrack* track = result[@"track"];

        if (avQueuePlayer?.itemsForPlayer?.count ?? 0) > 0 {
            if idx >= 0 {
                avQueuePlayer?.currentIndex() = idx
            }
        }
        commandDelegate.run(inBackground: { [self] in
            // This will wait for the AVPlayerItemStatusReadyToPlay status change, and then trigger playback.
            isWaitingToStartPlayback = !startPaused
            if isWaitingToStartPlayback {
                print("RmxAudioPlayer[setPlaylistItems] will wait for ready event to begin playback")
            }

            insertOrReplaceTracks(items, replace: true, startPosition: seekToPosition)
            if isWaitingToStartPlayback {
                playCommand(false) // but we will try to preempt it to avoid the button blinking paused.
            }

            let result = CDVPluginResult(status: CDVCommandStatus_OK)
            commandDelegate.send(result, callbackId: command?.callbackId)
        })
    }

    func addItem(_ command: CDVInvokedUrlCommand?) {
        let item = command?.arguments[0] as? [AnyHashable : Any]

        print("RmxAudioPlayer.execute=addItem, \(item ?? [:])")

        let newTrack = AudioTrack.initWithDictionary(item)
        if let newTrack = newTrack {
            var tempArr = [newTrack]
            addTracks(&tempArr)
        }

        let result = CDVPluginResult(status: CDVCommandStatus_OK)
        commandDelegate.send(result, callbackId: command?.callbackId)
    }

    func addAllItems(_ command: CDVInvokedUrlCommand?) {
        let items = command?.arguments[0] as? [AnyHashable]
        print("RmxAudioPlayer.execute=addAllItems, \(items ?? [])")

        insertOrReplaceTracks(items, replace: false, startPosition: -1)

        let result = CDVPluginResult(status: CDVCommandStatus_OK)
        commandDelegate.send(result, callbackId: command?.callbackId)
    }

    func removeItem(_ command: CDVInvokedUrlCommand?) {
        let trackIndex = command?.arguments[0] as? String
        let trackId = command?.arguments[1] as? String

        print("RmxAudioPlayer.execute=removeItem, \(trackId ?? ""), \(trackIndex ?? "")")

        let success = removeItem(withValues: trackIndex, trackId: trackId)

        let result = CDVPluginResult(status: CDVCommandStatus_OK, messageAs: success)
        commandDelegate.send(result, callbackId: command?.callbackId)
    }

    func removeItems(_ command: CDVInvokedUrlCommand?) {
        var items = command?.arguments[0] as? [AnyHashable]
        print("RmxAudioPlayer.execute=removeItems, \(items ?? [])")

        let removed = 0
        if items != nil || (items?.count ?? 0) > 0 {
            for item in items ?? [] {
                guard let item = item as? [AnyHashable : Any] else {
                    continue
                }
                let trackIndex = item["trackIndex"] as? String
                let trackId = item["trackId"] as? String

                if removeItem(withValues: trackIndex, trackId: trackId) {
                    removed += 1
                }
            }
        }

        let result = CDVPluginResult(status: CDVCommandStatus_OK, messageAs: removed)
        commandDelegate.send(result, callbackId: command?.callbackId)
    }

    func clearAllItems(_ command: CDVInvokedUrlCommand?) {
        print("RmxAudioPlayer.execute=clearAllItems")
        removeAllTracks(false)

        let result = CDVPluginResult(status: CDVCommandStatus_OK)
        commandDelegate.send(result, callbackId: command?.callbackId)
    }

    // Playback management
    func play(_ command: CDVInvokedUrlCommand?) {
        print("RmxAudioPlayer.execute=play")
        playCommand(false)

        let result = CDVPluginResult(status: CDVCommandStatus_OK)
        commandDelegate.send(result, callbackId: command?.callbackId)
    }

    func playTrack(byIndex command: CDVInvokedUrlCommand?) {
        let argVal = command?.argument(atIndex: 0, withDefault: NSNumber(value: 0))

        print("RmxAudioPlayer.execute=playTrackByIndex, \(argVal ?? 0)")
        let index = argVal?.intValue ?? 0

        if index < 0 || index >= (avQueuePlayer?.itemsForPlayer?.count ?? 0) {
            let result = CDVPluginResult(status: CDVCommandStatus_ERROR, messageAsString: "Provided index is out of bounds")
            commandDelegate.send(result, callbackId: command?.callbackId)
        } else {
            avQueuePlayer?.currentIndex() = argVal?.intValue ?? 0
            playCommand(false)

            let argVal1 = command?.argument(atIndex: 1, withDefault: NSNumber(value: 0.0))
            let positionTime = argVal1?.floatValue ?? 0.0
            seek(to: positionTime, isCommand: false)

            let result = CDVPluginResult(status: CDVCommandStatus_OK)
            commandDelegate.send(result, callbackId: command?.callbackId)
        }

        let argVal1 = command?.argument(atIndex: 1, withDefault: NSNumber(value: 0.0))

        let positionTime = argVal1?.floatValue ?? 0.0
        seek(to: positionTime, isCommand: false)
    }

    func playTrack(byId command: CDVInvokedUrlCommand?) {
        let trackId = command?.arguments[0] as? String
        print("RmxAudioPlayer.execute=playTrackById, \(trackId ?? "")")

        let result = findTrack(byId: trackId)
        let idx = (result?["index"] as? NSNumber)?.intValue ?? 0
        // AudioTrack* track = result[@"track"];

        if (avQueuePlayer?.itemsForPlayer?.count ?? 0) > 0 {
            if idx >= 0 {
                avQueuePlayer?.currentIndex() = idx
                playCommand(false)

                let argVal = command?.argument(atIndex: 1, withDefault: NSNumber(value: 0.0))
                let positionTime = argVal?.floatValue ?? 0.0
                seek(to: positionTime, isCommand: false)

                let result = CDVPluginResult(status: CDVCommandStatus_OK, messageAsNSUInteger: idx)
                commandDelegate.send(result, callbackId: command?.callbackId)
            } else {
                let result = CDVPluginResult(status: CDVCommandStatus_ERROR, messageAsString: "Track ID not found")
                commandDelegate.send(result, callbackId: command?.callbackId)
            }
        } else {
            let result = CDVPluginResult(status: CDVCommandStatus_ERROR, messageAsString: "The playlist is empty!")
            commandDelegate.send(result, callbackId: command?.callbackId)
        }
    }

    func pause(_ command: CDVInvokedUrlCommand?) {
        print("RmxAudioPlayer.execute=pause")
        isWaitingToStartPlayback = false
        pauseCommand(false)

        let result = CDVPluginResult(status: CDVCommandStatus_OK)
        commandDelegate.send(result, callbackId: command?.callbackId)
    }

    func skipForward(_ command: CDVInvokedUrlCommand?) {
        print("RmxAudioPlayer.execute=skipForward")
        playNext(false)

        let result = CDVPluginResult(status: CDVCommandStatus_OK)
        commandDelegate.send(result, callbackId: command?.callbackId)
    }

    func skipBack(_ command: CDVInvokedUrlCommand?) {
        print("RmxAudioPlayer.execute=skipBack")
        playPrevious(false)

        let result = CDVPluginResult(status: CDVCommandStatus_OK)
        commandDelegate.send(result, callbackId: command?.callbackId)
    }

    func seek(to command: CDVInvokedUrlCommand?) {
        let argVal = command?.argument(atIndex: 0, withDefault: NSNumber(value: 0.0))
        print("RmxAudioPlayer.execute=seekTo, \(argVal ?? 0)")

        let positionTime = argVal?.floatValue ?? 0.0
        seek(to: positionTime, isCommand: true)

        let result = CDVPluginResult(status: CDVCommandStatus_OK)
        commandDelegate.send(result, callbackId: command?.callbackId)
    }

    func seek(toQueuePosition command: CDVInvokedUrlCommand?) {
        let argVal = command?.argument(atIndex: 0, withDefault: NSNumber(value: 0.0))
        print("RmxAudioPlayer.execute=seekToQueuePosition, \(argVal ?? 0)")

        let positionTime = argVal?.floatValue ?? 0.0

        avQueuePlayer?.seekToTime(inQueue: CMTimeMakeWithSeconds(Float64(positionTime), preferredTimescale: Int32(Double(NSEC_PER_SEC))), completionHandler: { [self] complete in
            // I guess we could check if the seek actually succeeded.
            let result = CDVPluginResult(status: CDVCommandStatus_OK)
            commandDelegate.send(result, callbackId: command?.callbackId)
        })
    }

    func setPlaybackRate(_ command: CDVInvokedUrlCommand?) {
        let argVal = command?.argument(atIndex: 0, withDefault: NSNumber(value: 1.0))
        print("RmxAudioPlayer.execute=setPlaybackRate, \(argVal ?? 0)")

        = argVal?.floatValue ?? 0.0

        let result = CDVPluginResult(status: CDVCommandStatus_OK)
        commandDelegate.send(result, callbackId: command?.callbackId)
    }

    func setPlaybackVolume(_ command: CDVInvokedUrlCommand?) {
        let argVal = command?.argument(atIndex: 0, withDefault: NSNumber(value: Float( ?? 0.0)))
        print("RmxAudioPlayer.execute=setPlaybackVolume, \(argVal ?? 0)")

        = argVal?.floatValue ?? 0.0

        let result = CDVPluginResult(status: CDVCommandStatus_OK)
        commandDelegate.send(result, callbackId: command?.callbackId)
    }

    func setLoopAll(_ command: CDVInvokedUrlCommand?) {
        let loop2 = command?.argument(atIndex: 0)
        if ((loop2 is NSString) && (loop2 == "true")) || loop2?.boolValue ?? false {
            loop = true
        } else {
            loop = false
        }
        if let loop2 = loop2 {
            print("RmxAudioPlayer.execute=setLoopAll, \(loop2)")
        }

        let result = CDVPluginResult(status: CDVCommandStatus_OK)
        commandDelegate.send(result, callbackId: command?.callbackId)
    }

    // Get accessors to manually update values. Note:
    // these values are reported anyway via the onStatus event
    // stream, you don't normally need to read these directly.
    func getPlaybackRate(_ command: CDVInvokedUrlCommand?) {
        print("RmxAudioPlayer.execute=getPlaybackRate, \(self.rate())")
        let rate = self.rate()

        let result = CDVPluginResult(status: CDVCommandStatus_OK, messageAsDouble: rate)
        commandDelegate.send(result, callbackId: command?.callbackId)
    }

    func getPlaybackVolume(_ command: CDVInvokedUrlCommand?) {
        print("RmxAudioPlayer.execute=getPlaybackVolume, \(self.volume())")
        let volume = self.volume()

        let result = CDVPluginResult(status: CDVCommandStatus_OK, messageAsDouble: volume)
        commandDelegate.send(result, callbackId: command?.callbackId)
    }

    func getPlaybackPosition(_ command: CDVInvokedUrlCommand?) {
        let currentPosition = getTrackCurrentTime(nil)
        print("RmxAudioPlayer.execute=getPlaybackPosition, \(currentPosition)")

        let result = CDVPluginResult(status: CDVCommandStatus_OK, messageAsDouble: currentPosition)
        commandDelegate.send(result, callbackId: command?.callbackId)
    }

    func getCurrentBuffer(_ command: CDVInvokedUrlCommand?) {
        let trackStatus = getStatusItem(nil)
        print("RmxAudioPlayer.execute=getCurrentBuffer, \(trackStatus ?? [:])")

        let result = CDVPluginResult(status: CDVCommandStatus_OK, messageAsDictionary: trackStatus)
        commandDelegate.send(result, callbackId: command?.callbackId)
    }

    func getQueuePosition(_ command: CDVInvokedUrlCommand?) {
        let position = queuePosition
        print("RmxAudioPlayer.execute=getQueuePosition, \(position)")

        let result = CDVPluginResult(status: CDVCommandStatus_OK, messageAsDouble: position)
        commandDelegate.send(result, callbackId: command?.callbackId)
    }

    // Cleanup
    func release(_ command: CDVInvokedUrlCommand?) {
        print("RmxAudioPlayer.execute=release")
        isWaitingToStartPlayback = false
        releaseResources()

        let result = CDVPluginResult(status: CDVCommandStatus_OK)
        commandDelegate.send(result, callbackId: command?.callbackId)
    }


// MARK: - Cordova interface

    ///
    /// Cordova interface
    ///
    /// These are basically just passing through to the core functionality of the queue and this player.
    ///
    /// These functions don't really do anything interesting by themselves.
    ///
    ///
    ///
    ///
    ///
    func selectTrack(byIndex command: CDVInvokedUrlCommand?) {
        let argVal = command?.argument(atIndex: 0, withDefault: NSNumber(value: 0))
        print("RmxAudioPlayer.execute=selectTrackByIndex, \(argVal ?? 0)")

        let index = argVal?.intValue ?? 0

        if index < 0 || index >= (avQueuePlayer?.itemsForPlayer?.count ?? 0) {
            let result = CDVPluginResult(status: CDVCommandStatus_ERROR, messageAsString: "Provided index is out of bounds")
            commandDelegate.send(result, callbackId: command?.callbackId)
        } else {
            avQueuePlayer?.currentIndex() = argVal?.intValue ?? 0
            playCommand(false)
            let result = CDVPluginResult(status: CDVCommandStatus_OK)
            commandDelegate.send(result, callbackId: command?.callbackId)
        }
    }

    func selectTrack(byId command: CDVInvokedUrlCommand?) {
        let trackId = command?.arguments[0] as? String
        print("RmxAudioPlayer.execute=selectTrackById, \(trackId ?? "")")

        let result = findTrack(byId: trackId)
        let idx = (result?["index"] as? NSNumber)?.intValue ?? 0
        // AudioTrack* track = result[@"track"];

        if (avQueuePlayer?.itemsForPlayer?.count ?? 0) > 0 {
            if idx >= 0 {
                avQueuePlayer?.currentIndex() = idx
            }
        }

        commandDelegate.run(inBackground: { [self] in
            let result = CDVPluginResult(status: CDVCommandStatus_OK)
            commandDelegate.send(result, callbackId: command?.callbackId)
        })
    }

    // ******
    // Utilities for the above functions
    func insertOrReplaceTracks(_ items: [AnyHashable]?, replace: Bool, startPosition: Float) {
        if items == nil || items?.count == 0 {
            return
        }

        var newList = [AnyHashable](repeating: 0, count: items?.count ?? 0)
        for item in items ?? [] {
            guard let item = item as? [AnyHashable : Any] else {
                continue
            }
            let track = AudioTrack.initWithDictionary(item)
            if let track = track {
                newList.append(track)
            }
        }

        if replace {
            setTracks(&newList, startPosition: startPosition)
        } else {
            addTracks(&newList)
        }
    }

    func removeItem(withValues trackIndex: String?, trackId: String?) -> Bool {
        if (trackIndex as? NSNull) != NSNull() && Int(trackIndex ?? "") ?? 0 > 0 && Int(trackIndex ?? "") ?? 0 < (avQueuePlayer?.itemsForPlayer?.count ?? 0) {
            let item = avQueuePlayer?.itemsForPlayer?[Int(trackIndex ?? "") ?? 0] as? AudioTrack
            removeTrackObservers(item)
            if let item = item {
                avQueuePlayer?.remove(item)
            }
            return true
        } else if (trackId as? NSNull) != NSNull() && !(trackId == "") {
            let result = findTrack(byId: trackId)
            let idx = (result?["index"] as? NSNumber)?.intValue ?? 0
            let track = result?["track"] as? AudioTrack

            if idx >= 0 {
                // AudioTrack* item = [self avQueuePlayer].itemsForPlayer[idx];
                removeTrackObservers(track)
                if let track = track {
                    avQueuePlayer?.remove(track)
                }
                return true
            } else {
                return false
            }
        } else {
            return false
        }
    }

// MARK: - player actions

    ///
    /// Player actions.
    ///
    /// These are the public API for the player and wrap most of the complexity of the queue.
    ///
    ///
    ///
    ///
    ///
    ///
    ///
    func playCommand(_ isCommand: Bool) {
        wasPlayingInterrupted = false
        initializeMPCommandCenter()
        // [[self avQueuePlayer] play];

        if resetStreamOnPause {
            let currentTrack = avQueuePlayer?.currentItem as? AudioTrack
            if currentTrack != nil && currentTrack?.isStream ?? false {
                avQueuePlayer?.seek(to: .positiveInfinity, toleranceBefore: .zero, toleranceAfter: .zero)
                currentTrack?.seek(to: .positiveInfinity, toleranceBefore: .zero, toleranceAfter: .zero, completionHandler: nil)
            }
        }

        avQueuePlayer?. =
        avQueuePlayer?. =

        if isCommand {
            let action = "music-controls-play"
            print("\(action)")
        }
    }

    func pauseCommand(_ isCommand: Bool) {
        wasPlayingInterrupted = false
        initializeMPCommandCenter()
        avQueuePlayer?.pause()

        // When the track is a stream, we do not want it to hold the buffer at the current location;
        // it does in fact continue buffering afterwards but the buffer on iOS is rather small, so you'll end up
        // reaching a point where you jump forward in time however long you were paused.
        // The correct behavior for streams is to pick up at the current LIVE point in the stream, which we accomplish
        // by seeking to the "end" of the stream.
        if resetStreamOnPause {
            let currentTrack = avQueuePlayer?.currentItem as? AudioTrack
            if currentTrack != nil && currentTrack?.isStream ?? false {
                avQueuePlayer?.seek(to: .positiveInfinity, toleranceBefore: .zero, toleranceAfter: .zero)
                currentTrack?.seek(to: .positiveInfinity, toleranceBefore: .zero, toleranceAfter: .zero, completionHandler: nil)
            }
        }

        if isCommand {
            let action = "music-controls-pause"
            print("\(action)")
        }
    }

    func playPrevious(_ isCommand: Bool) {
        wasPlayingInterrupted = false
        initializeMPCommandCenter()

        avQueuePlayer?.playPreviousItem()

        if isCommand {
            let action = "music-controls-previous"
            print("\(action)")

            let playerItem = avQueuePlayer?.currentItem as? AudioTrack
            var param: [StringLiteralConvertible : NSNumber]? = nil
            if let to = playerItem?.toDict() {
                param = [
                    "currentIndex": NSNumber(value: avQueuePlayer?.currentIndex() ?? 0),
                    "currentItem": to
                ]
            }
            onStatus(.rmx_STATUS_SKIP_BACK, trackId: playerItem?.trackId, param: param)
        }
    }

    func playNext(_ isCommand: Bool) {
        wasPlayingInterrupted = false
        initializeMPCommandCenter()

        avQueuePlayer?.advanceToNextItem()

        if isCommand {
            let action = "music-controls-next"
            print("\(action)")

            let playerItem = avQueuePlayer?.currentItem as? AudioTrack
            var param: [StringLiteralConvertible : NSNumber]? = nil
            if let to = playerItem?.toDict() {
                param = [
                    "currentIndex": NSNumber(value: avQueuePlayer?.currentIndex() ?? 0),
                    "currentItem": to
                ]
            }
            onStatus(.rmx_STATUS_SKIP_FORWARD, trackId: playerItem?.trackId, param: param)
        }
    }

    func seek(to positionTime: Float, isCommand: Bool) {
        //Handle seeking with the progress slider on lockscreen or control center
        wasPlayingInterrupted = false
        initializeMPCommandCenter()

        let seekToTime = CMTimeMakeWithSeconds(Float64(positionTime), preferredTimescale: 1000)
        avQueuePlayer?.seek(to: seekToTime, toleranceBefore: .zero, toleranceAfter: .zero)

        let action = "music-controls-seek-to"
        print(String(format: "%@ %.3f", action, positionTime))

        if isCommand {
            let playerItem = avQueuePlayer?.currentItem as? AudioTrack
            onStatus(.rmxstatus_SEEK, trackId: playerItem?.trackId, param: [
                "position": NSNumber(value: positionTime)
            ])
        }
    }

    func rate() -> Float {
        return Float( ?? 0.0)
    }

    func setRate(_ rate: Float) {
        = rate
        avQueuePlayer?.rate = rate
    }

    func volume() -> Float {
        return Float( ?? 0.0)
    }

    func setVolume(_ volume: Float) {
        = volume
        avQueuePlayer?.volume = volume
    }

    func addTracks(_ tracks: inout [AudioTrack]) {
        for playerItem in tracks {
            guard let playerItem = playerItem as? AudioTrack else {
                continue
            }
            addTrackObservers(playerItem)
        }

        avQueuePlayer?.insertAllItems(&tracks)
    }

    func setTracks(_ tracks: inout [AudioTrack], startPosition: Float) {
        for item in avQueuePlayer?.itemsForPlayer ?? [] {
            guard let item = item as? AudioTrack else {
                continue
            }
            removeTrackObservers(item)
        }

        for playerItem in tracks {
            guard let playerItem = playerItem as? AudioTrack else {
                continue
            }
            addTrackObservers(playerItem)
        }

        isReplacingItems = true
        avQueuePlayer?.setItemsFor(&tracks)

        if startPosition > 0 {
            seek(to: startPosition, isCommand: false)
        }
    }

    func removeAllTracks(_ isCommand: Bool) {
        for item in avQueuePlayer!.itemsForPlayer ?? [] {
            guard let item = item as? AudioTrack else {
                continue
            }
            removeTrackObservers(item)
        }

        avQueuePlayer?.removeAllItems()
        wasPlayingInterrupted = false

        print("RmxAudioPlayer, removeAllTracks, ==> RMXSTATUS_PLAYLIST_CLEARED")
        onStatus(.rmxstatus_PLAYLIST_CLEARED, trackId: "INVALID", param: nil)

        // a.t.m there's no way for this to be triggered from within the plugin,
        // but it might get added at some point.
        if isCommand {
            let action = "music-controls-clear"
            print("\(action)")
        }
    }

// MARK: - remote control events

    ///
    /// Events - receive events from the iOS remote controls and command center.
    ///
    ///
    ///
    ///
    ///
    ///
    ///
    ///
    @objc func play(_ event: MPRemoteCommandEvent?) -> MPRemoteCommandHandlerStatus {
        playCommand(true)
        return .success
    }

    @objc func pause(_ event: MPRemoteCommandEvent?) -> MPRemoteCommandHandlerStatus {
        pauseCommand(true)
        return .success
    }

    @objc func togglePlayPauseTrackEvent(_ event: MPRemoteCommandEvent?) -> MPRemoteCommandHandlerStatus {
        if avQueuePlayer?.isPlaying ?? false {
            pauseCommand(true)
        } else {
            playCommand(true)
        }
        return .success
    }

    @objc func prevTrackEvent(_ event: MPRemoteCommandEvent?) -> MPRemoteCommandHandlerStatus {
        playPrevious(true)
        return .success
    }

    @objc func nextTrackEvent(_ event: MPRemoteCommandEvent?) -> MPRemoteCommandHandlerStatus {
        playNext(true)
        return .success
    }

    @objc func changedThumbSlider(onLockScreen event: MPChangePlaybackPositionCommandEvent?) -> MPRemoteCommandHandlerStatus {
        seek(to: Float(event?.positionTime ?? 0.0), isCommand: true)
        return .success
    }

// MARK: - notifications

    ///
    /// Notifications
    ///
    /// These handle the events raised by the queue and the player items.
    ///
    ///
    ///
    ///
    ///
    ///
    ///
    @objc func itemStalledPlaying(_ notification: Notification?) {
        // This happens when the network is insufficient to continue playback.
        let playerItem = avQueuePlayer?.currentItem as? AudioTrack
        let trackStatus = getStatusItem(playerItem)
        onStatus(.rmxstatus_STALLED, trackId: playerItem?.trackId, param: trackStatus)
        onStatus(.rmxstatus_PAUSE, trackId: playerItem?.trackId, param: trackStatus)
    }

    @objc func playerItemDidReachEnd(_ notification: Notification?) {
        if let object = notification?.object {
            print("Player item reached end: \(object)")
        }
        let playerItem = notification?.object as? AudioTrack
        // When an item finishes, immediately scrub it back to the beginning
        // so that the visual indicators show you can "play again" or whatever.
        // Might make sense to have a flag for this behavior.
        playerItem?.seek(to: .zero, toleranceBefore: .zero, toleranceAfter: .zero, completionHandler: nil)

        let trackStatus = getStatusItem(playerItem)
        onStatus(.rmxstatus_COMPLETED, trackId: playerItem?.trackId, param: trackStatus)
    }

    @objc func handleAudioSessionInterruption(_ interruptionNotification: Notification?) {
        if let interruptionNotification = interruptionNotification {
            print("Audio session interruption received: \(interruptionNotification)")
        }

        let userInfo = interruptionNotification?.userInfo
        let interruptionType = (userInfo?[AVAudioSessionInterruptionTypeKey] as? NSNumber)?.uintValue ?? 0

        switch interruptionType {
            case AVAudioSessionInterruptionTypeBegan:
                let suspended = (userInfo?[AVAudioSessionInterruptionWasSuspendedKey] as? NSNumber)?.boolValue ?? false
                print("AVAudioSessionInterruptionTypeBegan. Was suspended: \(suspended)")
                if avQueuePlayer?.isPlaying ?? false {
                    wasPlayingInterrupted = true
                }

                // [[self avQueuePlayer] pause];
                pauseCommand(false)
            case AVAudioSessionInterruptionTypeEnded:
                print("AVAudioSessionInterruptionTypeEnded")
                let interruptionOption = AVAudioSessionInterruptionOptions(rawValue: (userInfo?[AVAudioSessionInterruptionOptionKey] as? NSNumber)?.uintValue ?? 0)
                if interruptionOption == AVAudioSessionInterruptionOptionShouldResume {
                    if wasPlayingInterrupted {
                        avQueuePlayer?.play()
                    }
                }
                wasPlayingInterrupted = false
            default:
                break
        }
    }

    /*
     * This method only executes while the queue is playing, so we can use the playback position event.
     */
    func executePeriodicUpdate(_ time: CMTime) {
        let playerItem = avQueuePlayer?.currentItem as? AudioTrack

        if !CMTIME_IS_INDEFINITE(playerItem?.currentTime()) {
            updateNowPlayingTrackInfo(playerItem, updateTrackData: false)
            if avQueuePlayer?.isPlaying ?? false {
                let trackStatus = getStatusItem(playerItem)
                onStatus(.rmxstatus_PLAYBACK_POSITION, trackId: playerItem?.trackId, param: trackStatus)
                // NSLog(@" . %.5f / %.5f sec (%.1f %%) [%@]", currentTime, duration, (currentTime / duration)*100.0, name);
            }
        }

        return
    }

    func observeValue(forKeyPath keyPath: String?, of object: Any?, change: [String : Any]?, context: UnsafeMutableRawPointer?) {
        if (keyPath == "currentItem") && Int8(from: context ?? 0) == kAvQueuePlayerContext {
            let player = object as? AVBidirectionalQueuePlayer
            let playerItem = player?.currentItem as? AudioTrack
            handleCurrentItemChanged(playerItem)
            return
        }

        if (keyPath == "rate") && Int8(from: context ?? 0) == kAvQueuePlayerRateContext {
            let player = object as? AVBidirectionalQueuePlayer
            let playerItem = player?.currentItem as? AudioTrack

            if playerItem == nil {
                return
            }

            let trackStatus = getStatusItem(playerItem)
            print("Playback rate changed: \(1), is playing: \(player?.isPlaying ?? false)")

            if player?.isPlaying ?? false {
                onStatus(.rmxstatus_PLAYING, trackId: playerItem?.trackId, param: trackStatus as NSObject?)
            } else {
                onStatus(.rmxstatus_PAUSE, trackId: playerItem?.trackId, param: trackStatus as NSObject?)
            }
            return
        }

        if (keyPath == "status") && Int8(from: context ?? 0) == kPlayerItemStatusContext {
            let playerItem = object as? AudioTrack
            handleTrackStatusEvent(playerItem)
            return
        }

        if (keyPath == "duration") && Int8(context ?? 0) == kPlayerItemDurationContext {
            let playerItem = object as? AudioTrack
            handleTrackDuration(playerItem)
            return
        }

        if (keyPath == "loadedTimeRanges") && Int8(context ?? 0) == kPlayerItemTimeRangesContext {
            let playerItem = object as? AudioTrack
            handleTrackBuffering(playerItem)
            return
        }

        super.observeValue(forKeyPath: keyPath, of: object, change: change, context: context)
        return
    }

    func updateNowPlayingTrackInfo(_ playerItem: AudioTrack?, updateTrackData: Bool) {
        var currentItem = playerItem
        if currentItem == nil {
            currentItem = avQueuePlayer?.currentItem as? AudioTrack
        }

        let nowPlayingInfoCenter = MPNowPlayingInfoCenter.default()
        if updatedNowPlayingInfo == nil {
            let nowPlayingInfo = nowPlayingInfoCenter.nowPlayingInfo
            updatedNowPlayingInfo = nowPlayingInfo
        }

        // for (NSString* val in _updatedNowPlayingInfo.allKeys) {
        //     NSLog(@"%@ ==> %@", val, _updatedNowPlayingInfo[val]);
        // }

        var currentTime: Float? = nil
        if let currentTime1 = currentItem?.currentTime() {
            currentTime = Float(CMTimeGetSeconds(currentTime1))
        }
        var duration: Float? = nil
        if let duration1 = currentItem?.duration {
            duration = Float(CMTimeGetSeconds(duration1))
        }
        if CMTIME_IS_INDEFINITE(currentItem?.duration) {
            duration = 0
        }

        if updateTrackData {
            updatedNowPlayingInfo?[MPMediaItemPropertyArtist] = currentItem?.artist
            updatedNowPlayingInfo?[MPMediaItemPropertyTitle] = currentItem?.title
            updatedNowPlayingInfo?[MPMediaItemPropertyAlbumTitle] = currentItem?.album
            let mediaItemArtwork = createCoverArtwork(currentItem?.albumArt?.absoluteString)

            if let mediaItemArtwork = mediaItemArtwork {
                updatedNowPlayingInfo?[MPMediaItemPropertyArtwork] = mediaItemArtwork
            }
        }

        updatedNowPlayingInfo?[MPMediaItemPropertyPlaybackDuration] = NSNumber(value: duration ?? 0.0)
        updatedNowPlayingInfo?[MPNowPlayingInfoPropertyElapsedPlaybackTime] = NSNumber(value: currentTime ?? 0.0)
        updatedNowPlayingInfo?[MPNowPlayingInfoPropertyPlaybackRate] = NSNumber(value: 1.0)

        nowPlayingInfoCenter.nowPlayingInfo = updatedNowPlayingInfo as? [String : Any]

        let commandCenter = MPRemoteCommandCenter.shared()
        commandCenter.nextTrackCommand.isEnabled = !isAtEnd
        commandCenter.previousTrackCommand.isEnabled = !isAtBeginning
    }

    func createCoverArtwork(_ coverUri: String?) -> MPMediaItemArtwork? {
        var coverImage: UIImage? = nil
        if coverUri == nil {
            return nil
        }

        if coverUri?.lowercased().hasPrefix("file://") ?? false {
            let fullCoverImagePath = coverUri?.replacingOccurrences(of: "file://", with: "")

            if FileManager.default.fileExists(atPath: fullCoverImagePath ?? "") {
                coverImage = UIImage(contentsOfFile: fullCoverImagePath ?? "")
            }
        } else if coverUri?.hasPrefix("http://") ?? false || coverUri?.hasPrefix("https://") ?? false {
            let coverImageUrl = URL(string: coverUri ?? "")
            var coverImageData: Data? = nil
            if let coverImageUrl = coverImageUrl {
                coverImageData = Data(contentsOf: coverImageUrl)
            }
            if let coverImageData = coverImageData {
                coverImage = UIImage(data: coverImageData)
            }
        } else if !(coverUri == "") {
            let baseCoverImagePath = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask).map(\.path)[0]
            let fullCoverImagePath = "\(baseCoverImagePath)\(coverUri ?? "")"
            if FileManager.default.fileExists(atPath: fullCoverImagePath) {
                coverImage = UIImage(named: fullCoverImagePath)
            }
        } else {
            coverImage = UIImage(named: "none")
        }
        if let coverImage = coverImage {
            return isCoverImageValid(coverImage) ? MPMediaItemArtwork(image: coverImage) : nil
        }
        return nil
    }

    func isCoverImageValid(_ coverImage: UIImage?) -> Bool {
        return coverImage != nil && (coverImage?.ciImage != nil || coverImage?.cgImage != nil)
    }

    func handleCurrentItemChanged(_ playerItem: AudioTrack?) {
        if let playerItem = playerItem {
            print("Queue changed current item to: \(playerItem.toDict() ?? [:])")
            // NSLog(@"New music name: %@", ((AVURLAsset*)playerItem.asset).URL.pathComponents.lastObject);
            print("New item ID: \(playerItem.trackId ?? "")")
            print("Queue is at end: \(isAtEnd ? "YES" : "NO")")
            print("Queue changed current item to: \(playerItem != nil ? "NOTNIL" : "NIL")")

            print("Queue changed current item to: \(playerItem != nil ? "NOTNIL" : "NIL")")
            // When an item starts, immediately scrub it back to the beginning
            playerItem.seek(to: .zero, toleranceBefore: .zero, toleranceAfter: .zero, completionHandler: nil)
            // Update the command center
            updateNowPlayingTrackInfo(playerItem, updateTrackData: true)
        } else if loop {
            return
        }

        var info: [StringLiteralConvertible : [AnyHashable : Any]?]? = nil
        if let to = playerItem != nil ? playerItem?.toDict() : [:] {
            info = [
                "currentItem": to,
                "currentIndex": NSNumber(value: avQueuePlayer?.currentIndex() ?? 0),
                "isAtEnd": NSNumber(value: isAtEnd),
                "isAtBeginning": NSNumber(value: isAtBeginning),
                "hasNext": NSNumber(value: !isAtEnd),
                "hasPrevious": NSNumber(value: !isAtBeginning)
            ]
        }
        let trackId = playerItem != nil ? playerItem?.trackId : "NONE"
        onStatus(.rmxstatus_TRACK_CHANGED, trackId: trackId, param: info)

        if avQueuePlayer?.isAtEnd() ?? false && avQueuePlayer?.currentItem == nil {
            avQueuePlayer?.seek(to: .zero, toleranceBefore: .zero, toleranceAfter: .zero)

            if (avQueuePlayer?.itemsForPlayer?.count ?? 0) > 0 && !isReplacingItems {
                onStatus(.rmxstatus_PLAYLIST_COMPLETED, trackId: "INVALID", param: nil)
            }

            if loop && (avQueuePlayer?.itemsForPlayer?.count ?? 0) > 0 {
                avQueuePlayer?.play()
            } else {
                onStatus(.rmxstatus_STOPPED, trackId: "INVALID", param: nil)
            }
        }
    }

    func handleTrackStatusEvent(_ playerItem: AudioTrack?) {
        // NSString* name = ((AVURLAsset*)playerItem.asset).URL.pathComponents.lastObject;
        let name = playerItem?.trackId
        let status = playerItem?.status

        // Switch over the status
        switch status {
            case .readyToPlay:
                print("PlayerItem status changed to AVPlayerItemStatusReadyToPlay [\(name ?? "")]")
                let trackStatus = getStatusItem(playerItem)
                onStatus(.rmxstatus_CANPLAY, trackId: playerItem?.trackId, param: trackStatus)

                if isWaitingToStartPlayback {
                    isWaitingToStartPlayback = false
                    print("RmxAudioPlayer[setPlaylistItems] is beginning playback after waiting for ReadyToPlay event")
                    playCommand(false)
                }
            case .failed:
                // Failed. Examine AVPlayerItem.error
                isWaitingToStartPlayback = false
                var errorMsg = ""
                if playerItem?.error != nil {
                    errorMsg = "Error playing audio track: \((playerItem?.error as NSError?)?.localizedFailureReason ?? "")"
                }
                print("AVPlayerItemStatusFailed: Error playing audio track: \(errorMsg)")
                let errorParam = createError(withCode: .rmxerr_DECODE, message: errorMsg)
                onStatus(.rmxstatus_ERROR, trackId: playerItem?.trackId, param: errorParam)
            case .unknown:
                isWaitingToStartPlayback = false
                print("PlayerItem status changed to AVPlayerItemStatusUnknown [\(name ?? "")]")
                // Not ready
            default:
                break
        }
    }

    func handleTrackDuration(_ playerItem: AudioTrack?) {
        // This function isn't all that useful really in terms of state management.
        // It doesn't always fire, and it is not needed because the queue's periodic update can also
        // deliver this info.
        //NSString* name = ((AVURLAsset*)playerItem.asset).URL.pathComponents.lastObject;
        let name = playerItem?.trackId
        if !CMTIME_IS_INDEFINITE(playerItem?.duration) {
            var duration: Float? = nil
            if let duration1 = playerItem?.duration {
                duration = Float(CMTimeGetSeconds(duration1))
            }
            print("The track duration was changed [\(name ?? "")]: \(duration ?? 0.0)")

            // We will still report the duration though.
            let trackStatus = getStatusItem(playerItem)
            onStatus(.rmxstatus_DURATION, trackId: playerItem?.trackId, param: trackStatus)
        } else {
            if let URL = (playerItem?.asset as? AVURLAsset)?.url {
                print("Item duration is indefinite (unknown): \(URL)")
            }
        }
    }

    func handleTrackBuffering(_ playerItem: AudioTrack?) {
        //NSString* name = ((AVURLAsset*)playerItem.asset).URL.pathComponents.lastObject;
        let name = playerItem?.trackId
        let trackStatus = getStatusItem(playerItem)

        print(
            String(format: " . . . %.5f -> %.5f (%.1f %%) [%@]", (trackStatus?["bufferStart"] as? NSNumber)?.floatValue ?? 0.0, (trackStatus?["bufferStart"] as? NSNumber)?.floatValue ?? 0.0 + (trackStatus?["bufferEnd"] as? NSNumber)?.floatValue ?? 0.0, (trackStatus?["bufferPercent"] as? NSNumber)?.floatValue ?? 0.0, name ?? ""))

        onStatus(.rmxstatus_BUFFERING, trackId: playerItem?.trackId, param: trackStatus)

        if (trackStatus?["bufferPercent"] as? NSNumber)?.floatValue ?? 0.0 >= 100.0 {
            onStatus(.rmxstatus_LOADED, trackId: playerItem?.trackId, param: trackStatus)
        }
    }

    ///
    /// Status utilities
    ///
    /// These provide the statis objects and data for the player items when they update.
    ///
    /// It is largely this data that is actually reported to the consumers.
    ///
    ///
    ///
    ///
    ///

    // Not really needed, the dicts do this themselves but, blah.
    func getNumberFor(_ str: String?) -> NSNumber? {
        let f = NumberFormatter()
        f.numberStyle = .decimal
        f.locale = NSLocale.current
        return f.number(from: str ?? "")
    }

    func getStatusItem(_ playerItem: AudioTrack?) -> [AnyHashable : Any]? {
        var currentItem = playerItem
        if currentItem == nil {
            currentItem = avQueuePlayer?.currentItem as? AudioTrack
        }

        if currentItem == nil {
            return nil
        }

        let bufferInfo = getTrackBufferInfo(currentItem)
        var position = getTrackCurrentTime(currentItem)
        let duration = (bufferInfo?["duration"] as? NSNumber)?.floatValue ?? 0.0

        // Correct this value here, so that playbackPercent is not set to INFINITY
        if position.isNaN || position.isInfinite {
            position = 0.0
        }

        let playbackPercent = duration > 0 ? (position / duration) * 100.0 : 0.0

        var status = ""
        if currentItem?.status == .readyToPlay {
            status = "ready"
        } else if currentItem?.status == .failed {
            status = "error"
        } else {
            status = "unknown"
        }

        if avQueuePlayer?.currentItem == currentItem {
            if FloatLiteralConvertible(avQueuePlayer?. ?? 0) != 0.0 {
                status = "playing"

                if position <= 0 && (bufferInfo?["bufferPercent"] as? NSNumber)?.floatValue ?? 0.0 == 0 {
                    status = "loading"
                }
            } else {
                status = "paused"
            }
        }

        let info = [
            "trackId": currentItem?.trackId ?? "",
            "isStream": currentItem?.isStream ?? false ? NSNumber(value: 1) : NSNumber(value: 0),
            "currentIndex": NSNumber(value: avQueuePlayer?.currentIndex() ?? 0),
            "status": status,
            "currentPosition": NSNumber(value: position),
            "duration": NSNumber(value: duration),
            "playbackPercent": NSNumber(value: playbackPercent),
            "bufferPercent": NSNumber(value: (bufferInfo?["bufferPercent"] as? NSNumber)?.floatValue ?? 0.0),
            "bufferStart": NSNumber(value: (bufferInfo?["start"] as? NSNumber)?.floatValue ?? 0.0),
            "bufferEnd": NSNumber(value: (bufferInfo?["end"] as? NSNumber)?.floatValue ?? 0.0)
        ]
        return info
    }

    func getTrackCurrentTime(_ playerItem: AudioTrack?) -> Float {
        var currentItem = playerItem
        if currentItem == nil {
            currentItem = avQueuePlayer?.currentItem as? AudioTrack
        }

        if currentItem == nil {
            return 0.0
        }

        if !CMTIME_IS_INDEFINITE(currentItem?.currentTime()) && CMTIME_IS_VALID(currentItem?.currentTime()) {
            if let currentTime = currentItem?.currentTime() {
                return Float(CMTimeGetSeconds(currentTime))
            }
            return 0.0
        } else {
            return 0.0
        }
    }

    func getTrackBufferInfo(_ playerItem: AudioTrack?) -> [AnyHashable : Any]? {
        if !CMTIME_IS_INDEFINITE(playerItem?.duration) {
            var duration: Float? = nil
            if let duration1 = playerItem?.duration {
                duration = Float(CMTimeGetSeconds(duration1))
            }
            let timeRanges = playerItem?.loadedTimeRanges
            if timeRanges != nil && timeRanges?.count != nil {
                let timerange = timeRanges?[0].timeRangeValue
                var start: Float? = nil
                if let start1 = timerange?.start {
                    start = Float(CMTimeGetSeconds(start1))
                }
                var rangeEnd: Float? = nil
                if let duration1 = timerange?.duration {
                    rangeEnd = Float(CMTimeGetSeconds(duration1))
                }
                let bufferPercent = ((rangeEnd ?? 0.0) / (duration ?? 0.0)) * 100.0

                let bufferInfo = [
                    "start": NSNumber(value: start ?? 0.0),
                    "end": NSNumber(value: rangeEnd ?? 0.0),
                    "bufferPercent": NSNumber(value: bufferPercent),
                    "duration": NSNumber(value: duration ?? 0.0)
                ]
                return bufferInfo
            } else {
                let bufferInfo = [
                    "start": NSNumber(value: 0.0),
                    "end": NSNumber(value: 0.0),
                    "bufferPercent": NSNumber(value: 0.0),
                    "duration": NSNumber(value: duration ?? 0.0)
                ]
                return bufferInfo
            }
        }

        let bufferInfo = [
            "start": NSNumber(value: 0.0),
            "end": NSNumber(value: 0.0),
            "bufferPercent": NSNumber(value: 0.0),
            "duration": NSNumber(value: 0.0)
        ]
        return bufferInfo
    }

// MARK: - plugin initialization

    ///
    /// Object initialization. Mostly boring plumbing to initialize the objects and wire everything up.
    ///
    ///
    ///
    ///
    ///
    ///
    ///
    ///
    func initializeMPCommandCenter() {
        if !commandCenterRegistered {
            let commandCenter = MPRemoteCommandCenter.shared()
            commandCenter.playCommand.isEnabled = true
            commandCenter.playCommand.addTarget(self, action: #selector(play(_:)))
            commandCenter.pauseCommand.isEnabled = true
            commandCenter.pauseCommand.addTarget(self, action: #selector(pause(_:)))
            commandCenter.nextTrackCommand.isEnabled = true
            commandCenter.nextTrackCommand.addTarget(self, action: #selector(nextTrackEvent(_:)))
            commandCenter.previousTrackCommand.isEnabled = true
            commandCenter.previousTrackCommand.addTarget(self, action: #selector(prevTrackEvent(_:)))
            commandCenter.togglePlayPauseCommand.isEnabled = true
            commandCenter.togglePlayPauseCommand.addTarget(self, action: #selector(togglePlayPauseTrackEvent(_:)))

            if #available(iOS 9.0, *) {
                commandCenter.changePlaybackPositionCommand.isEnabled = true
                commandCenter.changePlaybackPositionCommand.addTarget(self, action: #selector(changedThumbSlider(onLockScreen:)))
            }

            commandCenterRegistered = true
        }
    }

    //- (NSUInteger) currentIndex {
    //  return [self avQueuePlayer].currentIndex;
    //}

    func findTrack(byId trackId: String?) -> [AnyHashable : Any]? {
        var idx = -1
        var track: AudioTrack? = nil

        if (avQueuePlayer?.itemsForPlayer?.count ?? 0) > 0 {
            var arr = avQueuePlayer?.itemsForPlayer
            let indexes = (arr as NSArray?)?.indexesOfObjects(passingTest: { obj, idx, stop in
                return (obj as? AudioTrack)?.trackId == trackId
            })

            if (indexes?.count ?? 0) > 0 {
                idx = indexes?.firstIndex ?? 0
                track = arr?[idx] as? AudioTrack
            }
        }

        // nil values are not permitted in NSDictionary; use NSNull instead
        if let null = track ?? NSNull() {
            return [
                "track": null,
                "index": NSNumber(value: idx)
            ]
        }
        return nil
    }

    func addTrackObservers(_ playerItem: AudioTrack?) {
        let options: NSKeyValueObservingOptions = [.old, .new]
        playerItem?.addObserver(self, forKeyPath: "status", options: options, context: UnsafeMutableRawPointer(mutating: &kPlayerItemStatusContext))
        playerItem?.addObserver(self, forKeyPath: "duration", options: options, context: UnsafeMutableRawPointer(mutating: &kPlayerItemDurationContext))
        playerItem?.addObserver(self, forKeyPath: "loadedTimeRanges", options: options, context: UnsafeMutableRawPointer(mutating: &kPlayerItemTimeRangesContext))

        // We don't need this one because we get the currentItem notification from the queue.
        // But we will wire it up anyway...
        let listener = NotificationCenter.default
        listener.addObserver(self, selector: #selector(playerItemDidReachEnd(_:)), name: .AVPlayerItemDidPlayToEndTime, object: playerItem)
        // Subscribe to the AVPlayerItem's PlaybackStalledNotification notification.
        listener.addObserver(self, selector: #selector(itemStalledPlaying(_:)), name: .AVPlayerItemPlaybackStalled, object: playerItem)

        onStatus(.rmxstatus_ITEM_ADDED, trackId: playerItem?.trackId, param: playerItem?.toDict())
        onStatus(.rmxstatus_LOADING, trackId: playerItem?.trackId, param: nil)
    }

    @objc func queueCleared(_ notification: Notification?) {
        isReplacingItems = false
        print("RmxAudioPlayer, queuePlayerCleared")
        onStatus(.rmxstatus_PLAYLIST_CLEARED, trackId: "INVALID", param: nil)
    }

    func removeTrackObservers(_ playerItem: AudioTrack?) {
        playerItem?.removeObserver(self, forKeyPath: "status")
        playerItem?.removeObserver(self, forKeyPath: "duration")
        playerItem?.removeObserver(self, forKeyPath: "loadedTimeRanges")
        NotificationCenter.default.removeObserver(self, name: .AVPlayerItemDidPlayToEndTime, object: playerItem)
        NotificationCenter.default.removeObserver(self, name: .AVPlayerItemPlaybackStalled, object: playerItem)

        onStatus(.rmxstatus_ITEM_REMOVED, trackId: playerItem?.trackId, param: playerItem?.toDict())
    }

    func activateAudioSession() {
        var categoryError: Error? = nil
        let avSession = AVAudioSession.sharedInstance()

        // If no devices are connected, play audio through the default speaker (rather than the earpiece).
        var options: AVAudioSession.CategoryOptions = .defaultToSpeaker

        // If both Bluetooth streaming options are enabled, the low quality stream is preferred; enable A2DP only.
        if #available(iOS 10.0, *) {
            options.insert(.allowBluetoothA2DP)
        } else {
            options.insert(.allowBluetooth)
        }

        do {
            try avSession.setCategory(.playAndRecord, options: options)
        } catch let categoryError {
        }
        if let categoryError = categoryError {
            print("Error setting category! \(categoryError.description())")
        }

        var activationError: Error? = nil
        let success = AVAudioSession.sharedInstance().setActive(true, error: &activationError)

        if !success {
            if let activationError = activationError {
                print("Could not activate audio session. \(activationError.localizedDescription)")
            } else {
                print("audio session could not be activated!")
            }
        }
    }

    /// Register the listener for pause and resume events.
    func observeLifeCycle() {
        let listener = NotificationCenter.default

        // These aren't really needed. the AVQueuePlayer handles this for us.
        // [listener addObserver:self selector:@selector(handleEnterBackground) name:UIApplicationDidEnterBackgroundNotification object:nil];
        // [listener addObserver:self selector:@selector(handleEnterForeground) name:UIApplicationWillEnterForegroundNotification object:nil];

        // We do need these.
        listener.addObserver(self, selector: #selector(handleAudioSessionInterruption(_:)), name: AVAudioSession.interruptionNotification, object: AVAudioSession.sharedInstance())
        listener.addObserver(self, selector: #selector(UIViewController.viewWillDisappear(_:)), name: CDVViewWillDisappearNotification, object: nil)

        // Listen to when the queue player tells us it emptied the items list
        listener.addObserver(self, selector: #selector(queueCleared(_:)), name: NSNotification.Name(AVBidirectionalQueueCleared), object: avQueuePlayer)
    }

    @objc func viewWillDisappear(_ notification: Notification?) {
        // For now, just capture it and log, and see when it triggers,
        // because that would seem to indicate the app closing in this case wouldn't it?
        print("RmxAudioPlayer: viewWillDisappear")
        onStatus(.rmxstatus_VIEWDISAPPEAR, trackId: "WINDOW", param: nil)
    }

    func createError(withCode code: RmxAudioErrorType, message: String?) -> [AnyHashable : Any]? {
        let finalMessage = message ?? ""

        return [
            "code": NSNumber(value: code.rawValue),
            "message": finalMessage ?? ""
        ]
    }

    func onStatus(_ what: RmxAudioStatusMessage, trackId: String?, param: NSObject?) {
        if let statusCallbackId = statusCallbackId {
            var status: [AnyHashable : Any] = [:]
            status["msgType"] = NSNumber(value: what.rawValue)
            // in the error case contains a dict with "code" and "message", otherwise a NSNumber
            if let param = param {
                status["value"] = param
            }
            status["trackId"] = trackId ?? ""

            var dict: [AnyHashable : Any] = [:]
            dict["action"] = "status"
            dict["status"] = status

            let result = CDVPluginResult(status: CDVCommandStatus_OK, messageAsDictionary: dict)
            result.keepCallbackAsBool = true // hold on to this.
            commandDelegate.send(result, callbackId: statusCallbackId)
        }
    }

    ///
    /// Cleanup
    ///
    ///
    ///
    ///
    ///
    ///
    ///
    ///
    func deregisterMusicControlsEventListener() {
        // We don't use the remote control, and no need to remove observer on
        // NSNotificationCenter, that is done automatically
        // [[UIApplication sharedApplication] endReceivingRemoteControlEvents];
        // [[NSNotificationCenter defaultCenter] removeObserver:self name:@"receivedEvent" object:nil];

        let commandCenter = MPRemoteCommandCenter.shared()
        commandCenter.playCommand.removeTarget(self)
        commandCenter.pauseCommand.removeTarget(self)
        commandCenter.nextTrackCommand.removeTarget(self)
        commandCenter.previousTrackCommand.removeTarget(self)
        commandCenter.togglePlayPauseCommand.removeTarget(self)

        // if (floor(NSFoundationVersionNumber) > NSFoundationVersionNumber_iOS_9_0) {
        if #available(iOS 9.0, *) {
            commandCenter.changePlaybackPositionCommand.isEnabled = false
            commandCenter.changePlaybackPositionCommand.removeTarget(self, action: nil)
        }

        commandCenterRegistered = false
    }

    func onMemoryWarning() {
        // override to remove caches, etc
        super.onMemoryWarning()

        // We can't really safely do this without alot of other changes to expect
        // the playlist to be empty.
        // Well, we've just destroyed everything, but ok.
        // [self removeAllTracks:YES];
        // NSLog(@"RmxAudioPlayer, queuePlayerCleared, MEMORY_WARNING");
        // [self onStatus:RMXSTATUS_PLAYLIST_CLEARED trackId:@"INVALID" param:@{@"reason": @"memory-warning"}];
    }

    func onReset() {
        // Override to cancel any long-running requests when the WebView navigates or refreshes.
        super.onReset()
        releaseResources()
    }

    deinit {
        // [super dealloc];
        releaseResources()
    }

    func releaseResources() {
        if let playbackTimeObserver = playbackTimeObserver {
            avQueuePlayer?.removeTimeObserver(playbackTimeObserver)
        }
        avQueuePlayer?.removeObserver(self, forKeyPath: "currentItem")
        avQueuePlayer?.removeObserver(self, forKeyPath: "rate")
        deregisterMusicControlsEventListener()

        // onReset or when killing app:
        if viewController.isFirstResponder {
            viewController.resignFirstResponder()
        }
        removeAllTracks(false)
        avQueuePlayer. = nil

        playbackTimeObserver = nil
    }
}
