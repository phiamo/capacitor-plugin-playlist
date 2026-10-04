//
//  OfflineDownloads.swift
//  PlaylistPlugin
//
//  Download orchestration (Story 59.5). Order on start: read FairPlay `skd://` from the remote
//  HLS → `provider.acquire` (background) → only then start the download task, so a refused
//  licence (incl. `offlineDeviceLimit`) fetches no media. Licences and renewal belong
//  to the host `AudioOfflineProvider`; this class never touches drm-kit.
//

import Capacitor
import Foundation

/// Licence clock: `expiresAt` = last successful acquire/renew + 27 days (CDM time is not read).
enum OfflineExpiry {
    static let licenseValidityDays: Int64 = 27
    static let licenseValidityMs: Int64 = licenseValidityDays * 24 * 60 * 60 * 1000

    static func expiresAt(_ acquiredAtMs: Int64) -> Int64 {
        acquiredAtMs + licenseValidityMs
    }
}

/// Wire states of the `download` event / `listDownloads`.
enum DownloadStates {
    static let queued = "queued"
    static let downloading = "downloading"
    static let completed = "completed"
    static let failed = "failed"
    static let expired = "expired"
}

struct DownloadEvent {
    let downloadId: String
    let state: String
    let progress: Float
    let error: String?
}

struct DownloadInfo {
    let downloadId: String
    let state: String
    let progress: Float
    let expiresAt: Int64?
    let needsRenewal: Bool
}

/// Download orchestration (Story 59.5).
final class OfflineDownloads: OfflineEngineListener {
    typealias EventSink = (DownloadEvent) -> Void
    typealias Work = () -> Void

    var eventSink: EventSink?

    private let engine: OfflineEngine
    private let meta: OfflineMetaStore
    private let executor: (@escaping Work) -> Void
    private let clock: () -> Int64
    private let providerSource: () -> AudioOfflineProvider?

    /// downloadId -> token of the in-flight prepare/acquire; removal means "cancelled".
    private let pendingLock = NSLock()
    private var pending: [String: ObjectIdentifier] = [:]
    private var pendingTokens: [String: NSObject] = [:]

    init(
        engine: OfflineEngine,
        meta: OfflineMetaStore,
        executor: @escaping (@escaping Work) -> Void,
        clock: @escaping () -> Int64 = { Int64(Date().timeIntervalSince1970 * 1000) },
        providerSource: @escaping () -> AudioOfflineProvider? = AudioOffline.getProvider
    ) {
        self.engine = engine
        self.meta = meta
        self.executor = executor
        self.clock = clock
        self.providerSource = providerSource
        engine.listener = self
        purgeFailed()
    }

    /// Starts (or retries with a fresh URL) a download. Returns a failure code synchronously
    /// (`AudioDrm.codeNoProvider`) or nil; everything else is reported through `download` events.
    func start(downloadId: String, url: String, drm: JSObject?) -> String? {
        guard let provider = providerSource() else {
            return AudioOffline.codeNoProvider
        }
        if engine.get(downloadId)?.state == .completed, meta.get(downloadId) != nil {
            emit(downloadId, DownloadStates.completed, 1, nil)
            return nil
        }
        let token = NSObject()
        pendingLock.lock()
        if pending[downloadId] != nil {
            pendingLock.unlock()
            return nil // a start for this id is already in flight
        }
        pendingTokens[downloadId] = token
        pending[downloadId] = ObjectIdentifier(token)
        pendingLock.unlock()
        // iOS discards partial media on retry, so queued progress is 0 (not the failed remainder).
        emit(downloadId, DownloadStates.queued, 0, nil)
        executor { [weak self] in
            self?.runStart(provider: provider, downloadId: downloadId, url: url, drm: drm, token: token)
        }
        return nil
    }

