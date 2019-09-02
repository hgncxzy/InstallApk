# InstallApk

### 简介

关于 Apk 普通安装和静默安装的总结。适配 Android 6.0 、Android 7.0 和 Android 9.0。

### 测试环境

Android 7.1 、Android 9.0 

### 应用场景

对于某些定制的系统而言，是需要做到静默安装某些业务 App 的。比如有两个 App，一个是业务 App A，一个是专门负责安装服务的 App B。当 A 收到后台的升级推送时，会将新版本的安装包下载到一个指定的目录，然后给 B 发送一条广播，让 B 安装 刚刚下载好的 A 的最新版本，并强制拉起 A 应用。这个过程是完全静默的，不需要人工干预。这类场景的升级可以应用在高铁站、机场等公共场合的智能终端等场景，因为这些地方的业务升级一般都会自动完成。

### Apk 安装的几种实现方式

一般来说，有几种方式： 

1. 标准的 Intent 
2. 把 apk 地址托管给浏览器，浏览器下载安装 
3.  pm install（需要 su 权限） 
4. 使用 PackageManager 进行安装（需要是系统级别的应用，或系统签名） 
5. 把 apk 地址托管给 DownloadManager 下载处理（类似2）

#### 非静默安装 -- 标准的 Intent 安装 Apk

