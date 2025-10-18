package com.maptsdb;

import java.util.List;
import java.util.ArrayList;
import java.util.NavigableMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * MapDB时序数据存储使用示例
 * 演示高频数据写入、时间范围查询和性能测试
 */
public class TimeSeriesExample {
    
    public static void main(String[] args) {
        // 创建时序数据库实例
        TimeSeriesDB tsdb = new TimeSeriesDB("iot_sensor_data.db");
        
        try {
            // 示例1：模拟温度传感器数据写入
            simulateTemperatureSensor(tsdb);
            
            // 示例2：时间范围查询
            demonstrateRangeQuery(tsdb);
            
            // 示例3：批量数据写入
            demonstrateBatchWrite(tsdb);
            
            // 示例4：性能测试
            performanceTest(tsdb);
            
            // 显示数据库统计信息
            showDatabaseStats(tsdb);
            
        } finally {
            // 关闭数据库连接
            tsdb.close();
        }
    }
    
    /**
     * 模拟温度传感器数据写入
     */
    private static void simulateTemperatureSensor(TimeSeriesDB tsdb) {
        System.out.println("=== 模拟温度传感器数据写入 ===");
        
        long startTime = System.currentTimeMillis();
        double baseTemp = 25.0;
        
        // 模拟1分钟的数据，每秒10个数据点
        for (int i = 0; i < 600; i++) {
            long timestamp = startTime + i * 100; // 每100ms一个数据点
            double temperature = baseTemp + ThreadLocalRandom.current().nextGaussian() * 2.0;
            
            tsdb.put(timestamp, temperature);
            
            if (i % 100 == 0) {
                System.out.printf("写入数据点 %d: 时间=%d, 温度=%.2f°C%n", 
                    i, timestamp, temperature);
            }
        }
        
        System.out.println("温度传感器数据写入完成\n");
    }
    
    /**
     * 演示时间范围查询
     */
    private static void demonstrateRangeQuery(TimeSeriesDB tsdb) {
        System.out.println("=== 时间范围查询演示 ===");
        
        long currentTime = System.currentTimeMillis();
        long startTime = currentTime - 30000; // 查询最近30秒的数据
        long endTime = currentTime;
        
        NavigableMap<Long, Double> recentData = tsdb.queryRange(startTime, endTime);
        
        System.out.printf("查询时间范围: %d 到 %d%n", startTime, endTime);
        System.out.printf("查询结果数量: %d 个数据点%n", recentData.size());
        
        // 显示前5个数据点
        int count = 0;
        for (java.util.Map.Entry<Long, Double> entry : recentData.entrySet()) {
            if (count++ >= 5) break;
            System.out.printf("  时间=%d, 温度=%.2f°C%n", 
                entry.getKey(), entry.getValue());
        }
        
        System.out.println();
    }
    
    /**
     * 演示批量数据写入
     */
    private static void demonstrateBatchWrite(TimeSeriesDB tsdb) {
        System.out.println("=== 批量数据写入演示 ===");
        
        List<TimeSeriesDB.DataPoint> batchData = new ArrayList<>();
        long baseTime = System.currentTimeMillis();
        
        // 准备1000个数据点
        for (int i = 0; i < 1000; i++) {
            long timestamp = baseTime + i * 10; // 每10ms一个数据点
            double value = 20.0 + Math.sin(i * 0.01) * 5.0; // 正弦波数据
            batchData.add(new TimeSeriesDB.DataPoint(timestamp, value));
        }
        
        long startTime = System.currentTimeMillis();
        tsdb.putBatch(batchData);
        long endTime = System.currentTimeMillis();
        
        System.out.printf("批量写入 %d 个数据点，耗时: %d ms%n", 
            batchData.size(), endTime - startTime);
        System.out.println();
    }
    
    /**
     * 性能测试
     */
    private static void performanceTest(TimeSeriesDB tsdb) {
        System.out.println("=== 性能测试 ===");
        
        int testDataPoints = 10000;
        long startTime = System.currentTimeMillis();
        long baseTime = startTime;
        
        // 写入性能测试
        for (int i = 0; i < testDataPoints; i++) {
            long timestamp = baseTime + i;
            double value = ThreadLocalRandom.current().nextDouble(0, 100);
            tsdb.put(timestamp, value);
        }
        
        long writeEndTime = System.currentTimeMillis();
        long writeDuration = writeEndTime - startTime;
        
        // 查询性能测试
        long queryStartTime = System.currentTimeMillis();
        long queryStart = baseTime;
        long queryEnd = baseTime + testDataPoints / 2;
        NavigableMap<Long, Double> queryResult = tsdb.queryRange(queryStart, queryEnd);
        long queryEndTime = System.currentTimeMillis();
        long queryDuration = queryEndTime - queryStartTime;
        
        System.out.printf("写入性能: %d 数据点/秒%n", 
            testDataPoints * 1000 / Math.max(writeDuration, 1));
        System.out.printf("查询结果: %d 个数据点%n", queryResult.size());
        System.out.printf("总数据点: %d%n", tsdb.getStats().getDataPointCount());
        System.out.println();
    }
    
    /**
     * 显示数据库统计信息
     */
    private static void showDatabaseStats(TimeSeriesDB tsdb) {
        System.out.println("=== 数据库统计信息 ===");
        TimeSeriesDB.DBStats stats = tsdb.getStats();
        System.out.println(stats);
        
        // 显示最新5个数据点
        List<TimeSeriesDB.DataPoint> latestData = tsdb.getLatest(5);
        System.out.println("最新5个数据点:");
        for (TimeSeriesDB.DataPoint point : latestData) {
            System.out.println("  " + point);
        }
    }
}
