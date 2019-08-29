package com.xzy.installapk

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Environment
import android.util.Log
import kotlinx.android.synthetic.main.activity_main.*
import java.io.File

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        // 权限
        Permission.isGrantExternalRW(this)

        // 安装
        install.setOnClickListener {

            val path = Environment.getExternalStorageDirectory().absolutePath
            val appName = "test.apk"

            val copyResult = FileUtils.copyFile(
                "$path/$appName",
                "$path/Locker/$appName"
            )
            if (copyResult) {
                val file = File("$path/Locker/$appName")
                ApkController.install(file.absolutePath, this)
            } else {
                Log.e("", "复制失败")
            }
//            val appName = "test.apk"
//            val file = File("/data/local/tmp/$appName")
//            ApkController.install(file.absolutePath, this)
        }

    }
}
