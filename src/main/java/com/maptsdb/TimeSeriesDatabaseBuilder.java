package com.maptsdb;

import org.mapdb.DB;
import org.mapdb.DBMaker;
import org.mapdb.Serializer;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentNavigableMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 基于Builder模式的时序数据库
 * 
 * <p>使用Builder模式提供流畅的API，支持链式调用和类型安全的数据源管理。</p>
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
 * <h3>使用示例：</h3>
 * <pre>{@code
 * // 创建数据库
 * TimeSeriesDatabase db = TimeSeriesDatabaseBuilder.builder()
 *     .path("data.db")
 *     .addDoubleSource("temperature", "环境温度")
 *     .addIntegerSource("humidity", "相对湿度")
 *     .enableMemoryMapping()
 *     .buildWithDynamicSources();
 * 
 * // 单条写入（需要手动commit）
 * db.putDouble("temperature", timestamp, 25.6);
 * db.commit();
 * 
 * // 便利写入（自动commit）
 * db.putDoubleAndCommit("temperature", timestamp, 25.6);
 * 
 * // 批量写入（自动commit）
 * List<DataPoint<Double>> data = Arrays.asList(
 *     new DataPoint<>(timestamp1, 25.6),
 *     new DataPoint<>(timestamp2, 26.1)
 * );
 * db.putDoubleBatch("temperature", data);
 * }</pre>
 * 
 * @author MapTSDB Team
 * @version 1.2.0
 * @since 1.0.0
 */
public class TimeSeriesDatabaseBuilder {
    
    // ==================== 内部类 ====================
    
    /**
     * 数据源配置
     */
    public static class DataSourceConfig {
        private final String sourceId;
        private final DataType dataType;
        private final String description;
        
        public DataSourceConfig(String sourceId, DataType dataType, String description) {
            this.sourceId = sourceId;
            this.dataType = dataType;
            this.description = description;
        }
        
        public String getSourceId() { return sourceId; }
        public DataType getDataType() { return dataType; }
        public String getDescription() { return description; }
    }
    
    /**
     * 数据库配置
     */
    public static class DatabaseConfig {
        private String dbPath;
        private boolean enableMemoryMapping = true;
        private boolean enableTransactions = true;
        private int concurrencyScale = 16;
        private long retentionDays = 30;
        private long cleanupIntervalHours = 1;
        private boolean enableCleaner = true;
        private boolean disablePreclear = true;
        
        // Getters and setters
        public String getDbPath() { return dbPath; }
        public boolean isEnableMemoryMapping() { return enableMemoryMapping; }
        public boolean isEnableTransactions() { return enableTransactions; }
        public int getConcurrencyScale() { return concurrencyScale; }
        public long getRetentionDays() { return retentionDays; }
        public long getCleanupIntervalHours() { return cleanupIntervalHours; }
        public boolean isEnableCleaner() { return enableCleaner; }
        public boolean isDisablePreclear() { return disablePreclear; }
    }
    
    /**
     * 数据类型枚举
     */
    public enum DataType {
        DOUBLE, INTEGER, LONG, FLOAT, OBJECT
    }
    
    /**
     * 主数据库类
     */
    public static class TimeSeriesDatabase {
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
        
        private TimeSeriesDatabase(DatabaseConfig config, Map<String, DataSourceConfig> dataSources) {
            this(config, dataSources, null);
        }
        
