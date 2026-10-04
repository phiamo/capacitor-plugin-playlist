//
//  AvAssetOfflineEngine.swift
//  PlaylistPlugin
//
//  `AVAssetDownloadURLSession` segment engine (Story 59.5). FairPlay is not exercised in XCTest;
//  unit tests inject a fake `OfflineEngine`.
//

import AVFoundation
import Foundation

enum EngineState: String {
    case queued
    case downloading
    case completed
    case failed
}

/// Engine view of one download. The delivery URL is never stored, logged or forwarded.
struct EngineDownload {
    let id: String
    let state: EngineState
    let progress: Float
    let localURL: URL?
    let failedByNetwork: Bool
}

/// A prepared HLS download: the FairPlay `skd://` identifier to license, and the source URL used
/// only for the in-flight job.
struct PreparedDownload {
    let downloadId: String
    let keyIdentifier: String?
    let url: URL
}

enum OfflineEngineError: Error {
    case network
    case unknown
}

protocol OfflineEngineListener: AnyObject {
    func onChanged(_ download: EngineDownload)
    func onRemoved(_ downloadId: String)
}

/// Segment engine behind `OfflineDownloads`. Production is `AvAssetOfflineEngine`; unit tests use a
/// fake so licence ordering and cleanup can be asserted without FairPlay or a network.
protocol OfflineEngine: AnyObject {
    var listener: OfflineEngineListener? { get set }

    /// Blocking; never on the main thread. Reads the HLS playlists, fetches no media.
    func prepare(downloadId: String, url: String) throws -> PreparedDownload

    /// Starts the asset download. Only called after the licence was acquired.
    func enqueue(_ prepared: PreparedDownload) throws

    /// Removes index entry and downloaded media.
    func remove(_ downloadId: String)

    func get(_ downloadId: String) -> EngineDownload?

    func all() -> [EngineDownload]
}

/// Reads the first FairPlay `skd://` URI from an HLS playlist (master or media).
enum HlsFairPlayKey {
    static func firstSkdIdentifier(in playlist: String) -> String? {
        for line in playlist.split(whereSeparator: \.isNewline) {
            let trimmed = line.trimmingCharacters(in: .whitespaces)
            guard trimmed.hasPrefix("#EXT-X-KEY:") || trimmed.hasPrefix("#EXT-X-SESSION-KEY:") else {
                continue
            }
            if let uri = attribute(named: "URI", in: trimmed), uri.hasPrefix("skd://") {
                return uri
            }
        }
        return nil
    }

    static func mediaPlaylistURLs(in playlist: String, base: URL) -> [URL] {
        var urls: [URL] = []
        let lines = playlist.split(omittingEmptySubsequences: false, whereSeparator: \.isNewline)
            .map { $0.trimmingCharacters(in: .whitespaces) }
        var expectStreamURI = false
        for line in lines {
            if expectStreamURI {
                expectStreamURI = false
                if !line.isEmpty, !line.hasPrefix("#"), let url = URL(string: line, relativeTo: base)?.absoluteURL {
                    urls.append(url)
                }
                continue
            }
            if line.hasPrefix("#EXT-X-STREAM-INF:") {
                expectStreamURI = true
                continue
            }
            if line.hasPrefix("#EXT-X-MEDIA:"), let uri = attribute(named: "URI", in: line),
               let url = URL(string: uri, relativeTo: base)?.absoluteURL {
                urls.append(url)
            }
        }
        return urls
    }

    private static func attribute(named name: String, in line: String) -> String? {
        let pattern = "\(name)="
        guard let range = line.range(of: pattern, options: .caseInsensitive) else {
            return nil
        }
        var rest = line[range.upperBound...]
        if rest.first == "\"" {
            rest = rest.dropFirst()
            guard let end = rest.firstIndex(of: "\"") else { return nil }
            return String(rest[..<end])
        }
        if rest.first == "'" {
            rest = rest.dropFirst()
            guard let end = rest.firstIndex(of: "'") else { return nil }
            return String(rest[..<end])
        }
        let end = rest.firstIndex(where: { $0 == "," || $0.isNewline }) ?? rest.endIndex
        return String(rest[..<end])
    }
}

/// Resolves a completed `.movpkg` from the current sandbox. Stored absolute `localPath` values
/// (old app-container UUIDs) are never trusted after restore/reinstall.
enum OfflineLocalAsset {
    static func fileName(for downloadId: String) -> String {
        let name = (downloadId as NSString).lastPathComponent
        if name.isEmpty || name == "." || name == ".." {
            return "_"
        }
        return name
    }