使用 Intent 安装 Apk 没什么好说的，只需要注意一点，就是对 Android 7.0 的兼容处理。这里强调下 Android 7.0 的处理方式。在 Android 7.0 下面，使用 FileProvider 共享文件，步骤如下([参考](https://www.jianshu.com/p/a256f7a37610?tdsourcetag=s_pcqq_aiomsg))：

##### 1. 指定 FileProvider (默认写法，不用修改)

```xml
  <!-- Android 7.0 文件访问的兼容处理 -->
        <provider
            android:name="androidx.core.content.FileProvider"
            android:authorities="${applicationId}.FileProvider"
            android:exported="false"
            android:grantUriPermissions="true">
            <meta-data
                android:name="android.support.FILE_PROVIDER_PATHS"
                android:resource="@xml/provider_path" />
        </provider>
```

##### 2. 在 res/xml/ 下面创建 xml 文件  provider_path.xml

以下路径已经包含所有的路径，可以按需保留或者修改。

```xml
<?xml version="1.0" encoding="utf-8"?>
<paths>
    <files-path name="files-path" path="/."/>
    <cache-path name="cache-path" path="/."/>
    <external-path name="external-path" path="/."/>
    <external-files-path name="external-files-path" path="/."/>
    <external-cache-path name="external-cache-path" path="/."/>
</paths>
```

##### 3.  启动代码做兼容处理

```kotlin
  /**
     * 描述: 安装
     */
    fun install(apkPath: String, context: Context): Boolean {
        // 先判断手机是否有root权限
        if (hasRootPermission()) {
            // 有root权限，利用静默安装实现
            return silentInstall(apkPath)
        } else {
            // 没有root权限，利用意图进行安装
            val file = File(apkPath)
            if (!file.exists()) {
                return false
            }
            val intent = Intent(Intent.ACTION_VIEW)
            val uri: Uri
            val type = "application/vnd.android.package-archive"
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
                uri = Uri.fromFile(file)
            } else {
                val authority = context.packageName + ".FileProvider"
                uri = FileProvider.getUriForFile(context, authority, file)
                intent.flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
            }
            context.grantUriPermission(
                context.packageName,
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
            intent.setDataAndType(uri, type)
            //intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            return true
        }
    }
```



#### 非静默安装 -- 使用浏览器下载并安装 Apk

很简单，直接上代码.或者参看例程 [filedownload](https://github.com/hgncxzy/AndroidNetworkTransmission/tree/master/filedownload)

```kotlin

    /**
     * 使用这种方法下载完全把工作交给了系统应用，自己的应用中不需要申请任何权限，方便简单快捷。但如此我们也不能知道
     * 下载文件的大小，不能监听下载进度和下载结果。
     *
     * @param context 上下文
     * @param url     下载 url
     */
    fun downloadFileByBrowser(context: Context, url: String) {
        val intent = Intent()
        intent.action = Intent.ACTION_VIEW
        intent.addCategory(Intent.CATEGORY_BROWSABLE)
        intent.data = Uri.parse(url)
        context.startActivity(intent)
    }
```

#### 把 apk 地址托管给 DownloadManager 下载处理（类似使用浏览器下载并安装 Apk）

这个也比较简单，直接看代码。参考例程 [filedownload](https://github.com/hgncxzy/AndroidNetworkTransmission/tree/master/filedownload)

##### 1. 定义下载器工具类

```kotlin
package com.xzy.installapk


import android.app.DownloadManager
import android.content.Context
import android.content.IntentFilter
import android.net.Uri
import android.os.Environment
import android.util.Log

import java.io.File
import java.util.Objects

/**
 * 调用系统下载器实现下载功能。
 *
 * @author xzy
 */
class SystemDownloadManager {
    private var callback: Callback? = null

    fun setCallback(callback: Callback) {
        this.callback = callback
    }

    fun downloadFileBySysDownloadManager(
        context: Context,
        url: String,
        fileName: String,
        mimeType: String
    ) {
        val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val request = DownloadManager.Request(Uri.parse(url))
        // 通知栏的下载通知
        request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
        request.setTitle(fileName)
        request.setMimeType(mimeType)
        // 保存到DIRECTORY_DOWNLOADS目录，文件名为 fileName
        val file = File(Environment.DIRECTORY_DOWNLOADS, fileName)
        if (file.exists()) {
            val result = file.delete()
            Log.d(TAG, "file.delete():$result")
        }
        request.setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, fileName)
        val downloadId = Objects.requireNonNull(downloadManager).enqueue(request)
        Log.d(TAG, "downloadId:$downloadId")
        //文件下载完成会发送完成广播，可注册广播进行监听
        val intentFilter = IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
        intentFilter.addAction(DownloadManager.ACTION_NOTIFICATION_CLICKED)
        intentFilter.addAction(DownloadManager.ACTION_VIEW_DOWNLOADS)
        val mDownloadBroadcast = DownloadBroadcast(file, mimeType)
        context.registerReceiver(mDownloadBroadcast, intentFilter)
        if (callback != null) {
            callback!!.callback(mDownloadBroadcast)
        }
    }

    interface Callback {
        fun callback(downloadBroadcast: DownloadBroadcast)
    }

    companion object {
        private val TAG = "SystemDownloadManager"

        val instance = SystemDownloadManager()
    }
}

```

##### 2 . 需要定义一个广播接收器，用于接收数据(依然兼容 Android 7.0 )

```kotlin
package com.xzy.installapk

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.Log

import androidx.core.content.FileProvider

import java.io.File

/**
 * 调用系统下载器对应的回调广播
 * @author xzy
 */
class DownloadBroadcast(private val mFile: File, private val mMimeType: String) :
    BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (DownloadManager.ACTION_DOWNLOAD_COMPLETE == action) {
            val intent1 = Intent(Intent.ACTION_VIEW)
            intent1.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (Build.VERSION.SDK_INT > Build.VERSION_CODES.M) {
                intent1.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                val uri1 = FileProvider.getUriForFile(
                    context,
                    BuildConfig.APPLICATION_ID + ".fileProvider",
                    mFile
                )
                intent1.setDataAndType(uri1, mMimeType)
            } else {
                intent1.setDataAndType(Uri.fromFile(mFile), mMimeType)
            }
            Log.d("mFile:", mFile.absolutePath)
            try {
                context.startActivity(intent1)
            } catch (e: Exception) {
                e.printStackTrace()
            }

        }
    }
}
```



####  静默安装 -- pm install（需要 su 权限） ，利用 Runtime.getRuntime().exec()

本例程中主要是以 pm install 命令来执行静默安装的。具体请看 silentInstall 方法。（需要 Root 权限）

```kotlin
 /**
     * 静默安装
     * @param apkPath
     * @return
     */
    private fun silentInstall(apkPath: String): Boolean {
        var result = false
        var dataOutputStream: DataOutputStream? = null
        var errorStream: BufferedReader? = null
        try {
            // 申请su权限
            val process = Runtime.getRuntime().exec("su")
            dataOutputStream = DataOutputStream(process.outputStream)
            // 执行pm install命令
            val command = "pm install -r $apkPath\n"
            dataOutputStream.write(command.toByteArray(Charset.forName("utf-8")))
            dataOutputStream.writeBytes("exit\n")
            dataOutputStream.flush()
            process.waitFor()
            errorStream = BufferedReader(InputStreamReader(process.errorStream))
            val msg = StringBuilder()
            var line: String?
            // 读取命令的执行结果
            do {
                line = errorStream.readLine()
                if (line != null) {
                    msg.append(line)
                } else {
                    break
                }
            } while (true)
            Log.d("TAG", "install msg is $msg")
            // 如果执行结果中包含 Failure 或者 denied 字样就认为是安装失败，否则就认为安装成功
            if (!msg.toString().contains("Failure") && !msg.toString().contains("denied")) {
                result = true
            }
        } catch (e: Exception) {
            Log.e("TAG", e.message, e)
        } finally {
            try {
                dataOutputStream?.close()
                errorStream?.close()
            } catch (e: IOException) {
                Log.e("TAG", e.message, e)
            }

        }
        Log.d("TAG", "install result is: $result")
        return result
    }
```



####  静默安装 --使用 PackageManager 进行安装（需要是系统级别的应用，或系统签名） 

```kotlin
// todo
```

### 参考 

1. [Android 实现静默安装的几种方式](https://blog.csdn.net/u013341672/article/details/69320412)
2. [Android P使用pm install安装apk报错](https://blog.csdn.net/xuebijun/article/details/82852414)
3. [Android 开发实现静默安装(需要 Root 权限)](https://www.cnblogs.com/feijian/p/5201572.html)
4. [Android 安装应用](https://blog.csdn.net/yeshennet/article/details/78031268)

### 联系

1. ID : hgncxzy
2. 邮箱：[hgncxzy@qq.com](mailto:hgncxzy@qq.com)
3. 项目地址：https://github.com/hgncxzy/InstallApk



