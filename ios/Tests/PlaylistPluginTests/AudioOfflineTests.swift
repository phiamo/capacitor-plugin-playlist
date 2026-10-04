//
//  AudioOfflineTests.swift
//  PlaylistPluginTests
//
//  Story 59.5: registry, typedError (incl. offlineDeviceLimit), attachOffline, queue downloadId.
//

import AVFoundation
import Capacitor
import XCTest
@testable import PlaylistPlugin

@MainActor
final class AudioOfflineTests: XCTestCase {

    override func tearDown() {
        AudioOffline.setProvider(nil)
        AudioDrm.setProvider(nil)
        OfflineDownloads.setSharedForTest(nil)
        _ = AudioOffline.takeBackgroundCompletionHandler()
        super.tearDown()
    }

    func test_typedError_acceptsOfflineDeviceLimit_andCollapsesTheRest() {
        XCTAssertEqual(AudioDrm.typedError(AudioDrm.errorOfflineDeviceLimit), AudioDrm.errorOfflineDeviceLimit)
        XCTAssertEqual(AudioOffline.typedError(AudioDrm.errorNotEntitled), AudioDrm.errorNotEntitled)
        XCTAssertEqual(AudioOffline.typedError("licenseDenied"), AudioDrm.errorUnknown)
        XCTAssertEqual(AudioOffline.typedError(nil), AudioDrm.errorUnknown)
    }

    func test_stateOf_normalisesUnknownValues() {
        let stub = FakeAudioOfflineProvider()
        XCTAssertEqual(AudioOffline.stateOf(stub, downloadId: "d"), AudioOffline.stateActive)
        stub.stateResult = AudioOffline.stateExpired
        XCTAssertEqual(AudioOffline.stateOf(stub, downloadId: "d"), AudioOffline.stateExpired)
        stub.stateResult = "weird"
        XCTAssertEqual(AudioOffline.stateOf(stub, downloadId: "d"), AudioOffline.stateNone)
    }

    func test_attachOffline_withoutProvider_isNoOp() {
        let asset = AVURLAsset(url: URL(fileURLWithPath: "/offline/d"))
        AudioOffline.attachOffline(downloadId: "d", asset: asset)
        XCTAssertNil(AudioOffline.getProvider())
    }

    func test_attachOffline_callsProvider() {
        let stub = FakeAudioOfflineProvider()
        AudioOffline.setProvider(stub)
        let asset = AVURLAsset(url: URL(fileURLWithPath: "/offline/d"))
        AudioOffline.attachOffline(downloadId: "d1", asset: asset)
        XCTAssertEqual(stub.attached, ["d1"])
        XCTAssertTrue(stub.attachedAssets.first === asset)
    }

    func test_setProvider_roundTrips() {
        XCTAssertNil(AudioOffline.getProvider())
        let stub = FakeAudioOfflineProvider()
        AudioOffline.setProvider(stub)
        XCTAssertTrue(AudioOffline.getProvider() === stub)
        AudioOffline.setProvider(nil)
        XCTAssertNil(AudioOffline.getProvider())
    }

    func test_handleEventsForBackgroundURLSession_storesMatchingHandlerOnce() {
        OfflineDownloads.setSharedForTest(OfflineDownloads(
            engine: FakeEngine(),
            meta: OfflineMetaStore(file: FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)),
            executor: { $0() }
        ))
        _ = AudioOffline.takeBackgroundCompletionHandler()

        AudioOffline.handleEventsForBackgroundURLSession("other.session") {}
        XCTAssertNil(AudioOffline.takeBackgroundCompletionHandler())

        var storedCalled = false
        AudioOffline.handleEventsForBackgroundURLSession(AudioOffline.backgroundSessionIdentifier) {
            storedCalled = true
        }
        let first = AudioOffline.takeBackgroundCompletionHandler()
        XCTAssertNotNil(first)
        first?()
        XCTAssertTrue(storedCalled)
        XCTAssertNil(AudioOffline.takeBackgroundCompletionHandler())
    }
}

@MainActor
final class AudioTrackOfflineWiringTests: XCTestCase {

    private var tmp: URL!

