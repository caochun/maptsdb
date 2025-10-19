package com.maptsdb;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * NumericTimeSeriesDb 测试类
 */
public class NumericTimeSeriesDbTest {
    
    @TempDir
    Path tempDir;
    
    private NumericTimeSeriesDb numericDb;
    private ObjectTimeSeriesDb objectDb;
    private String dbPath;
    
    @BeforeEach
    void setUp() {
        dbPath = tempDir.resolve("test.db").toString();
        numericDb = new NumericTimeSeriesDb(dbPath);
        objectDb = new ObjectTimeSeriesDb(dbPath + "_object");
    }
    
    @AfterEach
    void tearDown() {
        if (numericDb != null) {
            numericDb.close();
        }
        if (objectDb != null) {
            objectDb.close();
        }
    }
    
    @Test
    void testDoubleOperations() {
        // 测试Double类型操作
        numericDb.createDoubleSource("test_double");
        
        long timestamp = System.currentTimeMillis();
        double value = 3.14159;
        
        numericDb.putDouble("test_double", timestamp, value);
        Double result = numericDb.getDouble("test_double", timestamp);
        
        assertNotNull(result);
        assertEquals(value, result, 0.0001);
    }
    
    @Test
    void testIntegerOperations() {
        // 测试Integer类型操作
        numericDb.createIntegerSource("test_integer");
        
        long timestamp = System.currentTimeMillis();
        int value = 42;
        
        numericDb.putInteger("test_integer", timestamp, value);
        Integer result = numericDb.getInteger("test_integer", timestamp);
        
        assertNotNull(result);
        assertEquals(value, result.intValue());
    }
    
    @Test
    void testLongOperations() {
        // 测试Long类型操作
        numericDb.createLongSource("test_long");
        
        long timestamp = System.currentTimeMillis();
        long value = 123456789L;
        
        numericDb.putLong("test_long", timestamp, value);
        Long result = numericDb.getLong("test_long", timestamp);
        
        assertNotNull(result);
        assertEquals(value, result.longValue());
    }
    
    @Test
    void testFloatOperations() {
        // 测试Float类型操作
        numericDb.createFloatSource("test_float");
        
        long timestamp = System.currentTimeMillis();
        float value = 2.718f;
        
        numericDb.putFloat("test_float", timestamp, value);
        Float result = numericDb.getFloat("test_float", timestamp);
        
        assertNotNull(result);
        assertEquals(value, result, 0.001f);
    }
    
    @Test
    void testBatchOperations() {
        // 测试批量操作
        numericDb.createDoubleSource("test_batch");
        
        List<DataPoint<Double>> dataPoints = new ArrayList<>();
        Random random = new Random();
        long baseTime = System.currentTimeMillis();
        
        for (int i = 0; i < 1000; i++) {
            dataPoints.add(new DataPoint<>(baseTime + i, random.nextDouble()));
        }
        
        numericDb.putDoubleBatch("test_batch", dataPoints);
        
        // 验证数据
        Map<Long, Double> range = numericDb.queryDoubleRange("test_batch", baseTime, baseTime + 999);
        assertEquals(1000, range.size());
    }
    
    @Test
    void testDataSourceManagement() {
        // 测试数据源管理
        numericDb.createDoubleSource("source1");
        numericDb.createIntegerSource("source2");
        numericDb.createLongSource("source3");
        numericDb.createFloatSource("source4");
        
        Set<String> sources = numericDb.getDataSources();
        assertEquals(4, sources.size());
        assertTrue(sources.contains("source1"));
        assertTrue(sources.contains("source2"));
        assertTrue(sources.contains("source3"));
        assertTrue(sources.contains("source4"));
        
        Map<String, Long> stats = numericDb.getDataSourcesStats();
        assertEquals(4, stats.size());
    }
    