    private func runStart(
        provider: AudioOfflineProvider,
        downloadId: String,
        url: String,
        drm: JSObject?,
        token: NSObject
    ) {
        defer { removePending(downloadId, token: token) }
        if let existing = engine.get(downloadId), existing.state != .completed {
            // iOS has no query-stripped cache: a retry discards partial media.
            engine.remove(downloadId)
        }
        let prepared: PreparedDownload
        do {
            prepared = try engine.prepare(downloadId: downloadId, url: url)
        } catch let error as OfflineEngineError where error == .network {
            fail(downloadId, AudioDrm.errorNetwork, token: token)
            return
        } catch {
            if isNetwork(error) {
                fail(downloadId, AudioDrm.errorNetwork, token: token)
            } else {
                fail(downloadId, AudioDrm.errorUnknown, token: token)
            }
            return
        }
        guard let keyIdentifier = prepared.keyIdentifier else {
            fail(downloadId, AudioDrm.errorUnknown, token: token)
            return
        }
        guard isCurrent(downloadId, token: token) else { return }
        let refusal = provider.acquire(
            downloadId: downloadId,
            keyIdentifier: keyIdentifier,
            drm: drm ?? [:]
        )
        if let refusal {
            fail(downloadId, AudioOffline.typedError(refusal), token: token)
            return
        }
        guard isCurrent(downloadId, token: token) else {
            // Cancelled or deleted while the licence request was in flight.
            releaseQuietly(provider, downloadId: downloadId)
            return
        }
        do {
            let now = clock()
            meta.put(downloadId, entry: OfflineMetaStore.Entry(acquiredAt: now, expiresAt: OfflineExpiry.expiresAt(now)))
            try engine.enqueue(prepared)
        } catch {
            releaseQuietly(provider, downloadId: downloadId)
            meta.remove(downloadId)
            fail(downloadId, AudioDrm.errorUnknown, token: token)
        }
    }

    private func fail(_ downloadId: String, _ error: String, token: NSObject) {
        guard isCurrent(downloadId, token: token) else { return }
        emit(downloadId, DownloadStates.failed, engine.get(downloadId)?.progress ?? 0, error)
    }

    /// Cancels a non-completed download; a no-op for completed ones.
    func cancel(_ downloadId: String) {
        if engine.get(downloadId)?.state == .completed {
            return
        }
        removeAll(downloadId)
    }

    /// Removes asset, index entry, metadata and the licence, in any state.
    func delete(_ downloadId: String) {
        removeAll(downloadId)
    }

    private func removeAll(_ downloadId: String) {
        pendingLock.lock()
        pending.removeValue(forKey: downloadId)
        pendingTokens.removeValue(forKey: downloadId)
        pendingLock.unlock()
        engine.remove(downloadId)
        meta.remove(downloadId)
        if let provider = providerSource() {
            executor { [weak self] in
                self?.releaseQuietly(provider, downloadId: downloadId)
            }
        }
    }

    private func releaseQuietly(_ provider: AudioOfflineProvider, downloadId: String) {
        provider.release(downloadId: downloadId)
    }

    /// Renews the licence online. `onResult` gets nil on success or the typed error; a refusal also
    /// moves the download to `expired` only when the provider's state for it is actually `expired`.
    func renew(_ downloadId: String, drm: JSObject?, onResult: @escaping (String?) -> Void) {
        guard let provider = providerSource() else {
            onResult(AudioOffline.codeNoProvider)
            return
        }
        if engine.get(downloadId) == nil && meta.get(downloadId) == nil {
            onResult(AudioDrm.errorUnknown)
            return
        }
        executor { [weak self] in
            guard let self else { return }
            let refusal = provider.renew(downloadId: downloadId, drm: drm)
            if refusal == nil {
                let now = self.clock()
                self.meta.put(downloadId, entry: OfflineMetaStore.Entry(acquiredAt: now, expiresAt: OfflineExpiry.expiresAt(now)))
                let current = self.engine.get(downloadId)
                self.emit(
                    downloadId,
                    current.map { self.stateName($0) } ?? DownloadStates.completed,
                    current?.progress ?? 1,
                    nil
                )
                onResult(nil)
            } else {
                let typed = AudioOffline.typedError(refusal)
                if AudioOffline.stateOf(provider, downloadId: downloadId) == AudioOffline.stateExpired {
                    self.emit(downloadId, DownloadStates.expired, self.engine.get(downloadId)?.progress ?? 1, typed)
                }
                onResult(typed)
            }
        }
    }

