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
 * 基于MapDB的时序数据存储系统
 * 
 * <p>支持高频写入、时间范围查询和数据压缩，专为物联网和边缘计算场景设计。</p>
 * 
 * <p>主要特性：</p>
 * <ul>
 *   <li>高性能写入：支持每秒数十万数据点的写入性能</li>
 *   <li>时间范围查询：高效的时间序列数据查询</li>
 *   <li>数据压缩：内置序列化器，减少存储空间</li>
 *   <li>并发支持：原生支持多线程并发操作</li>
 *   <li>嵌入式部署：无需独立数据库进程，适合边缘设备</li>
 * </ul>
 * 
 * @author MapTSDB Team
 * @version 1.0.0
 * @since 1.0.0
 */
public class TimeSeriesDB {
    
    // ==================== 私有字段 ====================
    
    /** MapDB数据库实例 */
    private final DB db;
    
    /** 多数据源存储映射 */
    private final Map<String, ConcurrentNavigableMap<Long, Double>> dataSources;
    
    /** 定时任务调度器 */
    private final ScheduledExecutorService scheduler;
    
    /** 数据库文件路径 */
    private final String dbPath;
    
    // ==================== 构造函数 ====================
    
    /**
     * 创建时序数据库实例
     * 
     * @param dbPath 数据库文件路径，如果文件不存在会自动创建
     * @throws IllegalArgumentException 如果dbPath为null或空字符串
     */
    public TimeSeriesDB(String dbPath) {
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
    
    // ==================== 公共方法 ====================
    
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
        
        ConcurrentNavigableMap<Long, Double> sourceData = db.treeMap(sourceId)
                .keySerializer(Serializer.LONG_PACKED)  // 时间戳压缩序列化
                .valueSerializer(Serializer.DOUBLE)     // 浮点数序列化
                .createOrOpen();
        
        dataSources.put(sourceId, sourceData);
    }
    
    /**
     * 写入单个时序数据点
     * 
     * @param timestamp 时间戳（毫秒）
     * @param value 数据值
     * @throws IllegalArgumentException 如果timestamp小于0
     */
    public void put(long timestamp, double value) {
        put("default", timestamp, value);
    }
    
    /**
     * 写入指定数据源的时序数据点
     * 
     * @param sourceId 数据源ID
     * @param timestamp 时间戳（毫秒）
     * @param value 数据值
     * @throws IllegalArgumentException 如果参数无效
     */
    public void put(String sourceId, long timestamp, double value) {
        if (sourceId == null || sourceId.trim().isEmpty()) {
            throw new IllegalArgumentException("数据源ID不能为空");
        }
        if (timestamp < 0) {
            throw new IllegalArgumentException("时间戳不能为负数");
        }
        
        ConcurrentNavigableMap<Long, Double> sourceData = dataSources.get(sourceId);
        if (sourceData == null) {
            createDataSource(sourceId);
            sourceData = dataSources.get(sourceId);
        }
        
        sourceData.put(timestamp, value);
        db.commit(); // 立即提交事务，确保数据持久化
    }
    
    /**
     * 批量写入时序数据（优化版本）
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
     * 批量写入指定数据源的时序数据（优化版本）
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
        
        ConcurrentNavigableMap<Long, Double> sourceData = dataSources.get(sourceId);
        if (sourceData == null) {
            createDataSource(sourceId);
            sourceData = dataSources.get(sourceId);
        }
        
        // 预分配容量，避免HashMap扩容开销
        Map<Long, Double> dataMap = new HashMap<>(dataPoints.size());
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
     * 获取指定时间戳的数据
     * 
     * @param timestamp 时间戳
     * @return 数据值，如果不存在返回null
     */
    public Double get(long timestamp) {
        return get("default", timestamp);
    }
    
