package com.xzy.installapk

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Build

object Permission {
    fun isGrantExternalRW(activity: Activity): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
            && activity.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            != PackageManager.PERMISSION_GRANTED
        ) {

            activity.requestPermissions(
                arrayOf(
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                ), 1
            )

            return false
        }

        return true
    }
}