    func list() -> [DownloadInfo] {
        let provider = providerSource()
        var seen = Set<String>()
        var out: [DownloadInfo] = []
        for download in engine.all() where download.state != .failed {
            seen.insert(download.id)
            var state = stateName(download)
            if state == DownloadStates.completed,
               let provider,
               AudioOffline.stateOf(provider, downloadId: download.id) == AudioOffline.stateExpired {
                state = DownloadStates.expired
            }
            out.append(
                DownloadInfo(
                    downloadId: download.id,
                    state: state,
                    progress: download.progress,
                    expiresAt: meta.get(download.id)?.expiresAt,
                    needsRenewal: needsRenewal(provider, downloadId: download.id)
                )
            )
        }
        pendingLock.lock()
        let pendingIds = Array(pending.keys)
        pendingLock.unlock()
        for id in pendingIds where seen.insert(id).inserted {
            out.append(
                DownloadInfo(
                    downloadId: id,
                    state: DownloadStates.queued,
                    progress: 0,
                    expiresAt: meta.get(id)?.expiresAt,
                    needsRenewal: false
                )
            )
        }
        return out
    }

    func localAssetURL(_ downloadId: String) -> URL? {
        guard let url = engine.get(downloadId)?.localURL else { return nil }
        var isDir: ObjCBool = false
        guard FileManager.default.fileExists(atPath: url.path, isDirectory: &isDir) else {
            return nil
        }
        return url
    }

    func onChanged(_ download: EngineDownload) {
        emit(
            download.id,
            stateName(download),
            download.progress,
            download.state == .failed
                ? (download.failedByNetwork ? AudioDrm.errorNetwork : AudioDrm.errorUnknown)
                : nil
        )
    }

    func onRemoved(_ downloadId: String) {
        // Removal is already reflected by the callers (cancel/delete); nothing to emit.
    }

    private func purgeFailed() {
        for download in engine.all() where download.state == .failed {
            engine.remove(download.id)
            meta.remove(download.id)
        }
    }

    private func needsRenewal(_ provider: AudioOfflineProvider?, downloadId: String) -> Bool {
        provider?.needsRenewal(downloadId: downloadId) == true
    }

    private func emit(_ downloadId: String, _ state: String, _ progress: Float, _ error: String?) {
        eventSink?(DownloadEvent(downloadId: downloadId, state: state, progress: min(1, max(0, progress)), error: error))
    }

    private func stateName(_ download: EngineDownload) -> String {
        switch download.state {
        case .queued: return DownloadStates.queued
        case .downloading: return DownloadStates.downloading
        case .completed: return DownloadStates.completed
        case .failed: return DownloadStates.failed
        }
    }

    private func isCurrent(_ downloadId: String, token: NSObject) -> Bool {
        pendingLock.lock()
        defer { pendingLock.unlock() }
        return pendingTokens[downloadId] === token
    }

    private func removePending(_ downloadId: String, token: NSObject) {
        pendingLock.lock()
        defer { pendingLock.unlock() }
        if pendingTokens[downloadId] === token {
            pendingTokens.removeValue(forKey: downloadId)
            pending.removeValue(forKey: downloadId)
        }
    }

    private func isNetwork(_ error: Error) -> Bool {
        if let engine = error as? OfflineEngineError { return engine == .network }
        let ns = error as NSError
        return ns.domain == NSURLErrorDomain || error is URLError
    }

    // MARK: - Singleton

    private static let sharedLock = NSLock()
    private static var instance: OfflineDownloads?

    static var shared: OfflineDownloads {
        sharedLock.lock()
        defer { sharedLock.unlock() }
        if let instance { return instance }
        let created = createDefault()
        instance = created
        return created
    }

    static func setSharedForTest(_ next: OfflineDownloads?) {
        sharedLock.lock()
        instance = next
        sharedLock.unlock()
    }

    private static func createDefault() -> OfflineDownloads {
        let root: URL
        do {
            root = try OfflineStorage.rootDirectory()
        } catch {
            root = FileManager.default.temporaryDirectory.appendingPathComponent(OfflineStorage.folderName, isDirectory: true)
            try? FileManager.default.createDirectory(at: root, withIntermediateDirectories: true)
            OfflineStorage.excludeFromBackup(root)
            let assets = OfflineStorage.assetsDirectory(root: root)
            try? FileManager.default.createDirectory(at: assets, withIntermediateDirectories: true)
            OfflineStorage.excludeFromBackup(assets)
        }
        let queue = DispatchQueue(label: "org.dwbn.plugins.playlist.offline", qos: .utility)
        return OfflineDownloads(
            engine: AvAssetOfflineEngine(root: root),
            meta: OfflineMetaStore(file: OfflineStorage.metaFile(root: root)),
            executor: { work in queue.async(execute: work) }
        )
    }
}
