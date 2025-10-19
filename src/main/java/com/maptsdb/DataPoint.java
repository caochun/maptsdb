package com.maptsdb;

/**
 * 数据点类
 * 
 * @param <T> 数据类型
 */
public class DataPoint<T> {
    private final long timestamp;
    private final T value;
    
    public DataPoint(long timestamp, T value) {
        this.timestamp = timestamp;
        this.value = value;
    }
    
    public long getTimestamp() {
        return timestamp;
    }
    
    public T getValue() {
        return value;
    }
    
    @Override
    public String toString() {
        return "DataPoint{" +
                "timestamp=" + timestamp +
                ", value=" + value +
                '}';
    }
}