    /**
     * 获取指定数据源和时间戳的数据
     * 
     * @param sourceId 数据源ID
     * @param timestamp 时间戳
     * @return 数据值，如果不存在返回null
     */
    public Double get(String sourceId, long timestamp) {
        ConcurrentNavigableMap<Long, Double> sourceData = dataSources.get(sourceId);
        if (sourceData == null) {
            return null;
        }
        return sourceData.get(timestamp);
    }
    
    /**
     * 时间范围查询
     * 
     * @param startTime 开始时间戳（包含）
     * @param endTime 结束时间戳（包含）
     * @return 时间范围内的数据映射，按时间戳排序
     * @throws IllegalArgumentException 如果startTime > endTime
     */
    public NavigableMap<Long, Double> queryRange(long startTime, long endTime) {
        return queryRange("default", startTime, endTime);
    }
    
    /**
     * 查询指定数据源的时间范围内的数据
     * 
     * @param sourceId 数据源ID
     * @param startTime 开始时间戳（包含）
     * @param endTime 结束时间戳（包含）
     * @return 时间范围内的数据映射，按时间戳排序
     * @throws IllegalArgumentException 如果参数无效
     */
    public NavigableMap<Long, Double> queryRange(String sourceId, long startTime, long endTime) {
        if (sourceId == null || sourceId.trim().isEmpty()) {
            throw new IllegalArgumentException("数据源ID不能为空");
        }
        if (startTime > endTime) {
            throw new IllegalArgumentException("开始时间不能大于结束时间");
        }
        
        ConcurrentNavigableMap<Long, Double> sourceData = dataSources.get(sourceId);
        if (sourceData == null) {
            return new TreeMap<>();
        }
        
        return sourceData.subMap(startTime, true, endTime, true);
    }
    
    /**
     * 获取最新N个数据点
     * 
     * @param count 数据点数量，必须大于0
     * @return 最新的数据点列表，按时间戳降序排列
     * @throws IllegalArgumentException 如果count <= 0
     */
    public List<DataPoint> getLatest(int count) {
        return getLatest("default", count);
    }
    
