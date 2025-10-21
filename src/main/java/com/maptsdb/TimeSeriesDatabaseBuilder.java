package com.maptsdb;

import java.util.HashMap;
import java.util.Map;

/**
 * 时序数据库Builder
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
 * db.putBatchDouble("temperature", data);
 * }</pre>
 * 
 * @author MapTSDB Team
 * @version 1.0.0
 * @since 1.0.0
 */
public class TimeSeriesDatabaseBuilder {
    
    private final DatabaseConfig config;
    private final Map<String, DataSourceConfig> dataSources;
    
    /**
     * 创建Builder实例
     */
    private TimeSeriesDatabaseBuilder() {
        this.config = new DatabaseConfig();
        this.dataSources = new HashMap<>();
    }
    
    /**
     * 创建Builder实例
     * 
     * @return Builder实例
     */
    public static TimeSeriesDatabaseBuilder builder() {
        return new TimeSeriesDatabaseBuilder();
    }
    
    /**
     * 设置数据库文件路径
     * 
     * @param dbPath 数据库文件路径
     * @return Builder实例
     */
    public TimeSeriesDatabaseBuilder path(String dbPath) {
        if (dbPath == null || dbPath.trim().isEmpty()) {
            throw new IllegalArgumentException("数据库路径不能为空");
        }
        this.config.setDbPath(dbPath);
        return this;
    }
    
    /**
     * 添加Double类型数据源
     * 
     * @param sourceId 数据源ID
     * @return Builder实例
     */
    public TimeSeriesDatabaseBuilder addDoubleSource(String sourceId) {
        return addDoubleSource(sourceId, null);
    }
    
    /**
     * 添加Double类型数据源
     * 
     * @param sourceId 数据源ID
     * @param description 数据源描述
     * @return Builder实例
     */
    public TimeSeriesDatabaseBuilder addDoubleSource(String sourceId, String description) {
        if (sourceId == null || sourceId.trim().isEmpty()) {
            throw new IllegalArgumentException("数据源ID不能为空");
        }
        addDataSource(sourceId, DataType.DOUBLE, description);
        return this;
    }
    
    /**
     * 添加Integer类型数据源
     * 
     * @param sourceId 数据源ID
     * @return Builder实例
     */
    public TimeSeriesDatabaseBuilder addIntegerSource(String sourceId) {
        return addIntegerSource(sourceId, null);
    }
    
    /**
     * 添加Integer类型数据源
     * 
     * @param sourceId 数据源ID
     * @param description 数据源描述
     * @return Builder实例
     */
    public TimeSeriesDatabaseBuilder addIntegerSource(String sourceId, String description) {
        addDataSource(sourceId, DataType.INTEGER, description);
        return this;
    }
    
    /**
     * 添加Long类型数据源
     * 
     * @param sourceId 数据源ID
     * @return Builder实例
     */
    public TimeSeriesDatabaseBuilder addLongSource(String sourceId) {
        return addLongSource(sourceId, null);
    }
    
    /**
     * 添加Long类型数据源
     * 
     * @param sourceId 数据源ID
     * @param description 数据源描述
     * @return Builder实例
     */
    public TimeSeriesDatabaseBuilder addLongSource(String sourceId, String description) {
        addDataSource(sourceId, DataType.LONG, description);
        return this;
    }
    
    /**
     * 添加Float类型数据源
     * 
     * @param sourceId 数据源ID
     * @return Builder实例
     */
    public TimeSeriesDatabaseBuilder addFloatSource(String sourceId) {
        return addFloatSource(sourceId, null);
    }
    
    /**
     * 添加Float类型数据源
     * 
     * @param sourceId 数据源ID
     * @param description 数据源描述
     * @return Builder实例
     */
    public TimeSeriesDatabaseBuilder addFloatSource(String sourceId, String description) {
        addDataSource(sourceId, DataType.FLOAT, description);
        return this;
    }
    
    /**
     * 添加Object类型数据源
     * 
     * @param sourceId 数据源ID
     * @return Builder实例
     */
    public TimeSeriesDatabaseBuilder addObjectSource(String sourceId) {
        return addObjectSource(sourceId, null);
    }
    
    /**
     * 添加Object类型数据源
     * 
     * @param sourceId 数据源ID
     * @param description 数据源描述
     * @return Builder实例
     */
    public TimeSeriesDatabaseBuilder addObjectSource(String sourceId, String description) {
        addDataSource(sourceId, DataType.OBJECT, description);
        return this;
    }
    
