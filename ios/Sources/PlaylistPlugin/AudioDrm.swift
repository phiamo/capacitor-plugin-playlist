//
//  AudioDrm.swift
//  PlaylistPlugin
//
//  Story 58.5: Swift port of the Android `AudioDrm`/`AudioDrmProvider`/`AudioDrmSession` trio
//  (org.dwbn.plugins.playlist), reusing capacitor-video-player's `VideoDrm.swift` (Story 58.4) as
//  the AVFoundation-shaped Swift pattern (attach-to-asset, not Android Media3's
//  `applyDrm(MediaItem.Builder)`).
//

import AVFoundation
import Capacitor
import Foundation

/// Plugin-owned DRM session. The host (Story 58.5) wraps drm-kit `FairPlaySession`; this plugin
/// never imports drm-kit.
public protocol AudioDrmSession: AnyObject {
    /// Routes the asset's FairPlay key requests through the session. Call before any
    /// `AVPlayerItem` is built from `asset`.
    func attach(to asset: AVURLAsset)
    func start()
    func release()
}

/// Host-registered factory for `AudioDrmSession`. Register from the app at launch via
/// `AudioDrm.setProvider(_:)`; do not pin drm-kit in this plugin.
public protocol AudioDrmProvider {
    /// Open a session for a playlist item's `drm` options. `onError` receives exactly one
    /// discriminator: `blockedByStreamLimit` | `notEntitled` | `expired` | `network` | `unknown`.
    func open(_ drm: JSObject, onError: @escaping (String) -> Void) -> AudioDrmSession
}

/// Plugin-owned DRM provider registry. The host app registers drm-kit in Story 58.5; this module
/// does not depend on drm-kit. Mirrors Android `AudioDrm` (org.dwbn.plugins.playlist) and this
/// plugin's own `VideoDrm.swift` sibling (capacitor-video-player, Story 58.4).
public final class AudioDrm {

    public static let codeNoProvider = "noProvider"

    public static let errorBlockedByStreamLimit = "blockedByStreamLimit"
    public static let errorNotEntitled = "notEntitled"
    public static let errorExpired = "expired"
    public static let errorNetwork = "network"
    public static let errorUnknown = "unknown"

    private static let lock = NSLock()
    private static var _provider: AudioDrmProvider?

    private init() {}

    public static func setProvider(_ next: AudioDrmProvider?) {
        lock.lock()
        _provider = next
        lock.unlock()
    }

    public static func getProvider() -> AudioDrmProvider? {
        lock.lock()
        defer { lock.unlock() }
        return _provider
    }

    /// Result of `open(_:onError:)`: no `drm` set, a registered session, or a missing-provider
    /// failure.
    public enum OpenAttempt {
        case none
        case noProvider
        case ok(AudioDrmSession)

        public var session: AudioDrmSession? {
            if case .ok(let session) = self {
                return session
            }
            return nil
        }

        public var failureCode: String? {
            if case .noProvider = self {
                return AudioDrm.codeNoProvider
            }
            return nil
        }
    }

    /// Open a session when `drm` is set. Missing `drm` is plain playback. Missing provider with
    /// `drm` set is `.noProvider`.
    public static func open(_ drm: JSObject?, onError: ((String) -> Void)?) -> OpenAttempt {
        guard let drm = drm else {
            return .none
        }
        guard let registered = getProvider() else {
            return .noProvider
        }
        let typedOnError: (String) -> Void = { error in
            onError?(AudioDrm.typedError(error))
        }
        let session = registered.open(drm, onError: typedOnError)
        return .ok(session)
    }

    public static func typedError(_ error: String) -> String {
        switch error {
        case errorBlockedByStreamLimit, errorNotEntitled, errorExpired, errorNetwork, errorUnknown:
            return error
        default:
            return errorUnknown
        }
    }
}
