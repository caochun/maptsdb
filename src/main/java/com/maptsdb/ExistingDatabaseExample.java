package com.maptsdb;

import java.util.Set;

/**
 * 现有数据库打开功能示例
 * 
 * <p>演示如何打开已存在的数据库并继续读写操作。</p>
 * 
 * @author MapTSDB Team
 * @version 1.0.0
 * @since 1.0.0
 */
public class ExistingDatabaseExample {
    
    public static void main(String[] args) {
        String dbPath = "example_existing.db";
        
        // 1. 创建数据库并写入一些数据
        System.out.println("=== 步骤1：创建数据库并写入数据 ===");
        createAndWriteData(dbPath);
        
        // 2. 重新打开现有数据库
        System.out.println("\n=== 步骤2：重新打开现有数据库 ===");
        openAndReadData(dbPath);
        
        // 3. 继续写入新数据
        System.out.println("\n=== 步骤3：继续写入新数据 ===");
        continueWritingData(dbPath);
        
        // 4. 动态添加新数据源
        System.out.println("\n=== 步骤4：动态添加新数据源 ===");
        addNewDataSource(dbPath);
    }
    
    /**
     * 创建数据库并写入数据
     */
    private static void createAndWriteData(String dbPath) {
        TimeSeriesDatabase db = TimeSeriesDatabaseBuilder.builder()
            .path(dbPath)
            .addDoubleSource("temperature", "环境温度")
            .addIntegerSource("humidity", "相对湿度")
            .addObjectSource("status", "设备状态")
            .buildWithDynamicSources();
        
        long timestamp = System.currentTimeMillis();
        
        // 写入数据
        db.putDouble("temperature", timestamp, 25.6);
        db.putInteger("humidity", timestamp, 65);
        db.putStringToObject("status", timestamp, "正常运行");
        db.commit();
        
        System.out.println("已创建数据库并写入数据：");
        System.out.println("  温度: " + db.getDouble("temperature", timestamp) + "°C");
        System.out.println("  湿度: " + db.getInteger("humidity", timestamp) + "%");
        System.out.println("  状态: " + db.getStringFromObject("status", timestamp));
        
        db.close();
    }
    
    /**
     * 打开现有数据库并读取数据
     */
    private static void openAndReadData(String dbPath) {
        // 使用新的API打开现有数据库
        TimeSeriesDatabase db = TimeSeriesDatabaseBuilder.openExisting(dbPath);
        
        // 显示恢复的数据源
        Set<String> dataSources = db.getDataSourceIds();
        System.out.println("恢复的数据源: " + dataSources);
        
        // 读取之前写入的数据 - 使用查询范围来获取最近的数据
        long currentTime = System.currentTimeMillis();
        long startTime = currentTime - 5000; // 查询最近5秒的数据
        
        // 查询温度数据
        java.util.NavigableMap<Long, Double> tempData = db.queryRangeDouble("temperature", startTime, currentTime);
        if (!tempData.isEmpty()) {
            java.util.Map.Entry<Long, Double> latestTemp = tempData.lastEntry();
            System.out.println("读取到的数据：");
            System.out.println("  温度: " + latestTemp.getValue() + "°C (时间戳: " + latestTemp.getKey() + ")");
        }
        
        // 查询湿度数据
        java.util.NavigableMap<Long, Integer> humidityData = db.queryRangeInteger("humidity", startTime, currentTime);
        if (!humidityData.isEmpty()) {
            java.util.Map.Entry<Long, Integer> latestHumidity = humidityData.lastEntry();
            System.out.println("  湿度: " + latestHumidity.getValue() + "% (时间戳: " + latestHumidity.getKey() + ")");
        }
        
        // 查询状态数据
        java.util.NavigableMap<Long, Object> statusData = db.queryRangeObject("status", startTime, currentTime);
        if (!statusData.isEmpty()) {
            java.util.Map.Entry<Long, Object> latestStatus = statusData.lastEntry();
            System.out.println("  状态: " + latestStatus.getValue() + " (时间戳: " + latestStatus.getKey() + ")");
        }
        
        db.close();
    }
    
    /**
     * 继续写入新数据
     */
    private static void continueWritingData(String dbPath) {
        TimeSeriesDatabase db = TimeSeriesDatabaseBuilder.openExisting(dbPath);
        
        long newTimestamp = System.currentTimeMillis();
        
        // 继续写入新数据
        db.putDouble("temperature", newTimestamp, 26.1);
        db.putInteger("humidity", newTimestamp, 68);
        db.putStringToObject("status", newTimestamp, "温度上升");
        db.commit();
        
        System.out.println("已写入新数据：");
        System.out.println("  温度: " + db.getDouble("temperature", newTimestamp) + "°C");
        System.out.println("  湿度: " + db.getInteger("humidity", newTimestamp) + "%");
        System.out.println("  状态: " + db.getStringFromObject("status", newTimestamp));
        
        db.close();
    }
    
    /**
     * 动态添加新数据源
     */
    private static void addNewDataSource(String dbPath) {
        // 使用支持动态添加的API
        TimeSeriesDatabase db = TimeSeriesDatabaseBuilder.openExistingWithDynamicSources(dbPath);
        
        // 动态添加新数据源
        db.addDoubleSource("pressure", "大气压力");
        db.addObjectSource("alerts", "告警信息");
        
        long timestamp = System.currentTimeMillis();
        
        // 向新数据源写入数据
        db.putDouble("pressure", timestamp, 1013.25);
        db.putStringToObject("alerts", timestamp, "系统正常，无告警");
        db.commit();
        
        System.out.println("已添加新数据源并写入数据：");
        System.out.println("  压力: " + db.getDouble("pressure", timestamp) + " hPa");
        System.out.println("  告警: " + db.getStringFromObject("alerts", timestamp));
        
        // 显示所有数据源
        System.out.println("所有数据源: " + db.getDataSourceIds());
        
        db.close();
    }
}