    @Test
    void testPerformanceComparison() {
        System.out.println("\n=== 性能对比测试 ===");
        
        int dataCount = 100000;
        Random random = new Random();
        long baseTime = System.currentTimeMillis();
        
        // 准备测试数据
        List<DataPoint<Double>> numericData = new ArrayList<>();
        List<ObjectTimeSeriesDb.DataPoint> objectData = new ArrayList<>();
        
        for (int i = 0; i < dataCount; i++) {
            double value = random.nextDouble();
            long timestamp = baseTime + i;
            
            numericData.add(new DataPoint<>(timestamp, value));
            objectData.add(new ObjectTimeSeriesDb.DataPoint(timestamp, value));
        }
        
        // 测试NumericTimeSeriesDb性能
        System.out.println("测试 NumericTimeSeriesDb...");
        numericDb.createDoubleSource("perf_test");
        
        long startTime = System.currentTimeMillis();
        numericDb.putDoubleBatch("perf_test", numericData);
        long numericTime = System.currentTimeMillis() - startTime;
        
        // 测试ObjectTimeSeriesDb性能
        System.out.println("测试 ObjectTimeSeriesDb...");
        objectDb.createDataSource("perf_test");
        
        startTime = System.currentTimeMillis();
        objectDb.putBatch("perf_test", objectData);
        long objectTime = System.currentTimeMillis() - startTime;
        
        // 输出结果
        System.out.println("\n=== 性能测试结果 ===");
        System.out.println("数据量: " + dataCount + " 条");
        System.out.println("NumericTimeSeriesDb 写入时间: " + numericTime + " ms");
        System.out.println("ObjectTimeSeriesDb 写入时间: " + objectTime + " ms");
        
        double numericRate = (double) dataCount / numericTime * 1000;
        double objectRate = (double) dataCount / objectTime * 1000;
        
        System.out.println("NumericTimeSeriesDb 写入速率: " + String.format("%.0f", numericRate) + " 条/秒");
        System.out.println("ObjectTimeSeriesDb 写入速率: " + String.format("%.0f", objectRate) + " 条/秒");
        
        double improvement = (double) objectTime / numericTime;
        System.out.println("性能提升: " + String.format("%.2f", improvement) + " 倍");
        
        // 验证数据一致性
        Map<Long, Double> numericResult = numericDb.queryDoubleRange("perf_test", baseTime, baseTime + dataCount - 1);
        Map<Long, Object> objectResult = objectDb.queryRange("perf_test", baseTime, baseTime + dataCount - 1);
        
        assertEquals(dataCount, numericResult.size());
        assertEquals(dataCount, objectResult.size());
        
        System.out.println("数据验证: 通过");
        System.out.println("==================\n");
    }
    
    @Test
    void testMemoryUsage() {
        System.out.println("\n=== 内存使用对比 ===");
        
        int dataCount = 50000;
        Random random = new Random();
        long baseTime = System.currentTimeMillis();
        
        // 准备测试数据
        List<DataPoint<Double>> numericTestData = new ArrayList<>();
        List<ObjectTimeSeriesDb.DataPoint> objectTestData = new ArrayList<>();
        for (int i = 0; i < dataCount; i++) {
            double value = random.nextDouble();
            numericTestData.add(new DataPoint<>(baseTime + i, value));
            objectTestData.add(new ObjectTimeSeriesDb.DataPoint(baseTime + i, value));
        }
        
        // 测试NumericTimeSeriesDb内存使用
        numericDb.createDoubleSource("memory_test");
        numericDb.putDoubleBatch("memory_test", numericTestData);
        long numericSize = numericDb.getStorageSize();
        
        // 测试ObjectTimeSeriesDb内存使用
        objectDb.createDataSource("memory_test");
        objectDb.putBatch("memory_test", objectTestData);
        long objectSize = objectDb.getStorageSize();
        
        System.out.println("数据量: " + dataCount + " 条");
        System.out.println("NumericTimeSeriesDb 存储大小: " + numericSize + " 字节");
        System.out.println("ObjectTimeSeriesDb 存储大小: " + objectSize + " 字节");
        
        double sizeRatio = (double) objectSize / numericSize;
        System.out.println("存储大小比例: " + String.format("%.2f", sizeRatio) + " (Object/Numeric)");
        System.out.println("==================\n");
    }
}
