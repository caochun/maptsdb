package com.maptsdb;

import org.mapdb.DB;
import org.mapdb.DBMaker;
import org.mapdb.Serializer;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Set;
import java.util.concurrent.ConcurrentNavigableMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 时序数据库主类
 * 
 * <p>提供高性能的时序数据存储和查询功能，支持多种数据类型和批量操作。</p>
 * 
 * <h3>主要特性：</h3>
 * <ul>
 *   <li>支持多种数据类型：Double、Integer、Long、Float、Object</li>
 *   <li>高性能批量写入：使用putAll优化</li>
 *   <li>灵活的事务管理：支持手动commit和自动commit</li>
 *   <li>内存映射优化：提供接近内存的读写性能</li>
 *   <li>并发安全：原生支持多线程操作</li>
 *   <li>数据清理：支持自动清理过期数据</li>
 * </ul>
 * 
 * @author MapTSDB Team
 * @version 1.0.0
 * @since 1.0.0
 */
public class TimeSeriesDatabase {
    private final DB db;
    private final DatabaseConfig config;
    private final Map<String, DataSourceConfig> dataSources;
    private final Map<String, ConcurrentNavigableMap<Long, Double>> doubleMaps;
    private final Map<String, ConcurrentNavigableMap<Long, Integer>> integerMaps;
    private final Map<String, ConcurrentNavigableMap<Long, Long>> longMaps;
    private final Map<String, ConcurrentNavigableMap<Long, Float>> floatMaps;
    private final Map<String, ConcurrentNavigableMap<Long, Object>> objectMaps;
    private final ScheduledExecutorService scheduler;
    private final TimeSeriesDatabaseBuilder builder; // 用于动态添加数据源
    
    /**
     * 创建时序数据库实例
     * 
     * @param config 数据库配置
     * @param dataSources 数据源配置
     */
    TimeSeriesDatabase(DatabaseConfig config, Map<String, DataSourceConfig> dataSources) {
        this(config, dataSources, null);
    }
    
    /**
     * 创建时序数据库实例（支持动态数据源）
     * 
     * @param config 数据库配置
     * @param dataSources 数据源配置
     * @param builder Builder实例（用于动态添加数据源）
     */
    TimeSeriesDatabase(DatabaseConfig config, Map<String, DataSourceConfig> dataSources, TimeSeriesDatabaseBuilder builder) {
        this.config = config;
        this.dataSources = dataSources;
        this.builder = builder;
        this.doubleMaps = new HashMap<>();
        this.integerMaps = new HashMap<>();
        this.longMaps = new HashMap<>();
        this.floatMaps = new HashMap<>();
        this.objectMaps = new HashMap<>();
        
        // 创建MapDB实例
        this.db = createMapDB();
        
        // 创建调度器
        this.scheduler = Executors.newScheduledThreadPool(1);
        
        // 初始化数据源
        initializeDataSources();
        
        // 启动清理任务
        startCleanupTask();
    }
    
    /**
     * 创建MapDB实例
     * 
     * @return MapDB实例
     */
    private DB createMapDB() {
        DBMaker.Maker maker = DBMaker.fileDB(config.getDbPath());
        
        if (config.isEnableMemoryMapping()) {
            maker.fileMmapEnable();
        }
        if (config.isDisablePreclear()) {
            maker.fileMmapPreclearDisable();
        }
        if (config.isEnableCleaner()) {
            maker.cleanerHackEnable();
        }
        if (config.isEnableTransactions()) {
            maker.transactionEnable();
        }
        
        maker.closeOnJvmShutdown();
        maker.concurrencyScale(config.getConcurrencyScale());
        
        return maker.make();
    }
    
    /**
     * 初始化数据源
     */
    private void initializeDataSources() {
        for (DataSourceConfig sourceConfig : dataSources.values()) {
            createDataSource(sourceConfig);
        }
    }
    