        private TimeSeriesDatabase(DatabaseConfig config, Map<String, DataSourceConfig> dataSources, TimeSeriesDatabaseBuilder builder) {
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
        
        private void initializeDataSources() {
            for (DataSourceConfig sourceConfig : dataSources.values()) {
                createDataSource(sourceConfig);
            }
        }
        
        private void createDataSource(DataSourceConfig sourceConfig) {
            String mapName = sourceConfig.getSourceId() + "_" + sourceConfig.getDataType().name().toLowerCase();
            
            switch (sourceConfig.getDataType()) {
                case DOUBLE:
                    ConcurrentNavigableMap<Long, Double> doubleMap = db.treeMap(mapName)
                            .keySerializer(Serializer.LONG_PACKED)
                            .valueSerializer(Serializer.DOUBLE)
                            .createOrOpen();
                    doubleMaps.put(sourceConfig.getSourceId(), doubleMap);
                    break;
                    
                case INTEGER:
                    ConcurrentNavigableMap<Long, Integer> integerMap = db.treeMap(mapName)
                            .keySerializer(Serializer.LONG_PACKED)
                            .valueSerializer(Serializer.INTEGER)
                            .createOrOpen();
                    integerMaps.put(sourceConfig.getSourceId(), integerMap);
                    break;
                    
                case LONG:
                    ConcurrentNavigableMap<Long, Long> longMap = db.treeMap(mapName)
                            .keySerializer(Serializer.LONG_PACKED)
                            .valueSerializer(Serializer.LONG)
                            .createOrOpen();
                    longMaps.put(sourceConfig.getSourceId(), longMap);
                    break;
                    
                case FLOAT:
                    ConcurrentNavigableMap<Long, Float> floatMap = db.treeMap(mapName)
                            .keySerializer(Serializer.LONG_PACKED)
                            .valueSerializer(Serializer.FLOAT)
                            .createOrOpen();
                    floatMaps.put(sourceConfig.getSourceId(), floatMap);
                    break;
                    
                case OBJECT:
                    ConcurrentNavigableMap<Long, Object> objectMap = db.treeMap(mapName)
                            .keySerializer(Serializer.LONG)
                            .valueSerializer(Serializer.JAVA)
                            .createOrOpen();
                    objectMaps.put(sourceConfig.getSourceId(), objectMap);
                    break;
            }
        }
        
        private void startCleanupTask() {
            long intervalMs = config.getCleanupIntervalHours() * 60 * 60 * 1000;
            scheduler.scheduleAtFixedRate(this::cleanupOldData, 1, 1, TimeUnit.HOURS);
        }
        
        // ==================== 公共方法 ====================
        
        /**
         * 写入Double数据
         */
        public void putDouble(String sourceId, long timestamp, double value) {
            validateSource(sourceId, DataType.DOUBLE);
            ConcurrentNavigableMap<Long, Double> map = doubleMaps.get(sourceId);
            map.put(timestamp, value);
            // 移除立即commit，提升性能
        }
        
        /**
         * 写入Integer数据
         */
        public void putInteger(String sourceId, long timestamp, int value) {
            validateSource(sourceId, DataType.INTEGER);
            ConcurrentNavigableMap<Long, Integer> map = integerMaps.get(sourceId);
            map.put(timestamp, value);
            // 移除立即commit，提升性能
        }
        
        /**
         * 写入Long数据
         */
        public void putLong(String sourceId, long timestamp, long value) {
            validateSource(sourceId, DataType.LONG);
            ConcurrentNavigableMap<Long, Long> map = longMaps.get(sourceId);
            map.put(timestamp, value);
            // 移除立即commit，提升性能
        }
        
        /**
         * 写入Float数据
         */
        public void putFloat(String sourceId, long timestamp, float value) {
            validateSource(sourceId, DataType.FLOAT);
            ConcurrentNavigableMap<Long, Float> map = floatMaps.get(sourceId);
            map.put(timestamp, value);
            // 移除立即commit，提升性能
        }
        
        /**
         * 写入Object数据
         */
        public void putObject(String sourceId, long timestamp, Object value) {
            validateSource(sourceId, DataType.OBJECT);
            if (value == null) {
                throw new IllegalArgumentException("对象值不能为null");
            }
            ConcurrentNavigableMap<Long, Object> map = objectMaps.get(sourceId);
            map.put(timestamp, value);
            // 移除立即commit，提升性能
        }
        
        /**
         * 写入Double到Object数据源
         */
        public void putDoubleToObject(String sourceId, long timestamp, double value) {
            putObject(sourceId, timestamp, value);
        }
        
        /**
         * 写入Integer到Object数据源
         */
        public void putIntegerToObject(String sourceId, long timestamp, int value) {
            putObject(sourceId, timestamp, value);
        }
        
        /**
         * 写入Long到Object数据源
         */
        public void putLongToObject(String sourceId, long timestamp, long value) {
            putObject(sourceId, timestamp, value);
        }
        
        /**
         * 写入String到Object数据源
         */
        public void putStringToObject(String sourceId, long timestamp, String value) {
            putObject(sourceId, timestamp, value);
        }
        
        /**
         * 写入Boolean到Object数据源
         */
        public void putBooleanToObject(String sourceId, long timestamp, boolean value) {
            putObject(sourceId, timestamp, value);
        }
        
        /**
         * 写入Float到Object数据源
         */
        public void putFloatToObject(String sourceId, long timestamp, float value) {
            putObject(sourceId, timestamp, value);
        }
        
        // ==================== 单条写入并提交方法 ====================
        
        /**
         * 写入Double数据并立即提交
         */
        public void putDoubleAndCommit(String sourceId, long timestamp, double value) {
            putDouble(sourceId, timestamp, value);
            db.commit();
        }
        
        /**
         * 写入Integer数据并立即提交
         */
        public void putIntegerAndCommit(String sourceId, long timestamp, int value) {
            putInteger(sourceId, timestamp, value);
            db.commit();
        }
        
        /**
         * 写入Long数据并立即提交
         */
        public void putLongAndCommit(String sourceId, long timestamp, long value) {
            putLong(sourceId, timestamp, value);
            db.commit();
        }
        
        /**
         * 写入Float数据并立即提交
         */
        public void putFloatAndCommit(String sourceId, long timestamp, float value) {
            putFloat(sourceId, timestamp, value);
            db.commit();
        }
        
        /**
         * 写入Object数据并立即提交
         */
        public void putObjectAndCommit(String sourceId, long timestamp, Object value) {
            putObject(sourceId, timestamp, value);
            db.commit();
        }
        
        /**
         * 写入Double到Object数据源并立即提交
         */
        public void putDoubleToObjectAndCommit(String sourceId, long timestamp, double value) {
            putDoubleToObject(sourceId, timestamp, value);
            db.commit();
        }
        
        /**
         * 写入Integer到Object数据源并立即提交
         */
        public void putIntegerToObjectAndCommit(String sourceId, long timestamp, int value) {
            putIntegerToObject(sourceId, timestamp, value);
            db.commit();
        }
        
        /**
         * 写入Long到Object数据源并立即提交
         */
        public void putLongToObjectAndCommit(String sourceId, long timestamp, long value) {
            putLongToObject(sourceId, timestamp, value);
            db.commit();
        }
        
        /**
         * 写入String到Object数据源并立即提交
         */
        public void putStringToObjectAndCommit(String sourceId, long timestamp, String value) {
            putStringToObject(sourceId, timestamp, value);
            db.commit();
        }
        
        /**
         * 写入Boolean到Object数据源并立即提交
         */
        public void putBooleanToObjectAndCommit(String sourceId, long timestamp, boolean value) {
            putBooleanToObject(sourceId, timestamp, value);
            db.commit();
        }
        
        /**
         * 写入Float到Object数据源并立即提交
         */
        public void putFloatToObjectAndCommit(String sourceId, long timestamp, float value) {
            putFloatToObject(sourceId, timestamp, value);
            db.commit();
        }
        
        // ==================== 批量写入方法 ====================
        
        /**
         * 批量写入Double数据（优化版本）
         */
        public void putDoubleBatch(String sourceId, java.util.List<DataPoint<Double>> dataPoints) {
            validateSource(sourceId, DataType.DOUBLE);
            if (dataPoints == null || dataPoints.isEmpty()) {
                return;
            }
            
            ConcurrentNavigableMap<Long, Double> map = doubleMaps.get(sourceId);
            java.util.Map<Long, Double> batchMap = new java.util.HashMap<>(dataPoints.size());
            
            for (DataPoint<Double> point : dataPoints) {
                batchMap.put(point.getTimestamp(), point.getValue());
            }
            
            map.putAll(batchMap);
            db.commit();
        }
        
        /**
         * 批量写入Integer数据（优化版本）
         */
        public void putIntegerBatch(String sourceId, java.util.List<DataPoint<Integer>> dataPoints) {
            validateSource(sourceId, DataType.INTEGER);
            if (dataPoints == null || dataPoints.isEmpty()) {
                return;
            }
            
            ConcurrentNavigableMap<Long, Integer> map = integerMaps.get(sourceId);
            java.util.Map<Long, Integer> batchMap = new java.util.HashMap<>(dataPoints.size());
            
            for (DataPoint<Integer> point : dataPoints) {
                batchMap.put(point.getTimestamp(), point.getValue());
            }
            
            map.putAll(batchMap);
            db.commit();
        }
        
        /**
         * 批量写入Long数据（优化版本）
         */
        public void putLongBatch(String sourceId, java.util.List<DataPoint<Long>> dataPoints) {
            validateSource(sourceId, DataType.LONG);
            if (dataPoints == null || dataPoints.isEmpty()) {
                return;
            }
            
            ConcurrentNavigableMap<Long, Long> map = longMaps.get(sourceId);
            java.util.Map<Long, Long> batchMap = new java.util.HashMap<>(dataPoints.size());
            
            for (DataPoint<Long> point : dataPoints) {
                batchMap.put(point.getTimestamp(), point.getValue());
            }
            
            map.putAll(batchMap);
            db.commit();
        }
        
        /**
         * 批量写入Float数据（优化版本）
         */
        public void putFloatBatch(String sourceId, java.util.List<DataPoint<Float>> dataPoints) {
            validateSource(sourceId, DataType.FLOAT);
            if (dataPoints == null || dataPoints.isEmpty()) {
                return;
            }
            
            ConcurrentNavigableMap<Long, Float> map = floatMaps.get(sourceId);
            java.util.Map<Long, Float> batchMap = new java.util.HashMap<>(dataPoints.size());
            
            for (DataPoint<Float> point : dataPoints) {
                batchMap.put(point.getTimestamp(), point.getValue());
            }
            
            map.putAll(batchMap);
            db.commit();
        }
        
        /**
         * 批量写入Object数据（优化版本）
         */
        public void putObjectBatch(String sourceId, java.util.List<DataPoint<Object>> dataPoints) {
            validateSource(sourceId, DataType.OBJECT);
            if (dataPoints == null || dataPoints.isEmpty()) {
                return;
            }
            
            ConcurrentNavigableMap<Long, Object> map = objectMaps.get(sourceId);
            java.util.Map<Long, Object> batchMap = new java.util.HashMap<>(dataPoints.size());
            
            for (DataPoint<Object> point : dataPoints) {
                if (point.getValue() == null) {
                    throw new IllegalArgumentException("对象值不能为null");
                }
                batchMap.put(point.getTimestamp(), point.getValue());
            }
            
            map.putAll(batchMap);
            db.commit();
        }
        
        /**
         * 查询Double数据
         */
        public Double getDouble(String sourceId, long timestamp) {
            validateSource(sourceId, DataType.DOUBLE);
            return doubleMaps.get(sourceId).get(timestamp);
        }
        
        /**
         * 查询Integer数据
         */
        public Integer getInteger(String sourceId, long timestamp) {
            validateSource(sourceId, DataType.INTEGER);
            return integerMaps.get(sourceId).get(timestamp);
        }
        
        /**
         * 查询Long数据
         */
        public Long getLong(String sourceId, long timestamp) {
            validateSource(sourceId, DataType.LONG);
            return longMaps.get(sourceId).get(timestamp);
        }
        
        /**
         * 查询Float数据
         */
        public Float getFloat(String sourceId, long timestamp) {
            validateSource(sourceId, DataType.FLOAT);
            return floatMaps.get(sourceId).get(timestamp);
        }
        
        /**
         * 查询Object数据
         */
        public Object getObject(String sourceId, long timestamp) {
            validateSource(sourceId, DataType.OBJECT);
            return objectMaps.get(sourceId).get(timestamp);
        }
        
        /**
         * 查询Object数据并转换为指定类型
         */
        @SuppressWarnings("unchecked")
        public <T> T getObject(String sourceId, long timestamp, Class<T> type) {
            Object value = getObject(sourceId, timestamp);
            if (value != null && type.isInstance(value)) {
                return (T) value;
            }
            return null;
        }
        
        /**
         * 查询Double类型数据（从Object数据源）
         */
        public Double getDoubleFromObject(String sourceId, long timestamp) {
            return getObject(sourceId, timestamp, Double.class);
        }
        
        /**
         * 查询Integer类型数据（从Object数据源）
         */
        public Integer getIntegerFromObject(String sourceId, long timestamp) {
            return getObject(sourceId, timestamp, Integer.class);
        }
        
        /**
         * 查询String类型数据（从Object数据源）
         */
        public String getStringFromObject(String sourceId, long timestamp) {
            return getObject(sourceId, timestamp, String.class);
        }
        
        /**
         * 查询Boolean类型数据（从Object数据源）
         */
        public Boolean getBooleanFromObject(String sourceId, long timestamp) {
            return getObject(sourceId, timestamp, Boolean.class);
        }
        
        /**
         * 获取所有数据源
         */
        public Set<String> getDataSources() {
            return new HashSet<>(dataSources.keySet());
        }
        
        /**
         * 获取数据源信息
         */
        public DataSourceConfig getDataSourceInfo(String sourceId) {
            return dataSources.get(sourceId);
        }
        
        /**
         * 手动提交事务
         */
        public void commit() {
            db.commit();
        }
        
        /**
         * 获取统计信息
         */
        public Map<String, Long> getStatistics() {
            Map<String, Long> stats = new HashMap<>();
            for (Map.Entry<String, DataSourceConfig> entry : dataSources.entrySet()) {
                String sourceId = entry.getKey();
                DataType dataType = entry.getValue().getDataType();
                long count = 0;
                
                switch (dataType) {
                    case DOUBLE:
                        count = doubleMaps.get(sourceId).size();
                        break;
                    case INTEGER:
                        count = integerMaps.get(sourceId).size();
                        break;
                    case LONG:
                        count = longMaps.get(sourceId).size();
                        break;
                    case FLOAT:
                        count = floatMaps.get(sourceId).size();
                        break;
                    case OBJECT:
                        count = objectMaps.get(sourceId).size();
                        break;
                }
                
                stats.put(sourceId + " (" + dataType + ")", count);
            }
            return stats;
        }
        
        // ==================== 动态添加数据源 ====================
        
        /**
         * 动态添加Double数据源
         */
        public void addDoubleSource(String sourceId) {
            addDataSource(sourceId, DataType.DOUBLE, null);
        }
        
        /**
         * 动态添加Double数据源（带描述）
         */
        public void addDoubleSource(String sourceId, String description) {
            addDataSource(sourceId, DataType.DOUBLE, description);
        }
        
        /**
         * 动态添加Integer数据源
         */
        public void addIntegerSource(String sourceId) {
            addDataSource(sourceId, DataType.INTEGER, null);
        }
        
        /**
         * 动态添加Integer数据源（带描述）
         */
        public void addIntegerSource(String sourceId, String description) {
            addDataSource(sourceId, DataType.INTEGER, description);
        }
        
        /**
         * 动态添加Long数据源
         */
        public void addLongSource(String sourceId) {
            addDataSource(sourceId, DataType.LONG, null);
        }
        
        /**
         * 动态添加Long数据源（带描述）
         */
        public void addLongSource(String sourceId, String description) {
            addDataSource(sourceId, DataType.LONG, description);
        }
        
        /**
         * 动态添加Float数据源
         */
        public void addFloatSource(String sourceId) {
            addDataSource(sourceId, DataType.FLOAT, null);
        }
        
        /**
         * 动态添加Float数据源（带描述）
         */
        public void addFloatSource(String sourceId, String description) {
            addDataSource(sourceId, DataType.FLOAT, description);
        }
        
        /**
         * 动态添加Object数据源
         */
        public void addObjectSource(String sourceId) {
            addDataSource(sourceId, DataType.OBJECT, null);
        }
        
        /**
         * 动态添加Object数据源（带描述）
         */
        public void addObjectSource(String sourceId, String description) {
            addDataSource(sourceId, DataType.OBJECT, description);
        }
        
        /**
         * 动态添加数据源（内部方法）
         */
        private void addDataSource(String sourceId, DataType dataType, String description) {
            if (builder == null) {
                throw new UnsupportedOperationException("此数据库实例不支持动态添加数据源，请使用 buildWithDynamicSources() 创建");
            }
            
            if (sourceId == null || sourceId.trim().isEmpty()) {
                throw new IllegalArgumentException("数据源ID不能为空");
            }
            
            if (dataSources.containsKey(sourceId)) {
                DataSourceConfig existing = dataSources.get(sourceId);
                if (existing.getDataType() != dataType) {
                    throw new IllegalArgumentException(
                        String.format("数据源 '%s' 已存在，类型为 %s，不能创建为 %s 类型", 
                            sourceId, existing.getDataType(), dataType));
                }
                return; // 数据源已存在且类型匹配
            }
            
            DataSourceConfig sourceConfig = new DataSourceConfig(sourceId, dataType, description);
            dataSources.put(sourceId, sourceConfig);
            createDataSource(sourceConfig);
        }
        
        private void validateSource(String sourceId, DataType expectedType) {
            DataSourceConfig config = dataSources.get(sourceId);
            if (config == null) {
                throw new IllegalArgumentException("数据源 '" + sourceId + "' 不存在");
            }
            if (config.getDataType() != expectedType) {
                throw new IllegalArgumentException(
                    String.format("数据源 '%s' 类型为 %s，不能写入 %s 类型数据", 
                        sourceId, config.getDataType(), expectedType));
            }
        }
        
        private void cleanupOldData() {
            long cutoffTime = System.currentTimeMillis() - (config.getRetentionDays() * 24 * 60 * 60 * 1000);
            
            for (ConcurrentNavigableMap<Long, Double> map : doubleMaps.values()) {
                map.headMap(cutoffTime).clear();
            }
            for (ConcurrentNavigableMap<Long, Integer> map : integerMaps.values()) {
                map.headMap(cutoffTime).clear();
            }
            for (ConcurrentNavigableMap<Long, Long> map : longMaps.values()) {
                map.headMap(cutoffTime).clear();
            }
            for (ConcurrentNavigableMap<Long, Float> map : floatMaps.values()) {
                map.headMap(cutoffTime).clear();
            }
            for (ConcurrentNavigableMap<Long, Object> map : objectMaps.values()) {
                map.headMap(cutoffTime).clear();
            }
            
            db.commit();
        }
        
        public void close() {
            scheduler.shutdown();
            try {
                if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                    scheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                scheduler.shutdownNow();
                Thread.currentThread().interrupt();
            }
            
            if (db != null && !db.isClosed()) {
                db.close();
            }
        }
    }
    
