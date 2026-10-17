//
//  OfflineMetaStore.swift
//  PlaylistPlugin
//
//  Plugin-owned download metadata: only the licence clock. Delivery URLs are never stored here.
//  Lives under Application Support and is excluded from backup (Story 59.5).
//

import Foundation

/// Where offline media, index and metadata live: Application Support, `isExcludedFromBackup`.
enum OfflineStorage {
    static let folderName = "org.dwbn.plugins.playlist.offline"

    static func rootDirectory(fileManager: FileManager = .default) throws -> URL {
        let appSupport = try fileManager.url(
            for: .applicationSupportDirectory,
            in: .userDomainMask,
            appropriateFor: nil,
            create: true
        )
        let root = appSupport.appendingPathComponent(folderName, isDirectory: true)
        try fileManager.createDirectory(at: root, withIntermediateDirectories: true)
        excludeFromBackup(root)
        let assets = root.appendingPathComponent("assets", isDirectory: true)
        try fileManager.createDirectory(at: assets, withIntermediateDirectories: true)
        excludeFromBackup(assets)
        return root
    }

    static func metaFile(root: URL) -> URL {
        root.appendingPathComponent("meta.json")
    }

    static func indexFile(root: URL) -> URL {
        root.appendingPathComponent("index.json")
    }

    static func assetsDirectory(root: URL) -> URL {
        root.appendingPathComponent("assets", isDirectory: true)
    }

    static func excludeFromBackup(_ url: URL) {
        var url = url
        var values = URLResourceValues()
        values.isExcludedFromBackup = true
        try? url.setResourceValues(values)
    }
}

/// Plugin-owned download metadata: only the licence clock. Delivery URLs are never stored here.
final class OfflineMetaStore {
    struct Entry: Equatable {
        let acquiredAt: Int64
        let expiresAt: Int64
    }

    private let file: URL
    private let lock = NSLock()

    init(file: URL) {
        self.file = file
    }

    func get(_ downloadId: String) -> Entry? {
        lock.lock()
        defer { lock.unlock() }
        return read()[downloadId]
    }

    func put(_ downloadId: String, entry: Entry) {
        lock.lock()
        defer { lock.unlock() }
        var all = read()
        all[downloadId] = entry
        write(all)
    }

    func remove(_ downloadId: String) {
        lock.lock()
        defer { lock.unlock() }
        var all = read()
        if all.removeValue(forKey: downloadId) != nil {
            write(all)
        }
    }

    func all() -> [String: Entry] {
        lock.lock()
        defer { lock.unlock() }
        return read()
    }

    private func read() -> [String: Entry] {
        guard FileManager.default.fileExists(atPath: file.path) else {
            return [:]
        }
        guard
            let data = try? Data(contentsOf: file),
            let root = try? JSONSerialization.jsonObject(with: data) as? [String: Any]
        else {
            return [:]
        }
        var out: [String: Entry] = [:]
        for (key, value) in root {
            guard let item = value as? [String: Any] else { continue }
            let acquiredAt = int64(item["acquiredAt"])
            let expiresAt = int64(item["expiresAt"])
            out[key] = Entry(acquiredAt: acquiredAt, expiresAt: expiresAt)
        }
        return out
    }

    private func write(_ all: [String: Entry]) {
        var root: [String: Any] = [:]
        for (id, entry) in all {
            root[id] = ["acquiredAt": entry.acquiredAt, "expiresAt": entry.expiresAt]
        }
        guard let data = try? JSONSerialization.data(withJSONObject: root, options: []) else {
            return
        }
        try? FileManager.default.createDirectory(
            at: file.deletingLastPathComponent(),
            withIntermediateDirectories: true
        )
        OfflineStorage.excludeFromBackup(file.deletingLastPathComponent())
        let tmp = file.appendingPathExtension("tmp")
        do {
            try data.write(to: tmp, options: .atomic)
            _ = try? FileManager.default.removeItem(at: file)
            try FileManager.default.moveItem(at: tmp, to: file)
        } catch {
            try? data.write(to: file, options: .atomic)
            try? FileManager.default.removeItem(at: tmp)
        }
        OfflineStorage.excludeFromBackup(file)
    }

    private func int64(_ value: Any?) -> Int64 {
        if let n = value as? Int64 { return n }
        if let n = value as? Int { return Int64(n) }
        if let n = value as? NSNumber { return n.int64Value }
        if let s = value as? String, let n = Int64(s) { return n }
        return 0
    }
}