    /**
     * 设置数据保留天数
     * 
     * @param days 保留天数
     * @return Builder实例
     */
    public TimeSeriesDatabaseBuilder withRetentionDays(long days) {
        if (days <= 0) {
            throw new IllegalArgumentException("保留天数必须大于0");
        }
        this.config.setRetentionDays(days);
        return this;
    }
    
    /**
     * 设置清理间隔
     * 
     * @param hours 清理间隔小时数
     * @return Builder实例
     */
    public TimeSeriesDatabaseBuilder withCleanupInterval(long hours) {
        this.config.setCleanupIntervalHours(hours);
        return this;
    }
    
    /**
     * 设置清理间隔（支持TimeUnit）
     * 
     * @param time 时间值
     * @param unit 时间单位
     * @return Builder实例
     */
    public TimeSeriesDatabaseBuilder withCleanupInterval(int time, java.util.concurrent.TimeUnit unit) {
        this.config.setCleanupIntervalHours(unit.toHours(time));
        return this;
    }
    
    /**
     * 启用内存映射
     * 
     * @return Builder实例
     */
    public TimeSeriesDatabaseBuilder enableMemoryMapping() {
        this.config.setEnableMemoryMapping(true);
        return this;
    }
    
    /**
     * 禁用内存映射
     * 
     * @return Builder实例
     */
    public TimeSeriesDatabaseBuilder disableMemoryMapping() {
        this.config.setEnableMemoryMapping(false);
        return this;
    }
    
    /**
     * 启用事务
     * 
     * @return Builder实例
     */
    public TimeSeriesDatabaseBuilder enableTransactions() {
        this.config.setEnableTransactions(true);
        return this;
    }
    
    /**
     * 禁用事务
     * 
     * @return Builder实例
     */
    public TimeSeriesDatabaseBuilder disableTransactions() {
        this.config.setEnableTransactions(false);
        return this;
    }
    
    /**
     * 设置并发级别
     * 
     * @param scale 并发级别
     * @return Builder实例
     */
    public TimeSeriesDatabaseBuilder withConcurrencyScale(int scale) {
        if (scale <= 0) {
            throw new IllegalArgumentException("并发级别必须大于0");
        }
        this.config.setConcurrencyScale(scale);
        return this;
    }
    
    /**
     * 构建数据库实例（不支持动态添加数据源）
     * 
     * @return 时序数据库实例
     */
    public TimeSeriesDatabase build() {
        if (config.getDbPath() == null || config.getDbPath().trim().isEmpty()) {
            throw new IllegalStateException("数据库路径不能为空");
        }
        if (dataSources.isEmpty()) {
            throw new IllegalStateException("至少需要添加一个数据源");
        }
        return new TimeSeriesDatabase(config, new HashMap<>(dataSources));
    }
    
    /**
     * 构建数据库实例（支持动态添加数据源）
     * 
     * @return 时序数据库实例
     */
    public TimeSeriesDatabase buildWithDynamicSources() {
        return new TimeSeriesDatabase(config, new HashMap<>(dataSources), this);
    }
    
    /**
     * 打开现有数据库（自动恢复数据源配置）
     * 
     * @param dbPath 数据库文件路径
     * @return 时序数据库实例
     */
    public static TimeSeriesDatabase openExisting(String dbPath) {
        if (dbPath == null || dbPath.trim().isEmpty()) {
            throw new IllegalArgumentException("数据库路径不能为空");
        }
        
        DatabaseConfig config = new DatabaseConfig();
        config.setDbPath(dbPath);
        
        // 使用空的数据源配置，让数据库自动恢复现有数据源
        return new TimeSeriesDatabase(config, new HashMap<>());
    }
    
    /**
     * 打开现有数据库（自动恢复数据源配置，支持动态添加）
     * 
     * @param dbPath 数据库文件路径
     * @return 时序数据库实例
     */
    public static TimeSeriesDatabase openExistingWithDynamicSources(String dbPath) {
        if (dbPath == null || dbPath.trim().isEmpty()) {
            throw new IllegalArgumentException("数据库路径不能为空");
        }
        
        DatabaseConfig config = new DatabaseConfig();
        config.setDbPath(dbPath);
        
        // 使用空的数据源配置，让数据库自动恢复现有数据源
        return new TimeSeriesDatabase(config, new HashMap<>(), null);
    }
    
    /**
     * 添加数据源到配置
     * 
     * @param sourceId 数据源ID
     * @param dataType 数据类型
     * @param description 描述
     */
    private void addDataSource(String sourceId, DataType dataType, String description) {
        if (dataSources.containsKey(sourceId)) {
            throw new IllegalArgumentException("数据源已存在: " + sourceId);
        }
        
        dataSources.put(sourceId, new DataSourceConfig(sourceId, dataType, description));
    }
}