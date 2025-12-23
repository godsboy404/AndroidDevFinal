// 位置：app/src/main/java/com/yourpackage/utils/DistanceCalculator.java
package com.leaf.collegeidleapp.util;

public class DistanceCalculator {

    /**
     * 计算两个经纬度之间的距离
     */
    public static double calculateDistance(double lat1, double lon1, double lat2, double lon2) {
        // 使用Haversine公式
        final int R = 6371000; // 地球半径（米）

        double latDistance = Math.toRadians(lat2 - lat1);
        double lonDistance = Math.toRadians(lon2 - lon1);

        double a = Math.sin(latDistance / 2) * Math.sin(latDistance / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(lonDistance / 2) * Math.sin(lonDistance / 2);

        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));

        return R * c;
    }

    /**
     * 格式化距离显示
     */
    public static String formatDistance(double distanceMeters) {
        if (distanceMeters < 1000) {
            return String.format("%.2f 米", distanceMeters);
        } else {
            return String.format("%.2f 公里", distanceMeters / 1000);
        }
    }
}