    // ==================== Builder 类 ====================
    
    private final DatabaseConfig config;
    private final Map<String, DataSourceConfig> dataSources;
    
    private TimeSeriesDatabaseBuilder() {
        this.config = new DatabaseConfig();
        this.dataSources = new HashMap<>();
    }
    
    /**
     * 创建Builder实例
     */
    public static TimeSeriesDatabaseBuilder builder() {
        return new TimeSeriesDatabaseBuilder();
    }
    
    /**
     * 设置数据库路径
     */
    public TimeSeriesDatabaseBuilder path(String dbPath) {
        if (dbPath == null || dbPath.trim().isEmpty()) {
            throw new IllegalArgumentException("数据库路径不能为空");
        }
        this.config.dbPath = dbPath;
        return this;
    }
    
    /**
     * 添加Double类型数据源
     */
    public TimeSeriesDatabaseBuilder addDoubleSource(String sourceId) {
        return addDoubleSource(sourceId, null);
    }
    
    /**
     * 添加Double类型数据源（带描述）
     */
    public TimeSeriesDatabaseBuilder addDoubleSource(String sourceId, String description) {
        addDataSource(sourceId, DataType.DOUBLE, description);
        return this;
    }
    
    /**
     * 添加Integer类型数据源
     */
    public TimeSeriesDatabaseBuilder addIntegerSource(String sourceId) {
        return addIntegerSource(sourceId, null);
    }
    
