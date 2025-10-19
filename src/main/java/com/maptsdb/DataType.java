package com.maptsdb;

/**
 * 时序数据库支持的数据类型枚举
 * 
 * <p>定义了时序数据库支持的所有数据类型，用于类型安全和序列化器选择。</p>
 * 
 * @author MapTSDB Team
 * @version 1.0.0
 * @since 1.0.0
 */
public enum DataType {
    /** 双精度浮点数类型 */
    DOUBLE,
    
    /** 整数类型 */
    INTEGER,
    
    /** 长整数类型 */
    LONG,
    
    /** 单精度浮点数类型 */
    FLOAT,
    
    /** 对象类型（通用） */
    OBJECT
}
