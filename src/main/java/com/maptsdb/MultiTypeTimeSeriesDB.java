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
import java.util.HashMap;
import java.util.NavigableMap;
import java.util.stream.Collectors;

/**
 * 支持多种数据类型的时序数据库
 * 支持：Double, Integer, Long, String, Boolean, Float
 */
public class MultiTypeTimeSeriesDB {
    
    private final DB db;
    private final ConcurrentNavigableMap<Long, Object> timeSeriesData;
    private final ScheduledExecutorService scheduler;
    private final String dbPath;
    
    /**
     * 构造函数
     * @param dbPath 数据库文件路径
     */
    public MultiTypeTimeSeriesDB(String dbPath) {
        this.dbPath = dbPath;
        
        // 初始化MapDB数据库
        this.db = DBMaker.fileDB(dbPath)
                .fileMmapEnable()           // 启用内存映射文件
                .fileMmapPreclearDisable()  // 禁用预清理以提高性能
                .cleanerHackEnable()        // 启用清理器以防止内存泄漏
                .transactionEnable()        // 启用事务支持
                .closeOnJvmShutdown()      // JVM关闭时自动关闭
                .concurrencyScale(16)      // 设置并发级别
                .make();
        
        // 创建时序数据存储结构 - 使用通用Object类型
        this.timeSeriesData = db.treeMap("time_series")
                .keySerializer(Serializer.LONG)    // 时间戳序列化
                .valueSerializer(Serializer.JAVA) // 使用Java序列化器支持任意对象
                .createOrOpen();
        
        // 初始化定时任务调度器
        this.scheduler = Executors.newScheduledThreadPool(2);
        
        // 启动数据清理任务（每小时执行一次）
        scheduler.scheduleAtFixedRate(this::cleanupOldData, 1, 1, TimeUnit.HOURS);
    }
    
    /**
     * 写入Double类型数据
     */
    public void putDouble(long timestamp, double value) {
        timeSeriesData.put(timestamp, value);
        db.commit();
    }
    
    /**
     * 写入Integer类型数据
     */
    public void putInteger(long timestamp, int value) {
        timeSeriesData.put(timestamp, value);
        db.commit();
    }
    
    /**
     * 写入Long类型数据
     */
    public void putLong(long timestamp, long value) {
        timeSeriesData.put(timestamp, value);
        db.commit();
    }
    
    /**
     * 写入String类型数据
     */
    public void putString(long timestamp, String value) {
        timeSeriesData.put(timestamp, value);
        db.commit();
    }
    
    /**
     * 写入Boolean类型数据
     */
    public void putBoolean(long timestamp, boolean value) {
        timeSeriesData.put(timestamp, value);
        db.commit();
    }
    
    /**
     * 写入Float类型数据
     */
    public void putFloat(long timestamp, float value) {
        timeSeriesData.put(timestamp, value);
        db.commit();
    }
    
    /**
     * 通用写入方法
     */
    public void put(long timestamp, Object value) {
        timeSeriesData.put(timestamp, value);
        db.commit();
    }
    
    /**
     * 批量写入数据点
     * @param dataPoints 数据点列表
     */
    public void putBatch(List<DataPoint> dataPoints) {
        for (DataPoint point : dataPoints) {
            timeSeriesData.put(point.getTimestamp(), point.getValue());
        }
        db.commit(); // 批量提交事务
    }
    
    /**
     * 优化的批量写入方法（使用putAll）
     * @param dataPoints 数据点列表
     */
    public void putBatchOptimized(List<DataPoint> dataPoints) {
        // 预分配容量，避免HashMap扩容开销
        Map<Long, Object> dataMap = new HashMap<>(dataPoints.size());
        for (DataPoint point : dataPoints) {
            dataMap.put(point.getTimestamp(), point.getValue());
        }
        timeSeriesData.putAll(dataMap);
        db.commit(); // 批量提交事务
    }
    
