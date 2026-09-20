//  Converted to Swift 5.3 by Swiftify v5.3.19197 - https://swiftify.com/
//
//  AVBidirectionalQueuePlayer.swift
//  IntervalPlayer
//
//  Created by Daniel Giovannelli on 2/18/13.
//  This class subclasses AVQueuePlayer to create a class with the same functionality as AVQueuePlayer
//  but with the added ability to go backwards in the queue - a function that is impossible in a normal
//  AVQueuePlayer since items on the queue are destroyed when they are finished playing.
//
//  IMPORTANT NOTE: This version of AVQueuePlayer assumes that ARC IS ENABLED. If ARC is NOT enabled and you
//  use this library, you'll get memory leaks on the two fields that have been added to the class, int
//  nowPlayingIndex and NSArray itemsForPlayer.
//
//  Note also that this classrequires that the AVFoundation framework be included in your project.

//
//  AVBidirectionalQueuePlayer.swift
//  IntervalPlayer
//
//  Created by Daniel Giovannelli on 2/18/13.
//
//  2014/07/16  (JRTaal) Greatly simplified and cleaned up code, meanwhile fixed number of bugs.
//                       Renamed to more apt AVBidirectionalQueuePlayer
//  2018/03/29  (codinronan) expanded feature set, added accessors and additional convenience methods & events.
//

import AVFoundation
import MediaPlayer

let AVBidirectionalQueueAddedItem = "AVBidirectionalQueuePlayer.AddedItem"
let AVBidirectionalQueueAddedAllItems = "AVBidirectionalQueuePlayer.AddedAllItems"
let AVBidirectionalQueueRemovedItem = "AVBidirectionalQueuePlayer.RemovedItem"
let AVBidirectionalQueueCleared = "AVBidirectionalQueuePlayer.Cleared"

class AVBidirectionalQueuePlayer: AVQueuePlayer {
    var queuedAudioTracks: [AudioTrack] = []

    var isPlaying: Bool {
        timeControlStatus == .playing
    }

    var isAtBeginning: Bool {
        // This function simply returns whether or not the AVBidirectionalQueuePlayer is at the first item. This is
        // useful for implementing custom behavior if the user tries to play a previous item at the start of
        // the queue (such as restarting the item).
        currentIndex() == 0
    }

    var isAtEnd: Bool {
        guard let currentIndex = currentIndex() else { return true }
        return currentIndex >= (queuedAudioTracks.endIndex - 1)
    }

    var currentAudioTrack: AudioTrack? { currentItem as? AudioTrack }

    override init() {
        super.init()
    }
    init(items: [AudioTrack]) {
        // This function calls the constructor for AVQueuePlayer, then sets up the nowPlayingIndex to 0 and saves the array that the player was generated from as itemsForPlayer
        super.init(items: items)
        queuedAudioTracks = items
    }

    // Two methods need to be added to the AVQueuePlayer: one which will play the last song in the queue, and one which will return if the queue is at the beginning (in case the user wishes to implement special behavior when a queue is at its first item, such as restarting a song). A getIndex method to return the current index is also provided.

    // NEW METHODS

    /// Snapshots `item`'s current playback position so a later skip back to it resumes correctly
    /// instead of restarting at 0. Guards against NaN/indefinite CMTime values.
    private func snapshotPosition(of item: AudioTrack?) {
        guard let item = item else { return }
        let seconds = item.currentTime().seconds
        if seconds.isFinite && seconds >= 0 {
            item.startPositionSeconds = seconds
        }
    }

    /// The saved resume position for `item`, in CMTime form, matching the `1000` timescale used
    /// elsewhere in this plugin for position seeks. Defaults to zero when absent.
    private func resumeTime(for item: AudioTrack?) -> CMTime {
        CMTimeMakeWithSeconds(Float64(item?.startPositionSeconds ?? 0), preferredTimescale: 1000)
    }

