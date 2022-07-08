//  Converted to Swift 5.3 by Swiftify v5.3.19197 - https://swiftify.com/
//
// RmxAudioPlayer.swift
// Music Controls Capacitor Plugin
//
// Created by Juan Gonzalez on 12/16/16.
//


import AVFoundation
import Capacitor
import MediaPlayer
import UIKit

extension String: Error {}

final class RmxAudioPlayer: NSObject {

    var statusUpdater: StatusUpdater? = nil

    private var playbackTimeObserver: Any?
    private var wasPlayingInterrupted = false
    private var commandCenterRegistered = false
    private var resetStreamOnPause = false
    private var updatedNowPlayingInfo: [String : Any]?
    private var isReplacingItems = false
    private var isWaitingToStartPlayback = false
    private var loop = false

    private let avQueuePlayer = AVBidirectionalQueuePlayer(items: [])

    private var lastTrackId: String? = nil
    private var lastRate: Float? = nil
    override init() {
        super.init()

        activateAudioSession()
        observeLifeCycle()
    }

    deinit {
        releaseResources()
    }

    func setOptions(_ options: [String:Any]) {
        print("RmxAudioPlayer.execute=setOptions, \(options)")
        resetStreamOnPause = (options["resetStreamOnPause"] as? NSNumber)?.boolValue ?? false
    }

    func initialize() {
        print("RmxAudioPlayer.execute=initialize")

        avQueuePlayer.actionAtItemEnd = .advance
        avQueuePlayer.addObserver(self, forKeyPath: "currentItem", options: .new, context: nil)
        avQueuePlayer.addObserver(self, forKeyPath: "rate", options: .new, context: nil)
        avQueuePlayer.addObserver(self, forKeyPath: "timeControlStatus", options: .new, context: nil)

        let interval = CMTimeMakeWithSeconds(Float64(1.0), preferredTimescale: Int32(Double(NSEC_PER_SEC)))
        playbackTimeObserver = avQueuePlayer.addPeriodicTimeObserver(forInterval: interval, queue: .main, using: { [weak self] time in
            self?.executePeriodicUpdate(time)
        })

        onStatus(.rmxstatus_REGISTER, trackId: "INIT", param: nil)
    }

    func setPlaylistItems(_ items: [AudioTrack], options: [String:Any]) {
        print("RmxAudioPlayer.execute=setPlaylistItems, \(options), \(items)")

        var seekToPosition: Float = 0.0
        let retainPosition = options["retainPosition"] != nil ? (options["retainPosition"] as? Bool) ?? false : false
        let playFromPosition = options["playFromPosition"] != nil ? (options["playFromPosition"] as? Float) ?? 0.0 : 0.0

        let playFromId = ((options["playFromId"] != nil) ? options["playFromId"] : nil) as? String

        let startPaused = options["startPaused"] != nil ? (options["startPaused"] as? Bool) ?? false : true

        if playFromPosition > 0.0 {
            seekToPosition = playFromPosition
        }
        else if retainPosition {
            seekToPosition = getTrackCurrentTime(nil)
        }

        let result = findTrack(byId: playFromId)
        let idx = (result?["index"] as? NSNumber)?.intValue ?? 0

        if !avQueuePlayer.queuedAudioTracks.isEmpty {
            if idx >= 0 {
                avQueuePlayer.setCurrentIndex(idx)
            }
        }

        // This will wait for the AVPlayerItemStatusReadyToPlay status change, and then trigger playback.
        isWaitingToStartPlayback = !startPaused
        if isWaitingToStartPlayback {
            print("RmxAudioPlayer[setPlaylistItems] will wait for ready event to begin playback")
        }

        setTracks(items, startPosition: seekToPosition)
        if isWaitingToStartPlayback {
            playCommand(false) // but we will try to preempt it to avoid the button blinking paused.
        }
    }

    func addItem(_ item: AudioTrack) {
        print("RmxAudioPlayer.execute=addItem, \(item)")

        let tempArr = [item]
        addTracks(tempArr, startPosition: -1)
    }
    func addAllItems(_ items: [AudioTrack]) {
        addTracks(items, startPosition: -1)
    }

    func removeItems(_ items: JSArray) -> Int {
        print("RmxAudioPlayer.execute=removeItems, \(items)")

        var removed = 0
        if items.count > 0 {
            for item in items {
                guard let item = item as? [String: String] else {
                    continue
                }
                if let id = item["trackId"] {
                    do {
                        try removeItem(id)
                        removed += 1
                    } catch {}
                }
                else if let index = Int(item["trackIndex"]!) {
                    do {
                        try removeItem(index)
                        removed += 1
                    } catch {}
                }

            }
        }

        return removed
    }

