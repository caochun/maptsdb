package com.maptsdb;

import java.util.List;
import java.util.NavigableMap;
import java.util.Map;

/**
 * 多数据类型时序数据库使用示例
 * 演示如何存储和查询不同类型的数据
 */
public class MultiTypeExample {
    
    public static void main(String[] args) {
        // 创建多类型时序数据库实例
        MultiTypeTimeSeriesDB tsdb = new MultiTypeTimeSeriesDB("multi_type_data.db");
        
        try {
            // 示例1：存储不同类型的数据
            demonstrateMultiTypeData(tsdb);
            
            // 示例2：按类型查询数据
            demonstrateTypeFilteredQuery(tsdb);
            
            // 示例3：混合数据类型查询
            demonstrateMixedDataQuery(tsdb);
            
            // 显示数据库统计信息
            showDatabaseStats(tsdb);
            
        } finally {
            // 关闭数据库连接
            tsdb.close();
        }
    }
    
    /**
     * 演示存储不同类型的数据
     */
    private static void demonstrateMultiTypeData(MultiTypeTimeSeriesDB tsdb) {
        System.out.println("=== 存储多种数据类型 ===");
        
        long baseTime = System.currentTimeMillis();
        
        // 存储温度数据（Double）
        tsdb.putDouble(baseTime + 1000, 25.5);
        tsdb.putDouble(baseTime + 2000, 26.8);
        tsdb.putDouble(baseTime + 3000, 24.2);
        
        // 存储湿度数据（Integer）
        tsdb.putInteger(baseTime + 1000, 65);
        tsdb.putInteger(baseTime + 2000, 70);
        tsdb.putInteger(baseTime + 3000, 60);
        
        // 存储设备状态（String）
        tsdb.putString(baseTime + 1000, "ONLINE");
        tsdb.putString(baseTime + 2000, "ONLINE");
        tsdb.putString(baseTime + 3000, "OFFLINE");
        
        // 存储开关状态（Boolean）
        tsdb.putBoolean(baseTime + 1000, true);
        tsdb.putBoolean(baseTime + 2000, true);
        tsdb.putBoolean(baseTime + 3000, false);
        
        // 存储计数器（Long）
        tsdb.putLong(baseTime + 1000, 1000L);
        tsdb.putLong(baseTime + 2000, 1001L);
        tsdb.putLong(baseTime + 3000, 1002L);
        
        // 存储压力数据（Float）
        tsdb.putFloat(baseTime + 1000, 1013.25f);
        tsdb.putFloat(baseTime + 2000, 1012.80f);
        tsdb.putFloat(baseTime + 3000, 1014.10f);
        
        System.out.println("已存储多种类型的数据：温度、湿度、状态、开关、计数器、压力");
        System.out.println();
    }
    
    /**
     * 演示按类型查询数据
     */
    private static void demonstrateTypeFilteredQuery(MultiTypeTimeSeriesDB tsdb) {
        System.out.println("=== 按类型查询数据 ===");
        
        long currentTime = System.currentTimeMillis();
        long startTime = currentTime - 10000;
        long endTime = currentTime;
        
        // 查询所有Double类型数据（温度）
        List<MultiTypeTimeSeriesDB.TypedDataPoint<Double>> temperatureData = 
            tsdb.queryRangeByType(startTime, endTime, Double.class);
        System.out.printf("温度数据 (%d 个数据点):%n", temperatureData.size());
        for (MultiTypeTimeSeriesDB.TypedDataPoint<Double> point : temperatureData) {
            System.out.printf("  时间=%d, 温度=%.1f°C%n", point.getTimestamp(), point.getValue());
        }
        
        // 查询所有Integer类型数据（湿度）
        List<MultiTypeTimeSeriesDB.TypedDataPoint<Integer>> humidityData = 
            tsdb.queryRangeByType(startTime, endTime, Integer.class);
        System.out.printf("湿度数据 (%d 个数据点):%n", humidityData.size());
        for (MultiTypeTimeSeriesDB.TypedDataPoint<Integer> point : humidityData) {
            System.out.printf("  时间=%d, 湿度=%d%%%n", point.getTimestamp(), point.getValue());
        }
        
        // 查询所有String类型数据（状态）
        List<MultiTypeTimeSeriesDB.TypedDataPoint<String>> statusData = 
            tsdb.queryRangeByType(startTime, endTime, String.class);
        System.out.printf("状态数据 (%d 个数据点):%n", statusData.size());
        for (MultiTypeTimeSeriesDB.TypedDataPoint<String> point : statusData) {
            System.out.printf("  时间=%d, 状态=%s%n", point.getTimestamp(), point.getValue());
        }
        
        System.out.println();
    }
    
    /**
     * 演示混合数据类型查询
     */
    private static void demonstrateMixedDataQuery(MultiTypeTimeSeriesDB tsdb) {
        System.out.println("=== 混合数据类型查询 ===");
        
        long currentTime = System.currentTimeMillis();
        long startTime = currentTime - 10000;
        long endTime = currentTime;
        
        // 查询时间范围内的所有数据
        NavigableMap<Long, Object> allData = tsdb.queryRange(startTime, endTime);
        
        System.out.printf("时间范围内的所有数据 (%d 个数据点):%n", allData.size());
        for (Map.Entry<Long, Object> entry : allData.entrySet()) {
            Object value = entry.getValue();
            String typeName = value != null ? value.getClass().getSimpleName() : "null";
            System.out.printf("  时间=%d, 值=%s, 类型=%s%n", 
                entry.getKey(), value, typeName);
        }
        
        System.out.println();
    }
    
    /**
     * 演示类型安全的数据访问
     */
    private static void demonstrateTypeSafeAccess(MultiTypeTimeSeriesDB tsdb) {
        System.out.println("=== 类型安全的数据访问 ===");
        
        long timestamp = System.currentTimeMillis() - 5000;
        
        // 类型安全的数据获取
        Double temperature = tsdb.getDouble(timestamp);
        Integer humidity = tsdb.getInteger(timestamp);
        String status = tsdb.getString(timestamp);
        Boolean switchState = (Boolean) tsdb.get(timestamp);
        
        System.out.printf("时间戳 %d 的数据:%n", timestamp);
        System.out.printf("  温度: %s%n", temperature != null ? temperature + "°C" : "无数据");
        System.out.printf("  湿度: %s%n", humidity != null ? humidity + "%" : "无数据");
        System.out.printf("  状态: %s%n", status != null ? status : "无数据");
        System.out.printf("  开关: %s%n", switchState != null ? (switchState ? "开启" : "关闭") : "无数据");
        
        System.out.println();
    }
    
    /**
     * 显示数据库统计信息
     */
    private static void showDatabaseStats(MultiTypeTimeSeriesDB tsdb) {
        System.out.println("=== 数据库统计信息 ===");
        MultiTypeTimeSeriesDB.DBStats stats = tsdb.getStats();
        System.out.println(stats);
        
        // 显示最新5个数据点
        List<MultiTypeTimeSeriesDB.DataPoint> latestData = tsdb.getLatest(5);
        System.out.println("最新5个数据点:");
        for (MultiTypeTimeSeriesDB.DataPoint point : latestData) {
            System.out.println("  " + point);
        }
    }
}