    static func relativeHint(downloadId: String) -> String {
        "assets/\(fileName(for: downloadId)).movpkg"
    }

    static func movpkgURL(downloadId: String, root: URL) -> URL {
        OfflineStorage.assetsDirectory(root: root)
            .appendingPathComponent("\(fileName(for: downloadId)).movpkg", isDirectory: true)
    }

    /// Ignores `storedLocalPath`. Returns the current-sandbox URL when the package exists.
    static func resolveCompletedURL(
        downloadId: String,
        storedLocalPath: String?,
        root: URL,
        fileManager: FileManager = .default
    ) -> URL? {
        _ = storedLocalPath
        let dest = movpkgURL(downloadId: downloadId, root: root)
        var isDir: ObjCBool = false
        guard fileManager.fileExists(atPath: dest.path, isDirectory: &isDir) else {
            return nil
        }
        return dest
    }
}

/// `AVAssetDownloadURLSession` implementation. A failed download is discarded; a retry with a
/// fresh URL starts a new task (no query-stripped cache reuse).
final class AvAssetOfflineEngine: NSObject, OfflineEngine, AVAssetDownloadDelegate {
    weak var listener: OfflineEngineListener?

    private let root: URL
    private let fileManager: FileManager
    private let fetcher: (URL) throws -> String
    private let lock = NSLock()
    private var store: [String: EngineDownload] = [:]
    private var tasks: [String: AVAssetDownloadTask] = [:]
    private lazy var session: AVAssetDownloadURLSession = makeSession()

    init(
        root: URL,
        fileManager: FileManager = .default,
        fetcher: ((URL) throws -> String)? = nil
    ) {
        self.root = root
        self.fileManager = fileManager
        self.fetcher = fetcher ?? Self.fetchPlaylist
        super.init()
        loadIndex()
        _ = session
    }

    func prepare(downloadId: String, url: String) throws -> PreparedDownload {
        guard let source = URL(string: url) else {
            throw OfflineEngineError.unknown
        }
        let playlist: String
        do {
            playlist = try fetcher(source)
        } catch {
            throw mapFetchError(error)
        }
        if let skd = HlsFairPlayKey.firstSkdIdentifier(in: playlist) {
            return PreparedDownload(downloadId: downloadId, keyIdentifier: skd, url: source)
        }
        for media in HlsFairPlayKey.mediaPlaylistURLs(in: playlist, base: source) {
            let child: String
            do {
                child = try fetcher(media)
            } catch {
                throw mapFetchError(error)
            }
            if let skd = HlsFairPlayKey.firstSkdIdentifier(in: child) {
                return PreparedDownload(downloadId: downloadId, keyIdentifier: skd, url: source)
            }
        }
        return PreparedDownload(downloadId: downloadId, keyIdentifier: nil, url: source)
    }

    func enqueue(_ prepared: PreparedDownload) throws {
        lock.lock()
        discardLocked(prepared.downloadId)
        lock.unlock()

        let asset = AVURLAsset(url: prepared.url)
        // Persistable key must be on the download asset as well as the later local playback asset.
        AudioOffline.attachOffline(downloadId: prepared.downloadId, asset: asset)
        asset.resourceLoader.preloadsEligibleContentKeys = true
        let config = AVAssetDownloadConfiguration(asset: asset, title: prepared.downloadId)
        let task = session.makeAssetDownloadTask(downloadConfiguration: config)
        task.taskDescription = prepared.downloadId
        let snapshot = EngineDownload(
            id: prepared.downloadId,
            state: .queued,
            progress: 0,
            localURL: nil,
            failedByNetwork: false
        )
        lock.lock()
        tasks[prepared.downloadId] = task
        store[prepared.downloadId] = snapshot
        persistIndexLocked()
        lock.unlock()
        task.resume()
        listener?.onChanged(snapshot)
    }

    func remove(_ downloadId: String) {
        lock.lock()
        discardLocked(downloadId)
        store.removeValue(forKey: downloadId)
        persistIndexLocked()
        lock.unlock()
        listener?.onRemoved(downloadId)
    }

    func get(_ downloadId: String) -> EngineDownload? {
        lock.lock()
        defer { lock.unlock() }
        return store[downloadId]
    }