    func playPreviousItem() {
        // This function is the meat of this library: it allows for going backwards in an AVQueuePlayer,
        // basically by clearing the player and repopulating it from the index of the last item played.
        // It should be noted that if the player is on its first item, this function will do nothing. It will
        // not restart the item or anything like that; if you want that functionality you can implement it
        // yourself fairly easily using the isAtBeginning method to test if the player is at its start.
        guard
            let currentAudioTrack = currentAudioTrack,
            let tempNowPlayingIndex = queuedAudioTracks.firstIndex(of: currentAudioTrack)
        else {
            return
        }

        // Snapshot the position being left so a later skip back within this session resumes
        // correctly instead of restarting the track from its queue-build-time position.
        snapshotPosition(of: currentAudioTrack)

        if tempNowPlayingIndex == 0 {
            let currentrate = rate
            if currentrate != 0.0 {
                pause()
            }
            seek(to: .zero, toleranceBefore: .zero, toleranceAfter: .zero)
            // [self play];
            rate = currentrate
        } else if tempNowPlayingIndex > 0 {
            let currentrate = rate
            if currentrate != 0.0 {
                pause()
            }

            // Note: it is necessary to have seekToTime called twice in this method, once before and once after re-making the array. If it is not present before, the player will resume from the same spot in the next item when the previous item finishes playing; if it is not present after, the previous item will be played from the same spot that the current item was on.
            seek(to: .zero, toleranceBefore: .zero, toleranceAfter: .zero)

            // The next two lines are necessary since RemoveAllItems resets both the nowPlayingIndex and _itemsForPlayer
            let tempPlaylist = queuedAudioTracks
            super.removeAllItems()

            var offset = 1
            while true {
                let _it = tempPlaylist[tempNowPlayingIndex - offset]
                if _it.error != nil {
                    offset += 1
                }
                break
            }

            var newCurrentTrack: AudioTrack?
            for i in (tempNowPlayingIndex - offset)..<(tempPlaylist.count) {
                let item = tempPlaylist[i]
                if newCurrentTrack == nil {
                    newCurrentTrack = item
                }
                item.seek(to: resumeTime(for: item), toleranceBefore: .zero, toleranceAfter: .zero, completionHandler: nil)
                super.insert(item, after: nil)
            }

            // Not a typo; see above comment. Seeks the player itself to the new current item's
            // saved position — without this, AVQueuePlayer can override the per-item seek above.
            seek(to: resumeTime(for: newCurrentTrack), toleranceBefore: .zero, toleranceAfter: .zero)

            // [self play];
            rate = currentrate
        }
    }
    open override func advanceToNextItem() {
        snapshotPosition(of: currentAudioTrack)
        if currentIndex() == nil || currentIndex()! < queuedAudioTracks.count - 1{
            super.advanceToNextItem();
            // AVQueuePlayer's own advance always starts the new item at 0. If this item was
            // previously left partway through (e.g. skipped past earlier this session), resume it.
            if let track = currentAudioTrack, track.startPositionSeconds > 0 {
                seek(to: resumeTime(for: track), toleranceBefore: .zero, toleranceAfter: .zero)
            }
        } else {
            // Wraparound to the start of the queue is still a native-advance, not an explicit JS
            // track selection, so it keeps native-skip semantics (resume at saved position).
            setCurrentIndex(0, completionHandler: { _ in }, resumeAtSavedPosition: true)
        }
    }

    func setCurrentIndex(_ currentIndex: Int) {
        setCurrentIndex(currentIndex, completionHandler: { _ in })
    }

    /// - Parameter resumeAtSavedPosition: When `true`, the new current item resumes at its saved
    ///   `startPositionSeconds` — native skip/advance semantics. When `false` (the default), it
    ///   starts at 0, matching Android's `beginPlayback`/explicit JS play-or-select default; callers
    ///   that received an explicit JS `position` apply their own seek afterward (see `playTrack`/
    ///   `selectTrack` in `RmxAudioPlayer`). Other queue items keep their own saved position
    ///   regardless of this flag — they're untouched by this operation.
    func setCurrentIndex(_ newCurrentIndex: Int, completionHandler: @escaping (Bool) -> Void, resumeAtSavedPosition: Bool = false) {
        let currentrate = rate
        if currentrate > 0 {
            pause()
        }

        // Snapshot the position being left so a later skip back within this session resumes
        // correctly instead of restarting the track from its queue-build-time position.
        snapshotPosition(of: currentAudioTrack)

        // Note: it is necessary to have seekToTime called twice in this method, once before and once after re-making the area. If it is not present before, the player will resume from the same spot in the next item when the previous item finishes playing; if it is not present after, the previous item will be played from the same spot that the current item was on.
        seek(to: .zero, toleranceBefore: .zero, toleranceAfter: .zero)
        // The next two lines are necessary since RemoveAllItems resets both the nowPlayingIndex and _itemsForPlayer
        let tempPlaylist = queuedAudioTracks
        super.removeAllItems()
        let newCurrentTrack: AudioTrack? = newCurrentIndex >= 0 && newCurrentIndex < tempPlaylist.count
            ? tempPlaylist[newCurrentIndex]
            : nil
        let targetTime: CMTime = resumeAtSavedPosition ? resumeTime(for: newCurrentTrack) : .zero
        for i in newCurrentIndex..<(tempPlaylist.count) {
            let item = tempPlaylist[i]
            // Only the new current item's target depends on resumeAtSavedPosition; every other
            // (untouched) queue item keeps resuming at its own previously-saved position.
            let itemSeekTime = (i == newCurrentIndex) ? targetTime : resumeTime(for: item)
            item.seek(to: itemSeekTime, toleranceBefore: .zero, toleranceAfter: .zero, completionHandler: nil)
            super.insert(item, after: nil)
        }
        // Not a typo; see above comment. Seeks the player itself to the new current item's target
        // position — without this, AVQueuePlayer can override the per-item seek above.
        seek(to: targetTime, toleranceBefore: .zero, toleranceAfter: .zero, completionHandler: completionHandler)
    }