    func clearAllItems() {
        print("RmxAudioPlayer.execute=clearAllItems")
        removeAllTracks()
    }

    func playTrack(index: Int, positionTime: Float?) throws {
        guard (0..<avQueuePlayer.queuedAudioTracks.count).contains(index) else {
            throw "Provided index is out of bounds"
        }
        
        if avQueuePlayer.currentIndex() != index {
            avQueuePlayer.setCurrentIndex(index)
        }
        playCommand(false)

        if positionTime != nil {
            seek(to: positionTime!, isCommand: false)
        }
    }

    func playTrack(_ trackId: String, positionTime: Float?) throws {
        guard !avQueuePlayer.queuedAudioTracks.isEmpty else {
            throw "The playlist is empty!"
        }
        
        if avQueuePlayer.currentAudioTrack?.trackId != trackId {
            let result = findTrack(byId: trackId)
            let idx = result?["index"] as? Int ?? -1
            guard idx >= 0 else {
                throw "Track ID not found"
            }
            avQueuePlayer.setCurrentIndex(idx)
        }
        playCommand(false)

        if positionTime != nil {
            seek(to: positionTime!, isCommand: false)
        }
    }

    func setPlaybackRate(_ rate: Float) {
        avQueuePlayer.rate = rate
    }

    // Not supporten in IOS ?https://developer.apple.com/documentation/avfoundation/avplayer/1390127-volume
    func setPlaybackVolume(_ volume: Float) {
        avQueuePlayer.volume = volume
    }

    func setLoopAll(_ loop: Bool) {
        self.loop = loop

        print("RmxAudioPlayer.execute=setLoopAll, \(loop)")
    }


    // MARK: - Capacitor interface

    ///
    /// Capacitor interface
    ///
    /// These are basically just passing through to the core functionality of the queue and this player.
    ///
    /// These functions don't really do anything interesting by themselves.
    func selectTrack(index: Int) throws {
        guard index >= 0 || index < avQueuePlayer.queuedAudioTracks.count else {
            throw "Index out of Playlist bounds"
        }
        avQueuePlayer.setCurrentIndex(index)
    }

    func selectTrack(id: String) throws {
        guard !avQueuePlayer.queuedAudioTracks.isEmpty else {
            throw "Queue is Empty"
        }
        let result = findTrack(byId: id)
        let idx = (result?["index"] as? NSNumber)?.intValue ?? 0

        if idx >= 0 {
            avQueuePlayer.setCurrentIndex(idx)
        }
    }

    func removeItem(_ index: Int) throws {
        guard index > -1 && index < avQueuePlayer.queuedAudioTracks.count else {
            throw "Index not found"
        }
        let item = avQueuePlayer.queuedAudioTracks[index]
        removeTrackObservers(item)
        avQueuePlayer.remove(item)
    }

    func removeItem(_ id: String) throws {
        let result = findTrack(byId: id)
        let idx = (result?["index"] as? NSNumber)?.intValue ?? 0
        let track = result?["track"] as? AudioTrack

        guard idx >= 0 else {
            throw "Could not find track by id" + id
        }
        // AudioTrack* item = [self avQueuePlayer].itemsForPlayer[idx];
        removeTrackObservers(track)

        if let track = track {
            avQueuePlayer.remove(track)
        }
    }

    // MARK: - player actions

    ///
    /// Player actions.
    ///
    /// These are the public API for the player and wrap most of the complexity of the queue.
    func playCommand(_ isCommand: Bool) {
        wasPlayingInterrupted = false
        initializeMPCommandCenter()

        if resetStreamOnPause,
           let currentTrack = avQueuePlayer.currentAudioTrack,
           currentTrack.isStream {
            print( "music-stream-play")
            avQueuePlayer.seek(to: .positiveInfinity, toleranceBefore: .zero, toleranceAfter: .zero)
            currentTrack.seek(to: .positiveInfinity, toleranceBefore: .zero, toleranceAfter: .zero, completionHandler: nil)
        }

            print( "music-controls-play ")
        
        avQueuePlayer.play()
    }

    func pauseCommand(_ isCommand: Bool) {
        wasPlayingInterrupted = false
        initializeMPCommandCenter()
        avQueuePlayer.pause()

        // When the track is a stream, we do not want it to hold the buffer at the current location;
        // it does in fact continue buffering afterwards but the buffer on iOS is rather small, so you'll end up
        // reaching a point where you jump forward in time however long you were paused.
        // The correct behavior for streams is to pick up at the current LIVE point in the stream, which we accomplish
        // by seeking to the "end" of the stream.
        if resetStreamOnPause,
           let currentTrack = avQueuePlayer.currentAudioTrack,
           currentTrack.isStream {
            avQueuePlayer.seek(to: .positiveInfinity, toleranceBefore: .zero, toleranceAfter: .zero)
            currentTrack.seek(to: .positiveInfinity, toleranceBefore: .zero, toleranceAfter: .zero, completionHandler: nil)
        }

        if isCommand {
            let action = "music-controls-pause"
            print("\(action)")
        }
    }

