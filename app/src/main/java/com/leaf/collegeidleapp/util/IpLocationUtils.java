package com.leaf.collegeidleapp.util;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.telecom.Call;
import android.util.Log;

import org.json.JSONObject;
import java.io.IOException;

import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * @ProjectName: MyApplication3
 * @Package: com.leaf.collegeidleapp.util
 * @ClassName: IpLocationUtils
 * @Description: 类作用描述
 * @Author: xuwh
 * @CreateDate: 2025/12/4 13:28
 * @UpdateUser: 更新者
 * @UpdateDate: 2025/12/4 13:28
 * @UpdateRemark: 更新说明
 * @Version: 1.0
 */
public class IpLocationUtils {

    private static final String TAG = "IpLocationUtils";
    // 可选免费API（选其一即可，部分API有调用限额）
    // 1. 高德IP定位（需申请key，免费，精度高）：https://lbs.amap.com/api/webservice/guide/api/ipconfig
    private static final String AMAP_IP_API = "https://restapi.amap.com/v3/ip?key=你的高德Key";
    // 2. 百度IP定位（需申请key）：http://api.map.baidu.com/location/ip?ak=你的百度Key
    // 3. 免费公共API（无需key，稳定性一般）：
    private static final String PUBLIC_IP_API = "http://ip-api.com/json/?lang=zh-CN";

    private final OkHttpClient mOkHttpClient;
    private final Context mContext;

    // IP定位结果回调
    public interface OnIpLocationListener {
        void onIpLocationSuccess(String country, String province, String city);
        void onIpLocationFailed(String errorMsg);
    }

    private IpLocationUtils(Context context) {
        this.mContext = context.getApplicationContext();
        this.mOkHttpClient = new OkHttpClient();
    }

    public static IpLocationUtils getInstance(Context context) {
        return new IpLocationUtils(context);
    }

    /**
     * 检查网络是否可用
     */
    private boolean isNetworkAvailable() {
        ConnectivityManager cm = (ConnectivityManager) mContext.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) return false;
        NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
        return activeNetwork != null && activeNetwork.isConnected();
    }

    /**
     * 获取IP定位信息（异步）
     */
    public void getIpLocation(OnIpLocationListener listener) {
        // 检查网络
        if (!isNetworkAvailable()) {
            listener.onIpLocationFailed("网络不可用");
            return;
        }

        // 构建请求（示例用 PUBLIC_IP_API，无需key）
        Request request = new Request.Builder()
                .url(PUBLIC_IP_API)
                .get()
                .build();

        mOkHttpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(okhttp3.Call call, IOException e) {
                Log.e(TAG, "IP定位请求失败：" + e.getMessage());
                listener.onIpLocationFailed("请求失败：" + e.getMessage());
            }

            @Override
            public void onResponse(okhttp3.Call call, Response response) throws IOException {
                if (!response.isSuccessful()) {
                    listener.onIpLocationFailed("响应失败：" + response.code());
                    return;
                }

                String responseBody = response.body().string();
                try {
                    // 解析返回结果（不同API格式不同，此处适配 PUBLIC_IP_API）
                    JSONObject json = new JSONObject(responseBody);
                    String status = json.getString("status");
                    if (!"success".equals(status)) {
                        String msg = json.getString("message");
                        listener.onIpLocationFailed("定位失败：" + msg);
                        return;
                    }

                    // 提取信息（PUBLIC_IP_API 返回格式）
                    String country = json.getString("country");
                    String province = json.getString("regionName"); // 省份
                    String city = json.getString("city"); // 城市

                    // 回调到主线程
                    listener.onIpLocationSuccess(country, province, city);
                } catch (Exception e) {
                    Log.e(TAG, "IP定位解析失败：" + e.getMessage());
                    listener.onIpLocationFailed("解析失败：" + e.getMessage());
                }
            }
        });
    }

    /**
     * 适配高德IP API的解析（如果用高德Key，替换解析逻辑）
     */
    private void parseAmapIpResponse(String responseBody, OnIpLocationListener listener) {
        try {
            JSONObject json = new JSONObject(responseBody);
            String status = json.getString("status");
            if (!"1".equals(status)) {
                listener.onIpLocationFailed("高德IP定位失败：" + json.getString("info"));
                return;
            }
            String country = json.getString("country");
            String province = json.getString("province");
            String city = json.getString("city");
            listener.onIpLocationSuccess(country, province, city);
        } catch (Exception e) {
            listener.onIpLocationFailed("高德API解析失败：" + e.getMessage());
        }
    }
}
