package com.leaf.collegeidleapp;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.leaf.collegeidleapp.adapter.AllCommodityAdapter;
import com.leaf.collegeidleapp.bean.Commodity;
import com.leaf.collegeidleapp.util.CommodityDbHelper;
import com.leaf.collegeidleapp.util.LocationUtils;
import com.leaf.collegeidleapp.util.DistanceCalculator;

import java.util.ArrayList;
import java.util.List;

/**
 * 主界面活动类
 *
 * @author: autumn_leaf
 */
public class MainActivity extends AppCompatActivity {

    private static final int REQUEST_LOCATION_PERMISSION = 1001;
    private LocationUtils mLocationUtils;

    ListView lvAllCommodity;
    List<Commodity> allCommodities = new ArrayList<>();
    ImageButton ibLearning, ibElectronic, ibDaily, ibSports;

    CommodityDbHelper dbHelper;
    AllCommodityAdapter adapter;

    private TextView tvLocation;
    private Button btnLocation;

    // 新增的UI组件
    private EditText etLat1, etLng1, etLat2, etLng2;
    private Button btnCalculateDistance;
    private TextView tvDistanceResult;

    // 折叠/展开相关组件
    private boolean isLocationExpanded = false;
    private LinearLayout locationExpandContent;
    private ImageView ivExpandCollapse;
    private TextView tvCurrentLocationSummary;
    private LinearLayout locationHeader;

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        lvAllCommodity = findViewById(R.id.lv_all_commodity);
        tvLocation = findViewById(R.id.tv_location);
        btnLocation = findViewById(R.id.btn_location);

        // 初始化新增的UI组件
        etLat1 = findViewById(R.id.et_lat1);
        etLng1 = findViewById(R.id.et_lng1);
        etLat2 = findViewById(R.id.et_lat2);
        etLng2 = findViewById(R.id.et_lng2);
        btnCalculateDistance = findViewById(R.id.btn_calculate_distance);
        tvDistanceResult = findViewById(R.id.tv_distance_result);

        // 初始化折叠/展开相关组件
        initCollapsibleLocation();