    /**
     * 超高性能批量写入方法（使用Stream API）
     * @param dataPoints 数据点列表
     */
    public void putBatchUltraFast(List<DataPoint> dataPoints) {
        // 使用Stream API一次性转换，避免中间集合
        Map<Long, Object> dataMap = dataPoints.stream()
            .collect(Collectors.toMap(
                DataPoint::getTimestamp,
                DataPoint::getValue,
                (existing, replacement) -> replacement, // 处理重复键
                () -> new HashMap<>(dataPoints.size()) // 预分配容量
            ));
        timeSeriesData.putAll(dataMap);
        db.commit();
    }
    
    /**
     * 获取数据（返回Object类型）
     */
    public Object get(long timestamp) {
        return timeSeriesData.get(timestamp);
    }
    
    /**
     * 获取Double类型数据
     */
    public Double getDouble(long timestamp) {
        Object value = timeSeriesData.get(timestamp);
        if (value instanceof Double) {
            return (Double) value;
        }
        return null;
    }
    
    /**
     * 获取Integer类型数据
     */
    public Integer getInteger(long timestamp) {
        Object value = timeSeriesData.get(timestamp);
        if (value instanceof Integer) {
            return (Integer) value;
        }
        return null;
    }
    
    /**
     * 获取String类型数据
     */
    public String getString(long timestamp) {
        Object value = timeSeriesData.get(timestamp);
        if (value instanceof String) {
            return (String) value;
        }
        return null;
    }
    
    /**
     * 时间范围查询
     */
    public NavigableMap<Long, Object> queryRange(long startTime, long endTime) {
        return timeSeriesData.subMap(startTime, true, endTime, true);
    }
    
    /**
     * 按类型过滤的时间范围查询
     */
    public <T> List<TypedDataPoint<T>> queryRangeByType(long startTime, long endTime, Class<T> type) {
        List<TypedDataPoint<T>> result = new ArrayList<>();
        NavigableMap<Long, Object> rangeData = timeSeriesData.subMap(startTime, true, endTime, true);
        
        for (Map.Entry<Long, Object> entry : rangeData.entrySet()) {
            if (type.isInstance(entry.getValue())) {
                result.add(new TypedDataPoint<>(entry.getKey(), type.cast(entry.getValue())));
            }
        }
        
        return result;
    }
    
    /**
     * 获取最新N个数据点
     */
    public List<DataPoint> getLatest(int count) {
        List<DataPoint> result = new ArrayList<>();
        Map.Entry<Long, Object> entry = timeSeriesData.lastEntry();
        
        for (int i = 0; i < count && entry != null; i++) {
            result.add(new DataPoint(entry.getKey(), entry.getValue()));
            entry = timeSeriesData.lowerEntry(entry.getKey());
        }
        
        return result;
    }
    
    /**
     * 获取数据统计信息
     */
    public DBStats getStats() {
        return new DBStats(
            timeSeriesData.size(),
            0, // 暂时不获取存储大小
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
     * 通用数据点类
     */
    public static class DataPoint {
        private final long timestamp;
        private final Object value;
        
        public DataPoint(long timestamp, Object value) {
            this.timestamp = timestamp;
            this.value = value;
        }
        
        public long getTimestamp() { return timestamp; }
        public Object getValue() { return value; }
        
        @Override
        public String toString() {
            return String.format("DataPoint{timestamp=%d, value=%s, type=%s}", 
                timestamp, value, value != null ? value.getClass().getSimpleName() : "null");
        }
    }
    
    /**
     * 类型化数据点类
     */
    public static class TypedDataPoint<T> {
        private final long timestamp;
        private final T value;
        
        public TypedDataPoint(long timestamp, T value) {
            this.timestamp = timestamp;
            this.value = value;
        }
        
        public long getTimestamp() { return timestamp; }
        public T getValue() { return value; }
        
        @Override
        public String toString() {
            return String.format("TypedDataPoint{timestamp=%d, value=%s, type=%s}", 
                timestamp, value, value != null ? value.getClass().getSimpleName() : "null");
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
