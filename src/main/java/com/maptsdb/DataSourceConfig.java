package com.maptsdb;

/**
 * 数据源配置类
 * 
 * <p>封装了数据源的基本信息，包括ID、数据类型和描述。</p>
 * 
 * @author MapTSDB Team
 * @version 1.0.0
 * @since 1.0.0
 */
public class DataSourceConfig {
    private final String sourceId;
    private final DataType dataType;
    private final String description;
    
    /**
     * 创建数据源配置
     * 
     * @param sourceId 数据源ID
     * @param dataType 数据类型
     * @param description 数据源描述
     */
    public DataSourceConfig(String sourceId, DataType dataType, String description) {
        this.sourceId = sourceId;
        this.dataType = dataType;
        this.description = description;
    }
    
    /**
     * 获取数据源ID
     * 
     * @return 数据源ID
     */
    public String getSourceId() {
        return sourceId;
    }
    
    /**
     * 获取数据类型
     * 
     * @return 数据类型
     */
    public DataType getDataType() {
        return dataType;
    }
    
    /**
     * 获取数据源描述
     * 
     * @return 数据源描述
     */
    public String getDescription() {
        return description;
    }
    
    @Override
    public String toString() {
        return String.format("DataSourceConfig{id='%s', type=%s, description='%s'}", 
            sourceId, dataType, description);
    }
}