    /**
     * 添加Integer类型数据源（带描述）
     */
    public TimeSeriesDatabaseBuilder addIntegerSource(String sourceId, String description) {
        addDataSource(sourceId, DataType.INTEGER, description);
        return this;
    }
    
    /**
     * 添加Long类型数据源
     */
    public TimeSeriesDatabaseBuilder addLongSource(String sourceId) {
        return addLongSource(sourceId, null);
    }
    
    /**
     * 添加Long类型数据源（带描述）
     */
    public TimeSeriesDatabaseBuilder addLongSource(String sourceId, String description) {
        addDataSource(sourceId, DataType.LONG, description);
        return this;
    }
    
    /**
     * 添加Float类型数据源
     */
    public TimeSeriesDatabaseBuilder addFloatSource(String sourceId) {
        return addFloatSource(sourceId, null);
    }
    
    /**
     * 添加Float类型数据源（带描述）
     */
    public TimeSeriesDatabaseBuilder addFloatSource(String sourceId, String description) {
        addDataSource(sourceId, DataType.FLOAT, description);
        return this;
    }
    
    /**
     * 添加Object类型数据源
     */
    public TimeSeriesDatabaseBuilder addObjectSource(String sourceId) {
        return addObjectSource(sourceId, null);
    }
    
    /**
     * 添加Object类型数据源（带描述）
     */
    public TimeSeriesDatabaseBuilder addObjectSource(String sourceId, String description) {
        addDataSource(sourceId, DataType.OBJECT, description);
        return this;
    }
    