    override func setUp() {
        super.setUp()
        tmp = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString, isDirectory: true)
        try? FileManager.default.createDirectory(at: tmp, withIntermediateDirectories: true)
    }

    override func tearDown() {
        AudioOffline.setProvider(nil)
        AudioDrm.setProvider(nil)
        OfflineDownloads.setSharedForTest(nil)
        try? FileManager.default.removeItem(at: tmp)
        super.tearDown()
    }

    func test_resolveCompletedURL_ignoresStaleAbsoluteLocalPath() {
        let dest = OfflineLocalAsset.movpkgURL(downloadId: "test-1", root: tmp)
        try? FileManager.default.createDirectory(at: dest, withIntermediateDirectories: true)
        let stale = "/var/mobile/Containers/Data/Application/02E6FFDC-87BD-49DF-92F8-90ACC1BCFDB8/Library/Application Support/org.dwbn.plugins.playlist.offline/assets/test-1.movpkg"
        let resolved = OfflineLocalAsset.resolveCompletedURL(
            downloadId: "test-1",
            storedLocalPath: stale,
            root: tmp
        )
        XCTAssertEqual(resolved, dest)
        XCTAssertFalse(resolved?.path.contains("02E6FFDC") ?? true)
    }

    func test_resolveCompletedURL_missingPackage_returnsNil() {
        let stale = "/var/mobile/Containers/Data/Application/DEAD/Library/Application Support/x/assets/test-1.movpkg"
        XCTAssertNil(OfflineLocalAsset.resolveCompletedURL(
            downloadId: "test-1",
            storedLocalPath: stale,
            root: tmp
        ))
    }

    func test_localAssetURL_rejectsMissingFile() {
        let missing = tmp.appendingPathComponent("gone.movpkg")
        let engine = FakeEngine()
        engine.store["d1"] = EngineDownload(id: "d1", state: .completed, progress: 1, localURL: missing, failedByNetwork: false)
        let downloads = OfflineDownloads(
            engine: engine,
            meta: OfflineMetaStore(file: tmp.appendingPathComponent("meta.json")),
            executor: { $0() }
        )
        XCTAssertNil(downloads.localAssetURL("d1"))
    }

    func test_downloadId_emptyAssetUrl_usesLocalAssetAndAttachOffline() {
        let local = tmp.appendingPathComponent("d1.movpkg")
        try? Data().write(to: local)
        let engine = FakeEngine()
        engine.store["d1"] = EngineDownload(id: "d1", state: .completed, progress: 1, localURL: local, failedByNetwork: false)
        let downloads = OfflineDownloads(
            engine: engine,
            meta: OfflineMetaStore(file: tmp.appendingPathComponent("meta.json")),
            executor: { $0() },
            clock: { 1_000_000 },
            providerSource: { AudioOffline.getProvider() }
        )
        OfflineDownloads.setSharedForTest(downloads)
        let stub = FakeAudioOfflineProvider()
        AudioOffline.setProvider(stub)

        let track = AudioTrack.initWithDictionary([
            "trackId": "a",
            "assetUrl": "",
            "downloadId": "d1",
            "title": "Talk",
            "artist": "",
            "album": ""
        ])

        XCTAssertNotNil(track)
        XCTAssertEqual(track?.downloadId, "d1")
        XCTAssertEqual(track?.assetUrl, local)
        XCTAssertEqual(stub.attached, ["d1"])
        XCTAssertFalse(track?.canUseNetworkResourcesForLiveStreamingWhilePaused ?? true)
        XCTAssertEqual(track?.toDict()?["downloadId"] as? String, "d1")
    }

    func test_expiredDownload_doesNotAttachOffline() {
        let local = tmp.appendingPathComponent("d1.movpkg")
        try? Data().write(to: local)
        let engine = FakeEngine()
        engine.store["d1"] = EngineDownload(id: "d1", state: .completed, progress: 1, localURL: local, failedByNetwork: false)
        let downloads = OfflineDownloads(
            engine: engine,
            meta: OfflineMetaStore(file: tmp.appendingPathComponent("meta.json")),
            executor: { $0() },
            providerSource: { AudioOffline.getProvider() }
        )
        OfflineDownloads.setSharedForTest(downloads)
        let stub = FakeAudioOfflineProvider()
        stub.stateResult = AudioOffline.stateExpired
        AudioOffline.setProvider(stub)

        let track = AudioTrack.initWithDictionary([
            "trackId": "a",
            "downloadId": "d1"
        ])

        XCTAssertNotNil(track)
        XCTAssertTrue(stub.attached.isEmpty)
    }

    func test_downloadIdAndDrm_skipsStreamingFairPlay() {
        let local = tmp.appendingPathComponent("d1.movpkg")
        try? Data().write(to: local)
        let engine = FakeEngine()
        engine.store["d1"] = EngineDownload(id: "d1", state: .completed, progress: 1, localURL: local, failedByNetwork: false)
        let downloads = OfflineDownloads(
            engine: engine,
            meta: OfflineMetaStore(file: tmp.appendingPathComponent("meta.json")),
            executor: { $0() },
            providerSource: { AudioOffline.getProvider() }
        )
        OfflineDownloads.setSharedForTest(downloads)
        let stub = FakeAudioOfflineProvider()
        AudioOffline.setProvider(stub)
        let session = FakeAudioDrmSession()
        AudioDrm.setProvider(FakeAudioDrmProvider { _, _ in session })

        let track = AudioTrack.initWithDictionary([
            "trackId": "a",
            "assetUrl": "https://cdn.example/drm/audio.m3u8",
            "downloadId": "d1",
            "drm": ["playbackSessionId": "sess-1"]
        ])

        XCTAssertNotNil(track)
        XCTAssertEqual(stub.attached, ["d1"])
        XCTAssertNil(track?.drmSession)
        XCTAssertFalse(session.startCalled)
    }

    func test_streamingItem_isUnchanged() {
        AudioOffline.setProvider(FakeAudioOfflineProvider())
        let track = AudioTrack.initWithDictionary([
            "trackId": "a",
            "assetUrl": "https://example.com/a.mp3"
        ])
        XCTAssertNotNil(track)
        XCTAssertNil(track?.downloadId)
        XCTAssertNil(track?.drmSession)
    }
}

