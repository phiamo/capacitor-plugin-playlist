/*
 * Apache 2.0 License
 *
 * Copyright (c) Sebastian Katzer 2017
 *
 * This file contains Original Code and/or Modifications of Original Code
 * as defined in and that are subject to the Apache License
 * Version 2.0 (the 'License'). You may not use this file except in
 * compliance with the License. Please obtain a copy of the License at
 * http://opensource.org/licenses/Apache-2.0/ and read it before using this
 * file.
 *
 * The Original Code and all software distributed under the License are
 * distributed on an 'AS IS' basis, WITHOUT WARRANTY OF ANY KIND, EITHER
 * EXPRESS OR IMPLIED, AND APPLE HEREBY DISCLAIMS ALL SUCH WARRANTIES,
 * INCLUDING WITHOUT LIMITATION, ANY WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE, QUIET ENJOYMENT OR NON-INFRINGEMENT.
 * Please see the License for the specific language governing rights and
 * limitations under the License.
 */
package org.dwbn.plugins.playlist.manager

import android.content.Context
import org.json.JSONObject

class Options {
    /**
     * Wrapped JSON object.
     */
    // The original JSON object
    val dict: JSONObject

    /**
     * Application context.
     */
    // The application context
    val context: Context

    /**
     * Constructor
     *
     * @param context The application context.
     */
    constructor(context: Context) {
        this.context = context
        dict = JSONObject()
    }

    /**
     * Constructor
     *
     * @param context The application context.
     * @param options The options dict map.
     */
    constructor(context: Context, options: JSONObject) {
        this.context = context
        dict = options
    }

    /**
     * JSON object as string.
     */
    override fun toString(): String {
        return dict.toString()
    }

    /**
     * icon resource ID for the local notification.
     */
    val icon: String
        get() = dict.optString("icon", DEFAULT_ICON)

    companion object {
        // Default icon path
        private const val DEFAULT_ICON = "icon"
    }
}