    /**
     * 创建数据源
     * 
     * @param sourceConfig 数据源配置
     */
    private void createDataSource(DataSourceConfig sourceConfig) {
        String sourceId = sourceConfig.getSourceId();
        DataType dataType = sourceConfig.getDataType();
        
        switch (dataType) {
            case DOUBLE:
                ConcurrentNavigableMap<Long, Double> doubleMap = db.treeMap(sourceId)
                    .keySerializer(Serializer.LONG_PACKED)
                    .valueSerializer(Serializer.DOUBLE)
                    .createOrOpen();
                doubleMaps.put(sourceId, doubleMap);
                break;
                
            case INTEGER:
                ConcurrentNavigableMap<Long, Integer> integerMap = db.treeMap(sourceId)
                    .keySerializer(Serializer.LONG_PACKED)
                    .valueSerializer(Serializer.INTEGER)
                    .createOrOpen();
                integerMaps.put(sourceId, integerMap);
                break;
                
            case LONG:
                ConcurrentNavigableMap<Long, Long> longMap = db.treeMap(sourceId)
                    .keySerializer(Serializer.LONG_PACKED)
                    .valueSerializer(Serializer.LONG)
                    .createOrOpen();
                longMaps.put(sourceId, longMap);
                break;
                
            case FLOAT:
                ConcurrentNavigableMap<Long, Float> floatMap = db.treeMap(sourceId)
                    .keySerializer(Serializer.LONG_PACKED)
                    .valueSerializer(Serializer.FLOAT)
                    .createOrOpen();
                floatMaps.put(sourceId, floatMap);
                break;
                
            case OBJECT:
                ConcurrentNavigableMap<Long, Object> objectMap = db.treeMap(sourceId)
                    .keySerializer(Serializer.LONG_PACKED)
                    .valueSerializer(Serializer.JAVA)
                    .createOrOpen();
                objectMaps.put(sourceId, objectMap);
                break;
        }
    }
    
    /**
     * 启动清理任务
     */
    private void startCleanupTask() {
        scheduler.scheduleAtFixedRate(this::cleanupOldData, 
            config.getCleanupIntervalHours(), 
            config.getCleanupIntervalHours(), 
            TimeUnit.HOURS);
    }
    
    /**
     * 清理过期数据
     */
    private void cleanupOldData() {
        long cutoffTime = System.currentTimeMillis() - (config.getRetentionDays() * 24 * 60 * 60 * 1000L);
        int totalCleaned = 0;
        
        // 清理Double数据
        for (ConcurrentNavigableMap<Long, Double> map : doubleMaps.values()) {
            totalCleaned += map.headMap(cutoffTime).size();
            map.headMap(cutoffTime).clear();
        }
        
        // 清理Integer数据
        for (ConcurrentNavigableMap<Long, Integer> map : integerMaps.values()) {
            totalCleaned += map.headMap(cutoffTime).size();
            map.headMap(cutoffTime).clear();
        }
        
        // 清理Long数据
        for (ConcurrentNavigableMap<Long, Long> map : longMaps.values()) {
            totalCleaned += map.headMap(cutoffTime).size();
            map.headMap(cutoffTime).clear();
        }
        
        // 清理Float数据
        for (ConcurrentNavigableMap<Long, Float> map : floatMaps.values()) {
            totalCleaned += map.headMap(cutoffTime).size();
            map.headMap(cutoffTime).clear();
        }
        
        // 清理Object数据
        for (ConcurrentNavigableMap<Long, Object> map : objectMaps.values()) {
            totalCleaned += map.headMap(cutoffTime).size();
            map.headMap(cutoffTime).clear();
        }
        
        if (totalCleaned > 0) {
            db.commit();
            System.out.println("清理了 " + totalCleaned + " 个过期数据点");
        }
    }
    
    // ==================== 公共API方法 ====================
    
    /**
     * 提交事务
     */
    public void commit() {
        db.commit();
    }
    
    /**
     * 关闭数据库
     */
    public void close() {
        scheduler.shutdown();
        if (db != null && !db.isClosed()) {
            db.close();
        }
    }
    
    /**
     * 获取所有数据源ID
     * 
     * @return 数据源ID集合
     */
    public Set<String> getDataSourceIds() {
        return dataSources.keySet();
    }
    
    /**
     * 获取数据源信息
     * 
     * @param sourceId 数据源ID
     * @return 数据源配置，如果不存在返回null
     */
    public DataSourceConfig getDataSourceInfo(String sourceId) {
        return dataSources.get(sourceId);
    }
    
