package com.wxyass.jpush;

import android.app.ActivityManager;
import android.app.ActivityManager.RunningTaskInfo;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.UserManager;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Toast;

import com.wxyass.jpush.module.PushMessage;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Iterator;
import java.util.List;

import cn.jpush.android.api.JPushInterface;

/**
 * 自定义接收器
 * 
 * 如果不定义这个 Receiver，则：
 * 1) 默认用户会打开主界面
 * 2) 接收不到自定义消息
 */
public class JPushReceiver extends BroadcastReceiver {
	private static final String TAG = "JIGUANG-Example";

	@Override
	public void onReceive(Context context, Intent intent) {

		Bundle bundle = intent.getExtras();
		Toast.makeText(context,"推送成功",Toast.LENGTH_SHORT).show();

		if (JPushInterface.ACTION_NOTIFICATION_RECEIVED.equals(intent.getAction())) {
			int notifactionId = bundle.getInt(JPushInterface.EXTRA_NOTIFICATION_ID);
			Log.d(TAG, "[JPushReceiver] 接收到推送下来的通知的ID: " + notifactionId);

		} else if (JPushInterface.ACTION_NOTIFICATION_OPENED.equals(intent.getAction())) {

			/**
			 * 此处可以通过写一个方法，决定出要跳转到那些页面，一些细节的处理，可以通过是不是从推送过来的，去多一个分支去处理。
			 * 1.应用未启动,------>启动主页----->不需要登陆信息类型，直接跳转到消息展示页面
			 *                         ----->需要登陆信息类型，由于应用都未启动，肯定不存在已经登陆这种情况------>跳转到登陆页面
			 *                                                                                                 ----->登陆完毕，跳转到信息展示页面。
			 *                                                                                                 ----->取消登陆，返回首页。
			 * 2.如果应用已经启动，------>不需要登陆的信息类型，直接跳转到信息展示页面。
			 *                 ------>需要登陆的信息类型------>已经登陆----->直接跳转到信息展示页面。
			 *                                      ------>未登陆------->则跳转到登陆页面
			 *                                                                      ----->登陆完毕，跳转到信息展示页面。
			 *                                                                      ----->取消登陆，回到首页。
			 *
			 * 3.startActivities(Intent[]);在推送中的妙用,注意startActivities在生命周期上的一个细节,
			 * 前面的Activity是不会真正创建的，直到要到对应的页面
			 * 4.如果为了复用，可以将极光推送封装到一个Manager类中,为外部提供init, setTag, setAlias,
			 * setNotificationCustom等一系列常用的方法。
			 */
			//PushMessage pushMessage = (PushMessage) ResponseEntityToModule.parseJsonToModule(bundle.getString(JPushInterface.EXTRA_EXTRA), PushMessage.class);

			if (getCurrentTask(context)) {// 如果应用已经启动(无论前台还是后台)
				Intent pushIntent = new Intent();
				pushIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
				//pushIntent.putExtra("pushMessage", pushMessage);
				pushIntent.setClass(context, PushMessageActivity.class);
				context.startActivity(pushIntent);

			} else {// 应用没有启动。。。
				Intent mainIntent = new Intent(context, MainActivity.class);
				mainIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
				context.startActivity(mainIntent);

			}
		}


	}

	// 打印所有的 intent extra 数据
	private static String printBundle(Bundle bundle) {
		StringBuilder sb = new StringBuilder();
		for (String key : bundle.keySet()) {
			if (key.equals(JPushInterface.EXTRA_NOTIFICATION_ID)) {
				sb.append("\nkey:" + key + ", value:" + bundle.getInt(key));
			}else if(key.equals(JPushInterface.EXTRA_CONNECTION_CHANGE)){
				sb.append("\nkey:" + key + ", value:" + bundle.getBoolean(key));
			} else if (key.equals(JPushInterface.EXTRA_EXTRA)) {
				if (TextUtils.isEmpty(bundle.getString(JPushInterface.EXTRA_EXTRA))) {
					// Logger.i(TAG, "This message has no Extra data");
					continue;
				}

				try {
					JSONObject json = new JSONObject(bundle.getString(JPushInterface.EXTRA_EXTRA));
					Iterator<String> it =  json.keys();

					while (it.hasNext()) {
						String myKey = it.next();
						sb.append("\nkey:" + key + ", value: [" +
								myKey + " - " +json.optString(myKey) + "]");
					}
				} catch (JSONException e) {
					// Logger.e(TAG, "Get message extra JSON error!");
				}

			} else {
				sb.append("\nkey:" + key + ", value:" + bundle.get(key));
			}
		}
		return sb.toString();
	}


	/**
	 * 这个是真正的获取指定包名的应用程序是否在运行(无论前台还是后台)
	 *
	 * @return
	 */
	private boolean getCurrentTask(Context context) {

		ActivityManager activityManager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
		List<RunningTaskInfo> appProcessInfos = activityManager.getRunningTasks(50);
		for (RunningTaskInfo process : appProcessInfos) {

			if (process.baseActivity.getPackageName().equals(context.getPackageName())
					|| process.topActivity.getPackageName().equals(context.getPackageName())) {

				return true;
			}
		}
		return false;
	}

}
