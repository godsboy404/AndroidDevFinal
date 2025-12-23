package com.leaf.collegeidleapp.util;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.location.LocationProvider;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.core.app.ActivityCompat;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * @ProjectName: MyApplication3
 * @Package: com.leaf.collegeidleapp.util
 * @ClassName: LocationUtils
 * @Description: 类作用描述
 * @CreateDate: 2025/12/4 13:04
 * @UpdateUser: 更新者
 * @UpdateDate: 2025/12/4 13:04
 * @UpdateRemark: 更新说明
 * @Version: 1.0
 */
public class LocationUtils {

    private static final String TAG = "LocationUtils";
    // 定位超时时间（5秒）
    private static final long LOCATION_TIMEOUT = 5000;
    // 定位最小更新距离（米）
    private static final float MIN_UPDATE_DISTANCE = 10;
    // 定位最小更新时间（毫秒）
    private static final long MIN_UPDATE_TIME = 1000;

    private static LocationUtils instance;
    private final Context mContext;
    private final LocationManager mLocationManager;
    private final ExecutorService mExecutorService; // 处理逆地理编码的线程池
    private final Handler mMainHandler; // 主线程回调

    // 定位监听器
    private LocationListener mLocationListener;
    // 超时任务
    private Runnable mTimeoutRunnable;
    // 结果回调
    private OnLocationResultListener mListener;

    // 定位结果回调（保持和之前一致）
    public interface OnLocationResultListener {
        void onLocationSuccess(double latitude, double longitude, AddressInfo addressInfo);
        void onLocationFailed(String errorMsg);
        void onPermissionDenied();
    }

    // 地址信息封装类（保持和之前一致）
    public static class AddressInfo {
        private String country; // 国家
        private String province; // 省份
        private String city; // 城市
        private String district; // 区县
        private String street; // 街道
        private String detailAddress; // 详细地址

        public String getCountry() { return country; }
        public void setCountry(String country) { this.country = country; }
        public String getProvince() { return province; }
        public void setProvince(String province) { this.province = province; }
        public String getCity() { return city; }
        public void setCity(String city) { this.city = city; }
        public String getDistrict() { return district; }
        public void setDistrict(String district) { this.district = district; }
        public String getStreet() { return street; }
        public void setStreet(String street) { this.street = street; }
        public String getDetailAddress() { return detailAddress; }
        public void setDetailAddress(String detailAddress) { this.detailAddress = detailAddress; }

        @Override
        public String toString() {
            return "AddressInfo{" +
                    "country='" + country + '\'' +
                    ", province='" + province + '\'' +
                    ", city='" + city + '\'' +
                    ", district='" + district + '\'' +
                    ", street='" + street + '\'' +
                    ", detailAddress='" + detailAddress + '\'' +
                    '}';
        }
    }

    private LocationUtils(Context context) {
        this.mContext = context.getApplicationContext();
        this.mLocationManager = (LocationManager) mContext.getSystemService(Context.LOCATION_SERVICE);
        this.mExecutorService = Executors.newSingleThreadExecutor();
        this.mMainHandler = new Handler(Looper.getMainLooper());
    }

    // 单例模式
    public static LocationUtils getInstance(Context context) {
        if (instance == null) {
            synchronized (LocationUtils.class) {
                if (instance == null) {
                    instance = new LocationUtils(context);
                }
            }
        }
        return instance;
    }

    /**
     * 核心方法：获取当前定位
     */
    public void getCurrentLocation(OnLocationResultListener listener) {
        this.mListener = listener;

        // 1. 检查权限
        if (!checkLocationPermission()) {
            notifyPermissionDenied();
            return;
        }

        // 2. 检查定位服务是否开启
        if (!isLocationServiceEnabled()) {
            notifyFailed("定位服务未开启，请前往设置开启");
            return;
        }

        // 3. 先尝试获取最后已知位置（缓存）
        Location lastLocation = getLastKnownLocation();
        if (lastLocation != null) {
            handleLocationResult(lastLocation);
            return;
        }

        // 4. 无缓存，注册定位监听获取实时位置
        registerLocationListener();

        // 5. 设置定位超时
        setLocationTimeout();
    }