    /**
     * 动态添加数据源
     * 
     * @param sourceId 数据源ID
     * @param dataType 数据类型
     * @param description 描述
     */
    public void addDataSource(String sourceId, DataType dataType, String description) {
        if (dataSources.containsKey(sourceId)) {
            throw new IllegalArgumentException("数据源已存在: " + sourceId);
        }
        
        DataSourceConfig sourceConfig = new DataSourceConfig(sourceId, dataType, description);
        dataSources.put(sourceId, sourceConfig);
        createDataSource(sourceConfig);
    }
    
    // ==================== Double类型方法 ====================
    
    /**
     * 写入Double数据
     * 
     * @param sourceId 数据源ID
     * @param timestamp 时间戳
     * @param value 数据值
     */
    public void putDouble(String sourceId, long timestamp, double value) {
        ConcurrentNavigableMap<Long, Double> map = doubleMaps.get(sourceId);
        if (map == null) {
            throw new IllegalArgumentException("数据源不存在: " + sourceId);
        }
        map.put(timestamp, value);
    }
    
    /**
     * 写入Double数据并提交
     * 
     * @param sourceId 数据源ID
     * @param timestamp 时间戳
     * @param value 数据值
     */
    public void putDoubleAndCommit(String sourceId, long timestamp, double value) {
        putDouble(sourceId, timestamp, value);
        commit();
    }
    
    /**
     * 批量写入Double数据
     * 
     * @param sourceId 数据源ID
     * @param dataPoints 数据点列表
     */
    public void putBatchDouble(String sourceId, List<DataPoint<Double>> dataPoints) {
        ConcurrentNavigableMap<Long, Double> map = doubleMaps.get(sourceId);
        if (map == null) {
            throw new IllegalArgumentException("数据源不存在: " + sourceId);
        }
        
        Map<Long, Double> dataMap = new HashMap<>(dataPoints.size());
        for (DataPoint<Double> point : dataPoints) {
            dataMap.put(point.getTimestamp(), point.getValue());
        }
        map.putAll(dataMap);
        commit();
    }
    
    /**
     * 获取Double数据
     * 
     * @param sourceId 数据源ID
     * @param timestamp 时间戳
     * @return 数据值，如果不存在返回null
     */
    public Double getDouble(String sourceId, long timestamp) {
        ConcurrentNavigableMap<Long, Double> map = doubleMaps.get(sourceId);
        if (map == null) {
            return null;
        }
        return map.get(timestamp);
    }
    
    /**
     * 查询Double数据范围
     * 
     * @param sourceId 数据源ID
     * @param startTime 开始时间
     * @param endTime 结束时间
     * @return 时间范围内的数据
     */
    public NavigableMap<Long, Double> queryRangeDouble(String sourceId, long startTime, long endTime) {
        ConcurrentNavigableMap<Long, Double> map = doubleMaps.get(sourceId);
        if (map == null) {
            return new java.util.TreeMap<>();
        }
        return map.subMap(startTime, true, endTime, true);
    }
    
    // ==================== Integer类型方法 ====================
    
    /**
     * 写入Integer数据
     * 
     * @param sourceId 数据源ID
     * @param timestamp 时间戳
     * @param value 数据值
     */
    public void putInteger(String sourceId, long timestamp, int value) {
        ConcurrentNavigableMap<Long, Integer> map = integerMaps.get(sourceId);
        if (map == null) {
            throw new IllegalArgumentException("数据源不存在: " + sourceId);
        }
        map.put(timestamp, value);
    }
    
    /**
     * 写入Integer数据并提交
     * 
     * @param sourceId 数据源ID
     * @param timestamp 时间戳
     * @param value 数据值
     */
    public void putIntegerAndCommit(String sourceId, long timestamp, int value) {
        putInteger(sourceId, timestamp, value);
        commit();
    }
    
