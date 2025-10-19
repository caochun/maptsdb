package com.maptsdb;

import org.mapdb.DB;
import org.mapdb.DBMaker;
import org.mapdb.Serializer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.concurrent.ConcurrentNavigableMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 支持多种数据类型的时序数据库
 * 
 * <p>支持Double、Integer、Long、String、Boolean、Float等多种数据类型的时序数据存储。</p>
 * 
 * <p>主要特性：</p>
 * <ul>
 *   <li>多类型支持：支持6种基本数据类型</li>
 *   <li>类型安全：提供类型安全的getter方法</li>
 *   <li>类型过滤查询：支持按数据类型过滤查询</li>
 *   <li>高性能存储：基于MapDB的高性能存储引擎</li>
 *   <li>并发支持：原生支持多线程并发操作</li>
 * </ul>
 * 
 * @author MapTSDB Team
 * @version 1.0.0
 * @since 1.0.0
 */
public class ObjectTimeSeriesDb {
    
    // ==================== 私有字段 ====================
    
    /** MapDB数据库实例 */
    private final DB db;
    
    /** 时序数据存储映射（支持任意对象类型） */
    private final ConcurrentNavigableMap<Long, Object> timeSeriesData;
    
    /** 定时任务调度器 */
    private final ScheduledExecutorService scheduler;
    
    /** 数据库文件路径 */
    private final String dbPath;
    
    // ==================== 构造函数 ====================
    
    /**
     * 创建多类型时序数据库实例
     * 
     * @param dbPath 数据库文件路径，如果文件不存在会自动创建
     * @throws IllegalArgumentException 如果dbPath为null或空字符串
     */
    public ObjectTimeSeriesDb(String dbPath) {
        if (dbPath == null || dbPath.trim().isEmpty()) {
            throw new IllegalArgumentException("数据库路径不能为空");
        }
        
        this.dbPath = dbPath;
        
        // 初始化MapDB数据库，启用性能优化配置
        this.db = DBMaker.fileDB(dbPath)
                .fileMmapEnable()           // 启用内存映射文件，提供接近内存的读写性能
                .fileMmapPreclearDisable()  // 禁用预清理以提高性能
                .cleanerHackEnable()        // 启用清理器以防止内存泄漏
                .transactionEnable()        // 启用事务支持，保证数据一致性
                .closeOnJvmShutdown()      // JVM关闭时自动关闭数据库
                .concurrencyScale(16)      // 设置并发级别，支持多线程操作
                .make();
        
        // 创建时序数据存储结构 - 使用通用Object类型
        // 使用标准序列化器（MapDB 3.1.0中增量编码序列化器的API可能不同）
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
     * 
     * @param timestamp 时间戳（毫秒）
     * @param value 任意类型的数据值
     * @throws IllegalArgumentException 如果timestamp小于0或value为null
     */
    public void put(long timestamp, Object value) {
        if (timestamp < 0) {
            throw new IllegalArgumentException("时间戳不能为负数");
        }
        if (value == null) {
            throw new IllegalArgumentException("数据值不能为null");
        }
        timeSeriesData.put(timestamp, value);
        db.commit();
    }
    
    /**
     * 批量写入数据点（优化版本）
     * 
     * <p>使用预分配的HashMap和putAll方法，显著提高批量写入性能。</p>
     * 
     * @param dataPoints 数据点列表，不能为null
     * @throws IllegalArgumentException 如果dataPoints为null或包含无效数据
     */
    public void putBatch(List<DataPoint> dataPoints) {
        if (dataPoints == null) {
            throw new IllegalArgumentException("数据点列表不能为null");
        }
        
        if (dataPoints.isEmpty()) {
            return; // 空列表直接返回
        }
        
        // 预分配容量，避免HashMap扩容开销
        Map<Long, Object> dataMap = new HashMap<>(dataPoints.size());
        for (DataPoint point : dataPoints) {
            if (point == null) {
                throw new IllegalArgumentException("数据点不能为null");
            }
            if (point.getTimestamp() < 0) {
                throw new IllegalArgumentException("时间戳不能为负数");
            }
            dataMap.put(point.getTimestamp(), point.getValue());
        }
        timeSeriesData.putAll(dataMap);
        db.commit(); // 批量提交事务
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
        long storageSize = getStorageSize();
        return new DBStats(
            timeSeriesData.size(),
            storageSize,
            System.currentTimeMillis()
        );
    }
    
    /**
     * 获取数据库文件存储大小
     * @return 存储大小（字节）
     */
    private long getStorageSize() {
        try {
            java.io.File dbFile = new java.io.File(dbPath);
            if (dbFile.exists()) {
                return dbFile.length();
            }
        } catch (Exception e) {
            // 如果获取失败，返回0
        }
        return 0;
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
