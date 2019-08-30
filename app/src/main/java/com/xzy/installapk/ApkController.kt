import android.content.Context

import android.content.Intent

import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.core.content.FileProvider
import java.io.*

import java.nio.charset.Charset
import java.lang.reflect.AccessibleObject.setAccessible
import android.content.pm.PackageManager
import android.os.RemoteException


@Suppress("unused")
object ApkController {
    /**
     * 描述: 安装
     */
    fun install(apkPath: String, context: Context): Boolean {
        // 先判断手机是否有root权限
        if (hasRootPermission()) {
            // 有root权限，利用静默安装实现
            // return installSilent(apkPath)
            return installSilent2(apkPath, context)
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
     * 静默安装 利用 Runtime.getRuntime().exec()
     * @param apkPath
     * @return boolean true -- 安装成功，false -- 安装失败
     */
    private fun installSilent(apkPath: String): Boolean {
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
     * install slient 使用 ProcessBuilder
     *
     * @param filePath 文件路径
     * @return boolean true -- 安装成功，false -- 安装失败
     */
    private fun installSilent2(filePath: String, context: Context): Boolean {
        val file = File(filePath)
        if (!file.exists()) {
            return false
        }
        var args: Array<String> = if (Build.VERSION.SDK_INT > Build.VERSION_CODES.M) {
            arrayOf("pm", "install", "-r", "-i", context.packageName, "--user", "0", filePath)
        } else {
            arrayOf("pm", "install", "-r", filePath)
        }
        val processBuilder = ProcessBuilder(*args)

        var process: Process? = null
        var successResult: BufferedReader? = null
        var errorResult: BufferedReader? = null
        val successMsg = StringBuilder()
        val errorMsg = StringBuilder()
        var result: Boolean
        try {
            process = processBuilder.start()
            successResult = BufferedReader(InputStreamReader(process!!.inputStream))
            errorResult = BufferedReader(InputStreamReader(process.errorStream))
            var s: String?
            do {
                s = successResult.readLine()
                if (s != null) {
                    successMsg.append(s)
                } else
                    break
            } while (true)

            do {
                s = errorResult.readLine()
                if (s != null) {
                    errorMsg.append(s)
                } else
                    break
            } while (true)
        } catch (e: IOException) {

        } catch (e: Exception) {

        } finally {
            try {
                successResult?.close()
                errorResult?.close()
            } catch (e: IOException) {
                e.printStackTrace()
            }

            process?.destroy()
        }
        result = successMsg.toString().contains("Success") ||
                successMsg.toString().contains("success")
        Log.e("", "ErrorMsg:$errorMsg")
        return result
    }


//    fun installSilentWithReflection(context: Context, filePath: String) {
//        try {
//            val packageManager = context.packageManager
//            val method = packageManager.javaClass.getDeclaredMethod(
//                "installPackage",
//                Uri::class.java,
//                IPackageInstallObserver::class.java,
//                Int::class.javaPrimitiveType!!,
//                String::class.java
//            )
//            method.isAccessible = true
//            val apkFile = File(filePath)
//            val apkUri = Uri.fromFile(apkFile)
//
//            method.invoke(packageManager, arrayOf(apkUri, object : IPackageInstallObserver.Stub() {
//                @Throws(RemoteException::class)
//                fun packageInstalled(pkgName: String, resultCode: Int) {
//                    Log.d("", "packageInstalled = $pkgName; resultCode = $resultCode")
//                }
//            }, Integer.valueOf(2), "com.ali.babasecurity.yunos"))
//            //PackageManager.INSTALL_REPLACE_EXISTING = 2;
//        } catch (e: NoSuchMethodException) {
//            e.printStackTrace()
//        } catch (e: Exception) {
//            e.printStackTrace()
//        }
//
//    }


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