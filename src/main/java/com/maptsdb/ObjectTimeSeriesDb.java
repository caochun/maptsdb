package com.maptsdb;

import org.mapdb.DB;
import org.mapdb.DBMaker;
import org.mapdb.Serializer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentNavigableMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

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
    
    /** 多数据源存储映射（支持任意对象类型） */
    private final Map<String, ConcurrentNavigableMap<Long, Object>> dataSources;
    
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
        
        // 初始化多数据源存储映射
        this.dataSources = new HashMap<>();
        
        // 加载现有的数据源（如果数据库已存在）
        loadExistingDataSources();
        
        // 初始化定时任务调度器
        this.scheduler = Executors.newScheduledThreadPool(2);
        
        // 启动数据清理任务（每小时执行一次）
        scheduler.scheduleAtFixedRate(this::cleanupOldData, 1, 1, TimeUnit.HOURS);
    }
    
    // ==================== 数据源管理 ====================
    
    /**
     * 创建数据源
     * 
     * @param sourceId 数据源ID
     * @throws IllegalArgumentException 如果sourceId为null或空字符串
     */
    public void createDataSource(String sourceId) {
        if (sourceId == null || sourceId.trim().isEmpty()) {
            throw new IllegalArgumentException("数据源ID不能为空");
        }
        
        if (dataSources.containsKey(sourceId)) {
            return; // 数据源已存在
        }
        
        ConcurrentNavigableMap<Long, Object> sourceData = createDataSourceMap(sourceId);
        dataSources.put(sourceId, sourceData);
    }
    
    /**
     * 获取所有数据源列表
     * 
     * @return 数据源ID集合
     */
    public Set<String> getDataSources() {
        return new HashSet<>(dataSources.keySet());
    }
    
    /**
     * 获取数据源统计信息
     * 
     * @return 各数据源的数据点数量映射
     */
    public Map<String, Long> getDataSourcesStats() {
        Map<String, Long> stats = new HashMap<>();
        for (Map.Entry<String, ConcurrentNavigableMap<Long, Object>> entry : dataSources.entrySet()) {
            stats.put(entry.getKey(), (long) entry.getValue().size());
        }
        return stats;
    }
    
    /**
     * 删除数据源
     * 
     * @param sourceId 数据源ID
     * @return 是否删除成功
     */
    public boolean removeDataSource(String sourceId) {
        if (sourceId == null || sourceId.trim().isEmpty()) {
            return false;
        }
        
        ConcurrentNavigableMap<Long, Object> sourceData = dataSources.remove(sourceId);
        if (sourceData != null) {
            sourceData.clear();
            db.commit();
            return true;
        }
        return false;
    }
    
    // ==================== 数据写入方法 ====================
    
    /**
     * 写入Double类型数据
     */
    public void putDouble(long timestamp, double value) {
        putDouble("default", timestamp, value);
    }
    
    /**
     * 写入指定数据源的Double类型数据
     */
    public void putDouble(String sourceId, long timestamp, double value) {
        ConcurrentNavigableMap<Long, Object> sourceData = getOrCreateSource(sourceId);
        sourceData.put(timestamp, value);
        db.commit();
    }
    
    /**
     * 写入Integer类型数据
     */
    public void putInteger(long timestamp, int value) {
        putInteger("default", timestamp, value);
    }
    
    /**
     * 写入指定数据源的Integer类型数据
     */
    public void putInteger(String sourceId, long timestamp, int value) {
        ConcurrentNavigableMap<Long, Object> sourceData = getOrCreateSource(sourceId);
        sourceData.put(timestamp, value);
        db.commit();
    }
    
    /**
     * 写入Long类型数据
     */
    public void putLong(long timestamp, long value) {
        putLong("default", timestamp, value);
    }
    
    /**
     * 写入指定数据源的Long类型数据
     */
    public void putLong(String sourceId, long timestamp, long value) {
        ConcurrentNavigableMap<Long, Object> sourceData = getOrCreateSource(sourceId);
        sourceData.put(timestamp, value);
        db.commit();
    }
    
    /**
     * 写入String类型数据
     */
    public void putString(long timestamp, String value) {
        putString("default", timestamp, value);
    }
    
    /**
     * 写入指定数据源的String类型数据
     */
    public void putString(String sourceId, long timestamp, String value) {
        ConcurrentNavigableMap<Long, Object> sourceData = getOrCreateSource(sourceId);
        sourceData.put(timestamp, value);
        db.commit();
    }
    
    /**
     * 写入Boolean类型数据
     */
    public void putBoolean(long timestamp, boolean value) {
        putBoolean("default", timestamp, value);
    }
    
    /**
     * 写入指定数据源的Boolean类型数据
     */
    public void putBoolean(String sourceId, long timestamp, boolean value) {
        ConcurrentNavigableMap<Long, Object> sourceData = getOrCreateSource(sourceId);
        sourceData.put(timestamp, value);
        db.commit();
    }
    
    /**
     * 写入Float类型数据
     */
    public void putFloat(long timestamp, float value) {
        putFloat("default", timestamp, value);
    }
    
    /**
     * 写入指定数据源的Float类型数据
     */
    public void putFloat(String sourceId, long timestamp, float value) {
        ConcurrentNavigableMap<Long, Object> sourceData = getOrCreateSource(sourceId);
        sourceData.put(timestamp, value);
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
        put("default", timestamp, value);
    }
    
    /**
     * 写入指定数据源的通用方法
     * 
     * @param sourceId 数据源ID
     * @param timestamp 时间戳（毫秒）
     * @param value 任意类型的数据值
     * @throws IllegalArgumentException 如果参数无效
     */
    public void put(String sourceId, long timestamp, Object value) {
        if (sourceId == null || sourceId.trim().isEmpty()) {
            throw new IllegalArgumentException("数据源ID不能为空");
        }
        if (timestamp < 0) {
            throw new IllegalArgumentException("时间戳不能为负数");
        }
        if (value == null) {
            throw new IllegalArgumentException("数据值不能为null");
        }
        
        ConcurrentNavigableMap<Long, Object> sourceData = getOrCreateSource(sourceId);
        sourceData.put(timestamp, value);
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
        putBatch("default", dataPoints);
    }
    
    /**
     * 批量写入指定数据源的数据点（优化版本）
     * 
     * <p>使用预分配的HashMap和putAll方法，显著提高批量写入性能。</p>
     * 
     * @param sourceId 数据源ID
     * @param dataPoints 数据点列表，不能为null
     * @throws IllegalArgumentException 如果参数无效
     */
    public void putBatch(String sourceId, List<DataPoint> dataPoints) {
        if (sourceId == null || sourceId.trim().isEmpty()) {
            throw new IllegalArgumentException("数据源ID不能为空");
        }
        if (dataPoints == null) {
            throw new IllegalArgumentException("数据点列表不能为null");
        }
        
        if (dataPoints.isEmpty()) {
            return; // 空列表直接返回
        }
        
        ConcurrentNavigableMap<Long, Object> sourceData = getOrCreateSource(sourceId);
        
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
        
        sourceData.putAll(dataMap);
        db.commit(); // 批量提交事务
    }
    
    /**
     * 获取数据（返回Object类型）
     */
    public Object get(long timestamp) {
        return get("default", timestamp);
    }
    
    /**
     * 获取指定数据源的数据（返回Object类型）
     */
    public Object get(String sourceId, long timestamp) {
        ConcurrentNavigableMap<Long, Object> sourceData = dataSources.get(sourceId);
        if (sourceData == null) {
            return null;
        }
        return sourceData.get(timestamp);
    }
    
    /**
     * 获取Double类型数据
     */
    public Double getDouble(long timestamp) {
        return getDouble("default", timestamp);
    }
    
    /**
     * 获取指定数据源的Double类型数据
     */
    public Double getDouble(String sourceId, long timestamp) {
        Object value = get(sourceId, timestamp);
        if (value instanceof Double) {
            return (Double) value;
        }
        return null;
    }
    
    /**
     * 获取Integer类型数据
     */
    public Integer getInteger(long timestamp) {
        return getInteger("default", timestamp);
    }
    
    /**
     * 获取指定数据源的Integer类型数据
     */
    public Integer getInteger(String sourceId, long timestamp) {
        Object value = get(sourceId, timestamp);
        if (value instanceof Integer) {
            return (Integer) value;
        }
        return null;
    }
    
    /**
     * 获取String类型数据
     */
    public String getString(long timestamp) {
        return getString("default", timestamp);
    }
    
    /**
     * 获取指定数据源的String类型数据
     */
    public String getString(String sourceId, long timestamp) {
        Object value = get(sourceId, timestamp);
        if (value instanceof String) {
            return (String) value;
        }
        return null;
    }
    
    /**
     * 时间范围查询
     */
    public NavigableMap<Long, Object> queryRange(long startTime, long endTime) {
        return queryRange("default", startTime, endTime);
    }
    
    /**
     * 查询指定数据源的时间范围内的数据
     */
    public NavigableMap<Long, Object> queryRange(String sourceId, long startTime, long endTime) {
        ConcurrentNavigableMap<Long, Object> sourceData = dataSources.get(sourceId);
        if (sourceData == null) {
            return new TreeMap<>();
        }
        return sourceData.subMap(startTime, true, endTime, true);
    }
    
    /**
     * 按类型过滤的时间范围查询
     */
    public <T> List<TypedDataPoint<T>> queryRangeByType(long startTime, long endTime, Class<T> type) {
        return queryRangeByType("default", startTime, endTime, type);
    }
    
    /**
     * 查询指定数据源按类型过滤的时间范围查询
     */
    public <T> List<TypedDataPoint<T>> queryRangeByType(String sourceId, long startTime, long endTime, Class<T> type) {
        List<TypedDataPoint<T>> result = new ArrayList<>();
        NavigableMap<Long, Object> rangeData = queryRange(sourceId, startTime, endTime);
        
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
        return getLatest("default", count);
    }
    
    /**
     * 获取指定数据源的最新N个数据点
     */
    public List<DataPoint> getLatest(String sourceId, int count) {
        ConcurrentNavigableMap<Long, Object> sourceData = dataSources.get(sourceId);
        if (sourceData == null) {
            return new ArrayList<>();
        }
        
        List<DataPoint> result = new ArrayList<>();
        Map.Entry<Long, Object> entry = sourceData.lastEntry();
        
        for (int i = 0; i < count && entry != null; i++) {
            result.add(new DataPoint(entry.getKey(), entry.getValue()));
            entry = sourceData.lowerEntry(entry.getKey());
        }
        
        return result;
    }
    
    /**
     * 获取数据统计信息
     */
    public DBStats getStats() {
        long storageSize = getStorageSize();
        long totalDataPoints = dataSources.values().stream()
            .mapToLong(ConcurrentNavigableMap::size)
            .sum();
        return new DBStats(
            totalDataPoints,
            storageSize,
            System.currentTimeMillis()
        );
    }
    
    /**
     * 加载现有的数据源
     */
    private void loadExistingDataSources() {
        // 尝试加载"default"数据源（向后兼容）
        try {
            ConcurrentNavigableMap<Long, Object> defaultSource = createDataSourceMap("default");
            if (!defaultSource.isEmpty()) {
                dataSources.put("default", defaultSource);
            }
        } catch (Exception e) {
            // 如果加载失败，忽略错误
        }
        
        // 这里可以添加逻辑来发现其他数据源
        // 由于MapDB的限制，我们无法直接枚举所有表名
        // 所以采用按需创建的方式
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
        int totalCleaned = 0;
        
        for (ConcurrentNavigableMap<Long, Object> sourceData : dataSources.values()) {
            int cleaned = sourceData.headMap(thirtyDaysAgo).size();
            sourceData.headMap(thirtyDaysAgo).clear();
            totalCleaned += cleaned;
        }
        
        if (totalCleaned > 0) {
            db.commit();
            System.out.println("清理了" + totalCleaned + "个30天前的历史数据点");
        }
    }
    
    /**
     * 创建数据源映射（处理类型转换）
     * 
     * @param sourceId 数据源ID
     * @return 数据源映射
     */
    @SuppressWarnings("unchecked")
    private ConcurrentNavigableMap<Long, Object> createDataSourceMap(String sourceId) {
        return (ConcurrentNavigableMap<Long, Object>) db.treeMap(sourceId)
                .keySerializer(Serializer.LONG)
                .valueSerializer(Serializer.JAVA)
                .createOrOpen();
    }
    
    /**
     * 获取或创建数据源
     * 
     * @param sourceId 数据源ID
     * @return 数据源映射
     */
    private ConcurrentNavigableMap<Long, Object> getOrCreateSource(String sourceId) {
        ConcurrentNavigableMap<Long, Object> sourceData = dataSources.get(sourceId);
        if (sourceData == null) {
            createDataSource(sourceId);
            sourceData = dataSources.get(sourceId);
        }
        return sourceData;
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
