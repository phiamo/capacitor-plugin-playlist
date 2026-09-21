import XCTest
@testable import PlaylistPlugin

/// Captures every onStatus(...) call made by an RmxAudioPlayer, so tests can assert exactly
/// which (and how many) status events a mutation produced. Mirrors PlaylistMutationTests'
/// private helper of the same name/shape — kept file-local rather than shared, matching the
/// existing test suite's convention.
private final class RecordingStatusUpdater: StatusUpdater {
    private(set) var calls: [[String: Any]] = []

    func onStatus(_ data: [String: Any]) {
        calls.append(data)
    }

    private func msgType(of data: [String: Any]) -> Int? {
        guard let status = data["status"] as? [String: Any], let msgType = status["msgType"] as? NSNumber else {
            return nil
        }
        return msgType.intValue
    }

    func count(of message: RmxAudioStatusMessage) -> Int {
        calls.filter { msgType(of: $0) == message.rawValue }.count
    }

    func lastValue(of message: RmxAudioStatusMessage) -> [String: Any]? {
        calls.last { msgType(of: $0) == message.rawValue }
            .flatMap { $0["status"] as? [String: Any] }
            .flatMap { $0["value"] as? [String: Any] }
    }
}

@MainActor
private func makeTrack(id: String) -> AudioTrack {
    AudioTrack.initWithDictionary([
        "trackId": id,
        "assetUrl": "https://example.com/\(id).mp3",
        "artist": "Artist",
        "album": "Album",
        "title": "Title \(id)"
    ])!
}

/// Issue #143 parity: iOS's primary stall signal is `AVPlayerItemPlaybackStalledNotification`
/// (`itemStalledPlaying`), with a `checkForStall`/`executePeriodicUpdate` position-freeze poll as
/// a backstop matching Android/Web (same class of bug: the primary signal can fail to fire while
/// playback is genuinely frozen). `checkForStall`'s own state tracking can't be exercised
/// end-to-end here — a live AVPlayer never reports genuine progress against an
/// unloaded/undownloadable asset in this test target (see PositionResumeTests) — so this suite
/// covers the pure threshold decision directly, and the notification-driven parts of the
/// contract (single-emit-per-episode, the "stalled" status string, reset on track change) via
/// itemStalledPlaying, which needs no live playback.
@MainActor
final class StallDetectionTests: XCTestCase {

    private func makePlayer(trackIds: [String]) -> (RmxAudioPlayer, RecordingStatusUpdater) {
        let player = RmxAudioPlayer()
        player.addAllItems(trackIds.map { makeTrack(id: $0) })
        player.avQueuePlayer.setCurrentIndex(0)
        let updater = RecordingStatusUpdater()
        player.statusUpdater = updater
        return (player, updater)
    }

    // MARK: - RmxAudioPlayer.hasStalled (pure threshold decision, mirrors Android's
    // PlaylistPlaybackPolicy.hasStalled)

    func testHasStalled_belowThreshold_isFalse() {
        XCTAssertFalse(RmxAudioPlayer.hasStalled(msSinceLastProgress: 5_999, thresholdMs: 6_000))
    }

    func testHasStalled_atOrAboveThreshold_isTrue() {
        XCTAssertTrue(RmxAudioPlayer.hasStalled(msSinceLastProgress: 6_000, thresholdMs: 6_000))
        XCTAssertTrue(RmxAudioPlayer.hasStalled(msSinceLastProgress: 11_000, thresholdMs: 6_000))
    }

    // MARK: - itemStalledPlaying (native AVPlayerItemPlaybackStalledNotification handler)

    func testItemStalledPlaying_emitsExactlyOncePerEpisode() {
        let (player, updater) = makePlayer(trackIds: ["a"])

        player.itemStalledPlaying(nil)
        player.itemStalledPlaying(nil)

        XCTAssertEqual(updater.count(of: .rmxstatus_STALLED), 1)
    }

    func testItemStalledPlaying_reportsStalledStatusString() {
        let (player, updater) = makePlayer(trackIds: ["a"])

        player.itemStalledPlaying(nil)

        XCTAssertEqual(updater.lastValue(of: .rmxstatus_STALLED)?["status"] as? String, "stalled")
    }

    func testHandleCurrentItemChanged_resetsStalledState() {
        let (player, updater) = makePlayer(trackIds: ["a", "b"])
        player.itemStalledPlaying(nil)
        XCTAssertEqual(updater.count(of: .rmxstatus_STALLED), 1)

        // A track change (native skip or explicit JS selection) must clear the stall state —
        // otherwise the next track would start already reporting "stalled". Called directly
        // (rather than via avQueuePlayer's KVO, which is only wired up after initialize()) so
        // this doesn't depend on that plumbing.
        player.handleCurrentItemChanged(player.avQueuePlayer.queuedAudioTracks[1])

        player.itemStalledPlaying(nil)
        XCTAssertEqual(updater.count(of: .rmxstatus_STALLED), 2)
    }
}
