package org.dwbn.plugins.playlist

import android.app.Activity
import android.content.Context

/**
 * R replacement for PhoneGap Build.
 * Code adopted from https://github.com/EddyVerbruggen/barcodescanner-lib-aar
 *
 * ([^.\w])R\.(\w+)\.(\w+)
 * $1fakeR("$2", "$3")
 *
 * @author Maciej Nux Jaros
 */
class FakeR {
    var context: Context
        private set
    private var packageName: String

    constructor(activity: Activity) {
        context = activity.applicationContext
        packageName = context.packageName
    }

    constructor(context: Context) {
        this.context = context
        packageName = context.packageName
    }

    fun getId(group: String?, key: String?): Int {
        return context.resources.getIdentifier(key, group, packageName)
    }

    companion object {
        fun getId(context: Context, group: String?, key: String?): Int {
            return context.resources.getIdentifier(key, group, context.packageName)
        }
    }
}