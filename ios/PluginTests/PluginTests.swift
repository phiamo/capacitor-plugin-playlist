import XCTest
@testable import Plugin

class PluginTests: XCTestCase {

    func testPlaybackPositionSuppressedWhenWebViewInactive() {
        let player = RmxAudioPlayer()
        player.setWebViewActive(false)
        XCTAssertFalse(player.shouldEmitStatusToBridge(.rmxstatus_PLAYBACK_POSITION))
    }

    func testPlaybackPositionEmittedWhenWebViewActive() {
        let player = RmxAudioPlayer()
        player.setWebViewActive(true)
        XCTAssertTrue(player.shouldEmitStatusToBridge(.rmxstatus_PLAYBACK_POSITION))
    }

    func testPlayingEmittedWhenWebViewInactive() {
        let player = RmxAudioPlayer()
        player.setWebViewActive(false)
        XCTAssertTrue(player.shouldEmitStatusToBridge(.rmxstatus_PLAYING))
    }
}