    func replaceAllItems(with items: [AudioTrack]) {
        removeAllItemsSilently()
        appendItems(items)
    }

    func appendItems(_ items: [AudioTrack]) {
        for item in items {
            insert(item, after: nil)
        }

        let center = NotificationCenter.default
        center.post(name: NSNotification.Name(AVBidirectionalQueueAddedAllItems), object: self, userInfo: [
            "items": items
        ])
    }
/* The following methods of AVQueuePlayer are overridden by AVBidirectionalQueuePlayer:
 – initWithItems: to keep track of the array used to create the player
 + queuePlayerWithItems: to keep track of the array used to create the player
 – advanceToNextItem to update the now playing index
 – insertItem:afterItem: to update the now playing index
 – removeAllItems to update the now playing index
 – removeItem:  to update the now playing index
 */

    func currentIndex() -> Int? {
        guard let currentAudioTrack = currentAudioTrack else { return nil }
        return queuedAudioTracks.firstIndex(of: currentAudioTrack)
    }

    // OVERRIDDEN AVQUEUEPLAYER METHODS
    /*
    resolving #9 and 11 and taking this out remove code after stabilize
    override func play() {
        if isAtEnd {
            // we could add a flag here to indicate looping
            setCurrentIndex(0)
        }

        super.play()
    }*/
    // This does the same thing as the normal AVQueuePlayer removeAllItems, but clears our collection copy
    override func removeAllItems() {
        super.removeAllItems()
        queuedAudioTracks.removeAll()

        MPNowPlayingInfoCenter.default().nowPlayingInfo?.removeAll()

        NotificationCenter.default.post(name: NSNotification.Name(AVBidirectionalQueueCleared), object: self, userInfo: nil)
    }

    func removeAllTrackObservers() {
        for item in queuedAudioTracks {
            removeTrackObservers(item)
        }
    }

    func removeTrackObservers(_ playerItem: AudioTrack?) {
        NotificationCenter.default.removeObserver(self, name: .AVPlayerItemDidPlayToEndTime, object: playerItem)
        NotificationCenter.default.removeObserver(self, name: .AVPlayerItemPlaybackStalled, object: playerItem)
    }

    func removeAllItemsSilently() {
        super.removeAllItems()
        queuedAudioTracks.removeAll()
    }

    func remove(_ item: AudioTrack) {
        // This method calls the superclass to remove the items from the AVQueuePlayer itself, then removes
        // any instance of the item from the itemsForPlayer array. This mimics the behavior of removeItem on
        // AVQueuePlayer, which removes all instances of the item in question from the queue.
        // It also subtracts 1 from the nowPlayingIndex for every time the item shows up in the itemsForPlayer
        // array before the current value.
        super.remove(item)

        if let index = queuedAudioTracks.firstIndex(of: item) {
            queuedAudioTracks.remove(at: index)
        }
        NotificationCenter.default.post(name: NSNotification.Name(AVBidirectionalQueueRemovedItem), object: self, userInfo: [
            "item": item
        ])
    }

    func insert(_ item: AudioTrack, after afterItem: AudioTrack?) {
        // This method calls the superclass to add the new item to the AVQueuePlayer, then adds that item to the
        // proper location in the itemsForPlayer array and increments the nowPlayingIndex if necessary.
        super.insert(item, after: afterItem)

        if afterItem != nil && queuedAudioTracks.contains(afterItem!) {
            // AfterItem is non-nil
            if (queuedAudioTracks.firstIndex(of: afterItem!) ?? NSNotFound) < (queuedAudioTracks.count - 1) {
                queuedAudioTracks.insert(item, at: (queuedAudioTracks.firstIndex(of: afterItem!) ?? NSNotFound) + 1)
            } else {
                queuedAudioTracks.append(item)
            }
        } else {
            // afterItem is nil
            queuedAudioTracks.append(item)
        }

        NotificationCenter.default.post(name: NSNotification.Name(AVBidirectionalQueueAddedItem), object: self, userInfo: [
            "item": item
        ])
    }
    /* The following methods of AVQueuePlayer are overridden by AVBidirectionalQueuePlayer:
     – initWithItems: to keep track of the array used to create the player
     + queuePlayerWithItems: to keep track of the array used to create the player
     – advanceToNextItem to update the now playing index
     – insertItem:afterItem: to update the now playing index
     – removeAllItems to update the now playing index
     – removeItem:  to update the now playing index
     */
}
