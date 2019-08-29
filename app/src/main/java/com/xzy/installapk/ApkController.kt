import android.content.Context

import android.content.Intent

import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.core.content.FileProvider
import java.io.*

import java.nio.charset.Charset

@Suppress("unused")
object ApkController {
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

    /**
     * 描述: 卸载
     */
    fun uninstall(packageName: String, context: Context): Boolean {
        return if (hasRootPermission()) {
            // 有root权限，利用静默卸载实现
            silentUninstall(packageName)
        } else {
            val packageURI = Uri.parse("package:$packageName")
            val uninstallIntent = Intent(Intent.ACTION_DELETE, packageURI)
            uninstallIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            context.startActivity(uninstallIntent)
            true
        }
    }

    /**
     * 判断手机是否有root权限
     */
    private fun hasRootPermission(): Boolean {
        val printWriter: PrintWriter?
        var process: Process? = null
        try {
            process = Runtime.getRuntime().exec("su")
            printWriter = PrintWriter(process!!.outputStream)
            printWriter.flush()
            printWriter.close()
            val value = process.waitFor()
            return returnResult(value)
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            process?.destroy()
        }
        return false
    }


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

    /**
     * 静默卸载
     */
    private fun silentUninstall(packageName: String): Boolean {
        val printWriter: PrintWriter?
        var process: Process? = null
        try {
            process = Runtime.getRuntime().exec("su")
            printWriter = PrintWriter(process!!.outputStream)
            printWriter.println("LD_LIBRARY_PATH=/vendor/lib:/system/lib ")
            printWriter.println("pm uninstall $packageName")
            printWriter.flush()
            printWriter.close()
            val value = process.waitFor()
            return returnResult(value)
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            process?.destroy()
        }
        return false
    }

    /**
     * 启动 app
     * com.exmaple.client/.MainActivity
     * com.exmaple.client/com.exmaple.client.MainActivity
     */
    fun startApp(packageName: String, activityName: String): Boolean {
        val isSuccess = false
        val cmd = "am start -n $packageName/$activityName \n"
        var process: Process? = null
        try {
            process = Runtime.getRuntime().exec(cmd)
            val value = process!!.waitFor()
            return returnResult(value)
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            process?.destroy()
        }
        return isSuccess
    }

    /**
     * 将文件复制到system/app 目录
     * @param apkPath 特别注意格式：该路径不能是：/storage/emulated/0/app/QDemoTest4.apk 需要是：/sdcard/app/QDemoTest4.apk
     * @return
     */
    fun copy2SystemApp(apkPath: String, appName: String): Boolean {
        val printWriter: PrintWriter?
        var process: Process? = null
        // val appName = "chetou.apk"
        var cmd: String

        try {
            process = Runtime.getRuntime().exec("su")
            printWriter = PrintWriter(process!!.outputStream)
//            cmd = "mount -o remount,rw -t yaffs2 /dev/block/mtdblock3 /system"
            cmd = "mount -o remount,rw -t yaffs2 /dev/block/mtdblock3 /data"
            Log.e("copy2SystemApp", cmd)
            printWriter.println(cmd)

            // cmd = "cat $apkPath > /system/app/$appName"
            cmd = "cat $apkPath > /data/local/tmp/$appName"
            Log.e("copy2SystemApp", cmd)
            printWriter.println(cmd)

//            cmd = "chmod 777 /system/app/$appName -R"
            cmd = "chmod 777 /data/local/tmp/$appName -R"
            Log.e("copy2SystemApp", cmd)
            printWriter.println(cmd)

//            cmd = "mount -o remount,ro -t yaffs2 /dev/block/mtdblock3 /system"
            cmd = "mount -o remount,ro -t yaffs2 /dev/block/mtdblock3 /data"
            Log.e("copy2SystemApp", cmd)
            printWriter.println(cmd)
//            printWriter.println("reboot")  //重启
            printWriter.println("exit")
            printWriter.flush()
            printWriter.close()
            val value = process.waitFor()
            return returnResult(value)
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            process?.destroy()
        }
        return false
    }

    private fun returnResult(value: Int): Boolean {
        // 代表成功
        return when (value) {
            0 -> true
            1 -> // 失败
                false
            else -> // 未知情况
                false
        }
    }


}