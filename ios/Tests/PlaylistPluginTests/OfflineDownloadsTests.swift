//
//  OfflineDownloadsTests.swift
//  PlaylistPluginTests
//
//  Story 59.5: licence-first download orchestration, expiry maths, pending guard, HLS skd://
//  parse, backup exclusion. No FairPlay in XCTest — the engine is a fake.
//

import AVFoundation
import Capacitor
import XCTest
@testable import PlaylistPlugin

@MainActor
final class OfflineDownloadsTests: XCTestCase {
    private var tmp: URL!
    private var calls: [String] = []
    private var events: [DownloadEvent] = []
    private var now: Int64 = 1_000_000
    private var engine: FakeEngine!
    private var provider: FakeAudioOfflineProvider!
    private var meta: OfflineMetaStore!
    private var downloads: OfflineDownloads!

    override func setUp() {
        super.setUp()
        tmp = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString, isDirectory: true)
        try? FileManager.default.createDirectory(at: tmp, withIntermediateDirectories: true)
        calls = []
        events = []
        engine = FakeEngine()
        engine.calls = { [weak self] in self?.calls.append($0) }
        provider = FakeAudioOfflineProvider()
        provider.onCall = { [weak self] in self?.calls.append($0) }
        meta = OfflineMetaStore(file: tmp.appendingPathComponent("meta.json"))
        downloads = OfflineDownloads(
            engine: engine,
            meta: meta,
            executor: { $0() },
            clock: { [weak self] in self?.now ?? 0 },
            providerSource: { [weak self] in self?.provider }
        )
        downloads.eventSink = { [weak self] in self?.events.append($0) }
    }

    override func tearDown() {
        AudioOffline.setProvider(nil)
        OfflineDownloads.setSharedForTest(nil)
        try? FileManager.default.removeItem(at: tmp)
        super.tearDown()
    }

    private func drm() -> JSObject { ["playbackSessionId": "s1"] }

    func test_start_acquiresLicenceBeforeEnqueue() {
        XCTAssertNil(downloads.start(downloadId: "d1", url: "https://cdn.example/a.m3u8?t=1", drm: drm()))
        XCTAssertEqual(calls, ["prepare:d1", "acquire:d1", "enqueue:d1"])
        XCTAssertEqual(events.first?.state, DownloadStates.queued)
    }

    func test_start_thenProgressAndComplete_listShowsExpiresAt() {
        downloads.start(downloadId: "d1", url: "https://cdn.example/a.m3u8", drm: drm())
        engine.emit("d1", .downloading, 0.5)
        engine.emit("d1", .completed, 1)

        XCTAssertEqual(events.map(\.state), [
            DownloadStates.queued, DownloadStates.downloading, DownloadStates.completed
        ])
        XCTAssertEqual(events[1].progress, 0.5)
        let info = downloads.list().first
        XCTAssertEqual(info?.state, DownloadStates.completed)
        XCTAssertEqual(info?.expiresAt, now + OfflineExpiry.licenseValidityMs)
    }

    func test_start_usesProviderExpiresAtInsteadOfTwentySevenDayEstimate() {
        provider.expiresAtResult = now + 300_000

        downloads.start(downloadId: "d1", url: "https://cdn.example/a.m3u8", drm: drm())
        engine.emit("d1", .completed, 1)

        XCTAssertEqual(downloads.list().first?.expiresAt, now + 300_000)
    }

    func test_start_licenceRefused_fetchesNoMediaAndFails() {
        provider.acquireResult = AudioDrm.errorOfflineDeviceLimit
        downloads.start(downloadId: "d1", url: "https://cdn.example/a.m3u8", drm: drm())

        XCTAssertEqual(calls, ["prepare:d1", "acquire:d1"])
        XCTAssertEqual(events.last?.state, DownloadStates.failed)
        XCTAssertEqual(events.last?.error, AudioDrm.errorOfflineDeviceLimit)
        XCTAssertNil(meta.get("d1"))
        XCTAssertTrue(downloads.list().isEmpty)
    }

    func test_start_notEntitled_isTyped() {
        provider.acquireResult = AudioDrm.errorNotEntitled
        downloads.start(downloadId: "d1", url: "https://cdn.example/a.m3u8", drm: drm())
        XCTAssertEqual(events.last?.error, AudioDrm.errorNotEntitled)
        XCTAssertFalse(calls.contains("enqueue:d1"))
    }

    func test_start_unknownRefusal_collapsesToUnknown() {
        provider.acquireResult = "licenseDenied"
        downloads.start(downloadId: "d1", url: "https://cdn.example/a.m3u8", drm: drm())
        XCTAssertEqual(events.last?.error, AudioDrm.errorUnknown)
    }

    func test_start_withoutProvider_returnsNoProvider() {
        let bare = OfflineDownloads(
            engine: engine,
            meta: meta,
            executor: { $0() },
            clock: { 0 },
            providerSource: { nil }
        )
        XCTAssertEqual(bare.start(downloadId: "d1", url: "https://x/a.m3u8", drm: drm()), AudioOffline.codeNoProvider)
        XCTAssertTrue(calls.isEmpty)
    }

    func test_start_prepareIoError_failsNetworkWithoutLicence() {
        engine.prepareError = OfflineEngineError.network
        downloads.start(downloadId: "d1", url: "https://cdn.example/a.m3u8", drm: drm())
        XCTAssertEqual(events.last?.error, AudioDrm.errorNetwork)
        XCTAssertFalse(calls.contains("acquire:d1"))
    }

    func test_start_noSkdIdentifier_failsWithoutLicence() {
        engine.keyIdentifier = nil
        downloads.start(downloadId: "d1", url: "https://cdn.example/a.m3u8", drm: drm())
        XCTAssertEqual(events.last?.state, DownloadStates.failed)
        XCTAssertEqual(events.last?.error, AudioDrm.errorUnknown)
        XCTAssertFalse(calls.contains("acquire:d1"))
    }

    func test_start_segmentFailure_discardsPartialThenIsResumable() {
        downloads.start(downloadId: "d1", url: "https://cdn.example/a.m3u8?t=1", drm: drm())
        engine.emit("d1", .failed, 0.4, failedByNetwork: true)
        XCTAssertEqual(events.last?.state, DownloadStates.failed)
        XCTAssertEqual(events.last?.error, AudioDrm.errorNetwork)

        calls.removeAll()
        downloads.start(downloadId: "d1", url: "https://cdn.example/a.m3u8?t=2", drm: drm())

        XCTAssertEqual(calls, ["remove:d1", "prepare:d1", "acquire:d1", "enqueue:d1"])
        XCTAssertEqual(events.last { $0.state == DownloadStates.queued }?.progress, 0)
    }

    func test_start_alreadyCompleted_isIdempotent() {
        downloads.start(downloadId: "d1", url: "https://cdn.example/a.m3u8", drm: drm())
        engine.emit("d1", .completed, 1)
        calls.removeAll()

        XCTAssertNil(downloads.start(downloadId: "d1", url: "https://cdn.example/a.m3u8", drm: drm()))
        XCTAssertTrue(calls.isEmpty)
        XCTAssertEqual(events.last?.state, DownloadStates.completed)
    }

    func test_start_completedWithExpiredLicence_redownloads() {
        downloads.start(downloadId: "d1", url: "https://cdn.example/a.m3u8", drm: drm())
        engine.emit("d1", .completed, 1)
        provider.stateResult = AudioOffline.stateExpired
        calls.removeAll()

        XCTAssertNil(downloads.start(downloadId: "d1", url: "https://cdn.example/a.m3u8?t=2", drm: drm()))
        XCTAssertEqual(calls, ["remove:d1", "release:d1", "prepare:d1", "acquire:d1", "enqueue:d1"])
    }

    func test_cancelDuringLicenceRequest_releasesLicenceAndEnqueuesNothing() {
        provider.onAcquire = { [weak self] in self?.downloads.cancel("d1") }
        downloads.start(downloadId: "d1", url: "https://cdn.example/a.m3u8", drm: drm())

        XCTAssertFalse(calls.contains("enqueue:d1"))
        XCTAssertTrue(calls.contains("release:d1"))
        XCTAssertNil(meta.get("d1"))
        XCTAssertTrue(downloads.list().isEmpty)
    }

    func test_cancel_nonCompleted_removesEverything() {
        downloads.start(downloadId: "d1", url: "https://cdn.example/a.m3u8", drm: drm())
        engine.emit("d1", .downloading, 0.2)
        calls.removeAll()

        downloads.cancel("d1")

        XCTAssertEqual(calls, ["remove:d1", "release:d1"])
        XCTAssertNil(meta.get("d1"))
        XCTAssertNil(engine.get("d1"))
    }

    func test_cancel_completed_isNoOp() {
        downloads.start(downloadId: "d1", url: "https://cdn.example/a.m3u8", drm: drm())
        engine.emit("d1", .completed, 1)
        calls.removeAll()

        downloads.cancel("d1")

        XCTAssertTrue(calls.isEmpty)
        XCTAssertNotNil(meta.get("d1"))
        XCTAssertEqual(downloads.list().count, 1)
    }

    func test_list_skipsFailedEngineRows() {
        engine.emit("ghost", .failed, 0.2)
        XCTAssertTrue(downloads.list().isEmpty)
    }

    func test_init_purgesFailedEngineRows() {
        engine.emit("ghost", .failed, 0.2)
        let cleaned = OfflineDownloads(
            engine: engine,
            meta: meta,
            executor: { $0() },
            clock: { [self] in now },
            providerSource: { [self] in provider }
        )
        XCTAssertNil(engine.get("ghost"))
        XCTAssertTrue(cleaned.list().isEmpty)
    }

    func test_delete_completed_removesAssetMetaAndLicence_ignoringReleaseFailure() {
        downloads.start(downloadId: "d1", url: "https://cdn.example/a.m3u8", drm: drm())
        engine.emit("d1", .completed, 1)
        provider.releaseThrows = true
        calls.removeAll()

        downloads.delete("d1")

        XCTAssertEqual(calls, ["remove:d1", "release:d1"])
        XCTAssertNil(meta.get("d1"))
        XCTAssertTrue(downloads.list().isEmpty)
    }

    func test_renew_success_refreshesExpiresAt() {
        downloads.start(downloadId: "d1", url: "https://cdn.example/a.m3u8", drm: drm())
        engine.emit("d1", .completed, 1)
        now += 20 * 24 * 60 * 60 * 1000
        var result: String? = "pending"
        downloads.renew("d1", drm: nil) { result = $0 }

        XCTAssertNil(result)
        XCTAssertEqual(downloads.list().first?.expiresAt, now + OfflineExpiry.licenseValidityMs)
        XCTAssertEqual(events.last?.state, DownloadStates.completed)
    }

    func test_renew_success_usesProviderExpiresAt() {
        downloads.start(downloadId: "d1", url: "https://cdn.example/a.m3u8", drm: drm())
        engine.emit("d1", .completed, 1)
        provider.expiresAtResult = now + 300_000
        var result: String? = "pending"
        downloads.renew("d1", drm: nil) { result = $0 }

        XCTAssertNil(result)
        XCTAssertEqual(downloads.list().first?.expiresAt, now + 300_000)
    }

    func test_renew_refused_movesToExpiredWithTypedError() {
        downloads.start(downloadId: "d1", url: "https://cdn.example/a.m3u8", drm: drm())
        engine.emit("d1", .completed, 1)
        let expiresBefore = meta.get("d1")!.expiresAt
        provider.renewResult = AudioDrm.errorNotEntitled
        provider.expireOnRenewRefusal = true
        var result: String?
        downloads.renew("d1", drm: nil) { result = $0 }

        XCTAssertEqual(result, AudioDrm.errorNotEntitled)
        XCTAssertEqual(events.last?.state, DownloadStates.expired)
        XCTAssertEqual(events.last?.error, AudioDrm.errorNotEntitled)
        XCTAssertEqual(meta.get("d1")?.expiresAt, expiresBefore)
    }

    func test_renew_refusedWhileProviderStateStaysActive_emitsNoExpiredEvent() {
        downloads.start(downloadId: "d1", url: "https://cdn.example/a.m3u8", drm: drm())
        engine.emit("d1", .completed, 1)
        let eventsBefore = events.count
        provider.renewResult = AudioDrm.errorExpired
        var result: String?
        downloads.renew("d1", drm: nil) { result = $0 }

        XCTAssertEqual(result, AudioDrm.errorExpired)
        XCTAssertEqual(events.count, eventsBefore)
        XCTAssertEqual(downloads.list().first?.state, DownloadStates.completed)
    }

    func test_start_whileAlreadyPending_isNoOp() {
        provider.onAcquire = { [weak self] in
            XCTAssertNil(self?.downloads.start(downloadId: "d1", url: "https://cdn.example/a.m3u8", drm: self?.drm()))
        }
        downloads.start(downloadId: "d1", url: "https://cdn.example/a.m3u8", drm: drm())
        XCTAssertEqual(calls, ["prepare:d1", "acquire:d1", "enqueue:d1"])
    }

    func test_start_enqueueThrows_releasesLicenceCleansMetaAndFails() {
        engine.enqueueError = OfflineEngineError.unknown
        downloads.start(downloadId: "d1", url: "https://cdn.example/a.m3u8", drm: drm())
        XCTAssertTrue(calls.contains("release:d1"))
        XCTAssertNil(meta.get("d1"))
        XCTAssertEqual(events.last?.state, DownloadStates.failed)
        XCTAssertEqual(events.last?.error, AudioDrm.errorUnknown)
    }

    func test_renew_networkFailure_doesNotExpire() {
        downloads.start(downloadId: "d1", url: "https://cdn.example/a.m3u8", drm: drm())
        engine.emit("d1", .completed, 1)
        let eventsBefore = events.count
        provider.renewResult = AudioDrm.errorNetwork
        var result: String?
        downloads.renew("d1", drm: nil) { result = $0 }
        XCTAssertEqual(result, AudioDrm.errorNetwork)
        XCTAssertEqual(events.count, eventsBefore)
    }

    func test_renew_unknownDownload_andNoProvider() {
        var result: String?
        downloads.renew("nope", drm: nil) { result = $0 }
        XCTAssertEqual(result, AudioDrm.errorUnknown)

        let bare = OfflineDownloads(
            engine: engine,
            meta: meta,
            executor: { $0() },
            clock: { 0 },
            providerSource: { nil }
        )
        bare.renew("d1", drm: nil) { result = $0 }
        XCTAssertEqual(result, AudioOffline.codeNoProvider)
    }

    func test_list_marksCompletedDownloadExpiredWhenProviderSaysSo() {
        downloads.start(downloadId: "d1", url: "https://cdn.example/a.m3u8", drm: drm())
        engine.emit("d1", .completed, 1)
        provider.stateResult = AudioOffline.stateExpired
        provider.needsRenewalResult = true
        let info = downloads.list().first
        XCTAssertEqual(info?.state, DownloadStates.expired)
        XCTAssertEqual(info?.needsRenewal, true)
    }

    func test_expiryMaths_is27Days() {
        XCTAssertEqual(OfflineExpiry.licenseValidityMs, 27 * 24 * 60 * 60 * 1000)
        XCTAssertEqual(OfflineExpiry.expiresAt(5_000), 5_000 + OfflineExpiry.licenseValidityMs)
    }

    func test_metaStore_persistsAndRemoves() {
        let file = tmp.appendingPathComponent("persist.json")
        OfflineMetaStore(file: file).put("d1", entry: OfflineMetaStore.Entry(acquiredAt: 1, expiresAt: 2))
        let reopened = OfflineMetaStore(file: file)
        XCTAssertEqual(reopened.get("d1"), OfflineMetaStore.Entry(acquiredAt: 1, expiresAt: 2))
        reopened.remove("d1")
        XCTAssertNil(OfflineMetaStore(file: file).get("d1"))
        let text = (try? String(contentsOf: file)) ?? ""
        XCTAssertFalse(text.contains("http"))
    }

    func test_storage_isExcludedFromBackup() throws {
        let dir = tmp.appendingPathComponent("offline", isDirectory: true)
        try FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
        OfflineStorage.excludeFromBackup(dir)
        let values = try dir.resourceValues(forKeys: [.isExcludedFromBackupKey])
        XCTAssertEqual(values.isExcludedFromBackup, true)
    }

    func test_hlsParser_readsSkdFromMediaAndMaster() {
        let media = """
        #EXTM3U
        #EXT-X-KEY:METHOD=SAMPLE-AES,URI="skd://kid-hex",KEYFORMAT="com.apple.streamingkeydelivery"
        #EXTINF:4,
        seg0.ts
        """
        XCTAssertEqual(HlsFairPlayKey.firstSkdIdentifier(in: media), "skd://kid-hex")

        let master = """
        #EXTM3U
        #EXT-X-STREAM-INF:BANDWIDTH=128000
        audio/stream.m3u8
        """
        let base = URL(string: "https://cdn.example/audio.m3u8")!
        XCTAssertEqual(
            HlsFairPlayKey.mediaPlaylistURLs(in: master, base: base).first,
            URL(string: "https://cdn.example/audio/stream.m3u8")
        )
        XCTAssertNil(HlsFairPlayKey.firstSkdIdentifier(in: master))

        let session = """
        #EXTM3U
        #EXT-X-SESSION-KEY:METHOD=SAMPLE-AES,URI="skd://session-kid",KEYFORMAT="com.apple.streamingkeydelivery"
        #EXT-X-STREAM-INF:BANDWIDTH=128000
        audio/stream.m3u8
        """
        XCTAssertEqual(HlsFairPlayKey.firstSkdIdentifier(in: session), "skd://session-kid")
    }
}