    func all() -> [EngineDownload] {
        lock.lock()
        defer { lock.unlock() }
        return Array(store.values)
    }

    func urlSessionDidFinishEvents(forBackgroundURLSession session: URLSession) {
        let handler = AudioOffline.takeBackgroundCompletionHandler()
        DispatchQueue.main.async {
            handler?()
        }
    }

    func urlSession(
        _ session: URLSession,
        assetDownloadTask: AVAssetDownloadTask,
        didFinishDownloadingTo location: URL
    ) {
        guard let downloadId = assetDownloadTask.taskDescription,
              isLive(assetDownloadTask, downloadId: downloadId) else { return }
        let dest = movpkgURL(for: downloadId)
        try? fileManager.removeItem(at: dest)
        do {
            try fileManager.moveItem(at: location, to: dest)
            OfflineStorage.excludeFromBackup(dest)
            update(downloadId, state: .completed, progress: 1, localURL: dest, failedByNetwork: false)
        } catch {
            update(downloadId, state: .failed, progress: progress(of: downloadId), localURL: nil, failedByNetwork: false)
            try? fileManager.removeItem(at: location)
        }
    }

    func urlSession(
        _ session: URLSession,
        assetDownloadTask: AVAssetDownloadTask,
        didLoad timeRange: CMTimeRange,
        totalTimeRangesLoaded loadedTimeRanges: [NSValue],
        timeRangeExpectedToLoad: CMTimeRange
    ) {
        guard let downloadId = assetDownloadTask.taskDescription,
              isLive(assetDownloadTask, downloadId: downloadId) else { return }
        let loaded = loadedTimeRanges.reduce(0.0) { $0 + $1.timeRangeValue.duration.seconds }
        let expected = timeRangeExpectedToLoad.duration.seconds
        let progress = expected > 0 ? Float(min(1, max(0, loaded / expected))) : 0
        lock.lock()
        let local = store[downloadId]?.localURL
        lock.unlock()
        update(downloadId, state: .downloading, progress: progress, localURL: local, failedByNetwork: false)
    }

    func urlSession(_ session: URLSession, task: URLSessionTask, didCompleteWithError error: Error?) {
        guard let downloadId = task.taskDescription else { return }
        lock.lock()
        let live = store[downloadId] != nil && tasks[downloadId] === task
        if live {
            tasks.removeValue(forKey: downloadId)
        }
        let current = store[downloadId]
        lock.unlock()
        guard live else { return }
        if let error = error {
            let network = isNetworkError(error)
            discardMedia(downloadId)
            update(downloadId, state: .failed, progress: current?.progress ?? 0, localURL: nil, failedByNetwork: network)
            return
        }
        if current?.state != .completed {
            // Finished without a location (or move failed): treat as a failed download.
            discardMedia(downloadId)
            update(downloadId, state: .failed, progress: current?.progress ?? 0, localURL: nil, failedByNetwork: false)
        }
    }

    private func makeSession() -> AVAssetDownloadURLSession {
        let config = URLSessionConfiguration.background(withIdentifier: AudioOffline.backgroundSessionIdentifier)
        config.isDiscretionary = false
        config.sessionSendsLaunchEvents = true
        let session = AVAssetDownloadURLSession(
            configuration: config,
            assetDownloadDelegate: self,
            delegateQueue: OperationQueue()
        )
        session.getAllTasks { [weak self] all in
            guard let self else { return }
            self.lock.lock()
            for task in all {
                guard let downloadTask = task as? AVAssetDownloadTask,
                      let id = downloadTask.taskDescription,
                      !id.isEmpty,
                      self.tasks[id] == nil else { continue }
                self.tasks[id] = downloadTask
            }
            self.lock.unlock()
        }
        return session
    }

    private func discardLocked(_ downloadId: String) {
        if let task = tasks.removeValue(forKey: downloadId) {
            task.cancel()
        }
        if let local = store[downloadId]?.localURL {
            try? fileManager.removeItem(at: local)
        }
        try? fileManager.removeItem(at: movpkgURL(for: downloadId))
    }

    private func discardMedia(_ downloadId: String) {
        lock.lock()
        defer { lock.unlock() }
        if let local = store[downloadId]?.localURL {
            try? fileManager.removeItem(at: local)
        }
        try? fileManager.removeItem(at: movpkgURL(for: downloadId))
    }

    /// Ignore system callbacks after cancel/delete already cleared this download.
    private func isLive(_ task: URLSessionTask, downloadId: String) -> Bool {
        lock.lock()
        defer { lock.unlock() }
        return store[downloadId] != nil && tasks[downloadId] === task
    }

