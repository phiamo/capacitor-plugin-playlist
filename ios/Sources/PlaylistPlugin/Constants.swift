//  Converted to Swift 5.3 by Swiftify v5.3.19197 - https://swiftify.com/
let DOCUMENTS_SCHEME_PREFIX = "documents://"
let HTTP_SCHEME_PREFIX = "http://"
let HTTPS_SCHEME_PREFIX = "https://"
let CDVFILE_PREFIX = "cdvfile://"
enum RmxAudioErrorType : Int {
    case rmxerr_NONE_ACTIVE = 0
    case rmxerr_ABORTED = 1
    case rmxerr_NETWORK = 2
    case rmxerr_DECODE = 3
    case rmxerr_NONE_SUPPORTED = 4
}

enum RmxAudioStatusMessage : Int {
    case rmxstatus_NONE = 0
    case rmxstatus_REGISTER = 1
    case rmxstatus_INIT = 2
    case rmxstatus_ERROR = 5
    case rmxstatus_LOADING = 10
    case rmxstatus_CANPLAY = 11
    case rmxstatus_LOADED = 15
    case rmxstatus_STALLED = 20
    case rmxstatus_BUFFERING = 25
    case rmxstatus_PLAYING = 30
    case rmxstatus_PAUSE = 35
    case rmxstatus_PLAYBACK_POSITION = 40
    case rmxstatus_SEEK = 45
    case rmxstatus_COMPLETED = 50
    case rmxstatus_DURATION = 55
    case rmxstatus_STOPPED = 60
    case rmx_STATUS_SKIP_FORWARD = 90
    case rmx_STATUS_SKIP_BACK = 95
    case rmxstatus_TRACK_CHANGED = 100
    case rmxstatus_PLAYLIST_COMPLETED = 105
    case rmxstatus_ITEM_ADDED = 110
    case rmxstatus_ITEM_REMOVED = 115
    case rmxstatus_PLAYLIST_CLEARED = 120
    case rmxstatus_VIEWDISAPPEAR = 200
}
