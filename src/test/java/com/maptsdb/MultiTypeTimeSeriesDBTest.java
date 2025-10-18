package com.maptsdb;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import java.util.List;
import java.util.ArrayList;
import java.io.File;

import static org.junit.jupiter.api.Assertions.*;

/**
 * MultiTypeTimeSeriesDB单元测试
 */
public class MultiTypeTimeSeriesDBTest {
    
    private MultiTypeTimeSeriesDB tsdb;
    private String testDbPath = "test_multitype_timeseries.db";
    
    @BeforeEach
    void setUp() {
        // 清理可能存在的测试数据库文件
        File dbFile = new File(testDbPath);
        if (dbFile.exists()) {
            dbFile.delete();
        }
        
        tsdb = new MultiTypeTimeSeriesDB(testDbPath);
    }
    
    @AfterEach
    void tearDown() {
        if (tsdb != null) {
            tsdb.close();
        }
        
        // 清理测试数据库文件
        File dbFile = new File(testDbPath);
        if (dbFile.exists()) {
            dbFile.delete();
        }
    }
    
    @Test
    @DisplayName("测试Double类型数据存储")
    void testDoubleData() {
        long timestamp = System.currentTimeMillis();
        double value = 25.5;
        
        tsdb.putDouble(timestamp, value);
        
        Double retrievedValue = tsdb.getDouble(timestamp);
        assertNotNull(retrievedValue);
        assertEquals(value, retrievedValue, 0.001);
    }
    
    @Test
    @DisplayName("测试Integer类型数据存储")
    void testIntegerData() {
        long timestamp = System.currentTimeMillis();
        int value = 65;
        
        tsdb.putInteger(timestamp, value);
        
        Integer retrievedValue = tsdb.getInteger(timestamp);
        assertNotNull(retrievedValue);
        assertEquals(value, retrievedValue);
    }
    
    @Test
    @DisplayName("测试String类型数据存储")
    void testStringData() {
        long timestamp = System.currentTimeMillis();
        String value = "ONLINE";
        
        tsdb.putString(timestamp, value);
        
        String retrievedValue = tsdb.getString(timestamp);
        assertNotNull(retrievedValue);
        assertEquals(value, retrievedValue);
    }
    
    @Test
    @DisplayName("测试Boolean类型数据存储")
    void testBooleanData() {
        long timestamp = System.currentTimeMillis();
        boolean value = true;
        
        tsdb.putBoolean(timestamp, value);
        
        Object retrievedValue = tsdb.get(timestamp);
        assertNotNull(retrievedValue);
        assertTrue(retrievedValue instanceof Boolean);
        assertEquals(value, (Boolean) retrievedValue);
    }
    
    @Test
    @DisplayName("测试Long类型数据存储")
    void testLongData() {
        long timestamp = System.currentTimeMillis();
        long value = 1000L;
        
        tsdb.putLong(timestamp, value);
        
        Object retrievedValue = tsdb.get(timestamp);
        assertNotNull(retrievedValue);
        assertTrue(retrievedValue instanceof Long);
        assertEquals(value, (Long) retrievedValue);
    }
    
    @Test
    @DisplayName("测试Float类型数据存储")
    void testFloatData() {
        long timestamp = System.currentTimeMillis();
        float value = 1013.25f;
        
        tsdb.putFloat(timestamp, value);
        
        Object retrievedValue = tsdb.get(timestamp);
        assertNotNull(retrievedValue);
        assertTrue(retrievedValue instanceof Float);
        assertEquals(value, (Float) retrievedValue, 0.001f);
    }
    
    @Test
    @DisplayName("测试按类型查询")
    void testTypeFilteredQuery() {
        long baseTime = System.currentTimeMillis();
        
        // 写入不同类型的数据
        tsdb.putDouble(baseTime + 1000, 25.5);
        tsdb.putInteger(baseTime + 2000, 65);
        tsdb.putString(baseTime + 3000, "ONLINE");
        tsdb.putDouble(baseTime + 4000, 26.8);
        tsdb.putInteger(baseTime + 5000, 70);
        
        // 查询Double类型数据
        List<MultiTypeTimeSeriesDB.TypedDataPoint<Double>> doubleData = 
            tsdb.queryRangeByType(baseTime, baseTime + 10000, Double.class);
        assertEquals(2, doubleData.size());
        
        // 查询Integer类型数据
        List<MultiTypeTimeSeriesDB.TypedDataPoint<Integer>> integerData = 
            tsdb.queryRangeByType(baseTime, baseTime + 10000, Integer.class);
        assertEquals(2, integerData.size());
        
        // 查询String类型数据
        List<MultiTypeTimeSeriesDB.TypedDataPoint<String>> stringData = 
            tsdb.queryRangeByType(baseTime, baseTime + 10000, String.class);
        assertEquals(1, stringData.size());
    }
    
    @Test
    @DisplayName("测试混合数据类型查询")
    void testMixedDataQuery() {
        long baseTime = System.currentTimeMillis();
        
        // 写入混合类型数据
        tsdb.putDouble(baseTime + 1000, 25.5);
        tsdb.putInteger(baseTime + 2000, 65);
        tsdb.putString(baseTime + 3000, "ONLINE");
        
        // 查询所有数据
        var allData = tsdb.queryRange(baseTime, baseTime + 10000);
        assertEquals(3, allData.size());
        
        // 验证数据类型
        assertTrue(allData.get(baseTime + 1000) instanceof Double);
        assertTrue(allData.get(baseTime + 2000) instanceof Integer);
        assertTrue(allData.get(baseTime + 3000) instanceof String);
    }
    
    @Test
    @DisplayName("测试数据持久化")
    void testDataPersistence() {
        long timestamp = System.currentTimeMillis();
        String value = "PERSISTENT";
        
        // 写入数据
        tsdb.putString(timestamp, value);
        tsdb.close();
        
        // 重新打开数据库
        MultiTypeTimeSeriesDB newTsdb = new MultiTypeTimeSeriesDB(testDbPath);
        
        // 验证数据仍然存在
        String retrievedValue = newTsdb.getString(timestamp);
        assertNotNull(retrievedValue);
        assertEquals(value, retrievedValue);
        
        newTsdb.close();
    }
    
    @Test
    @DisplayName("测试通用数据访问")
    void testGenericDataAccess() {
        long timestamp = System.currentTimeMillis();
        String value = "GENERIC";
        
        // 使用通用put方法
        tsdb.put(timestamp, value);
        
        // 使用通用get方法
        Object retrievedValue = tsdb.get(timestamp);
        assertNotNull(retrievedValue);
        assertTrue(retrievedValue instanceof String);
        assertEquals(value, retrievedValue);
    }
}