    /**
     * 检查定位权限
     */
    private boolean checkLocationPermission() {
        return ActivityCompat.checkSelfPermission(mContext, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                || ActivityCompat.checkSelfPermission(mContext, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    /**
     * 检查定位服务是否开启
     */
    private boolean isLocationServiceEnabled() {
        // 检查GPS和网络定位是否至少一个开启
        boolean gpsEnabled = mLocationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);
        boolean networkEnabled = mLocationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER);
        return gpsEnabled || networkEnabled;
    }

    /**
     * 获取最后已知位置（优先GPS，其次网络）
     */
    private Location getLastKnownLocation() {
        Location bestLocation = null;
        try {
            // 遍历所有可用的定位提供者
            for (String provider : mLocationManager.getProviders(true)) {
                Location location = mLocationManager.getLastKnownLocation(provider);
                if (location == null) continue;

                // 选择精度更高的位置
                if (bestLocation == null || location.getAccuracy() < bestLocation.getAccuracy()) {
                    bestLocation = location;
                }
            }
        } catch (SecurityException e) {
            Log.e(TAG, "获取最后已知位置权限异常：" + e.getMessage());
        } catch (Exception e) {
            Log.e(TAG, "获取最后已知位置失败：" + e.getMessage());
        }
        return bestLocation;
    }

    /**
     * 注册定位监听器
     */
    private void registerLocationListener() {
        if (mLocationListener != null) {
            unregisterLocationListener();
        }

        mLocationListener = new LocationListener() {
            @Override
            public void onLocationChanged(Location location) {
                // 收到位置更新，取消超时，处理结果
                cancelTimeout();
                handleLocationResult(location);
                // 注销监听（仅获取一次定位）
                unregisterLocationListener();
            }

            @Override
            public void onStatusChanged(String provider, int status, Bundle extras) {
                // 定位提供者状态变化
                if (status == LocationProvider.OUT_OF_SERVICE || status == LocationProvider.TEMPORARILY_UNAVAILABLE) {
                    notifyFailed("定位服务不可用：" + provider);
                    unregisterLocationListener();
                    cancelTimeout();
                }
            }

            @Override
            public void onProviderEnabled(String provider) {}

            @Override
            public void onProviderDisabled(String provider) {}
        };

        try {
            // 注册GPS定位监听（高精度）
            if (mLocationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                mLocationManager.requestLocationUpdates(
                        LocationManager.GPS_PROVIDER,
                        MIN_UPDATE_TIME,
                        MIN_UPDATE_DISTANCE,
                        mLocationListener
                );
            }

            // 注册网络定位监听（低精度，补位）
            if (mLocationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                mLocationManager.requestLocationUpdates(
                        LocationManager.NETWORK_PROVIDER,
                        MIN_UPDATE_TIME,
                        MIN_UPDATE_DISTANCE,
                        mLocationListener
                );
            }
        } catch (SecurityException e) {
            Log.e(TAG, "注册定位监听权限异常：" + e.getMessage());
            notifyPermissionDenied();
        } catch (Exception e) {
            Log.e(TAG, "注册定位监听失败：" + e.getMessage());
            notifyFailed("注册定位监听失败：" + e.getMessage());
        }
    }

    /**
     * 注销定位监听器
     */
    private void unregisterLocationListener() {
        if (mLocationManager != null && mLocationListener != null) {
            try {
                mLocationManager.removeUpdates(mLocationListener);
            } catch (Exception e) {
                Log.e(TAG, "注销定位监听失败：" + e.getMessage());
            }
            mLocationListener = null;
        }
    }

    /**
     * 设置定位超时
     */
    private void setLocationTimeout() {
        cancelTimeout();
        mTimeoutRunnable = () -> {
            unregisterLocationListener();
            notifyFailed("定位超时（" + LOCATION_TIMEOUT / 1000 + "秒）");
        };
        mMainHandler.postDelayed(mTimeoutRunnable, LOCATION_TIMEOUT);
    }

    /**
     * 取消超时任务
     */
    private void cancelTimeout() {
        if (mTimeoutRunnable != null) {
            mMainHandler.removeCallbacks(mTimeoutRunnable);
            mTimeoutRunnable = null;
        }
    }

    /**
     * 处理定位结果（经纬度转地址）
     */
    private void handleLocationResult(Location location) {
        double latitude = location.getLatitude();
        double longitude = location.getLongitude();

        // 子线程处理逆地理编码
        mExecutorService.execute(() -> {
            AddressInfo addressInfo = getAddressFromLatLng(latitude, longitude);
            // 检查是否获取到城市信息，未获取则用IP定位兜底
            if (addressInfo.getCity() == null || addressInfo.getCity().isEmpty()) {
                // 调用IP定位
                IpLocationUtils.getInstance(mContext).getIpLocation(new IpLocationUtils.OnIpLocationListener() {
                    @Override
                    public void onIpLocationSuccess(String country, String province, String city) {
                        // 填充IP定位的城市信息
                        addressInfo.setCountry(country);
                        addressInfo.setProvince(province);
                        addressInfo.setCity(city);
                        // 主线程回调
                        mMainHandler.post(() -> notifySuccess(latitude, longitude, addressInfo));
                    }

                    @Override
                    public void onIpLocationFailed(String errorMsg) {
                        Log.w(TAG, "IP定位兜底失败：" + errorMsg);
                        // 即使IP定位失败，仍回调原始结果
                        mMainHandler.post(() -> notifySuccess(latitude, longitude, addressInfo));
                    }
                });
            } else {
                // Geocoder成功，直接回调
                mMainHandler.post(() -> notifySuccess(latitude, longitude, addressInfo));
            }
        });
    }

    /**
     * 逆地理编码：经纬度转地址（子线程执行）
     */
    private AddressInfo getAddressFromLatLng(double latitude, double longitude) {
        AddressInfo addressInfo = new AddressInfo();
        if (!Geocoder.isPresent()) {
            Log.e(TAG, "Geocoder服务不可用（建议接入高德/百度地图SDK）");
            return addressInfo;
        }

        Geocoder geocoder = new Geocoder(mContext, Locale.getDefault());
        try {
            List<Address> addresses = geocoder.getFromLocation(latitude, longitude, 1);
            if (addresses != null && !addresses.isEmpty()) {
                Address address = addresses.get(0);
                // 适配不同安卓版本/地区的字段差异
                addressInfo.setCountry(address.getCountryName());
                // 省份：优先用adminArea，无则用subAdminArea
                addressInfo.setProvince(address.getAdminArea() != null ? address.getAdminArea() : address.getSubAdminArea());
                // 城市：优先用locality，无则用subLocality
                addressInfo.setCity(address.getLocality() != null ? address.getLocality() : address.getSubLocality());
                addressInfo.setDistrict(address.getSubAdminArea());
                addressInfo.setStreet(address.getThoroughfare() != null ? address.getThoroughfare() : address.getFeatureName());
                addressInfo.setDetailAddress(address.getAddressLine(0));
                Log.d(TAG, "逆地理编码成功：" + addressInfo);
            } else {
                Log.e(TAG, "未匹配到地址信息");
            }
        } catch (IOException e) {
            Log.e(TAG, "逆地理编码IO异常：" + e.getMessage());
        } catch (Exception e) {
            Log.e(TAG, "逆地理编码异常：" + e.getMessage());
        }
        return addressInfo;
    }

    // 回调通知方法
    private void notifySuccess(double lat, double lng, AddressInfo info) {
        if (mListener != null) {
            mListener.onLocationSuccess(lat, lng, info);
        }
    }

    private void notifyFailed(String msg) {
        if (mListener != null) {
            mListener.onLocationFailed(msg);
        }
    }

    private void notifyPermissionDenied() {
        if (mListener != null) {
            mListener.onPermissionDenied();
        }
    }

    /**
     * 释放资源（必须调用）
     */
    public void release() {
        cancelTimeout();
        unregisterLocationListener();
        mExecutorService.shutdown();
        mListener = null;
        instance = null;
    }
}