    func playPrevious(_ isCommand: Bool) {
        wasPlayingInterrupted = false
        initializeMPCommandCenter()

        avQueuePlayer.playPreviousItem()

        if isCommand {
            let action = "music-controls-previous"
            print("\(action)")

            let playerItem = avQueuePlayer.currentAudioTrack
            var param: [String : Any]? = nil
            if let to = playerItem?.toDict() {
                param = [
                    "currentIndex": NSNumber(value: avQueuePlayer.currentIndex() ?? 0),
                    "currentItem": to
                ]
            }
            onStatus(.rmx_STATUS_SKIP_BACK, trackId: playerItem?.trackId, param: param)
        }
    }

    func playNext(_ isCommand: Bool) {
        wasPlayingInterrupted = false
        initializeMPCommandCenter()

        avQueuePlayer.advanceToNextItem()

        if isCommand {
            let action = "music-controls-next"
            print("\(action)")

            let playerItem = avQueuePlayer.currentAudioTrack
            var param: [String : Any]? = nil
            if let to = playerItem?.toDict() {
                param = [
                    "currentIndex": NSNumber(value: avQueuePlayer.currentIndex() ?? 0),
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
        avQueuePlayer.seek(to: seekToTime, toleranceBefore: .zero, toleranceAfter: .zero)

        let action = "music-controls-seek-to"
        print(String(format: "%@ %.3f", action, positionTime))

        if isCommand {
            let playerItem = avQueuePlayer.currentAudioTrack
            onStatus(.rmxstatus_SEEK, trackId: playerItem?.trackId, param: [
                "position": NSNumber(value: positionTime)
            ])
        }
    }

    func setVolume(_ volume: Float) {
        avQueuePlayer.volume = volume
    }

    func addTracks(_ tracks: [AudioTrack], startPosition: Float) {
        for playerItem in tracks {
            addTrackObservers(playerItem)
        }

        avQueuePlayer.appendItems(tracks)

        if startPosition > 0 {
            seek(to: startPosition, isCommand: false)
        }
    }

    func setTracks(_ tracks: [AudioTrack], startPosition: Float) {
        for item in avQueuePlayer.queuedAudioTracks {
            removeTrackObservers(item)
        }

        for playerItem in tracks {
            addTrackObservers(playerItem)
        }

        isReplacingItems = true
        avQueuePlayer.replaceAllItems(with: tracks)

        if startPosition > 0 {
            seek(to: startPosition, isCommand: false)
        }
    }

    func removeAllTracks() {
        for item in avQueuePlayer.queuedAudioTracks {
            removeTrackObservers(item)
        }

        avQueuePlayer.removeAllItems()
        wasPlayingInterrupted = false

    }

    // MARK: - remote control events

    ///
    /// Events - receive events from the iOS remote controls and command center.
    @objc func play(_ event: MPRemoteCommandEvent?) -> MPRemoteCommandHandlerStatus {
        playCommand(true)
        return .success
    }

    @objc func pause(_ event: MPRemoteCommandEvent?) -> MPRemoteCommandHandlerStatus {
        pauseCommand(true)
        return .success
    }

    @objc func togglePlayPauseTrackEvent(_ event: MPRemoteCommandEvent?) -> MPRemoteCommandHandlerStatus {
        if avQueuePlayer.isPlaying {
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
    @objc func itemStalledPlaying(_ notification: Notification?) {
        // This happens when the network is insufficient to continue playback.
        let playerItem = avQueuePlayer.currentAudioTrack
        let trackStatus = getStatusItem(playerItem)

        onStatus(.rmxstatus_STALLED, trackId: playerItem?.trackId, param: trackStatus)
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
        if (avQueuePlayer.isAtEnd) {
            onStatus(.rmxstatus_PLAYLIST_COMPLETED, trackId: "INVALID", param: nil)
        }
    }

    @objc func handleAudioSessionInterruption(_ interruptionNotification: Notification?) {
        if let interruptionNotification = interruptionNotification {
            print("Audio session interruption received: \(interruptionNotification)")
        }
        guard let userInfo = interruptionNotification?.userInfo,
              let typeValue = userInfo[AVAudioSessionInterruptionTypeKey] as? UInt,
              let interruptionType = AVAudioSession.InterruptionType(rawValue: typeValue) else {
                print("notification.userInfo?[AVAudioSessionInterruptionTypeKey]",
                      interruptionNotification?.userInfo?[AVAudioSessionInterruptionTypeKey] as Any)
              return
        }

        switch interruptionType {
        case AVAudioSession.InterruptionType.began:
            let suspended = (interruptionNotification?.userInfo?[AVAudioSessionInterruptionWasSuspendedKey] as? NSNumber)?.boolValue ?? false
                print("AVAudioSessionInterruptionTypeBegan. Was suspended: \(suspended)")
                if avQueuePlayer.isPlaying {
                    wasPlayingInterrupted = true
                }

                // [[self avQueuePlayer] pause];
                pauseCommand(false)
        case AVAudioSession.InterruptionType.ended:
                print("AVAudioSessionInterruptionTypeEnded")
            guard let optionsValue = userInfo[AVAudioSessionInterruptionOptionKey] as? UInt else { return }
            let options = AVAudioSession.InterruptionOptions(rawValue: optionsValue)
            if options.contains(.shouldResume) {
                if wasPlayingInterrupted {
                    avQueuePlayer.play()
                }
            } else {
                // Interruption ended. Playback should not resume.
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
        guard let playerItem = avQueuePlayer.currentAudioTrack else { return }

        if !CMTIME_IS_INDEFINITE(playerItem.currentTime()) {
            updateNowPlayingTrackInfo(playerItem, updateTrackData: false)
            if avQueuePlayer.isPlaying {
                let trackStatus = getStatusItem(playerItem)
                onStatus(.rmxstatus_PLAYBACK_POSITION, trackId: playerItem.trackId, param: trackStatus)
            }
        }

        return
    }

    override func observeValue(forKeyPath keyPath: String?, of object: Any?, change: [NSKeyValueChangeKey : Any]?, context: UnsafeMutableRawPointer?) {
        guard let change = change else {
            return
        }

        switch keyPath {
        case "currentItem":
            // only fire on real change!
            let player = object as? AVBidirectionalQueuePlayer
            let playerItem = player?.currentAudioTrack
            if playerItem != nil {
            guard self.lastTrackId != playerItem?.trackId else {
                return // todo should call super instead or?
            }
            print("observe change currentItem: lastTrackId \(self.lastTrackId) playerItem: \(playerItem?.trackId)")
            self.lastTrackId = playerItem?.trackId
            handleCurrentItemChanged(playerItem)
            }
            
        case "rate":
            guard lastRate != change[.newKey] as? Float else {
                return // todo should call super instead or?
            }
            self.lastRate = change[.newKey] as? Float
            let player = object as? AVBidirectionalQueuePlayer

            guard let playerItem = player?.currentAudioTrack else { return }

            let trackStatus = getStatusItem(playerItem)
            print("Playback rate changed: \(String(describing: change[.newKey])), is playing: \(player?.isPlaying ?? false)")

            if player?.isPlaying ?? false {
                onStatus(.rmxstatus_PLAYING, trackId: playerItem.trackId, param: trackStatus)
            } else {
                onStatus(.rmxstatus_PAUSE, trackId: playerItem.trackId, param: trackStatus)
            }
        case "status":
            DispatchQueue.main.debounce(interval: 0.2, context: self, action: { [self] in
                let playerItem = object as? AudioTrack
                handleTrackStatusEvent(playerItem)
            })
        case "timeControlStatus":
            let player = object as? AVBidirectionalQueuePlayer

            guard let playerItem = player?.currentAudioTrack else {
                return
            }
            guard lastTrackId != playerItem.trackId || player?.isAtBeginning ?? false else {
                return // todo should call super instead or?
            }

            let trackStatus = getStatusItem(playerItem)
            print("TCSPlayback rate changed: \(String(describing: change[.newKey])), is playing: \(player?.isPlaying ?? false)")

            if player?.timeControlStatus == .playing {
                onStatus(.rmxstatus_PLAYING, trackId: playerItem.trackId, param: trackStatus)
            } else if player?.timeControlStatus == .waitingToPlayAtSpecifiedRate {
                onStatus(.rmxstatus_BUFFERING, trackId: playerItem.trackId, param: trackStatus)
            } else {
                onStatus(.rmxstatus_PAUSE, trackId: playerItem.trackId, param: trackStatus)
            }
        case "duration":
            DispatchQueue.main.debounce(interval: 0.5, context: self, action: { [self] in
                let playerItem = object as? AudioTrack
                handleTrackDuration(playerItem)
            })
        case "loadedTimeRanges":
            let playerItem = object as? AudioTrack
            handleTrackBuffering(playerItem)
        default:
            super.observeValue(forKeyPath: keyPath, of: object, change: change, context: context)
        }
    }

    func updateNowPlayingTrackInfo(_ playerItem: AudioTrack?, updateTrackData: Bool) {
        let currentItem = playerItem ?? avQueuePlayer.currentAudioTrack
        let nowPlayingInfoCenter = MPNowPlayingInfoCenter.default()
        if updatedNowPlayingInfo == nil {
            let nowPlayingInfo = nowPlayingInfoCenter.nowPlayingInfo
            updatedNowPlayingInfo = nowPlayingInfo ?? [:]
        }

        var currentTime: Float? = nil
        if let currentTime1 = currentItem?.currentTime() {
            currentTime = Float(CMTimeGetSeconds(currentTime1))
        }
        var duration: Float? = nil
        if let duration1 = currentItem?.duration {
            duration = Float(CMTimeGetSeconds(duration1))
        }
        if CMTIME_IS_INDEFINITE(currentItem!.duration) {
            duration = 0
        }

        if updateTrackData {
            updatedNowPlayingInfo![MPMediaItemPropertyArtist] = currentItem?.artist
            updatedNowPlayingInfo![MPMediaItemPropertyTitle] = currentItem?.title
            updatedNowPlayingInfo![MPMediaItemPropertyAlbumTitle] = currentItem?.album

            if let mediaItemArtwork = createCoverArtwork(currentItem?.albumArt?.absoluteString) {
                updatedNowPlayingInfo![MPMediaItemPropertyArtwork] = mediaItemArtwork
            }
        }
        updatedNowPlayingInfo![MPMediaItemPropertyPlaybackDuration] = duration ?? 0.0
        updatedNowPlayingInfo![MPNowPlayingInfoPropertyElapsedPlaybackTime] = currentTime ?? 0.0
        updatedNowPlayingInfo![MPNowPlayingInfoPropertyPlaybackRate] = 1.0

        MPNowPlayingInfoCenter.default().nowPlayingInfo = updatedNowPlayingInfo

        let commandCenter = MPRemoteCommandCenter.shared()
        commandCenter.nextTrackCommand.isEnabled = !avQueuePlayer.isAtEnd
        commandCenter.previousTrackCommand.isEnabled = !avQueuePlayer.isAtBeginning
    }

    func createCoverArtwork(_ coverUriOrNil: String?) -> MPMediaItemArtwork? {
        guard let coverUri = coverUriOrNil else {
            return nil
        }
        var coverImage: UIImage? = nil
        if coverUri.hasPrefix("http://") || coverUri.hasPrefix("https://") {
            let coverImageUrl = URL(string: coverUri)!

            do {
                let coverImageData = try Data(contentsOf: coverImageUrl)
                coverImage = UIImage(data: coverImageData)
            } catch {
                print("Error creating the coverImageData");
            }
        } else {
            if FileManager.default.fileExists(atPath: coverUri) {
                coverImage = UIImage(contentsOfFile: coverUri)
            }
        }

        if isCoverImageValid(coverImage) {
            return MPMediaItemArtwork.init(boundsSize: coverImage!.size, requestHandler: { (size) -> UIImage in
                return coverImage!
            })
        }
        return nil;
    }

    func downloadImage(url: URL, completion: @escaping ((_ image: UIImage?) -> Void)){
        print("Started downloading \"\(url.deletingPathExtension().lastPathComponent)\".")
        self.getImageDataFromUrl(url) { (_ data: Data?) in
            DispatchQueue.main.async {
                print("Finished downloading \"\(url.deletingPathExtension().lastPathComponent)\".")
                completion(UIImage(data: data!))
            }
        }
    }

    func getImageDataFromUrl(_ url: URL, completion: @escaping ((_ data: Data?) -> Void)) {
        URLSession.shared.dataTask(with: url) { (data, response, error) in
            completion(data)
        }.resume()
    }

    func isCoverImageValid(_ coverImage: UIImage?) -> Bool {
        return coverImage != nil && (coverImage?.ciImage != nil || coverImage?.cgImage != nil)
    }

    func handleCurrentItemChanged(_ playerItem: AudioTrack?) {
        if let playerItem = playerItem {
            print("Queue changed current item to: \(playerItem.toDict() ?? [:])")
            // NSLog(@"New music name: %@", ((AVURLAsset*)playerItem.asset).URL.pathComponents.lastObject);
            print("New item ID: \(playerItem.trackId ?? "")")
            print("Queue is at end: \(avQueuePlayer.isAtEnd ? "YES" : "NO")")
            // When an item starts, immediately scrub it back to the beginning
            //playerItem.seek(to: .zero, toleranceBefore: .zero, toleranceAfter: .zero, completionHandler: nil)
            // Update the command center
            updateNowPlayingTrackInfo(playerItem, updateTrackData: true)
        } else if loop {
            return
        }

        var info: [String: Any] = [:]
        if let to = playerItem != nil ? playerItem?.toDict() : [:] {
            info = [
                "currentItem": to,
                "currentIndex": NSNumber(value: avQueuePlayer.currentIndex() ?? 0),
                "isAtEnd": NSNumber(value: avQueuePlayer.isAtEnd),
                "isAtBeginning": NSNumber(value: avQueuePlayer.isAtBeginning),
                "hasNext": NSNumber(value: !avQueuePlayer.isAtEnd),
                "hasPrevious": NSNumber(value: !avQueuePlayer.isAtBeginning)
            ]
        }
        let trackId = playerItem != nil ? playerItem?.trackId : "NONE"
        onStatus(.rmxstatus_TRACK_CHANGED, trackId: trackId, param: info)

        if avQueuePlayer.isAtEnd && avQueuePlayer.currentItem == nil {
            if !loop {
                avQueuePlayer.seek(to: .zero, toleranceBefore: .zero, toleranceAfter: .zero)
            }

            if !avQueuePlayer.queuedAudioTracks.isEmpty && !isReplacingItems {
                onStatus(.rmxstatus_PLAYLIST_COMPLETED, trackId: "INVALID", param: nil)
            }

            if loop && !avQueuePlayer.queuedAudioTracks.isEmpty {
                avQueuePlayer.setCurrentIndex(0)
                // not playing here
            } else {
                onStatus(.rmxstatus_STOPPED, trackId: "INVALID", param: nil)
            }
        }
    }

    func handleTrackStatusEvent(_ playerItem: AudioTrack?) {
        guard let playerItem = playerItem else {
            return
        }
        // NSString* name = ((AVURLAsset*)playerItem.asset).URL.pathComponents.lastObject;
        let name = playerItem.trackId
        let status = playerItem.status

        // Switch over the status
        switch status {
            case .readyToPlay:
                print("PlayerItem status changed to AVPlayerItemStatusReadyToPlay [\(name ?? "")]")
                let trackStatus = getStatusItem(playerItem)
                onStatus(.rmxstatus_CANPLAY, trackId: playerItem.trackId, param: trackStatus)

                if isWaitingToStartPlayback {
                    isWaitingToStartPlayback = false
                    print("RmxAudioPlayer[setPlaylistItems] is beginning playback after waiting for ReadyToPlay event")
                    playCommand(false)
                }
            case .failed:
                // Failed. Examine AVPlayerItem.error
                isWaitingToStartPlayback = false
                var errorMsg = ""
                if playerItem.error != nil {
                    print("\(playerItem.error)")
                    errorMsg = "Error playing audio track: \((playerItem.error as NSError?)?.localizedFailureReason ?? "")"
                }
                print("AVPlayerItemStatusFailed: \(errorMsg)")
                let errorParam = createError(withCode: .rmxerr_DECODE, message: errorMsg)
                onStatus(.rmxstatus_ERROR, trackId: playerItem.trackId, param: errorParam)
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

        guard let playerItem = playerItem else { return }

        if !CMTIME_IS_INDEFINITE(playerItem.duration) {
            let duration = CMTimeGetSeconds(playerItem.duration)
            print("The track duration was changed [\(playerItem.trackId ?? "")]: \(duration)")

            // We will still report the duration though.
            let trackStatus = getStatusItem(playerItem)
            onStatus(.rmxstatus_DURATION, trackId: playerItem.trackId, param: trackStatus)
        } else if let url = (playerItem.asset as? AVURLAsset)?.url {
            print("Item duration is indefinite (unknown): \(url)")
        }
    }

    func handleTrackBuffering(_ playerItem: AudioTrack?) {
        //NSString* name = ((AVURLAsset*)playerItem.asset).URL.pathComponents.lastObject;
        let name = playerItem?.trackId
        let trackStatus = getStatusItem(playerItem)

        print(
            String(format: " . . . %.5f -> %.5f (%.1f %%) [%@]",
                   (trackStatus?["bufferStart"] as? NSNumber)?.floatValue ?? 0.0,
                   (trackStatus?["bufferStart"] as? NSNumber)?.floatValue ?? 0.0 + (trackStatus?["bufferEnd"] as? NSNumber)!.floatValue ,
                   (trackStatus?["bufferPercent"] as? NSNumber)?.floatValue ?? 0.0, name ?? ""
            )
        )

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

    // Not really needed, the dicts do this themselves but, blah.
    func getNumberFor(_ str: String?) -> NSNumber? {
        let f = NumberFormatter()
        f.numberStyle = .decimal
        f.locale = NSLocale.current
        return f.number(from: str ?? "")
    }

    func getStatusItem(_ playerItem: AudioTrack?) -> [String : Any]? {
        guard let currentItem = playerItem ?? avQueuePlayer.currentAudioTrack else {
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

        var status: String
        switch currentItem.status {
        case .readyToPlay:
            status = "ready"
        case .failed:
            status = "error"
        default:
            status = "unknown"
        }

        if avQueuePlayer.currentItem == currentItem {
            if avQueuePlayer.rate != 0.0 {
                status = "playing"

                if position <= 0 && (bufferInfo?["bufferPercent"] as? NSNumber)?.floatValue ?? 0.0 == 0.0 {
                    status = "loading"
                }
            } else {
                status = "paused"
            }
        }

        return [
            "trackId": currentItem.trackId ?? "",
            "isStream": currentItem.isStream ? NSNumber(value: 1) : NSNumber(value: 0),
            "currentIndex": NSNumber(value: avQueuePlayer.currentIndex() ?? 0),
            "status": status,
            "currentPosition": NSNumber(value: position),
            "duration": NSNumber(value: duration),
            "playbackPercent": NSNumber(value: playbackPercent),
            "bufferPercent": NSNumber(value: (bufferInfo?["bufferPercent"] as? NSNumber)?.floatValue ?? 0.0),
            "bufferStart": NSNumber(value: (bufferInfo?["start"] as? NSNumber)?.floatValue ?? 0.0),
            "bufferEnd": NSNumber(value: (bufferInfo?["end"] as? NSNumber)?.floatValue ?? 0.0)
        ]
    }

    func getTrackCurrentTime(_ playerItem: AudioTrack?) -> Float {
        guard let currentItem = playerItem ?? avQueuePlayer.currentAudioTrack else {
            return 0
        }

        if !CMTIME_IS_INDEFINITE(currentItem.currentTime()) && CMTIME_IS_VALID(currentItem.currentTime()) {
            return Float(CMTimeGetSeconds(currentItem.currentTime()))
        } else {
            return 0
        }
    }

    func getTrackBufferInfo(_ playerItem: AudioTrack?) -> [String : Any]? {
        guard let playerItem = playerItem, !CMTIME_IS_INDEFINITE(playerItem.duration) else {
            return [
                "start": NSNumber(value: 0.0),
                "end": NSNumber(value: 0.0),
                "bufferPercent": NSNumber(value: 0.0),
                "duration": NSNumber(value: 0.0)
            ]
        }

        let duration = Float(CMTimeGetSeconds(playerItem.duration))
        let timeRanges = playerItem.loadedTimeRanges

        guard !timeRanges.isEmpty else {
            return [
                "start": NSNumber(value: 0.0),
                "end": NSNumber(value: 0.0),
                "bufferPercent": NSNumber(value: 0.0),
                "duration": NSNumber(value: duration)
            ]
        }

        let timerange = timeRanges[0].timeRangeValue
        let start = Float(CMTimeGetSeconds(timerange.start))
        let rangeEnd = Float(CMTimeGetSeconds(timerange.duration))
        let bufferPercent = (rangeEnd / duration) * 100.0

        return [
            "start": NSNumber(value: start),
            "end": NSNumber(value: rangeEnd),
            "bufferPercent": NSNumber(value: bufferPercent),
            "duration": NSNumber(value: duration)
        ]
    }

    // MARK: - plugin initialization

    ///
    /// Object initialization. Mostly boring plumbing to initialize the objects and wire everything up.
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
            commandCenter.changePlaybackPositionCommand.isEnabled = true
            commandCenter.changePlaybackPositionCommand.addTarget(self, action: #selector(changedThumbSlider(onLockScreen:)))

            commandCenterRegistered = true
        }
    }

    func findTrack(byId trackId: String?) -> [String: Any]? {
        let trackInformation: (Int, AudioTrack)? = avQueuePlayer.queuedAudioTracks
            .enumerated()
            .first(where: { _, track in
                track.trackId == trackId
            })

        guard
            let index = trackInformation?.0,
            let track = trackInformation?.1
        else {
            return nil
        }

        return [
            "track": track,
            "index": NSNumber(value: index)
        ]
    }

    func addTrackObservers(_ playerItem: AudioTrack?) {
        let options: NSKeyValueObservingOptions = [.old, .new]
        playerItem?.addObserver(self, forKeyPath: "status", options: options, context: nil)
        playerItem?.addObserver(self, forKeyPath: "duration", options: options, context: nil)
        playerItem?.addObserver(self, forKeyPath: "loadedTimeRanges", options: options, context: nil)

        // We don't need this one because we get the currentItem notification from the queue.
        // But we will wire it up anyway...
        let listener = NotificationCenter.default
        listener.addObserver(self, selector: #selector(playerItemDidReachEnd(_:)), name: .AVPlayerItemDidPlayToEndTime, object: playerItem)
        // Subscribe to the AVPlayerItem's PlaybackStalledNotification notification.
        listener.addObserver(self, selector: #selector(itemStalledPlaying(_:)), name: .AVPlayerItemPlaybackStalled, object: playerItem)

        onStatus(.rmxstatus_ITEM_ADDED, trackId: playerItem?.trackId, param: playerItem?.toDict())
    }

    @objc func queueCleared(_ notification: Notification?) {
        isReplacingItems = false
        print("RmxAudioPlayer, queuePlayerCleared")
        onStatus(.rmxstatus_PLAYLIST_CLEARED, trackId: "INVALID", param: nil)
    }

    func removeTrackObservers(_ playerItem: AudioTrack?) {
        NotificationCenter.default.removeObserver(self, name: .AVPlayerItemDidPlayToEndTime, object: playerItem)
        NotificationCenter.default.removeObserver(self, name: .AVPlayerItemPlaybackStalled, object: playerItem)

        onStatus(.rmxstatus_ITEM_REMOVED, trackId: playerItem?.trackId, param: playerItem?.toDict())
    }

    func activateAudioSession() {
        let avSession = AVAudioSession.sharedInstance()

        // If no devices are connected, play audio through the default speaker (rather than the earpiece).
        var options: AVAudioSession.CategoryOptions = .defaultToSpeaker

        // If both Bluetooth streaming options are enabled, the low quality stream is preferred; enable A2DP only.
        options.insert(.allowBluetoothA2DP)

        do {
            try avSession.setCategory(.playAndRecord, options: options)
        } catch {
            print("Error setting category! \(error.localizedDescription)")
        }

        do {
            try AVAudioSession.sharedInstance().setActive(true)
        } catch {
            print("Could not activate audio session. \(error.localizedDescription)")
        }
    }

    /// Register the listener for pause and resume events.
    func observeLifeCycle() {
        let listener = NotificationCenter.default

        // We do need these.
        listener.addObserver(self, selector: #selector(handleAudioSessionInterruption(_:)), name: AVAudioSession.interruptionNotification, object: AVAudioSession.sharedInstance())

        // Listen to when the queue player tells us it emptied the items list
        listener.addObserver(self, selector: #selector(queueCleared(_:)), name: NSNotification.Name(AVBidirectionalQueueCleared), object: avQueuePlayer)
    }

    func createError(withCode code: RmxAudioErrorType, message: String?) -> [String : Any]? {
        [
            "code": NSNumber(value: code.rawValue),
            "message": message ?? ""
        ]
    }

    func onStatus(_ what: RmxAudioStatusMessage, trackId: String?, param: [String:Any]?) {
        var status: [String : Any] = [:]
        status["msgType"] = NSNumber(value: what.rawValue)
        // in the error case contains a dict with "code" and "message", otherwise a NSNumber
        if let param = param {
            status["value"] = param
        }
        status["trackId"] = trackId ?? ""

        var dict: [String : Any] = [:]
        dict["action"] = "status"
        dict["status"] = status

        statusUpdater?.onStatus(dict)
    }

    /// Cleanup
    func deregisterMusicControlsEventListener() {
        let commandCenter = MPRemoteCommandCenter.shared()
        commandCenter.playCommand.removeTarget(self)
        commandCenter.pauseCommand.removeTarget(self)
        commandCenter.nextTrackCommand.removeTarget(self)
        commandCenter.previousTrackCommand.removeTarget(self)
        commandCenter.togglePlayPauseCommand.removeTarget(self)
        commandCenter.changePlaybackPositionCommand.isEnabled = false
        commandCenter.changePlaybackPositionCommand.removeTarget(self, action: nil)

        commandCenterRegistered = false
    }

    func onReset() {
        // Override to cancel any long-running requests when the WebView navigates or refreshes.
        releaseResources()
    }

    func releaseResources() {
        if let playbackTimeObserver = playbackTimeObserver {
            avQueuePlayer.removeTimeObserver(playbackTimeObserver)
        }
        deregisterMusicControlsEventListener()

        removeAllTracks()

        playbackTimeObserver = nil
        isWaitingToStartPlayback = false
    }
}