    /**
     * 批量写入Integer数据
     * 
     * @param sourceId 数据源ID
     * @param dataPoints 数据点列表
     */
    public void putBatchInteger(String sourceId, List<DataPoint<Integer>> dataPoints) {
        ConcurrentNavigableMap<Long, Integer> map = integerMaps.get(sourceId);
        if (map == null) {
            throw new IllegalArgumentException("数据源不存在: " + sourceId);
        }
        
        Map<Long, Integer> dataMap = new HashMap<>(dataPoints.size());
        for (DataPoint<Integer> point : dataPoints) {
            dataMap.put(point.getTimestamp(), point.getValue());
        }
        map.putAll(dataMap);
        commit();
    }
    
    /**
     * 获取Integer数据
     * 
     * @param sourceId 数据源ID
     * @param timestamp 时间戳
     * @return 数据值，如果不存在返回null
     */
    public Integer getInteger(String sourceId, long timestamp) {
        ConcurrentNavigableMap<Long, Integer> map = integerMaps.get(sourceId);
        if (map == null) {
            return null;
        }
        return map.get(timestamp);
    }
    
    /**
     * 查询Integer数据范围
     * 
     * @param sourceId 数据源ID
     * @param startTime 开始时间
     * @param endTime 结束时间
     * @return 时间范围内的数据
     */
    public NavigableMap<Long, Integer> queryRangeInteger(String sourceId, long startTime, long endTime) {
        ConcurrentNavigableMap<Long, Integer> map = integerMaps.get(sourceId);
        if (map == null) {
            return new java.util.TreeMap<>();
        }
        return map.subMap(startTime, true, endTime, true);
    }
    
    // ==================== Long类型方法 ====================
    
    /**
     * 写入Long数据
     * 
     * @param sourceId 数据源ID
     * @param timestamp 时间戳
     * @param value 数据值
     */
    public void putLong(String sourceId, long timestamp, long value) {
        ConcurrentNavigableMap<Long, Long> map = longMaps.get(sourceId);
        if (map == null) {
            throw new IllegalArgumentException("数据源不存在: " + sourceId);
        }
        map.put(timestamp, value);
    }
    
    /**
     * 写入Long数据并提交
     * 
     * @param sourceId 数据源ID
     * @param timestamp 时间戳
     * @param value 数据值
     */
    public void putLongAndCommit(String sourceId, long timestamp, long value) {
        putLong(sourceId, timestamp, value);
        commit();
    }
    
    /**
     * 批量写入Long数据
     * 
     * @param sourceId 数据源ID
     * @param dataPoints 数据点列表
     */
    public void putBatchLong(String sourceId, List<DataPoint<Long>> dataPoints) {
        ConcurrentNavigableMap<Long, Long> map = longMaps.get(sourceId);
        if (map == null) {
            throw new IllegalArgumentException("数据源不存在: " + sourceId);
        }
        
        Map<Long, Long> dataMap = new HashMap<>(dataPoints.size());
        for (DataPoint<Long> point : dataPoints) {
            dataMap.put(point.getTimestamp(), point.getValue());
        }
        map.putAll(dataMap);
        commit();
    }
    
    /**
     * 获取Long数据
     * 
     * @param sourceId 数据源ID
     * @param timestamp 时间戳
     * @return 数据值，如果不存在返回null
     */
    public Long getLong(String sourceId, long timestamp) {
        ConcurrentNavigableMap<Long, Long> map = longMaps.get(sourceId);
        if (map == null) {
            return null;
        }
        return map.get(timestamp);
    }
    
    /**
     * 查询Long数据范围
     * 
     * @param sourceId 数据源ID
     * @param startTime 开始时间
     * @param endTime 结束时间
     * @return 时间范围内的数据
     */
    public NavigableMap<Long, Long> queryRangeLong(String sourceId, long startTime, long endTime) {
        ConcurrentNavigableMap<Long, Long> map = longMaps.get(sourceId);
        if (map == null) {
            return new java.util.TreeMap<>();
        }
        return map.subMap(startTime, true, endTime, true);
    }
    
    // ==================== Float类型方法 ====================
    
    /**
     * 写入Float数据
     * 
     * @param sourceId 数据源ID
     * @param timestamp 时间戳
     * @param value 数据值
     */
    public void putFloat(String sourceId, long timestamp, float value) {
        ConcurrentNavigableMap<Long, Float> map = floatMaps.get(sourceId);
        if (map == null) {
            throw new IllegalArgumentException("数据源不存在: " + sourceId);
        }
        map.put(timestamp, value);
    }
    