    /**
     * 获取指定数据源的最新N个数据点
     * 
     * @param sourceId 数据源ID
     * @param count 数据点数量，必须大于0
     * @return 最新的数据点列表，按时间戳降序排列
     * @throws IllegalArgumentException 如果参数无效
     */
    public List<DataPoint> getLatest(String sourceId, int count) {
        if (sourceId == null || sourceId.trim().isEmpty()) {
            throw new IllegalArgumentException("数据源ID不能为空");
        }
        if (count <= 0) {
            throw new IllegalArgumentException("数据点数量必须大于0");
        }
        
        ConcurrentNavigableMap<Long, Double> sourceData = dataSources.get(sourceId);
        if (sourceData == null) {
            return new ArrayList<>();
        }
        
        List<DataPoint> result = new ArrayList<>();
        Map.Entry<Long, Double> entry = sourceData.lastEntry();
        
        for (int i = 0; i < count && entry != null; i++) {
            result.add(new DataPoint(entry.getKey(), entry.getValue()));
            entry = sourceData.lowerEntry(entry.getKey());
        }
        
        return result;
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
        for (Map.Entry<String, ConcurrentNavigableMap<Long, Double>> entry : dataSources.entrySet()) {
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
        
        ConcurrentNavigableMap<Long, Double> sourceData = dataSources.remove(sourceId);
        if (sourceData != null) {
            sourceData.clear();
            db.commit();
            return true;
        }
        return false;
    }
    
    /**
     * 获取数据库统计信息
     * 
     * @return 数据库统计信息，包含数据点数量、存储大小和最后更新时间
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
     * 关闭数据库连接
     * 
     * <p>关闭定时任务调度器和数据库连接，释放所有资源。</p>
     */
    public void close() {
        scheduler.shutdown();
        db.close();
    }
    
    // ==================== 私有方法 ====================
    
    /**
     * 加载现有的数据源
     */
    private void loadExistingDataSources() {
        // 尝试加载"default"数据源（向后兼容）
        try {
            ConcurrentNavigableMap<Long, Double> defaultSource = db.treeMap("default")
                    .keySerializer(Serializer.LONG_PACKED)
                    .valueSerializer(Serializer.DOUBLE)
                    .createOrOpen();
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
     * 
     * @return 存储大小（字节），如果获取失败返回0
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
     * 
     * <p>自动清理30天前的历史数据，减少存储空间占用。</p>
     */
    private void cleanupOldData() {
        long thirtyDaysAgo = System.currentTimeMillis() - 30L * 24 * 3600 * 1000;
        int totalCleaned = 0;
        
        for (ConcurrentNavigableMap<Long, Double> sourceData : dataSources.values()) {
            int cleaned = sourceData.headMap(thirtyDaysAgo).size();
            sourceData.headMap(thirtyDaysAgo).clear();
            totalCleaned += cleaned;
        }
        
        if (totalCleaned > 0) {
            db.commit();
            System.out.println("清理了" + totalCleaned + "个30天前的历史数据点");
        }
    }
    
    // ==================== 内部类 ====================
    
    /**
     * 时序数据点
     * 
     * <p>表示一个时序数据点，包含时间戳和数值。</p>
     */
    public static class DataPoint {
        
        /** 时间戳（毫秒） */
        private final long timestamp;
        
        /** 数据值 */
        private final double value;
        
        /**
         * 创建数据点
         * 
         * @param timestamp 时间戳（毫秒）
         * @param value 数据值
         */
        public DataPoint(long timestamp, double value) {
            this.timestamp = timestamp;
            this.value = value;
        }
        
        /**
         * 获取时间戳
         * 
         * @return 时间戳（毫秒）
         */
        public long getTimestamp() { 
            return timestamp; 
        }
        
        /**
         * 获取数据值
         * 
         * @return 数据值
         */
        public double getValue() { 
            return value; 
        }
        
        @Override
        public String toString() {
            return String.format("DataPoint{timestamp=%d, value=%.2f}", timestamp, value);
        }
        
        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (obj == null || getClass() != obj.getClass()) return false;
            DataPoint dataPoint = (DataPoint) obj;
            return timestamp == dataPoint.timestamp && 
                   Double.compare(dataPoint.value, value) == 0;
        }
        
        @Override
        public int hashCode() {
            return Long.hashCode(timestamp) ^ Double.hashCode(value);
        }
    }
    
    /**
     * 数据库统计信息
     * 
     * <p>包含数据库的基本统计信息，如数据点数量、存储大小等。</p>
     */
    public static class DBStats {
        
        /** 数据点数量 */
        private final long dataPointCount;
        
        /** 存储大小（字节） */
        private final long storageSize;
        
        /** 最后更新时间 */
        private final long lastUpdateTime;
        
        /**
         * 创建统计信息
         * 
         * @param dataPointCount 数据点数量
         * @param storageSize 存储大小（字节）
         * @param lastUpdateTime 最后更新时间
         */
        public DBStats(long dataPointCount, long storageSize, long lastUpdateTime) {
            this.dataPointCount = dataPointCount;
            this.storageSize = storageSize;
            this.lastUpdateTime = lastUpdateTime;
        }
        
        /**
         * 获取数据点数量
         * 
         * @return 数据点数量
         */
        public long getDataPointCount() { 
            return dataPointCount; 
        }
        
        /**
         * 获取存储大小
         * 
         * @return 存储大小（字节）
         */
        public long getStorageSize() { 
            return storageSize; 
        }
        
        /**
         * 获取最后更新时间
         * 
         * @return 最后更新时间（毫秒）
         */
        public long getLastUpdateTime() { 
            return lastUpdateTime; 
        }
        
        @Override
        public String toString() {
            return String.format("DBStats{dataPoints=%d, size=%d bytes, lastUpdate=%d}", 
                dataPointCount, storageSize, lastUpdateTime);
        }
    }
}
