/*
 * Copyright (C) 2019 Electric Coin Company
 *
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * This file has been modified by Electric Coin Company to translate it into Kotlin and add support for Firebase vision.
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package cash.z.android.cameraview

import android.content.Context
import android.util.SparseIntArray
import android.view.Display
import android.view.OrientationEventListener
import android.view.Surface


/**
 * Monitors the value returned from [Display.getRotation].
 */
internal abstract class DisplayOrientationDetector(context: Context) {

    private val mOrientationEventListener: OrientationEventListener

    var mDisplay: Display? = null

    var lastKnownDisplayOrientation = 0
        private set

    init {
        mOrientationEventListener = object : OrientationEventListener(context) {

            /** This is either Surface.Rotation_0, _90, _180, _270, or -1 (invalid).  */
            private var mLastKnownRotation = -1

            override fun onOrientationChanged(orientation: Int) {
                if (orientation == OrientationEventListener.ORIENTATION_UNKNOWN || mDisplay == null) {
                    return
                }
                val rotation = mDisplay!!.rotation
                if (mLastKnownRotation != rotation) {
                    mLastKnownRotation = rotation
                    dispatchOnDisplayOrientationChanged(DISPLAY_ORIENTATIONS.get(rotation))
                }
            }
        }
    }

    fun enable(display: Display) {
        mDisplay = display
        mOrientationEventListener.enable()
        // Immediately dispatch the first callback
        dispatchOnDisplayOrientationChanged(DISPLAY_ORIENTATIONS.get(display.rotation))
    }

    fun disable() {
        mOrientationEventListener.disable()
        mDisplay = null
    }

    fun dispatchOnDisplayOrientationChanged(displayOrientation: Int) {
        lastKnownDisplayOrientation = displayOrientation
        onDisplayOrientationChanged(displayOrientation)
    }

    /**
     * Called when display orientation is changed.
     *
     * @param displayOrientation One of 0, 90, 180, and 270.
     */
    abstract fun onDisplayOrientationChanged(displayOrientation: Int)

    companion object {

        /** Mapping from Surface.Rotation_n to degrees.  */
        val DISPLAY_ORIENTATIONS = SparseIntArray()

        init {
            DISPLAY_ORIENTATIONS.put(Surface.ROTATION_0, 0)
            DISPLAY_ORIENTATIONS.put(Surface.ROTATION_90, 90)
            DISPLAY_ORIENTATIONS.put(Surface.ROTATION_180, 180)
            DISPLAY_ORIENTATIONS.put(Surface.ROTATION_270, 270)
        }
    }

}
