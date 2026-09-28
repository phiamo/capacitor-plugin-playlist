//
//  PlaylistDrmTests.swift
//  PlaylistPluginTests
//
//  Story 58.5: replaces the old `PlaylistDrm.notSupportedRefusal` coverage (Story 57.5's iOS
//  placeholder) with `AudioDrm` registry/typed-error coverage (mirrors `VideoDrmTests` from
//  Story 58.4) plus an `AudioTrack` DRM-wiring test (mirrors
//  `FullScreenVideoPlayerViewDrmWiringTests`).
//

import AVFoundation
import Capacitor
import XCTest
@testable import PlaylistPlugin

@MainActor
final class AudioDrmTests: XCTestCase {

    override func tearDown() {
        AudioDrm.setProvider(nil)
        super.tearDown()
    }

    func test_getProvider_defaultIsNil() {
        XCTAssertNil(AudioDrm.getProvider())
    }

    func test_open_withoutDrm_isPlainPlayback() {
        let attempt = AudioDrm.open(nil, onError: nil)
        XCTAssertNil(attempt.failureCode)
        XCTAssertNil(attempt.session)
    }

    func test_open_drmWithoutProvider_isNoProvider() {
        let drm: JSObject = ["fairplayLicenseUrl": "https://license.example/fp"]
        let attempt = AudioDrm.open(drm, onError: nil)
        XCTAssertEqual(attempt.failureCode, AudioDrm.codeNoProvider)
        XCTAssertNil(attempt.session)
    }

    func test_open_withProvider_returnsSession() {
        let session = FakeAudioDrmSession()
        AudioDrm.setProvider(FakeAudioDrmProvider { _, _ in session })
        let drm: JSObject = ["playbackSessionId": "sess-1"]
        let attempt = AudioDrm.open(drm, onError: nil)
        XCTAssertNil(attempt.failureCode)
        XCTAssertTrue((attempt.session as? FakeAudioDrmSession) === session)
    }

    func test_providerOnError_emitsTypedDiscriminator() {
        var errors: [String] = []
        AudioDrm.setProvider(FakeAudioDrmProvider { _, onError in
            let session = FakeAudioDrmSession()
            session.onError = onError
            return session
        })
        let attempt = AudioDrm.open([:]) { errors.append($0) }
        let session = attempt.session as? FakeAudioDrmSession
        session?.onError?(AudioDrm.errorNotEntitled)
        XCTAssertEqual(errors, [AudioDrm.errorNotEntitled])
    }

    func test_typedError_passesThroughKnownDiscriminators() {
        for value in [
            AudioDrm.errorBlockedByStreamLimit,
            AudioDrm.errorNotEntitled,
            AudioDrm.errorExpired,
            AudioDrm.errorNetwork,
            AudioDrm.errorUnknown
        ] {
            XCTAssertEqual(AudioDrm.typedError(value), value)
        }
    }

    func test_typedError_coercesUnknownStrings() {
        XCTAssertEqual(AudioDrm.typedError("somethingElse"), AudioDrm.errorUnknown)
    }
}

@MainActor
final class AudioTrackDrmWiringTests: XCTestCase {

    override func tearDown() {
        AudioDrm.setProvider(nil)
        super.tearDown()
    }

    func test_drmSessionAttachedAndStartedDuringInit() {
        let session = FakeAudioDrmSession()
        AudioDrm.setProvider(FakeAudioDrmProvider { _, _ in session })

        let track = AudioTrack.initWithDictionary([
            "trackId": "a",
            "assetUrl": "https://cdn.example/drm/audio.m3u8",
            "drm": ["playbackSessionId": "sess-1"]
        ])

        XCTAssertNotNil(track)
        XCTAssertTrue(session.attachedAsset === (track?.asset as? AVURLAsset),
                      "drmSession.attach(to:) must be called with the track's own asset")
        XCTAssertTrue(session.startCalled)
        XCTAssertTrue((track?.drmSession as? FakeAudioDrmSession) === session)
    }

    func test_deinitReleasesTheDrmSessionExactlyOnce() {
        let session = FakeAudioDrmSession()
        AudioDrm.setProvider(FakeAudioDrmProvider { _, _ in session })

        var track: AudioTrack? = AudioTrack.initWithDictionary([
            "trackId": "a",
            "assetUrl": "https://cdn.example/drm/audio.m3u8",
            "drm": ["playbackSessionId": "sess-1"]
        ])
        XCTAssertNotNil(track)
        XCTAssertFalse(session.releaseCalled)

        track = nil
        XCTAssertTrue(session.releaseCalled)
        XCTAssertEqual(session.releaseCallCount, 1)
    }