final class FakeEngine: OfflineEngine {
    weak var listener: OfflineEngineListener?
    var prepareError: Error?
    var enqueueError: Error?
    var keyIdentifier: String? = "skd://kid"
    var store: [String: EngineDownload] = [:]
    var calls: ((String) -> Void)?

    func prepare(downloadId: String, url: String) throws -> PreparedDownload {
        calls?("prepare:\(downloadId)")
        if let prepareError { throw prepareError }
        guard let source = URL(string: url) else { throw OfflineEngineError.unknown }
        return PreparedDownload(downloadId: downloadId, keyIdentifier: keyIdentifier, url: source)
    }

    func enqueue(_ prepared: PreparedDownload) throws {
        calls?("enqueue:\(prepared.downloadId)")
        if let enqueueError { throw enqueueError }
        let previous = store[prepared.downloadId]
        store[prepared.downloadId] = EngineDownload(
            id: prepared.downloadId,
            state: .queued,
            progress: previous?.progress ?? 0,
            localURL: previous?.localURL,
            failedByNetwork: false
        )
    }

    func remove(_ downloadId: String) {
        calls?("remove:\(downloadId)")
        store.removeValue(forKey: downloadId)
    }

    func get(_ downloadId: String) -> EngineDownload? { store[downloadId] }

    func all() -> [EngineDownload] { Array(store.values) }

    func emit(_ id: String, _ state: EngineState, _ progress: Float, failedByNetwork: Bool = true) {
        let updated = EngineDownload(
            id: id,
            state: state,
            progress: progress,
            localURL: store[id]?.localURL,
            failedByNetwork: failedByNetwork
        )
        store[id] = updated
        listener?.onChanged(updated)
    }
}
