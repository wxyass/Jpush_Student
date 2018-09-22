package com.wxyass.jpush;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Environment;
import android.os.Looper;
import android.provider.Settings.Secure;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.Writer;
import java.lang.Thread.UncaughtExceptionHandler;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;


/**
 * UncaughtException处理类,当程序发生Uncaught异常的时候,
 * 由该类来接管程序,并记录发送错误报告.
 * 需要在Application中注册，为了要在程序启动器就监控整个程序。
 */
public class CrashHandler implements UncaughtExceptionHandler {
    public static final String TAG = "CrashHandler";
    public static final String APP_CACHE_PATH = //FileUtil.getBugPath();
            Environment.getExternalStorageDirectory().getPath() + "/YoungHeart/crash/";

    // 系统默认的UncaughtException处理类
    private UncaughtExceptionHandler mDefaultHandler;
    // CrashHandler实例
    private static CrashHandler instance;
    // 程序的Context对象
    private Context context;
    // 用来存储设备信息和异常信息
    //private Map<String, String> infos = new HashMap<String, String>();

    // 用于格式化日期,作为日志文件名的一部分
    private DateFormat formatter = new SimpleDateFormat("yyyy-MM-dd-HH-mm-ss");

    /** 保证只有一个CrashHandler实例 */
    private CrashHandler() {
    }

    /** 获取CrashHandler实例 ,单例模式 */
    public static CrashHandler getInstance() {
        if (instance == null)
            instance = new CrashHandler();
        return instance;
    }

    /**
     * 初始化
     */
    public void init(Context context) {
        this.context = context;
        // 获取系统默认的UncaughtException处理器
        mDefaultHandler = Thread.getDefaultUncaughtExceptionHandler();
        // 设置该CrashHandler为程序的默认处理器
        Thread.setDefaultUncaughtExceptionHandler(this);
    }

    /*
     * 切换发生Crash所在的Activity
     */
    public void switchCrashActivity(Context context) {
        this.context = context;
    }

    /**
     * 当UncaughtException发生时会转入该函数来处理
     */
    @Override
    public void uncaughtException(Thread thread, Throwable ex) {
        if (!handleException(ex) && mDefaultHandler != null) {
            // 如果用户没有处理则让系统默认的异常处理器来处理
            mDefaultHandler.uncaughtException(thread, ex);
        } else {
            try {
                Thread.sleep(3000);
            } catch (InterruptedException e) {
                Log.e(TAG, "error : ", e);
            }
            // 退出程序
            /*android.os.Process.killProcess(android.os.Process.myPid());
            System.exit(0);*/
            //关闭所有的activtys并结束进程
            //ExitAppUtils.getInstance().exit();// 自定义方法，关闭当前打开的所有avtivity
            android.os.Process.killProcess(android.os.Process.myPid());
        }
    }

    /**
     * 自定义错误处理,收集错误信息 发送错误报告等操作均在此完成.
     *
     * @param ex
     * @return true:如果处理了该异常信息;否则返回false.
     */
    private boolean handleException(Throwable ex) {
        if (ex == null) {
            return false;
        }

        //把crash发送到服务器
        sendCrashToServer(context, ex);

        // 使用Toast来显示异常信息
        new Thread() {
            @Override
            public void run() {
                Looper.prepare();
                Toast.makeText(context, "很抱歉,程序出现异常,即将退出.",
                        Toast.LENGTH_SHORT).show();
                Looper.loop();
            }
        }.start();

        // 保存日志文件
        saveCrachInfoInFile(ex);
        return true;
    }

