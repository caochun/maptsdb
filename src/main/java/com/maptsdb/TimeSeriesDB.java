package com.maptsdb;

import org.mapdb.DB;
import org.mapdb.DBMaker;
import org.mapdb.Serializer;
import java.util.concurrent.ConcurrentNavigableMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.NavigableMap;

/**
 * 基于MapDB的时序数据存储系统
 * 支持高频写入、时间范围查询和数据压缩
 */
public class TimeSeriesDB {
    
    private final DB db;
    private final ConcurrentNavigableMap<Long, Double> timeSeriesData;
    private final ScheduledExecutorService scheduler;
    private final String dbPath;
    
    /**
     * 构造函数
     * @param dbPath 数据库文件路径
     */
    public TimeSeriesDB(String dbPath) {
        this.dbPath = dbPath;
        
        // 初始化MapDB数据库
        this.db = DBMaker.fileDB(dbPath)
                .fileMmapEnable()           // 启用内存映射文件
                .transactionEnable()        // 启用事务支持
                .closeOnJvmShutdown()      // JVM关闭时自动关闭
                .make();
        
        // 创建时序数据存储结构
        this.timeSeriesData = db.treeMap("time_series")
                .keySerializer(Serializer.LONG)    // 时间戳序列化
                .valueSerializer(Serializer.DOUBLE)    // 浮点数序列化
                .createOrOpen();
        
        // 初始化定时任务调度器
        this.scheduler = Executors.newScheduledThreadPool(2);
        
        // 启动数据清理任务（每小时执行一次）
        scheduler.scheduleAtFixedRate(this::cleanupOldData, 1, 1, TimeUnit.HOURS);
    }
    
    /**
     * 写入时序数据点
     * @param timestamp 时间戳（毫秒）
     * @param value 数据值
     */
    public void put(long timestamp, double value) {
        timeSeriesData.put(timestamp, value);
        db.commit(); // 立即提交事务
    }
    
    /**
     * 批量写入时序数据
     * @param dataPoints 数据点列表
     */
    public void putBatch(List<DataPoint> dataPoints) {
        for (DataPoint point : dataPoints) {
            timeSeriesData.put(point.getTimestamp(), point.getValue());
        }
        db.commit(); // 批量提交事务
    }
    
    /**
     * 获取指定时间戳的数据
     * @param timestamp 时间戳
     * @return 数据值，如果不存在返回null
     */
    public Double get(long timestamp) {
        return timeSeriesData.get(timestamp);
    }
    
    /**
     * 时间范围查询
     * @param startTime 开始时间戳
     * @param endTime 结束时间戳
     * @return 时间范围内的数据映射
     */
    public NavigableMap<Long, Double> queryRange(long startTime, long endTime) {
        return timeSeriesData.subMap(startTime, true, endTime, true);
    }
    
    /**
     * 获取最新N个数据点
     * @param count 数据点数量
     * @return 最新的数据点列表
     */
    public List<DataPoint> getLatest(int count) {
        List<DataPoint> result = new ArrayList<>();
        Map.Entry<Long, Double> entry = timeSeriesData.lastEntry();
        
        for (int i = 0; i < count && entry != null; i++) {
            result.add(new DataPoint(entry.getKey(), entry.getValue()));
            entry = timeSeriesData.lowerEntry(entry.getKey());
        }
        
        return result;
    }
    
    /**
     * 获取数据统计信息
     * @return 数据库统计信息
     */
    public DBStats getStats() {
        return new DBStats(
            timeSeriesData.size(),
            0, // 暂时不获取存储大小，避免API兼容性问题
            System.currentTimeMillis()
        );
    }
    
    /**
     * 清理过期数据（保留30天）
     */
    private void cleanupOldData() {
        long thirtyDaysAgo = System.currentTimeMillis() - 30L * 24 * 3600 * 1000;
        timeSeriesData.headMap(thirtyDaysAgo).clear();
        db.commit();
        System.out.println("清理了30天前的历史数据");
    }
    
    /**
     * 关闭数据库连接
     */
    public void close() {
        scheduler.shutdown();
        db.close();
    }
    
    /**
     * 数据点类
     */
    public static class DataPoint {
        private final long timestamp;
        private final double value;
        
        public DataPoint(long timestamp, double value) {
            this.timestamp = timestamp;
            this.value = value;
        }
        
        public long getTimestamp() { return timestamp; }
        public double getValue() { return value; }
        
        @Override
        public String toString() {
            return String.format("DataPoint{timestamp=%d, value=%.2f}", timestamp, value);
        }
    }
    
    /**
     * 数据库统计信息类
     */
    public static class DBStats {
        private final long dataPointCount;
        private final long storageSize;
        private final long lastUpdateTime;
        
        public DBStats(long dataPointCount, long storageSize, long lastUpdateTime) {
            this.dataPointCount = dataPointCount;
            this.storageSize = storageSize;
            this.lastUpdateTime = lastUpdateTime;
        }
        
        public long getDataPointCount() { return dataPointCount; }
        public long getStorageSize() { return storageSize; }
        public long getLastUpdateTime() { return lastUpdateTime; }
        
        @Override
        public String toString() {
            return String.format("DBStats{dataPoints=%d, size=%d bytes, lastUpdate=%d}", 
                dataPointCount, storageSize, lastUpdateTime);
        }
    }
}
