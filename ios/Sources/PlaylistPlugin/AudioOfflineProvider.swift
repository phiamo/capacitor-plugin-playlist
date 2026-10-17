//
//  AudioOfflineProvider.swift
//  PlaylistPlugin
//
//  Host-registered owner of offline FairPlay licences and renewal (Story 59.5).
//  The host wraps drm-kit `FairPlayOfflineLicenseManager`; this plugin never imports drm-kit.
//

import AVFoundation
import Capacitor
import Foundation

/// Host-registered owner of offline licences and renewal. Register from the app at
/// launch via `AudioOffline.setProvider(_:)`. Errors are the five `AudioDrm` discriminators plus
/// `offlineDeviceLimit`; anything else is coerced to `unknown`.
///
/// `acquire` / `renew` / `release` may block and are never called on the main thread.
/// `state` / `needsRenewal` / `attachOffline` must be quick and local.
public protocol AudioOfflineProvider: AnyObject {
    /// No licence is held for the download.
    static var stateNone: String { get }
    /// A usable licence is held.
    static var stateActive: String { get }
    /// The licence has expired and must be renewed.
    static var stateExpired: String { get }

    /// Acquire and persist an offline licence for `downloadId`. Called after the FairPlay `skd://`
    /// identifier has been read from the remote HLS, and **before** any media is fetched.
    /// `keyIdentifier` is the `skd://` URI; `drm` is the descriptor passed to `startDownload`.
    /// - Returns: `nil` on success, otherwise an error discriminator (download then fails).
    func acquire(downloadId: String, keyIdentifier: String, drm: JSObject) -> String?

    /// Whether the stored licence should be renewed soon. Quick and local.
    func needsRenewal(downloadId: String) -> Bool

    /// Real licence expiry (epoch ms) from the host/drm-kit. `nil` when unknown.
    /// Called from `listDownloads` / acquire / renew off the main thread.
    func expiresAt(downloadId: String) -> Int64?

    /// Renew the licence of a downloaded item while online.
    /// - Returns: `nil` on success, otherwise an error discriminator.
    func renew(downloadId: String, drm: JSObject?) -> String?

    /// Drop the stored licence of `downloadId`. Failures are ignored.
    func release(downloadId: String)

    /// One of `none` | `active` | `expired`. Quick and local.
    func state(downloadId: String) -> String

    /// Route the local asset's FairPlay key requests through the stored persistable key. Call
    /// before any `AVPlayerItem` is built from `asset`.
    func attachOffline(downloadId: String, asset: AVURLAsset)
}

public extension AudioOfflineProvider {
    static var stateNone: String { "none" }
    static var stateActive: String { "active" }
    static var stateExpired: String { "expired" }
}
