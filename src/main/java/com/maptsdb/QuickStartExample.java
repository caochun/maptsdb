package com.maptsdb;

import java.util.*;

/**
 * MapTSDB 快速开始示例
 * 
 * 这个示例展示了如何使用MapTSDB进行时序数据存储和查询
 * 
 * 性能特点：
 * - 单条写入：400,000 条/秒 (数值类型)
 * - 批量写入：312,500 条/秒 (数值类型)
 * - 读取性能：833,333 条/秒 (数值类型)
 * - 并发写入：131,148 条/秒 (零数据丢失)
 */
public class QuickStartExample {
    
    public static void main(String[] args) {
        // 1. 创建数据库实例
        TimeSeriesDatabase db = TimeSeriesDatabaseBuilder.builder()
                .path("example.db")
                .addDoubleSource("temperature", "环境温度")
                .addIntegerSource("humidity", "相对湿度")
                .addObjectSource("status", "设备状态")
                .withRetentionDays(30)
                .enableMemoryMapping()
                .buildWithDynamicSources();
        
        try {
            // 2. 单条写入示例
            singleWriteExample(db);
            
            // 3. 批量写入示例
            batchWriteExample(db);
            
            // 4. 查询示例
            queryExample(db);
            
            // 5. 性能测试示例
            performanceTestExample(db);
            
        } finally {
            // 6. 关闭数据库
            db.close();
        }
    }
    
    /**
     * 单条写入示例
     */
    private static void singleWriteExample(TimeSeriesDatabase db) {
        System.out.println("=== 单条写入示例 ===");
        
        long timestamp = System.currentTimeMillis();
        
        // 写入不同类型的数据
        db.putDouble("temperature", timestamp, 25.6);
        db.putInteger("humidity", timestamp, 65);
        db.putObject("status", timestamp, "正常");
        
        // 重要：手动提交事务（提升性能的关键）
        db.commit();
        
        System.out.println("单条写入完成");
    }
    
    /**
     * 批量写入示例
     */
    private static void batchWriteExample(TimeSeriesDatabase db) {
        System.out.println("\n=== 批量写入示例 ===");
        
        // 准备批量数据
        List<DataPoint<Double>> tempData = new ArrayList<>();
        List<DataPoint<Integer>> humidityData = new ArrayList<>();
        List<DataPoint<Object>> statusData = new ArrayList<>();
        
        long baseTime = System.currentTimeMillis();
        for (int i = 0; i < 1000; i++) {
            long timestamp = baseTime + i * 1000;
            tempData.add(new DataPoint<>(timestamp, 20.0 + Math.random() * 10));
            humidityData.add(new DataPoint<>(timestamp, 50 + (int)(Math.random() * 30)));
            statusData.add(new DataPoint<>(timestamp, i % 2 == 0 ? "正常" : "警告"));
        }
        
        // 批量写入（高性能）
        db.putBatchDouble("temperature", tempData);
        db.putBatchInteger("humidity", humidityData);
        db.putBatchObject("status", statusData);
        
        // 批量写入后提交事务
        db.commit();
        
        System.out.println("批量写入完成：1000条数据");
    }
    
    /**
     * 查询示例
     */
    private static void queryExample(TimeSeriesDatabase db) {
        System.out.println("\n=== 查询示例 ===");
        
        // 写入一些测试数据
        long timestamp1 = System.currentTimeMillis();
        long timestamp2 = timestamp1 + 1000;
        long timestamp3 = timestamp1 + 2000;
        
        // 写入多个时间点的数据
        db.putDouble("temperature", timestamp1, 25.6);
        db.putInteger("humidity", timestamp1, 65);
        db.putObject("status", timestamp1, "正常");
        
        db.putDouble("temperature", timestamp2, 26.1);
        db.putInteger("humidity", timestamp2, 68);
        db.putObject("status", timestamp2, "警告");
        
        db.putDouble("temperature", timestamp3, 25.8);
        db.putInteger("humidity", timestamp3, 62);
        db.putObject("status", timestamp3, "正常");
        
        db.commit();
        
        // 查询单个数据点
        System.out.println("=== 单点查询 ===");
        Double temp1 = db.getDouble("temperature", timestamp1);
        Integer humidity1 = db.getInteger("humidity", timestamp1);
        String status1 = (String) db.getObject("status", timestamp1);
        
        System.out.println("时间点1 - 温度: " + temp1 + "°C, 湿度: " + humidity1 + "%, 状态: " + status1);
        
        Double temp2 = db.getDouble("temperature", timestamp2);
        Integer humidity2 = db.getInteger("humidity", timestamp2);
        String status2 = (String) db.getObject("status", timestamp2);
        
        System.out.println("时间点2 - 温度: " + temp2 + "°C, 湿度: " + humidity2 + "%, 状态: " + status2);
        
        // 查询不存在的数据点
        Double tempNotFound = db.getDouble("temperature", timestamp1 + 5000);
        System.out.println("不存在的数据点: " + tempNotFound);
        
        // 获取统计信息
        System.out.println("\n=== 数据统计 ===");
        // 获取数据源统计（这里需要实现统计方法）
        System.out.println("数据源统计: 需要实现统计方法");
        
        // 获取数据源信息
        System.out.println("数据源信息:");
        for (String sourceId : new String[]{"temperature", "humidity", "status"}) {
            DataSourceConfig config = db.getDataSourceInfo(sourceId);
            if (config != null) {
                System.out.println("  " + sourceId + ": " + config.getDescription());
            }
        }
    }
    
    /**
     * 性能测试示例
     */
    private static void performanceTestExample(TimeSeriesDatabase db) {
        System.out.println("\n=== 性能测试示例 ===");
        
        int testCount = 10000;
        long startTime = System.currentTimeMillis();
        
        // 单条写入性能测试
        for (int i = 0; i < testCount; i++) {
            long timestamp = System.currentTimeMillis() + i;
            db.putDouble("temperature", timestamp, Math.random() * 100);
        }
        db.commit();
        
        long writeTime = System.currentTimeMillis() - startTime;
        double writeRate = (double) testCount / writeTime * 1000;
        
        System.out.println("单条写入性能: " + String.format("%.0f", writeRate) + " 条/秒");
        
        // 批量写入性能测试
        List<DataPoint<Double>> batchData = new ArrayList<>();
        for (int i = 0; i < testCount; i++) {
            long timestamp = System.currentTimeMillis() + i;
            batchData.add(new DataPoint<>(timestamp, Math.random() * 100));
        }
        
        startTime = System.currentTimeMillis();
        db.putBatchDouble("temperature", batchData);
        db.commit();
        
        long batchTime = System.currentTimeMillis() - startTime;
        double batchRate = (double) testCount / batchTime * 1000;
        
        System.out.println("批量写入性能: " + String.format("%.0f", batchRate) + " 条/秒");
        
        // 读取性能测试
        startTime = System.currentTimeMillis();
        for (int i = 0; i < testCount; i++) {
            long timestamp = System.currentTimeMillis() + i;
            db.getDouble("temperature", timestamp);
        }
        
        long readTime = System.currentTimeMillis() - startTime;
        double readRate = (double) testCount / readTime * 1000;
        
        System.out.println("读取性能: " + String.format("%.0f", readRate) + " 条/秒");
    }
}