@MainActor
final class RmxAudioPlayerOfflineTests: XCTestCase {

    private final class RecordingStatusUpdater: StatusUpdater {
        private(set) var calls: [[String: Any]] = []
        func onStatus(_ data: [String: Any]) {
            calls.append(data)
        }
    }

    override func tearDown() {
        AudioOffline.setProvider(nil)
        OfflineDownloads.setSharedForTest(nil)
        super.tearDown()
    }

    func test_expiredLicence_emitsExpiredAndDoesNotPlay() {
        let stub = FakeAudioOfflineProvider()
        stub.stateResult = AudioOffline.stateExpired
        AudioOffline.setProvider(stub)
        let tmp = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString, isDirectory: true)
        try? FileManager.default.createDirectory(at: tmp, withIntermediateDirectories: true)
        let local = tmp.appendingPathComponent("d1.movpkg")
        try? Data().write(to: local)
        let engine = FakeEngine()
        engine.store["d1"] = EngineDownload(id: "d1", state: .completed, progress: 1, localURL: local, failedByNetwork: false)
        let downloads = OfflineDownloads(
            engine: engine,
            meta: OfflineMetaStore(file: tmp.appendingPathComponent("meta.json")),
            executor: { $0() },
            providerSource: { AudioOffline.getProvider() }
        )
        OfflineDownloads.setSharedForTest(downloads)
        defer { try? FileManager.default.removeItem(at: tmp) }

        let player = RmxAudioPlayer()
        player.addAllItems([
            AudioTrack.initWithDictionary([
                "trackId": "a",
                "downloadId": "d1",
                "title": "Talk",
                "artist": "",
                "album": ""
            ])!
        ])
        let updater = RecordingStatusUpdater()
        player.statusUpdater = updater

        player.playCommand(false)

        XCTAssertFalse(player.avQueuePlayer.isPlaying)
        XCTAssertEqual(updater.calls.count, 1)
        let status = updater.calls[0]["status"] as? [String: Any]
        XCTAssertEqual((status?["msgType"] as? NSNumber)?.intValue, RmxAudioStatusMessage.rmxstatus_ERROR.rawValue)
        let value = status?["value"] as? [String: Any]
        XCTAssertEqual(value?["error"] as? String, AudioDrm.errorExpired)
    }
}

final class FakeAudioOfflineProvider: AudioOfflineProvider {
    var acquireResult: String?
    var renewResult: String?
    var releaseThrows = false
    var stateResult = AudioOffline.stateActive
    var needsRenewalResult = false
    var onAcquire: (() -> Void)?
    var expireOnRenewRefusal = false
    var onCall: ((String) -> Void)?
    var attached: [String] = []
    var attachedAssets: [AVURLAsset] = []
    var calls: [String] = []

    private func record(_ name: String) {
        calls.append(name)
        onCall?(name)
    }

    func acquire(downloadId: String, keyIdentifier: String, drm: JSObject) -> String? {
        record("acquire:\(downloadId)")
        onAcquire?()
        return acquireResult
    }

    func needsRenewal(downloadId: String) -> Bool { needsRenewalResult }

    func renew(downloadId: String, drm: JSObject?) -> String? {
        if renewResult != nil && expireOnRenewRefusal {
            stateResult = AudioOffline.stateExpired
        }
        return renewResult
    }

    func release(downloadId: String) {
        record("release:\(downloadId)")
        if releaseThrows {
            // Failures are ignored by contract; the test still records the call.
        }
    }

    func state(downloadId: String) -> String { stateResult }

    func attachOffline(downloadId: String, asset: AVURLAsset) {
        attached.append(downloadId)
        attachedAssets.append(asset)
    }
}