    /**
     * 写入Float数据并提交
     * 
     * @param sourceId 数据源ID
     * @param timestamp 时间戳
     * @param value 数据值
     */
    public void putFloatAndCommit(String sourceId, long timestamp, float value) {
        putFloat(sourceId, timestamp, value);
        commit();
    }
    
    /**
     * 批量写入Float数据
     * 
     * @param sourceId 数据源ID
     * @param dataPoints 数据点列表
     */
    public void putBatchFloat(String sourceId, List<DataPoint<Float>> dataPoints) {
        ConcurrentNavigableMap<Long, Float> map = floatMaps.get(sourceId);
        if (map == null) {
            throw new IllegalArgumentException("数据源不存在: " + sourceId);
        }
        
        Map<Long, Float> dataMap = new HashMap<>(dataPoints.size());
        for (DataPoint<Float> point : dataPoints) {
            dataMap.put(point.getTimestamp(), point.getValue());
        }
        map.putAll(dataMap);
        commit();
    }
    
    /**
     * 获取Float数据
     * 
     * @param sourceId 数据源ID
     * @param timestamp 时间戳
     * @return 数据值，如果不存在返回null
     */
    public Float getFloat(String sourceId, long timestamp) {
        ConcurrentNavigableMap<Long, Float> map = floatMaps.get(sourceId);
        if (map == null) {
            return null;
        }
        return map.get(timestamp);
    }
    
    /**
     * 查询Float数据范围
     * 
     * @param sourceId 数据源ID
     * @param startTime 开始时间
     * @param endTime 结束时间
     * @return 时间范围内的数据
     */
    public NavigableMap<Long, Float> queryRangeFloat(String sourceId, long startTime, long endTime) {
        ConcurrentNavigableMap<Long, Float> map = floatMaps.get(sourceId);
        if (map == null) {
            return new java.util.TreeMap<>();
        }
        return map.subMap(startTime, true, endTime, true);
    }
    
    // ==================== Object类型方法 ====================
    
    /**
     * 写入Object数据
     * 
     * @param sourceId 数据源ID
     * @param timestamp 时间戳
     * @param value 数据值
     */
    public void putObject(String sourceId, long timestamp, Object value) {
        ConcurrentNavigableMap<Long, Object> map = objectMaps.get(sourceId);
        if (map == null) {
            throw new IllegalArgumentException("数据源不存在: " + sourceId);
        }
        map.put(timestamp, value);
    }
    
    /**
     * 写入Object数据并提交
     * 
     * @param sourceId 数据源ID
     * @param timestamp 时间戳
     * @param value 数据值
     */
    public void putObjectAndCommit(String sourceId, long timestamp, Object value) {
        putObject(sourceId, timestamp, value);
        commit();
    }
    
    /**
     * 批量写入Object数据
     * 
     * @param sourceId 数据源ID
     * @param dataPoints 数据点列表
     */
    public void putBatchObject(String sourceId, List<DataPoint<Object>> dataPoints) {
        ConcurrentNavigableMap<Long, Object> map = objectMaps.get(sourceId);
        if (map == null) {
            throw new IllegalArgumentException("数据源不存在: " + sourceId);
        }
        
        Map<Long, Object> dataMap = new HashMap<>(dataPoints.size());
        for (DataPoint<Object> point : dataPoints) {
            dataMap.put(point.getTimestamp(), point.getValue());
        }
        map.putAll(dataMap);
        commit();
    }
    
    /**
     * 获取Object数据
     * 
     * @param sourceId 数据源ID
     * @param timestamp 时间戳
     * @return 数据值，如果不存在返回null
     */
    public Object getObject(String sourceId, long timestamp) {
        ConcurrentNavigableMap<Long, Object> map = objectMaps.get(sourceId);
        if (map == null) {
            return null;
        }
        return map.get(timestamp);
    }
    
