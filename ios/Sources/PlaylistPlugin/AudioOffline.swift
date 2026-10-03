//
//  AudioOffline.swift
//  PlaylistPlugin
//
//  Plugin-owned registry for the host's `AudioOfflineProvider`. Register it from the app
//  via `AudioOffline.setProvider(_:)`; this module does not depend on drm-kit.
//

import AVFoundation
import Foundation

/// Plugin-owned offline-licence provider registry. The host app registers drm-kit in Story 59.6;
/// this module does not depend on drm-kit. Mirrors Android `AudioOffline`.
public final class AudioOffline {

    public static let codeNoProvider = AudioDrm.codeNoProvider

    /// Plugin-owned background `AVAssetDownloadURLSession` identifier. The host must forward
    /// `application(_:handleEventsForBackgroundURLSession:completionHandler:)` when `identifier`
    /// matches this value (wired in Story 59.6).
    public static let backgroundSessionIdentifier = "org.dwbn.plugins.playlist.offline"

    public static let stateNone = "none"
    public static let stateActive = "active"
    public static let stateExpired = "expired"

    private static let lock = NSLock()
    private static var _provider: AudioOfflineProvider?
    private static var backgroundCompletionHandler: (() -> Void)?

    private init() {}

    public static func setProvider(_ next: AudioOfflineProvider?) {
        lock.lock()
        _provider = next
        lock.unlock()
    }

    public static func getProvider() -> AudioOfflineProvider? {
        lock.lock()
        defer { lock.unlock() }
        return _provider
    }

    /// Typed error for a provider result: unknown discriminators collapse to `unknown`.
    public static func typedError(_ error: String?) -> String {
        guard let error = error else {
            return AudioDrm.errorUnknown
        }
        return AudioDrm.typedError(error)
    }

    /// Provider `state` for `downloadId`; unknown values read as `none`.
    public static func stateOf(_ registered: AudioOfflineProvider, downloadId: String) -> String {
        let state = registered.state(downloadId: downloadId)
        if state == stateActive || state == stateExpired {
            return state
        }
        return stateNone
    }

    /// Attach the stored offline licence to `asset`, or no-op when no provider is registered.
    public static func attachOffline(downloadId: String, asset: AVURLAsset) {
        guard let registered = getProvider() else {
            return
        }
        registered.attachOffline(downloadId: downloadId, asset: asset)
    }

    /// Store the system completion handler for the plugin-owned background session.
    public static func handleEventsForBackgroundURLSession(
        _ identifier: String,
        completionHandler: @escaping () -> Void
    ) {
        guard identifier == backgroundSessionIdentifier else {
            return
        }
        lock.lock()
        backgroundCompletionHandler = completionHandler
        lock.unlock()
        // Recreate the singleton so the session exists before the system delivers events.
        _ = OfflineDownloads.shared
    }

    static func takeBackgroundCompletionHandler() -> (() -> Void)? {
        lock.lock()
        defer { lock.unlock() }
        let handler = backgroundCompletionHandler
        backgroundCompletionHandler = nil
        return handler
    }
}
