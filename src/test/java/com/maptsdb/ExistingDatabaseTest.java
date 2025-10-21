package com.maptsdb;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.util.Map;
import java.util.Set;

/**
 * 现有数据库打开功能测试
 */
public class ExistingDatabaseTest {
    
    private static final String TEST_DB_PATH = "test_existing.db";
    
    @BeforeEach
    void setUp() {
        // 清理测试文件
        File dbFile = new File(TEST_DB_PATH);
        if (dbFile.exists()) {
            dbFile.delete();
        }
    }
    
    @AfterEach
    void tearDown() {
        // 清理测试文件
        File dbFile = new File(TEST_DB_PATH);
        if (dbFile.exists()) {
            dbFile.delete();
        }
    }
    
    @Test
    void testOpenExistingDatabase() {
        // 1. 创建数据库并写入数据
        TimeSeriesDatabase db1 = TimeSeriesDatabaseBuilder.builder()
            .path(TEST_DB_PATH)
            .addDoubleSource("temperature", "温度数据")
            .addIntegerSource("humidity", "湿度数据")
            .addObjectSource("status", "状态数据")
            .buildWithDynamicSources();
        
        // 写入测试数据
        long timestamp = System.currentTimeMillis();
        db1.putDouble("temperature", timestamp, 25.6);
        db1.putInteger("humidity", timestamp, 65);
        db1.putStringToObject("status", timestamp, "正常");
        db1.commit();
        db1.close();
        
        // 2. 重新打开现有数据库
        TimeSeriesDatabase db2 = TimeSeriesDatabaseBuilder.openExisting(TEST_DB_PATH);
        
        // 3. 验证数据源是否被正确恢复
        Set<String> dataSources = db2.getDataSourceIds();
        assertTrue(dataSources.contains("temperature"));
        assertTrue(dataSources.contains("humidity"));
        assertTrue(dataSources.contains("status"));
        
        // 4. 验证数据是否可访问
        Double temp = db2.getDouble("temperature", timestamp);
        Integer humidity = db2.getInteger("humidity", timestamp);
        String status = db2.getStringFromObject("status", timestamp);
        
        assertEquals(25.6, temp, 0.001);
        assertEquals(65, humidity);
        assertEquals("正常", status);
        
        // 5. 验证可以继续写入新数据
        long newTimestamp = timestamp + 1000;
        db2.putDouble("temperature", newTimestamp, 26.1);
        db2.putInteger("humidity", newTimestamp, 68);
        db2.commit();
        
        // 验证新数据
        Double newTemp = db2.getDouble("temperature", newTimestamp);
        Integer newHumidity = db2.getInteger("humidity", newTimestamp);
        
        assertEquals(26.1, newTemp, 0.001);
        assertEquals(68, newHumidity);
        
        db2.close();
    }
    
    @Test
    void testOpenExistingDatabaseWithDynamicSources() {
        // 1. 创建数据库并写入数据
        TimeSeriesDatabase db1 = TimeSeriesDatabaseBuilder.builder()
            .path(TEST_DB_PATH)
            .addDoubleSource("temperature", "温度数据")
            .buildWithDynamicSources();
        
        long timestamp = System.currentTimeMillis();
        db1.putDouble("temperature", timestamp, 25.6);
        db1.commit();
        db1.close();
        
        // 2. 重新打开现有数据库（支持动态添加）
        TimeSeriesDatabase db2 = TimeSeriesDatabaseBuilder.openExistingWithDynamicSources(TEST_DB_PATH);
        
        // 3. 验证现有数据源
        assertTrue(db2.getDataSourceIds().contains("temperature"));
        assertEquals(25.6, db2.getDouble("temperature", timestamp), 0.001);
        
        // 4. 验证可以动态添加新数据源
        db2.addIntegerSource("humidity", "湿度数据");
        db2.putInteger("humidity", timestamp, 65);
        db2.commit();
        
        // 5. 验证新数据源
        assertTrue(db2.getDataSourceIds().contains("humidity"));
        assertEquals(65, db2.getInteger("humidity", timestamp));
        
        db2.close();
    }
    
    @Test
    void testOpenNonExistentDatabase() {
        // 尝试打开不存在的数据库会创建一个新的空数据库
        // MapDB会自动创建不存在的数据库文件
        TimeSeriesDatabase db = TimeSeriesDatabaseBuilder.openExisting("non_existent.db");
        assertNotNull(db);
        assertTrue(db.getDataSourceIds().isEmpty());
        db.close();
        
        // 清理测试文件
        File dbFile = new File("non_existent.db");
        if (dbFile.exists()) {
            dbFile.delete();
        }
    }
    
    @Test
    void testOpenExistingDatabaseWithEmptyPath() {
        // 空路径应该抛出异常
        assertThrows(IllegalArgumentException.class, () -> {
            TimeSeriesDatabaseBuilder.openExisting("");
        });
        
        assertThrows(IllegalArgumentException.class, () -> {
            TimeSeriesDatabaseBuilder.openExisting(null);
        });
    }
    
    @Test
    void testDataTypeInference() {
        // 1. 创建数据库并写入不同类型的数据
        TimeSeriesDatabase db1 = TimeSeriesDatabaseBuilder.builder()
            .path(TEST_DB_PATH)
            .addDoubleSource("double_data")
            .addIntegerSource("int_data")
            .addLongSource("long_data")
            .addFloatSource("float_data")
            .addObjectSource("object_data")
            .buildWithDynamicSources();
        
        long timestamp = System.currentTimeMillis();
        db1.putDouble("double_data", timestamp, 25.6);
        db1.putInteger("int_data", timestamp, 65);
        db1.putLong("long_data", timestamp, 123456789L);
        db1.putFloat("float_data", timestamp, 3.14f);
        db1.putStringToObject("object_data", timestamp, "测试字符串");
        db1.commit();
        db1.close();
        
        // 2. 重新打开数据库
        TimeSeriesDatabase db2 = TimeSeriesDatabaseBuilder.openExisting(TEST_DB_PATH);
        
        // 3. 验证数据类型推断
        Map<String, DataSourceConfig> dataSourceInfo = new java.util.HashMap<>();
        for (String sourceId : db2.getDataSourceIds()) {
            dataSourceInfo.put(sourceId, db2.getDataSourceInfo(sourceId));
        }
        
        assertEquals(DataType.DOUBLE, dataSourceInfo.get("double_data").getDataType());
        assertEquals(DataType.INTEGER, dataSourceInfo.get("int_data").getDataType());
        assertEquals(DataType.LONG, dataSourceInfo.get("long_data").getDataType());
        assertEquals(DataType.FLOAT, dataSourceInfo.get("float_data").getDataType());
        assertEquals(DataType.OBJECT, dataSourceInfo.get("object_data").getDataType());
        
        db2.close();
    }
}