    /**
     * 查询Object数据范围
     * 
     * @param sourceId 数据源ID
     * @param startTime 开始时间
     * @param endTime 结束时间
     * @return 时间范围内的数据
     */
    public NavigableMap<Long, Object> queryRangeObject(String sourceId, long startTime, long endTime) {
        ConcurrentNavigableMap<Long, Object> map = objectMaps.get(sourceId);
        if (map == null) {
            return new java.util.TreeMap<>();
        }
        return map.subMap(startTime, true, endTime, true);
    }
    
    // ==================== 缺失的方法 ====================
    
    /**
     * 获取所有数据源ID
     * 
     * @return 数据源ID集合
     */
    public Set<String> getDataSources() {
        return dataSources.keySet();
    }
    
    /**
     * 获取数据库统计信息
     * 
     * @return 统计信息Map
     */
    public Map<String, Object> getStatistics() {
        Map<String, Object> stats = new HashMap<>();
        
        // 统计各类型数据源的数据量
        for (String sourceId : dataSources.keySet()) {
            DataSourceConfig config = dataSources.get(sourceId);
            long count = 0;
            
            switch (config.getDataType()) {
                case DOUBLE:
                    ConcurrentNavigableMap<Long, Double> doubleMap = doubleMaps.get(sourceId);
                    if (doubleMap != null) count = doubleMap.size();
                    break;
                case INTEGER:
                    ConcurrentNavigableMap<Long, Integer> integerMap = integerMaps.get(sourceId);
                    if (integerMap != null) count = integerMap.size();
                    break;
                case LONG:
                    ConcurrentNavigableMap<Long, Long> longMap = longMaps.get(sourceId);
                    if (longMap != null) count = longMap.size();
                    break;
                case FLOAT:
                    ConcurrentNavigableMap<Long, Float> floatMap = floatMaps.get(sourceId);
                    if (floatMap != null) count = floatMap.size();
                    break;
                case OBJECT:
                    ConcurrentNavigableMap<Long, Object> objectMap = objectMaps.get(sourceId);
                    if (objectMap != null) count = objectMap.size();
                    break;
            }
            
            stats.put(sourceId, count);
        }
        
        return stats;
    }
    
    /**
     * 获取数据库统计信息（返回Long类型）
     * 
     * @return 统计信息Map
     */
    public Map<String, Long> getStatisticsAsLong() {
        Map<String, Long> stats = new HashMap<>();
        
        // 统计各类型数据源的数据量
        for (String sourceId : dataSources.keySet()) {
            DataSourceConfig config = dataSources.get(sourceId);
            long count = 0;
            
            switch (config.getDataType()) {
                case DOUBLE:
                    ConcurrentNavigableMap<Long, Double> doubleMap = doubleMaps.get(sourceId);
                    if (doubleMap != null) count = doubleMap.size();
                    break;
                case INTEGER:
                    ConcurrentNavigableMap<Long, Integer> integerMap = integerMaps.get(sourceId);
                    if (integerMap != null) count = integerMap.size();
                    break;
                case LONG:
                    ConcurrentNavigableMap<Long, Long> longMap = longMaps.get(sourceId);
                    if (longMap != null) count = longMap.size();
                    break;
                case FLOAT:
                    ConcurrentNavigableMap<Long, Float> floatMap = floatMaps.get(sourceId);
                    if (floatMap != null) count = floatMap.size();
                    break;
                case OBJECT:
                    ConcurrentNavigableMap<Long, Object> objectMap = objectMaps.get(sourceId);
                    if (objectMap != null) count = objectMap.size();
                    break;
            }
            
            stats.put(sourceId, count);
        }
        
        return stats;
    }
    
    // ==================== 类型转换方法 ====================
    
    /**
     * 将Double数据写入Object数据源
     * 
     * @param sourceId 数据源ID
     * @param timestamp 时间戳
     * @param value 数据值
     */
    public void putDoubleToObject(String sourceId, long timestamp, double value) {
        putObject(sourceId, timestamp, value);
    }
    
    /**
     * 从Object数据源获取Double数据
     * 
     * @param sourceId 数据源ID
     * @param timestamp 时间戳
     * @return Double数据值
     */
    public Double getDoubleFromObject(String sourceId, long timestamp) {
        Object value = getObject(sourceId, timestamp);
        return value instanceof Double ? (Double) value : null;
    }
    