    /**
     * 设置数据保留天数
     */
    public TimeSeriesDatabaseBuilder withRetentionDays(long days) {
        if (days <= 0) {
            throw new IllegalArgumentException("保留天数必须大于0");
        }
        this.config.retentionDays = days;
        return this;
    }
    
    /**
     * 设置清理间隔
     */
    public TimeSeriesDatabaseBuilder withCleanupInterval(long hours, TimeUnit unit) {
        if (hours <= 0) {
            throw new IllegalArgumentException("清理间隔必须大于0");
        }
        this.config.cleanupIntervalHours = unit.toHours(hours);
        return this;
    }
    
    /**
     * 启用内存映射
     */
    public TimeSeriesDatabaseBuilder enableMemoryMapping() {
        this.config.enableMemoryMapping = true;
        return this;
    }
    
    /**
     * 禁用内存映射
     */
    public TimeSeriesDatabaseBuilder disableMemoryMapping() {
        this.config.enableMemoryMapping = false;
        return this;
    }
    
    /**
     * 启用事务
     */
    public TimeSeriesDatabaseBuilder enableTransactions() {
        this.config.enableTransactions = true;
        return this;
    }
    
    /**
     * 禁用事务
     */
    public TimeSeriesDatabaseBuilder disableTransactions() {
        this.config.enableTransactions = false;
        return this;
    }
    
