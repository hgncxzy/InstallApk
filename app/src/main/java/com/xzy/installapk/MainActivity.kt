package com.xzy.installapk

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.widget.Toast
import kotlinx.android.synthetic.main.activity_main.*
import java.io.File

@Suppress("DEPRECATION")
class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        // 权限
        Permission.isGrantExternalRW(this)

        // android 7.0 安装
        install.setOnClickListener {
            val path = Environment.getExternalStorageDirectory().absolutePath
            val appName = "test.apk"
            val file = File("$path/$appName")
            val result = ApkController.install(file.absolutePath, this)
            Toast.makeText(this, "安装结果:$result", Toast.LENGTH_SHORT).show()
        }

        // android 9.0 安装(兼容 Android 7.0)
        install2.setOnClickListener {

            val path = Environment.getExternalStorageDirectory().absolutePath
            val appName = "test.apk"

            val srcFilePath = "$path/$appName"
            val destFilePath = filesDir.absolutePath + "/$appName"

            val copyResult = FileUtils.copyFile(srcFilePath, destFilePath)
            if (copyResult) {
                val result = ApkController.install(filesDir.absolutePath + "/$appName", this)
                Toast.makeText(this, "安装结果:$result", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "文件拷贝复制失败", Toast.LENGTH_SHORT).show()
                Log.e("", "文件拷贝复制失败")
            }
        }
    }
}