    /**
     * 将String数据写入Object数据源
     * 
     * @param sourceId 数据源ID
     * @param timestamp 时间戳
     * @param value 数据值
     */
    public void putStringToObject(String sourceId, long timestamp, String value) {
        putObject(sourceId, timestamp, value);
    }
    
    /**
     * 从Object数据源获取String数据
     * 
     * @param sourceId 数据源ID
     * @param timestamp 时间戳
     * @return String数据值
     */
    public String getStringFromObject(String sourceId, long timestamp) {
        Object value = getObject(sourceId, timestamp);
        return value instanceof String ? (String) value : null;
    }
    
    /**
     * 将Boolean数据写入Object数据源
     * 
     * @param sourceId 数据源ID
     * @param timestamp 时间戳
     * @param value 数据值
     */
    public void putBooleanToObject(String sourceId, long timestamp, boolean value) {
        putObject(sourceId, timestamp, value);
    }
    
    /**
     * 从Object数据源获取Boolean数据
     * 
     * @param sourceId 数据源ID
     * @param timestamp 时间戳
     * @return Boolean数据值
     */
    public Boolean getBooleanFromObject(String sourceId, long timestamp) {
        Object value = getObject(sourceId, timestamp);
        return value instanceof Boolean ? (Boolean) value : null;
    }
    
    /**
     * 将Integer数据写入Object数据源
     * 
     * @param sourceId 数据源ID
     * @param timestamp 时间戳
     * @param value 数据值
     */
    public void putIntegerToObject(String sourceId, long timestamp, int value) {
        putObject(sourceId, timestamp, value);
    }
    
    /**
     * 从Object数据源获取Integer数据
     * 
     * @param sourceId 数据源ID
     * @param timestamp 时间戳
     * @return Integer数据值
     */
    public Integer getIntegerFromObject(String sourceId, long timestamp) {
        Object value = getObject(sourceId, timestamp);
        return value instanceof Integer ? (Integer) value : null;
    }
    
    // ==================== 批量方法别名 ====================
    
    /**
     * 批量写入Double数据（别名方法）
     * 
     * @param sourceId 数据源ID
     * @param dataPoints 数据点列表
     */
    public void putDoubleBatch(String sourceId, List<DataPoint<Double>> dataPoints) {
        putBatchDouble(sourceId, dataPoints);
    }
    
    /**
     * 批量写入Integer数据（别名方法）
     * 
     * @param sourceId 数据源ID
     * @param dataPoints 数据点列表
     */
    public void putIntegerBatch(String sourceId, List<DataPoint<Integer>> dataPoints) {
        putBatchInteger(sourceId, dataPoints);
    }
    
    /**
     * 批量写入Object数据（别名方法）
     * 
     * @param sourceId 数据源ID
     * @param dataPoints 数据点列表
     */
    public void putObjectBatch(String sourceId, List<DataPoint<Object>> dataPoints) {
        putBatchObject(sourceId, dataPoints);
    }
    
    // ==================== 动态数据源管理 ====================
    
    /**
     * 动态添加Double数据源
     * 
     * @param sourceId 数据源ID
     * @param description 描述
     */
    public void addDoubleSource(String sourceId, String description) {
        addDataSource(sourceId, DataType.DOUBLE, description);
    }
    
    /**
     * 动态添加Integer数据源
     * 
     * @param sourceId 数据源ID
     * @param description 描述
     */
    public void addIntegerSource(String sourceId, String description) {
        addDataSource(sourceId, DataType.INTEGER, description);
    }
    
    /**
     * 动态添加Long数据源
     * 
     * @param sourceId 数据源ID
     * @param description 描述
     */
    public void addLongSource(String sourceId, String description) {
        addDataSource(sourceId, DataType.LONG, description);
    }
    
    /**
     * 动态添加Float数据源
     * 
     * @param sourceId 数据源ID
     * @param description 描述
     */
    public void addFloatSource(String sourceId, String description) {
        addDataSource(sourceId, DataType.FLOAT, description);
    }
    
    /**
     * 动态添加Object数据源
     * 
     * @param sourceId 数据源ID
     * @param description 描述
     */
    public void addObjectSource(String sourceId, String description) {
        addDataSource(sourceId, DataType.OBJECT, description);
    }
}