    /**
     * 保存错误信息到文件中
     *
     * @param ex
     * @return 返回文件名称,便于将文件传送到服务器
     */
    private String saveCrachInfoInFile(Throwable ex) {
        StringBuffer sb = new StringBuffer();
        // 自定义信息
        sb.append("\n手机型号:" + android.os.Build.MODEL);
        sb.append("\nSDK版本:" + android.os.Build.VERSION.SDK);
        sb.append("\n系统版本:" + android.os.Build.VERSION.RELEASE);
        //sb.append("\n崩溃时间:" + DateUtil.getDateTimeStr(1));
        //sb.append("\n软件版本:" + DbtLog.getVersion()+"\n");
        //
        /*for (Map.Entry<String, String> entry : infos.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();
            sb.append(key + "=" + value + "\n");
        }*/
        // 崩溃信息
        Writer writer = new StringWriter();
        PrintWriter printWriter = new PrintWriter(writer);
        ex.printStackTrace(printWriter);
        Throwable cause = ex.getCause();
        while (cause != null) {
            cause.printStackTrace(printWriter);
            cause = cause.getCause();
        }
        printWriter.close();
        String result = writer.toString();
        sb.append(result);
        // 信息保存
        try {
            long timestamp = System.currentTimeMillis();
            String time = formatter.format(new Date());
            String fileName = "crash-" + time  + ".log";

            if (Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)) {
                File dir = new File(APP_CACHE_PATH);
                if (!dir.exists()) {
                    dir.mkdirs();
                }
                FileOutputStream fos = new FileOutputStream(APP_CACHE_PATH + fileName);
                fos.write(sb.toString().getBytes());
                fos.close();
            }

            return fileName;
        } catch (Exception e) {
            Log.e(TAG, "an error occured while writing file...", e);
        }
        return null;
    }

    /**
     * 收集程序崩溃的相关信息
     *
     * @param ctx
     */
    public void sendCrashToServer(Context ctx, Throwable ex) {
        // 崩溃信息集合
        HashMap<String, String> exceptionInfo = new HashMap<String, String>();
        //取出版本号
        PackageManager pm = ctx.getPackageManager();
        PackageInfo pi = null;
        try {
            pi = pm.getPackageInfo(ctx.getPackageName(), PackageManager.GET_ACTIVITIES);
            if (pi != null) {
                String versionName = pi.versionName == null ? "null": pi.versionName;
                String versionCode = pi.versionCode + "";
                exceptionInfo.put("versionname", versionName); // app版本名称
                exceptionInfo.put("versioncode", versionCode); // app版本号
            }
            // 崩溃信息
            exceptionInfo.put("exceptionsdetail", getStackTrace(ex));
            // Android系统版本(如:4.4.4)
            exceptionInfo.put("androidversion",  android.os.Build.VERSION.RELEASE);
            // 设备型号(如:Google Nexus 7 2013 - 4.4.4 - API 19 - 1200x1920)
            exceptionInfo.put("devicemodel", android.os.Build.MODEL);
            // SDK版本
            exceptionInfo.put("sdkversion", android.os.Build.VERSION.SDK);
            // 崩溃时间
            //exceptionInfo.put("crashdate", DateUtil.getDateTimeStr(1));


            final String rquestParam = exceptionInfo.toString();
            // 上传到后台

            //String filePath = FileUtil.getSDPath();
            // /storage/emulated/0
            //String SDCARD_DIR = Environment.getExternalStorageDirectory().getPath();

            //String pin = PinYin4jUtil.converterToFirstSpell("与系统中所有的Activity进行交互的类");

            //String yin = PinYin4jUtil.converterToSpell("与系统中所有的Activity进行交互的类");

            String adf ="123";
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     *
     * @Title: getDeviceID
     * @Description: 获取手机设备号
     * @param context
     * @return String
     * @throws
     */
    public static final String getDeviceID(Context context) {
        String deviceId = null;
        try {
            TelephonyManager tm = (TelephonyManager) context
                    .getSystemService(Context.TELEPHONY_SERVICE);
            //deviceId = tm.getDeviceId();
            tm = null;
            if (TextUtils.isEmpty(deviceId)) {
                deviceId = Secure.getString(context.getContentResolver(),
                        Secure.ANDROID_ID);
            }

        } catch (Exception e) {
            deviceId = Secure.getString(context.getContentResolver(),
                    Secure.ANDROID_ID);
        }

        return deviceId;
    }

    private String getStackTrace(Throwable th) {
        final Writer result = new StringWriter();
        final PrintWriter printWriter = new PrintWriter(result);

        // If the exception was thrown in a background thread inside
        // AsyncTask, then the actual exception can be found with getCause
        Throwable cause = th;
        while (cause != null) {
            cause.printStackTrace(printWriter);
            cause = cause.getCause();
        }
        final String stacktraceAsString = result.toString();
        printWriter.close();

        return stacktraceAsString;
    }

    private static int isWifi(Context mContext) {
        ConnectivityManager connectivityManager = (ConnectivityManager) mContext
                .getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo activeNetInfo = connectivityManager.getActiveNetworkInfo();
        if (activeNetInfo != null
                && activeNetInfo.getType() == ConnectivityManager.TYPE_WIFI) {
            return 1;
        }
        return 0;
    }
}