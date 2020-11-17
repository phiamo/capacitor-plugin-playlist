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

private var kAvQueuePlayerContext = 0
private var kAvQueuePlayerRateContext = 0
private var kPlayerItemStatusContext = 0
private var kPlayerItemDurationContext = 0
private var kPlayerItemTimeRangesContext = 0

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

    private lazy var avQueuePlayer: AVBidirectionalQueuePlayer = {
        let avQueuePlayer = AVBidirectionalQueuePlayer(items: [])
        
        avQueuePlayer.actionAtItemEnd = .advance
        avQueuePlayer.addObserver(self, forKeyPath: "currentItem", options: .new, context: UnsafeMutableRawPointer(mutating: &kAvQueuePlayerContext))
        avQueuePlayer.addObserver(self, forKeyPath: "rate", options: .new, context: UnsafeMutableRawPointer(mutating: &kAvQueuePlayerRateContext))

        let interval = CMTimeMakeWithSeconds(Float64(1.0), preferredTimescale: Int32(Double(NSEC_PER_SEC)))
        playbackTimeObserver = avQueuePlayer.addPeriodicTimeObserver(forInterval: interval, queue: .main, using: { [weak self] time in
            self?.executePeriodicUpdate(time)
        })
        
        //_avQueuePlayer.automaticallyWaitsToMinimizeStalling = NO;
        
        return avQueuePlayer
    }()

    // structural methods
    override init() {
        super.init()
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
    
    func setPlaylistItems(_ items: [AudioTrack], options: [String:Any]) {
        print("RmxAudioPlayer.execute=setPlaylistItems, \(options), \(items)")

        var seekToPosition: Float = 0.0
        let retainPosition = options["retainPosition"] != nil ? (options["retainPosition"] as? Bool) ?? false : false
        let playFromPosition = options["playFromPosition"] != nil ? (options["playFromPosition"] as? Float) ?? 0.0 : 0.0

        let playFromId = ((options["playFromId"] != nil) ? options["playFromId"] : nil) as? String

        let startPaused = options["startPaused"] != nil ? (options["startPaused"] as? Bool) ?? false : true

        if retainPosition {
            seekToPosition = getTrackCurrentTime(nil)
            if playFromPosition > 0.0 {
                seekToPosition = playFromPosition
            }
        }

        let result = findTrack(byId: playFromId)
        let idx = (result?["index"] as? NSNumber)?.intValue ?? 0

        if !avQueuePlayer.itemsForPlayer.isEmpty {
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

    func removeItems(_ command: CDVInvokedUrlCommand?) -> Int {
        let items = command?.arguments[0] as? [AnyHashable]
        print("RmxAudioPlayer.execute=removeItems, \(items ?? [])")

        var removed = 0
        if items != nil || (items?.count ?? 0) > 0 {
            for item in items ?? [] {
                guard let item = item as? [String: String] else {
                    continue
                }
                let trackIndex = Int(item["trackIndex"]!)
                let trackId = item["trackId"]

                if removeItem(trackIndex: trackIndex, trackId: trackId) {
                    removed += 1
                }
            }
        }
        
        return removed
    }

    func clearAllItems(_ command: CDVInvokedUrlCommand?) {
        print("RmxAudioPlayer.execute=clearAllItems")
        removeAllTracks(false)
    }

    func playTrack(index: Int, positionTime: Float) -> (Bool, String?) {
        guard index < 0 || index >= avQueuePlayer.itemsForPlayer.count else {
            return (false, "Provided index is out of bounds")
        }

        avQueuePlayer.setCurrentIndex(index)
        playCommand(false)

        seek(to: positionTime, isCommand: false)

        return (true, nil)
    }

    func playTrack(_ trackId: String, positionTime: Float) -> (Bool, String?) {
        let result = findTrack(byId: trackId)
        let idx = result?["index"] as? Int ?? -1
        // AudioTrack* track = result[@"track"];

        if !avQueuePlayer.itemsForPlayer.isEmpty {
            if idx >= 0 {
                avQueuePlayer.setCurrentIndex(idx)
                playCommand(false)
                seek(to: positionTime, isCommand: false)

                return (true, nil);
            } else {
                return (false, "Track ID not found")
            }
        } else {
            return (false, "The playlist is empty!")
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
        if (!loop) {
            self.loop = true
        } else {
            self.loop = false
        }
         
        print("RmxAudioPlayer.execute=setLoopAll, \(loop)")
    }

    // Cleanup
    func release(_ command: CDVInvokedUrlCommand?) {
        print("RmxAudioPlayer.execute=release")
        isWaitingToStartPlayback = false
        releaseResources()
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
    func selectTrack(index: Int?) -> Bool {
        guard let index = index else { return false }
        if index < 0 || index >= avQueuePlayer.itemsForPlayer.count {
            return false
        } else {
            avQueuePlayer.setCurrentIndex(index)
            playCommand(false)
            return true
        }
    }

    func selectTrack(trackId: String?) {
        let result = findTrack(byId: trackId)
        let idx = (result?["index"] as? NSNumber)?.intValue ?? 0

        if !avQueuePlayer.itemsForPlayer.isEmpty {
            if idx >= 0 {
                avQueuePlayer.setCurrentIndex(idx)
            }
        }
    }


    func removeItem(trackIndex: Int?, trackId: String?) -> Bool {
        guard let trackIndex = trackIndex else { return false }
        
        if trackIndex > -1 && trackIndex < avQueuePlayer.itemsForPlayer.count {
            let item = avQueuePlayer.itemsForPlayer[trackIndex]
            removeTrackObservers(item)
            avQueuePlayer.remove(item)
            return true
        } else if let trackId = trackId, !trackId.isEmpty {
            let result = findTrack(byId: trackId)
            let idx = (result?["index"] as? NSNumber)?.intValue ?? 0
            let track = result?["track"] as? AudioTrack

            if idx >= 0 {
                // AudioTrack* item = [self avQueuePlayer].itemsForPlayer[idx];
                removeTrackObservers(track)
                
                if let track = track {
                    avQueuePlayer.remove(track)
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
    func playCommand(_ isCommand: Bool) {
        wasPlayingInterrupted = false
        initializeMPCommandCenter()

        if resetStreamOnPause {
            let currentTrack = avQueuePlayer.currentItem as? AudioTrack
            if currentTrack != nil && currentTrack?.isStream ?? false {
                avQueuePlayer.seek(to: .positiveInfinity, toleranceBefore: .zero, toleranceAfter: .zero)
                currentTrack?.seek(to: .positiveInfinity, toleranceBefore: .zero, toleranceAfter: .zero, completionHandler: nil)
            }
        }

        if isCommand {
            let action = "music-controls-play"
            print("\(action)")
        }
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
        if resetStreamOnPause {
            if let currentTrack = avQueuePlayer.currentItem as? AudioTrack, currentTrack.isStream {
                avQueuePlayer.seek(to: .positiveInfinity, toleranceBefore: .zero, toleranceAfter: .zero)
                currentTrack.seek(to: .positiveInfinity, toleranceBefore: .zero, toleranceAfter: .zero, completionHandler: nil)
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

        avQueuePlayer.playPreviousItem()

        if isCommand {
            let action = "music-controls-previous"
            print("\(action)")

            let playerItem = avQueuePlayer.currentItem as? AudioTrack
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

            let playerItem = avQueuePlayer.currentItem as? AudioTrack
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
            let playerItem = avQueuePlayer.currentItem as? AudioTrack
            onStatus(.rmxstatus_SEEK, trackId: playerItem?.trackId, param: [
                "position": NSNumber(value: positionTime)
            ])
        }
    }

    func setRate(_ rate: Float) {
        avQueuePlayer.rate = rate
    }
    
    func setVolume(_ volume: Float) {
        avQueuePlayer.volume = volume
    }
    
    func addTracks(_ tracks: [AudioTrack], startPosition: Float) {
        for playerItem in tracks {
            addTrackObservers(playerItem)
        }

        avQueuePlayer.insertAllItems(tracks, append: true)
        
        if startPosition > 0 {
            seek(to: startPosition, isCommand: false)
        }
    }

    func setTracks(_ tracks: [AudioTrack], startPosition: Float) {
        for item in avQueuePlayer.itemsForPlayer {
            removeTrackObservers(item)
        }

        for playerItem in tracks {
            addTrackObservers(playerItem)
        }

        isReplacingItems = true
        avQueuePlayer.insertAllItems(tracks, append: false)

        if startPosition > 0 {
            seek(to: startPosition, isCommand: false)
        }
    }

    func removeAllTracks(_ isCommand: Bool) {
        for item in avQueuePlayer.itemsForPlayer {
            removeTrackObservers(item)
        }

        avQueuePlayer.removeAllItems()
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
    ///
    ///
    ///
    ///
    ///
    ///
    ///
    @objc func itemStalledPlaying(_ notification: Notification?) {
        // This happens when the network is insufficient to continue playback.
        let playerItem = avQueuePlayer.currentItem as? AudioTrack
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
        
        let optionalNumber: Int? = 42
        
        if let number = optionalNumber {
            print("is never nil: \(number)")
        }
        
        guard let number = optionalNumber else {
            return
        }
        
        print("\(number)")
        
        guard let playerItem = avQueuePlayer.currentItem as? AudioTrack else { return }

        if !CMTIME_IS_INDEFINITE(playerItem.currentTime()) {
            updateNowPlayingTrackInfo(playerItem, updateTrackData: false)
            if avQueuePlayer.isPlaying {
                let trackStatus = getStatusItem(playerItem)
                onStatus(.rmxstatus_PLAYBACK_POSITION, trackId: playerItem.trackId, param: trackStatus)
                // NSLog(@" . %.5f / %.5f sec (%.1f %%) [%@]", currentTime, duration, (currentTime / duration)*100.0, name);
            }
        }

        return
    }

    override func observeValue(forKeyPath keyPath: String?, of object: Any?, change: [NSKeyValueChangeKey : Any]?, context: UnsafeMutableRawPointer?) {
        if (keyPath == "currentItem") && context == &kAvQueuePlayerContext {
            let player = object as? AVBidirectionalQueuePlayer
            let playerItem = player?.currentItem as? AudioTrack
            handleCurrentItemChanged(playerItem)
            return
        }

        if (keyPath == "rate") && context == &kAvQueuePlayerRateContext {
            let player = object as? AVBidirectionalQueuePlayer
            let playerItem = player?.currentItem as? AudioTrack

            if playerItem == nil {
                return
            }

            let trackStatus = getStatusItem(playerItem)
            print("Playback rate changed: \(1), is playing: \(player?.isPlaying ?? false)")

            if player?.isPlaying ?? false {
                onStatus(.rmxstatus_PLAYING, trackId: playerItem?.trackId, param: trackStatus)
            } else {
                onStatus(.rmxstatus_PAUSE, trackId: playerItem?.trackId, param: trackStatus)
            }
            return
        }

        if (keyPath == "status") && context == &kPlayerItemStatusContext {
            let playerItem = object as? AudioTrack
            handleTrackStatusEvent(playerItem)
            return
        }

        if (keyPath == "duration") && context == &kPlayerItemDurationContext {
            let playerItem = object as? AudioTrack
            handleTrackDuration(playerItem)
            return
        }

        if (keyPath == "loadedTimeRanges") && context == &kPlayerItemTimeRangesContext {
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
            currentItem = avQueuePlayer.currentItem as? AudioTrack
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
        if CMTIME_IS_INDEFINITE(currentItem!.duration) {
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

        nowPlayingInfoCenter.nowPlayingInfo = updatedNowPlayingInfo

        let commandCenter = MPRemoteCommandCenter.shared()
        commandCenter.nextTrackCommand.isEnabled = !avQueuePlayer.isAtEnd
        commandCenter.previousTrackCommand.isEnabled = !avQueuePlayer.isAtBeginning
    }

    func createCoverArtwork(_ coverUri: String?) -> MPMediaItemArtwork? {
        print("Creating cover art : \(String(describing: coverUri))")
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
                do {
                    coverImageData = try Data(contentsOf: coverImageUrl)
                } catch {
                    print("Error creating the coverImageData");
                }
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
            return isCoverImageValid(coverImage) ? MPMediaItemArtwork.init(boundsSize: coverImage.size, requestHandler: { (size) -> UIImage in
                return coverImage
            }): nil
            
        }
        return nil
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
        print("Queue changed current item to: \(playerItem != nil ? "NOTNIL" : "NIL")")
        if let playerItem = playerItem {
            print("Queue changed current item to: \(playerItem.toDict() ?? [:])")
            // NSLog(@"New music name: %@", ((AVURLAsset*)playerItem.asset).URL.pathComponents.lastObject);
            print("New item ID: \(playerItem.trackId ?? "")")
            print("Queue is at end: \(avQueuePlayer.isAtEnd ? "YES" : "NO")")
            // When an item starts, immediately scrub it back to the beginning
            playerItem.seek(to: .zero, toleranceBefore: .zero, toleranceAfter: .zero, completionHandler: nil)
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
            avQueuePlayer.seek(to: .zero, toleranceBefore: .zero, toleranceAfter: .zero)

            if !avQueuePlayer.itemsForPlayer.isEmpty && !isReplacingItems {
                onStatus(.rmxstatus_PLAYLIST_COMPLETED, trackId: "INVALID", param: nil)
            }

            if loop && !avQueuePlayer.itemsForPlayer.isEmpty {
                avQueuePlayer.play()
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
        if !CMTIME_IS_INDEFINITE(playerItem!.duration) {
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

    func getStatusItem(_ playerItem: AudioTrack?) -> [String : Any]? {
        var currentItem = playerItem
        if currentItem == nil {
            currentItem = avQueuePlayer.currentItem as? AudioTrack
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

        if avQueuePlayer.currentItem == currentItem {
            if avQueuePlayer.rate != 0 {
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
            "currentIndex": NSNumber(value: avQueuePlayer.currentIndex() ?? 0),
            "status": status,
            "currentPosition": NSNumber(value: position),
            "duration": NSNumber(value: duration),
            "playbackPercent": NSNumber(value: playbackPercent),
            "bufferPercent": NSNumber(value: (bufferInfo?["bufferPercent"] as? NSNumber)?.floatValue ?? 0.0),
            "bufferStart": NSNumber(value: (bufferInfo?["start"] as? NSNumber)?.floatValue ?? 0.0),
            "bufferEnd": NSNumber(value: (bufferInfo?["end"] as? NSNumber)?.floatValue ?? 0.0)
        ] as [String : Any]
        return info
    }

    func getTrackCurrentTime(_ playerItem: AudioTrack?) -> Float {
        guard let currentItem = playerItem ?? (avQueuePlayer.currentItem as? AudioTrack) else {
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
        let trackInformation: (Int, AudioTrack)? = avQueuePlayer.itemsForPlayer
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
        let avSession = AVAudioSession.sharedInstance()

        // If no devices are connected, play audio through the default speaker (rather than the earpiece).
        var options: AVAudioSession.CategoryOptions = .defaultToSpeaker

        // If both Bluetooth streaming options are enabled, the low quality stream is preferred; enable A2DP only.
        options.insert(.allowBluetoothA2DP)

        do {
            try avSession.setCategory(.playAndRecord, options: options)
        }
        catch let categoryError {
            print("Error setting category! \(categoryError.localizedDescription)")
        }

        do {
            try AVAudioSession.sharedInstance().setActive(true)
        }
        catch let activationError {
            print("Could not activate audio session. \(activationError.localizedDescription)")
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

        // Listen to when the queue player tells us it emptied the items list
        listener.addObserver(self, selector: #selector(queueCleared(_:)), name: NSNotification.Name(AVBidirectionalQueueCleared), object: avQueuePlayer)
    }

    func createError(withCode code: RmxAudioErrorType, message: String?) -> [String : Any]? {
        let finalMessage = message ?? ""

        return [
            "code": NSNumber(value: code.rawValue),
            "message": finalMessage
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
        commandCenter.changePlaybackPositionCommand.isEnabled = false
        commandCenter.changePlaybackPositionCommand.removeTarget(self, action: nil)

        commandCenterRegistered = false
    }
    
    func onReset() {
        // Override to cancel any long-running requests when the WebView navigates or refreshes.
        //super.onReset()
        releaseResources()
    }

    deinit {
        releaseResources()
    }

    func releaseResources() {
        if let playbackTimeObserver = playbackTimeObserver {
            avQueuePlayer.removeTimeObserver(playbackTimeObserver)
        }
        avQueuePlayer.removeObserver(self as NSObject, forKeyPath: "currentItem")
        avQueuePlayer.removeObserver(self as NSObject, forKeyPath: "rate")
        deregisterMusicControlsEventListener()

        // onReset or when killing app:
        //if viewController.isFirstResponder {
        //    viewController.resignFirstResponder()
        //}
        removeAllTracks(false)
        avQueuePlayer.insertAllItems([], append: false)

        playbackTimeObserver = nil
    }
}