    func test_plainPlaybackWithoutDrmConstructsCleanly() {
        // No `drm` field: the track must build normally with a nil drmSession, and never touch
        // the registry even when a provider happens to be registered.
        AudioDrm.setProvider(FakeAudioDrmProvider { _, _ in FakeAudioDrmSession() })

        let track = AudioTrack.initWithDictionary([
            "trackId": "a",
            "assetUrl": "https://example.com/a.mp3"
        ])

        XCTAssertNotNil(track)
        XCTAssertNil(track?.drmSession)
    }

    func test_drmWithoutProvider_constructsPlainTrack() {
        // No provider registered: AudioTrack must not crash -- the `noProvider` reject already
        // happened upstream in Plugin.swift; this is a defensive fallback.
        let track = AudioTrack.initWithDictionary([
            "trackId": "a",
            "assetUrl": "https://cdn.example/drm/audio.m3u8",
            "drm": ["playbackSessionId": "sess-1"]
        ])

        XCTAssertNotNil(track)
        XCTAssertNil(track?.drmSession)
    }

    func test_onDrmErrorClosure_receivesTrackIdAndTypedError() {
        let session = FakeAudioDrmSession()
        AudioDrm.setProvider(FakeAudioDrmProvider { _, onError in
            session.onError = onError
            return session
        })

        var reported: (trackId: String, error: String)?
        let track = AudioTrack.initWithDictionary(
            [
                "trackId": "track-1",
                "assetUrl": "https://cdn.example/drm/audio.m3u8",
                "drm": ["playbackSessionId": "sess-1"]
            ],
            onDrmError: { trackId, error in
                reported = (trackId, error)
            }
        )
        XCTAssertNotNil(track)

        session.onError?(AudioDrm.errorExpired)
        XCTAssertEqual(reported?.trackId, "track-1")
        XCTAssertEqual(reported?.error, AudioDrm.errorExpired)
    }
}

/// Matrix row "drm set, provider registered" / Error Handling: "Session errors surface via
/// existing status channel value.error, typed string" -- the `onDrmError` closure tests above
/// only verify the closure itself fires; this verifies it is wired all the way through
/// `RmxAudioPlayer.reportDrmError` to the real `status` channel (`rmxstatus_ERROR`), matching
/// `StallDetectionTests`' `RecordingStatusUpdater` pattern.
@MainActor
final class RmxAudioPlayerDrmErrorRoutingTests: XCTestCase {

    private final class RecordingStatusUpdater: StatusUpdater {
        private(set) var calls: [[String: Any]] = []
        func onStatus(_ data: [String: Any]) {
            calls.append(data)
        }
    }

    func test_reportDrmError_emitsTypedErrorOnStatusChannel() {
        let player = RmxAudioPlayer()
        let updater = RecordingStatusUpdater()
        player.statusUpdater = updater

        player.reportDrmError(trackId: "track-1", error: AudioDrm.errorBlockedByStreamLimit)

        XCTAssertEqual(updater.calls.count, 1)
        let status = updater.calls[0]["status"] as? [String: Any]
        XCTAssertEqual((status?["msgType"] as? NSNumber)?.intValue, RmxAudioStatusMessage.rmxstatus_ERROR.rawValue)
        XCTAssertEqual(status?["trackId"] as? String, "track-1")
        let value = status?["value"] as? [String: Any]
        XCTAssertEqual(value?["error"] as? String, AudioDrm.errorBlockedByStreamLimit)
    }
}

// MARK: - Fakes

private final class FakeAudioDrmProvider: AudioDrmProvider {
    let factory: (JSObject, @escaping (String) -> Void) -> AudioDrmSession

    init(_ factory: @escaping (JSObject, @escaping (String) -> Void) -> AudioDrmSession) {
        self.factory = factory
    }

    func open(_ drm: JSObject, onError: @escaping (String) -> Void) -> AudioDrmSession {
        factory(drm, onError)
    }
}

private final class FakeAudioDrmSession: AudioDrmSession {
    var onError: ((String) -> Void)?
    var attachedAsset: AVURLAsset?
    var startCalled = false
    var releaseCalled = false
    var releaseCallCount = 0

    func attach(to asset: AVURLAsset) {
        attachedAsset = asset
    }

    func start() {
        startCalled = true
    }

    func release() {
        releaseCalled = true
        releaseCallCount += 1
    }
}
