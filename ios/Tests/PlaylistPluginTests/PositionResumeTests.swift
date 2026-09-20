import XCTest
@testable import PlaylistPlugin

@MainActor
private func makeTrackWithPosition(id: String, startPosition: Double? = nil) -> AudioTrack {
    var dict: [String: Any] = [
        "trackId": id,
        "assetUrl": "https://example.com/\(id).mp3",
        "artist": "Artist",
        "album": "Album",
        "title": "Title \(id)"
    ]
    if let startPosition = startPosition {
        dict["startPosition"] = startPosition
    }
    return AudioTrack.initWithDictionary(dict)!
}

/// Regression coverage for the "skip to track restarts at 0" bug: `AudioTrack` must carry a
/// per-track resume position parsed from JS, and `AVBidirectionalQueuePlayer` must select the
/// correct target track (whose stored position the caller then resumes from) when skipping,
/// rather than always landing on a track with a blanket-reset position.
@MainActor
final class PositionResumeTests: XCTestCase {

    // MARK: - AudioTrack parsing (mirrors the seconds convention used elsewhere in this plugin)

    func testInitWithDictionary_parsesStartPositionInSeconds() {
        let track = makeTrackWithPosition(id: "a", startPosition: 58.294)
        XCTAssertEqual(track.startPositionSeconds, 58.294, accuracy: 0.001)
    }

    func testInitWithDictionary_defaultsStartPositionToZero() {
        let track = makeTrackWithPosition(id: "a")
        XCTAssertEqual(track.startPositionSeconds, 0)
    }

    func testInitWithDictionary_rejectsNegativeStartPosition() {
        let track = makeTrackWithPosition(id: "a", startPosition: -5)
        XCTAssertEqual(track.startPositionSeconds, 0)
    }

    func testToDict_roundTripsStartPosition() {
        let track = makeTrackWithPosition(id: "a", startPosition: 12.5)
        let dict = track.toDict()
        XCTAssertEqual((dict?["startPosition"] as? NSNumber)?.doubleValue, 12.5)
    }

    // MARK: - AVBidirectionalQueuePlayer target selection

    private func makePlayer(trackIds: [String]) -> RmxAudioPlayer {
        let player = RmxAudioPlayer()
        player.addAllItems(trackIds.map { makeTrackWithPosition(id: $0) })
        return player
    }

    func testSetCurrentIndex_selectsTrackCarryingItsSavedPosition() {
        let player = makePlayer(trackIds: ["a", "b", "c"])
        player.avQueuePlayer.queuedAudioTracks[1].startPositionSeconds = 42

        player.avQueuePlayer.setCurrentIndex(1)

        XCTAssertEqual(player.avQueuePlayer.currentAudioTrack?.trackId, "b")
        XCTAssertEqual(player.avQueuePlayer.currentAudioTrack?.startPositionSeconds, 42)
    }

    func testPlayPreviousItem_targetsPriorTrackWithItsSavedPosition() {
        let player = makePlayer(trackIds: ["a", "b", "c"])
        player.avQueuePlayer.setCurrentIndex(1)
        XCTAssertEqual(player.avQueuePlayer.currentAudioTrack?.trackId, "b")

        // Seed "a"'s saved position now, after leaving it — setCurrentIndex's own leave-snapshot
        // already ran and would otherwise clobber a value seeded beforehand.
        player.avQueuePlayer.queuedAudioTracks[0].startPositionSeconds = 17

        player.avQueuePlayer.playPreviousItem()

        // Regression: this used to always land on position 0 regardless of the track's history.
        XCTAssertEqual(player.avQueuePlayer.currentAudioTrack?.trackId, "a")
        XCTAssertEqual(player.avQueuePlayer.currentAudioTrack?.startPositionSeconds, 17)
    }
}