    private func movpkgURL(for downloadId: String) -> URL {
        OfflineLocalAsset.movpkgURL(downloadId: downloadId, root: root)
    }

    /// Single path component so `/` and `..` cannot leave `assets/`.
    static func sanitizedFileName(_ downloadId: String) -> String {
        OfflineLocalAsset.fileName(for: downloadId)
    }

    private func update(
        _ downloadId: String,
        state: EngineState,
        progress: Float,
        localURL: URL?,
        failedByNetwork: Bool
    ) {
        let snapshot = EngineDownload(
            id: downloadId,
            state: state,
            progress: progress,
            localURL: localURL,
            failedByNetwork: failedByNetwork
        )
        lock.lock()
        store[downloadId] = snapshot
        persistIndexLocked()
        lock.unlock()
        listener?.onChanged(snapshot)
    }

    private func progress(of downloadId: String) -> Float {
        lock.lock()
        defer { lock.unlock() }
        return store[downloadId]?.progress ?? 0
    }

    private func persistIndexLocked() {
        var rootObj: [String: Any] = [:]
        for (id, download) in store {
            var item: [String: Any] = [
                "state": download.state.rawValue,
                "progress": download.progress,
                "failedByNetwork": download.failedByNetwork
            ]
            if download.state == .completed {
                item["localPath"] = OfflineLocalAsset.relativeHint(downloadId: id)
            }
            rootObj[id] = item
        }
        let file = OfflineStorage.indexFile(root: root)
        guard let data = try? JSONSerialization.data(withJSONObject: rootObj, options: []) else {
            return
        }
        try? fileManager.createDirectory(at: root, withIntermediateDirectories: true)
        OfflineStorage.excludeFromBackup(root)
        try? data.write(to: file, options: .atomic)
        OfflineStorage.excludeFromBackup(file)
    }

    private func loadIndex() {
        let file = OfflineStorage.indexFile(root: root)
        guard
            let data = try? Data(contentsOf: file),
            let rootObj = try? JSONSerialization.jsonObject(with: data) as? [String: Any]
        else {
            return
        }
        var downgraded = false
        for (id, value) in rootObj {
            guard let item = value as? [String: Any] else { continue }
            var state = EngineState(rawValue: item["state"] as? String ?? "") ?? .failed
            let progress = (item["progress"] as? NSNumber)?.floatValue ?? 0
            let failedByNetwork = item["failedByNetwork"] as? Bool ?? true
            var local: URL?
            if state == .completed {
                local = OfflineLocalAsset.resolveCompletedURL(
                    downloadId: id,
                    storedLocalPath: item["localPath"] as? String,
                    root: root,
                    fileManager: fileManager
                )
                if local == nil {
                    state = .failed
                    downgraded = true
                }
            }
            store[id] = EngineDownload(
                id: id,
                state: state,
                progress: progress,
                localURL: local,
                failedByNetwork: failedByNetwork
            )
        }
        if downgraded {
            persistIndexLocked()
        }
    }

    private func mapFetchError(_ error: Error) -> OfflineEngineError {
        isNetworkError(error) ? .network : .unknown
    }

    private func isNetworkError(_ error: Error) -> Bool {
        let ns = error as NSError
        if ns.domain == NSURLErrorDomain { return true }
        if error is URLError { return true }
        if let engine = error as? OfflineEngineError { return engine == .network }
        return false
    }

    private static func fetchPlaylist(_ url: URL) throws -> String {
        var result: Result<String, Error>?
        let sem = DispatchSemaphore(value: 0)
        var request = URLRequest(url: url)
        request.timeoutInterval = 30
        let task = URLSession.shared.dataTask(with: request) { data, response, error in
            defer { sem.signal() }
            if let error {
                result = .failure(error)
                return
            }
            let status = (response as? HTTPURLResponse)?.statusCode ?? 0
            if status >= 400 {
                result = .failure(URLError(.badServerResponse))
                return
            }
            guard let data, let text = String(data: data, encoding: .utf8) else {
                result = .failure(URLError(.cannotDecodeContentData))
                return
            }
            result = .success(text)
        }
        task.resume()
        sem.wait()
        switch result {
        case .success(let text):
            return text
        case .failure(let error):
            throw error
        case nil:
            throw URLError(.unknown)
        }
    }
}