        // 设置开始定位按钮点击事件
        btnLocation.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // 确保定位模块展开
                if (!isLocationExpanded) {
                    toggleLocationExpand();
                }
                // 初始化定位工具类
                mLocationUtils = LocationUtils.getInstance(MainActivity.this);
                // 检查权限并获取定位
                checkLocationPermissionAndGetLocation();
            }
        });

        // 设置计算距离按钮点击事件
        btnCalculateDistance.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                calculateAndDisplayDistance();
            }
        });

        dbHelper = new CommodityDbHelper(getApplicationContext(), CommodityDbHelper.DB_NAME, null, 1);
        adapter = new AllCommodityAdapter(getApplicationContext());
        allCommodities = dbHelper.readAllCommodities();
        adapter.setData(allCommodities);
        lvAllCommodity.setAdapter(adapter);

        final Bundle bundle = this.getIntent().getExtras();
        final TextView tvStuNumber = findViewById(R.id.tv_student_number);
        String str = "";
        if (bundle != null) {
            str = "欢迎" + bundle.getString("username") + ",你好!";
        }
        tvStuNumber.setText(str);

        // 当前登录的学生账号
        final String stuNum = tvStuNumber.getText().toString().substring(2,
                tvStuNumber.getText().length() - 4);

        ImageButton IbAddProduct = findViewById(R.id.ib_add_product);
        // 跳转到添加物品界面
        IbAddProduct.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(MainActivity.this, AddCommodityActivity.class);
                if (bundle != null) {
                    // 获取学生学号
                    bundle.putString("user_id", stuNum);
                    intent.putExtras(bundle);
                }
                startActivity(intent);
            }
        });

        ImageButton IbPersonalCenter = findViewById(R.id.ib_personal_center);
        // 跳转到个人中心界面
        IbPersonalCenter.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(MainActivity.this, PersonalCenterActivity.class);
                if (bundle != null) {
                    // 获取学生学号
                    bundle.putString("username1", stuNum);
                    intent.putExtras(bundle);
                }
                startActivity(intent);
            }
        });

        // 刷新界面
        TextView tvRefresh = findViewById(R.id.tv_refresh);
        tvRefresh.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                allCommodities = dbHelper.readAllCommodities();
                adapter.setData(allCommodities);
                lvAllCommodity.setAdapter(adapter);
            }
        });

        // 为每一个item设置点击事件
        lvAllCommodity.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                Commodity commodity = (Commodity) lvAllCommodity.getAdapter().getItem(position);
                Bundle bundle1 = new Bundle();
                bundle1.putInt("position", position);
                bundle1.putByteArray("picture", commodity.getPicture());
                bundle1.putString("title", commodity.getTitle());
                bundle1.putString("description", commodity.getDescription());
                bundle1.putFloat("price", commodity.getPrice());
                bundle1.putString("phone", commodity.getPhone());
                bundle1.putString("stuId", stuNum);
                Intent intent = new Intent(MainActivity.this, ReviewCommodityActivity.class);
                intent.putExtras(bundle1);
                startActivity(intent);
            }
        });

        // 点击不同的类别,显示不同的商品信息
        ibLearning = findViewById(R.id.ib_learning_use);
        ibElectronic = findViewById(R.id.ib_electric_product);
        ibDaily = findViewById(R.id.ib_daily_use);
        ibSports = findViewById(R.id.ib_sports_good);
        final Bundle bundle2 = new Bundle();

        // 学习用品
        ibLearning.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                bundle2.putInt("status", 1);
                Intent intent = new Intent(MainActivity.this, CommodityTypeActivity.class);
                intent.putExtras(bundle2);
                startActivity(intent);
            }
        });

        // 电子用品
        ibElectronic.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                bundle2.putInt("status", 2);
                Intent intent = new Intent(MainActivity.this, CommodityTypeActivity.class);
                intent.putExtras(bundle2);
                startActivity(intent);
            }
        });

        // 生活用品
        ibDaily.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                bundle2.putInt("status", 3);
                Intent intent = new Intent(MainActivity.this, CommodityTypeActivity.class);
                intent.putExtras(bundle2);
                startActivity(intent);
            }
        });

        // 体育用品
        ibSports.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                bundle2.putInt("status", 4);
                Intent intent = new Intent(MainActivity.this, CommodityTypeActivity.class);
                intent.putExtras(bundle2);
                startActivity(intent);
            }
        });
    }

    /**
     * 初始化折叠/展开定位模块
     */
    private void initCollapsibleLocation() {
        locationExpandContent = findViewById(R.id.location_expand_content);
        ivExpandCollapse = findViewById(R.id.iv_expand_collapse);
        tvCurrentLocationSummary = findViewById(R.id.tv_current_location_summary);
        locationHeader = findViewById(R.id.location_header);

        // 如果找不到tv_current_location_summary，从布局中移除或注释掉相关代码
        if (tvCurrentLocationSummary != null) {
            tvCurrentLocationSummary.setText("未获取位置");
        }

        // 设置折叠/展开点击事件
        if (locationHeader != null) {
            locationHeader.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    toggleLocationExpand();
                }
            });
        }
    }

    /**
     * 切换定位模块的折叠/展开状态
     */
    private void toggleLocationExpand() {
        if (isLocationExpanded) {
            // 折叠
            locationExpandContent.setVisibility(View.GONE);
            ivExpandCollapse.setImageResource(android.R.drawable.arrow_down_float);
        } else {
            // 展开
            locationExpandContent.setVisibility(View.VISIBLE);
            ivExpandCollapse.setImageResource(android.R.drawable.arrow_up_float);
        }
        isLocationExpanded = !isLocationExpanded;
    }

    /**
     * 计算并显示距离
     */
    private void calculateAndDisplayDistance() {
        try {
            // 获取输入值
            String lat1Str = etLat1.getText().toString().trim();
            String lng1Str = etLng1.getText().toString().trim();
            String lat2Str = etLat2.getText().toString().trim();
            String lng2Str = etLng2.getText().toString().trim();

            // 验证输入是否为空
            if (lat1Str.isEmpty() || lng1Str.isEmpty() || lat2Str.isEmpty() || lng2Str.isEmpty()) {
                tvDistanceResult.setText("请填写所有经纬度值");
                return;
            }

            // 转换为double
            double lat1 = Double.parseDouble(lat1Str);
            double lng1 = Double.parseDouble(lng1Str);
            double lat2 = Double.parseDouble(lat2Str);
            double lng2 = Double.parseDouble(lng2Str);

            // 验证经纬度范围
            if (lat1 < -90 || lat1 > 90 || lat2 < -90 || lat2 > 90) {
                tvDistanceResult.setText("纬度范围：-90 到 90 度");
                return;
            }

            if (lng1 < -180 || lng1 > 180 || lng2 < -180 || lng2 > 180) {
                tvDistanceResult.setText("经度范围：-180 到 180 度");
                return;
            }

            // 使用DistanceCalculator工具类计算距离
            double distance = DistanceCalculator.calculateDistance(lat1, lng1, lat2, lng2);

            // 格式化显示结果
            String formattedDistance = DistanceCalculator.formatDistance(distance);

            // 显示结果
            tvDistanceResult.setText("距离：" + formattedDistance);

        } catch (NumberFormatException e) {
            tvDistanceResult.setText("请输入有效的数字格式");
        } catch (Exception e) {
            tvDistanceResult.setText("计算错误：" + e.getMessage());
        }
    }

    /**
     * 检查定位权限，无权限则申请
     */
    private void checkLocationPermissionAndGetLocation() {
        if (ContextCompat.checkSelfPermission(this,
                android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
                && ContextCompat.checkSelfPermission(this,
                android.Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            // 申请权限
            ActivityCompat.requestPermissions(
                    this,
                    new String[]{
                            android.Manifest.permission.ACCESS_FINE_LOCATION,
                            android.Manifest.permission.ACCESS_COARSE_LOCATION
                    },
                    REQUEST_LOCATION_PERMISSION
            );
        } else {
            // 已有权限，直接获取定位
            getCurrentLocation();
        }
    }

    /**
     * 获取当前定位
     */
    private void getCurrentLocation() {
        mLocationUtils.getCurrentLocation(new LocationUtils.OnLocationResultListener() {
            @Override
            public void onLocationSuccess(double latitude, double longitude,
                                          LocationUtils.AddressInfo addressInfo) {
                // 定位成功，处理结果
                String result = String.format(
                        "纬度：%s\n经度：%s\n城市：%s\n详细地址：%s",
                        latitude, longitude,
                        addressInfo.getCity(),
                        addressInfo.getDetailAddress()
                );
                tvLocation.setText(result);

                // 可选：将获取的定位自动填入第一个输入框
                etLat1.setText(String.valueOf(latitude));
                etLng1.setText(String.valueOf(longitude));

                // 更新摘要信息
                if (tvCurrentLocationSummary != null) {
                    String summary = String.format("当前位置：%s, %s",
                            addressInfo.getCity(),
                            addressInfo.getDetailAddress() != null ?
                                    addressInfo.getDetailAddress() : "未知地址");
                    tvCurrentLocationSummary.setText(summary);
                }
            }

            @Override
            public void onLocationFailed(String errorMsg) {
                // 定位失败
                Toast.makeText(MainActivity.this, "定位失败：" + errorMsg, Toast.LENGTH_SHORT).show();
                if (tvCurrentLocationSummary != null) {
                    tvCurrentLocationSummary.setText("定位失败");
                }
            }

            @Override
            public void onPermissionDenied() {
                // 权限不足（理论上不会走到这里，因为已经提前申请）
                Toast.makeText(MainActivity.this, "定位权限不足", Toast.LENGTH_SHORT).show();
                if (tvCurrentLocationSummary != null) {
                    tvCurrentLocationSummary.setText("权限不足");
                }
            }
        });
    }

    /**
     * 权限申请结果回调
     */
    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions,
                                           int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_LOCATION_PERMISSION) {
            boolean isGranted = true;
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    isGranted = false;
                    break;
                }
            }
            if (isGranted) {
                // 权限申请成功，获取定位
                getCurrentLocation();
            } else {
                Toast.makeText(this, "定位权限被拒绝，无法获取位置信息", Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // 释放定位资源，避免内存泄漏
        if (mLocationUtils != null) {
            mLocationUtils.release();
        }
    }
}