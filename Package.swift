// swift-tools-version: 5.9
import PackageDescription

let package = Package(
    name: "CapacitorPluginPlaylist",
    platforms: [.iOS(.v18)],
    products: [
        .library(
            name: "CapacitorPluginPlaylist",
            targets: ["PlaylistPlugin"])
    ],
    dependencies: [
        .package(url: "https://github.com/ionic-team/capacitor-swift-pm.git", from: "8.0.0")
    ],
    targets: [
        .target(
            name: "PlaylistPlugin",
            dependencies: [
                .product(name: "Capacitor", package: "capacitor-swift-pm"),
                .product(name: "Cordova", package: "capacitor-swift-pm")
            ],
            path: "ios/Sources/PlaylistPlugin"),
        .testTarget(
            name: "PlaylistPluginTests",
            dependencies: ["PlaylistPlugin"],
            path: "ios/Tests/PlaylistPluginTests")
    ]
)