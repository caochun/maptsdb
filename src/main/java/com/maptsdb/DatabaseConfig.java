package com.maptsdb;

/**
 * 数据库配置类
 * 
 * <p>封装了数据库的所有配置选项，包括路径、性能优化选项和数据清理策略。</p>
 * 
 * @author MapTSDB Team
 * @version 1.0.0
 * @since 1.0.0
 */
public class DatabaseConfig {
    private String dbPath;
    private boolean enableMemoryMapping = true;
    private boolean enableTransactions = true;
    private int concurrencyScale = 16;
    private long retentionDays = 30;
    private long cleanupIntervalHours = 1;
    private boolean enableCleaner = true;
    private boolean disablePreclear = true;
    
    /**
     * 获取数据库文件路径
     * 
     * @return 数据库文件路径
     */
    public String getDbPath() {
        return dbPath;
    }
    
    /**
     * 设置数据库文件路径
     * 
     * @param dbPath 数据库文件路径
     */
    public void setDbPath(String dbPath) {
        this.dbPath = dbPath;
    }
    
    /**
     * 是否启用内存映射
     * 
     * @return 是否启用内存映射
     */
    public boolean isEnableMemoryMapping() {
        return enableMemoryMapping;
    }
    
    /**
     * 设置是否启用内存映射
     * 
     * @param enableMemoryMapping 是否启用内存映射
     */
    public void setEnableMemoryMapping(boolean enableMemoryMapping) {
        this.enableMemoryMapping = enableMemoryMapping;
    }
    
    /**
     * 是否启用事务
     * 
     * @return 是否启用事务
     */
    public boolean isEnableTransactions() {
        return enableTransactions;
    }
    
    /**
     * 设置是否启用事务
     * 
     * @param enableTransactions 是否启用事务
     */
    public void setEnableTransactions(boolean enableTransactions) {
        this.enableTransactions = enableTransactions;
    }
    
    /**
     * 获取并发级别
     * 
     * @return 并发级别
     */
    public int getConcurrencyScale() {
        return concurrencyScale;
    }
    
    /**
     * 设置并发级别
     * 
     * @param concurrencyScale 并发级别
     */
    public void setConcurrencyScale(int concurrencyScale) {
        this.concurrencyScale = concurrencyScale;
    }
    
    /**
     * 获取数据保留天数
     * 
     * @return 数据保留天数
     */
    public long getRetentionDays() {
        return retentionDays;
    }
    
    /**
     * 设置数据保留天数
     * 
     * @param retentionDays 数据保留天数
     */
    public void setRetentionDays(long retentionDays) {
        this.retentionDays = retentionDays;
    }
    
    /**
     * 获取清理间隔小时数
     * 
     * @return 清理间隔小时数
     */
    public long getCleanupIntervalHours() {
        return cleanupIntervalHours;
    }
    
    /**
     * 设置清理间隔小时数
     * 
     * @param cleanupIntervalHours 清理间隔小时数
     */
    public void setCleanupIntervalHours(long cleanupIntervalHours) {
        this.cleanupIntervalHours = cleanupIntervalHours;
    }
    
    /**
     * 是否启用清理器
     * 
     * @return 是否启用清理器
     */
    public boolean isEnableCleaner() {
        return enableCleaner;
    }
    
    /**
     * 设置是否启用清理器
     * 
     * @param enableCleaner 是否启用清理器
     */
    public void setEnableCleaner(boolean enableCleaner) {
        this.enableCleaner = enableCleaner;
    }
    
    /**
     * 是否禁用预清理
     * 
     * @return 是否禁用预清理
     */
    public boolean isDisablePreclear() {
        return disablePreclear;
    }
    
    /**
     * 设置是否禁用预清理
     * 
     * @param disablePreclear 是否禁用预清理
     */
    public void setDisablePreclear(boolean disablePreclear) {
        this.disablePreclear = disablePreclear;
    }
    
    @Override
    public String toString() {
        return String.format("DatabaseConfig{path='%s', memoryMapping=%s, transactions=%s, concurrency=%d, retention=%d days}", 
            dbPath, enableMemoryMapping, enableTransactions, concurrencyScale, retentionDays);
    }
}