    /**
     * 设置并发级别
     */
    public TimeSeriesDatabaseBuilder withConcurrencyScale(int scale) {
        if (scale <= 0) {
            throw new IllegalArgumentException("并发级别必须大于0");
        }
        this.config.concurrencyScale = scale;
        return this;
    }
    
        /**
         * 构建数据库实例
         */
        public TimeSeriesDatabase build() {
            if (config.dbPath == null) {
                throw new IllegalStateException("必须设置数据库路径");
            }
            if (dataSources.isEmpty()) {
                throw new IllegalStateException("必须至少添加一个数据源");
            }
            
            return new TimeSeriesDatabase(config, new HashMap<>(dataSources));
        }
        
        /**
         * 构建数据库实例（支持后续添加数据源）
         */
        public TimeSeriesDatabase buildWithDynamicSources() {
            if (config.dbPath == null) {
                throw new IllegalStateException("必须设置数据库路径");
            }
            if (dataSources.isEmpty()) {
                throw new IllegalStateException("必须至少添加一个数据源");
            }
            
            return new TimeSeriesDatabase(config, new HashMap<>(dataSources), this);
        }
    
    private void addDataSource(String sourceId, DataType dataType, String description) {
        if (sourceId == null || sourceId.trim().isEmpty()) {
            throw new IllegalArgumentException("数据源ID不能为空");
        }
        
        if (dataSources.containsKey(sourceId)) {
            DataSourceConfig existing = dataSources.get(sourceId);
            if (existing.getDataType() != dataType) {
                throw new IllegalArgumentException(
                    String.format("数据源 '%s' 已存在，类型为 %s，不能创建为 %s 类型", 
                        sourceId, existing.getDataType(), dataType));
            }
            return; // 数据源已存在且类型匹配
        }
        
        dataSources.put(sourceId, new DataSourceConfig(sourceId, dataType, description));
    }
}
