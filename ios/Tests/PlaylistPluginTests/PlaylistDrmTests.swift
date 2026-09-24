import XCTest
@testable import PlaylistPlugin

final class PlaylistDrmTests: XCTestCase {

    func testDrmPayloadIsNotSupported() {
        let refusal = PlaylistDrm.notSupportedRefusal(for: [[
            "trackId": "a",
            "assetUrl": "https://cdn.example/drm/audio.m3u8",
            "drm": [
                "widevineLicenseUrl": "https://license.example/wv",
                "playbackSessionId": "sess-1"
            ]
        ]])

        XCTAssertEqual(refusal?.code, "notSupported")
        XCTAssertEqual(refusal?.message, "DRM not supported on this platform yet")
    }

    func testPayloadWithoutDrmIsAllowed() {
        XCTAssertNil(PlaylistDrm.notSupportedRefusal(for: [[
            "trackId": "a",
            "assetUrl": "https://example.com/a.mp3"
        ]]))
    }
}
