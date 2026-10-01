import XCTest
@testable import PlaylistPlugin

/// Captures every onStatus(...) call made by an RmxAudioPlayer, so tests can assert exactly
/// which (and how many) status events a mutation produced.
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
}

@MainActor
private func makeTrack(id: String, url: String = "https://example.com/track.mp3") -> AudioTrack {
    AudioTrack.initWithDictionary([
        "trackId": id,
        "assetUrl": url,
        "artist": "Artist",
        "album": "Album",
        "title": "Title \(id)"
    ])!
}

/// Regression coverage for addItem/moveItem/replaceItem: the "insert/move/replace without
/// disrupting playback" contract documented in the README, and the specific event-flood bug
/// where moveItem/replaceItem/addItem(at: 0) used to rebuild the whole native queue via
/// setTracks(), re-firing RMXSTATUS_ITEM_ADDED for every already-known track.
@MainActor
final class PlaylistMutationTests: XCTestCase {

    private func makePlayer(trackIds: [String]) -> (RmxAudioPlayer, RecordingStatusUpdater) {
        let player = RmxAudioPlayer()
        player.addAllItems(trackIds.map { makeTrack(id: $0) })
        // Attach the recorder only after seeding, so setup noise doesn't pollute assertions.
        let updater = RecordingStatusUpdater()
        player.statusUpdater = updater
        return (player, updater)
    }

    // MARK: - addItem

    func testAddItemAtIndex_insertsAtCorrectPosition() throws {
        let (player, updater) = makePlayer(trackIds: ["a", "b", "c"])

        try player.addItem(makeTrack(id: "new"), at: 1)

        XCTAssertEqual(player.avQueuePlayer.queuedAudioTracks.map { $0.trackId }, ["a", "new", "b", "c"])
        XCTAssertEqual(updater.count(of: .rmxstatus_ITEM_ADDED), 1)
    }

    func testAddItemAtIndexZero_emitsExactlyOneItemAddedEvent() throws {
        let (player, updater) = makePlayer(trackIds: ["a", "b", "c"])

        try player.addItem(makeTrack(id: "new"), at: 0)

        XCTAssertEqual(player.avQueuePlayer.queuedAudioTracks.map { $0.trackId }, ["new", "a", "b", "c"])
        // Regression: addItem(at: 0) used to rebuild the whole queue via setTracks(), which
        // re-fired ITEM_ADDED for every already-known track (4 events instead of 1).
        XCTAssertEqual(updater.count(of: .rmxstatus_ITEM_ADDED), 1)
    }

    func testAddItem_duplicateTrackId_isIgnored() throws {
        let (player, updater) = makePlayer(trackIds: ["a", "b"])

        try player.addItem(makeTrack(id: "a"), at: 1)
        player.addItem(makeTrack(id: "b"))

        XCTAssertEqual(player.avQueuePlayer.queuedAudioTracks.map { $0.trackId }, ["a", "b"])
        XCTAssertEqual(updater.count(of: .rmxstatus_ITEM_ADDED), 0)
    }

    func testAddAllItems_skipsExistingTrackIds() {
        let (player, updater) = makePlayer(trackIds: ["a", "b"])

        player.addAllItems([makeTrack(id: "b"), makeTrack(id: "c")])

        XCTAssertEqual(player.avQueuePlayer.queuedAudioTracks.map { $0.trackId }, ["a", "b", "c"])
        XCTAssertEqual(updater.count(of: .rmxstatus_ITEM_ADDED), 1)
    }

    // MARK: - moveItem

    func testMoveItem_reordersTracks() throws {
        let (player, _) = makePlayer(trackIds: ["a", "b", "c", "d"])

        try player.moveItem(from: 0, to: 2)

        XCTAssertEqual(player.avQueuePlayer.queuedAudioTracks.map { $0.trackId }, ["b", "c", "a", "d"])
    }

    func testMoveItem_doesNotEmitItemAddedEvents() throws {
        let (player, updater) = makePlayer(trackIds: ["a", "b", "c", "d"])

        try player.moveItem(from: 0, to: 2)

        // Regression: moveItem used to rebuild the queue via setTracks(), which re-registered
        // observers and re-fired ITEM_ADDED for every track even though nothing was added.
        XCTAssertEqual(updater.count(of: .rmxstatus_ITEM_ADDED), 0)
        XCTAssertEqual(updater.count(of: .rmxstatus_ITEM_MOVED), 1)
    }

    func testMoveItem_outOfBoundsThrows() {
        let (player, _) = makePlayer(trackIds: ["a", "b"])
        XCTAssertThrowsError(try player.moveItem(from: 0, to: 5))
    }

    func testMoveItem_sameIndexIsANoOp() throws {
        let (player, updater) = makePlayer(trackIds: ["a", "b"])

        try player.moveItem(from: 1, to: 1)

        XCTAssertEqual(player.avQueuePlayer.queuedAudioTracks.map { $0.trackId }, ["a", "b"])
        XCTAssertEqual(updater.count(of: .rmxstatus_ITEM_MOVED), 0)
    }

    // MARK: - replaceItem

    func testReplaceItemByIndex_preservesTrackIdWhenOmitted() throws {
        let (player, updater) = makePlayer(trackIds: ["a", "b"])

        try player.replaceItem(at: 1, id: nil, with: [
            "assetUrl": "https://example.com/local-b.mp3",
            "artist": "New Artist",
            "album": "New Album",
            "title": "New Title"
        ])

        let tracks = player.avQueuePlayer.queuedAudioTracks
        XCTAssertEqual(tracks.map { $0.trackId }, ["a", "b"])
        XCTAssertEqual(tracks[1].assetUrl?.absoluteString, "https://example.com/local-b.mp3")
        XCTAssertEqual(updater.count(of: .rmxstatus_ITEM_REPLACED), 1)
    }

    func testReplaceItem_emitsExactlyOneReplacedEvent_noItemAddedFlood() throws {
        let (player, updater) = makePlayer(trackIds: ["a", "b", "c"])

        try player.replaceItem(at: 1, id: nil, with: [
            "assetUrl": "https://example.com/new.mp3",
            "artist": "Artist",
            "album": "Album",
            "title": "Title"
        ])

        // Regression: replaceItem used to rebuild the whole queue via setTracks(), which
        // re-fired ITEM_ADDED for every still-current track (3 events instead of 0).
        XCTAssertEqual(updater.count(of: .rmxstatus_ITEM_ADDED), 0)
        XCTAssertEqual(updater.count(of: .rmxstatus_ITEM_REPLACED), 1)
    }

    func testReplaceItem_byId() throws {
        let (player, _) = makePlayer(trackIds: ["a", "b", "c"])

        try player.replaceItem(at: nil, id: "b", with: [
            "assetUrl": "https://example.com/replaced.mp3",
            "artist": "Artist",
            "album": "Album",
            "title": "Title"
        ])

        let tracks = player.avQueuePlayer.queuedAudioTracks
        XCTAssertEqual(tracks.map { $0.trackId }, ["a", "b", "c"])
        XCTAssertEqual(tracks[1].assetUrl?.absoluteString, "https://example.com/replaced.mp3")
    }

    func testReplaceItem_missingTargetThrows() {
        let (player, _) = makePlayer(trackIds: ["a"])

        XCTAssertThrowsError(try player.replaceItem(at: nil, id: "missing", with: [
            "assetUrl": "https://example.com/x.mp3",
            "artist": "Artist",
            "album": "Album",
            "title": "Title"
        ]))
    }
}
