package com.maptsdb;

import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 简化的TimeSeriesDB vs MultiTypeTimeSeriesDB 性能对比测试
 */
public class SimplePerformanceComparison {
    
    private static final int TEST_DATA_POINTS = 5000;
    
    public static void main(String[] args) {
        System.out.println("=== MapDB时序数据库性能对比测试 ===\n");
        
        // 测试1：单线程写入性能对比
        compareSingleThreadWrite();
        
        // 测试2：批量写入性能对比
        compareBatchWrite();
        
        // 测试3：内存使用对比
        compareMemoryUsage();
        
        System.out.println("=== 性能对比总结 ===");
        System.out.println("1. TimeSeriesDB：专为Double类型优化，性能最优");
        System.out.println("2. MultiTypeTimeSeriesDB：支持多种类型，功能更全");
        System.out.println("3. 选择建议：");
        System.out.println("   - 仅需Double数据：使用TimeSeriesDB");
        System.out.println("   - 需要多类型数据：使用MultiTypeTimeSeriesDB");
    }
    
    /**
     * 单线程写入性能对比
     */
    private static void compareSingleThreadWrite() {
        System.out.println("=== 单线程写入性能对比 ===");
        
        // 测试TimeSeriesDB
        long startTime = System.currentTimeMillis();
        TimeSeriesDB tsdb = new TimeSeriesDB("perf_test_tsdb.db");
        
        for (int i = 0; i < TEST_DATA_POINTS; i++) {
            tsdb.put(System.currentTimeMillis() + i, ThreadLocalRandom.current().nextDouble(0, 100));
        }
        
        long tsdbTime = System.currentTimeMillis() - startTime;
        tsdb.close();
        
        // 测试MultiTypeTimeSeriesDB
        startTime = System.currentTimeMillis();
        MultiTypeTimeSeriesDB mtsdb = new MultiTypeTimeSeriesDB("perf_test_mtsdb.db");
        
        for (int i = 0; i < TEST_DATA_POINTS; i++) {
            mtsdb.putDouble(System.currentTimeMillis() + i, ThreadLocalRandom.current().nextDouble(0, 100));
        }
        
        long mtsdbTime = System.currentTimeMillis() - startTime;
        mtsdb.close();
        
        System.out.printf("TimeSeriesDB:     %d ms (%d 数据点/秒)%n", 
            tsdbTime, TEST_DATA_POINTS * 1000 / tsdbTime);
        System.out.printf("MultiTypeTSDB:   %d ms (%d 数据点/秒)%n", 
            mtsdbTime, TEST_DATA_POINTS * 1000 / mtsdbTime);
        System.out.printf("性能差异: %.1f%%%n", 
            (double)(mtsdbTime - tsdbTime) / tsdbTime * 100);
        System.out.println();
    }
    
    /**
     * 批量写入性能对比
     */
    private static void compareBatchWrite() {
        System.out.println("=== 批量写入性能对比 ===");
        
        // 测试TimeSeriesDB批量写入
        long startTime = System.currentTimeMillis();
        TimeSeriesDB tsdb = new TimeSeriesDB("perf_test_tsdb_batch.db");
        
        List<TimeSeriesDB.DataPoint> batchData = new ArrayList<>();
        for (int i = 0; i < TEST_DATA_POINTS; i++) {
            batchData.add(new TimeSeriesDB.DataPoint(
                System.currentTimeMillis() + i, 
                ThreadLocalRandom.current().nextDouble(0, 100)));
        }
        
        tsdb.putBatch(batchData);
        long tsdbTime = System.currentTimeMillis() - startTime;
        tsdb.close();
        
        // 测试MultiTypeTimeSeriesDB单条写入
        startTime = System.currentTimeMillis();
        MultiTypeTimeSeriesDB mtsdb = new MultiTypeTimeSeriesDB("perf_test_mtsdb_batch.db");
        
        for (int i = 0; i < TEST_DATA_POINTS; i++) {
            mtsdb.putDouble(System.currentTimeMillis() + i, 
                ThreadLocalRandom.current().nextDouble(0, 100));
        }
        
        long mtsdbTime = System.currentTimeMillis() - startTime;
        mtsdb.close();
        
        System.out.printf("TimeSeriesDB:     %d ms (%d 数据点/秒)%n", 
            tsdbTime, TEST_DATA_POINTS * 1000 / tsdbTime);
        System.out.printf("MultiTypeTSDB:   %d ms (%d 数据点/秒)%n", 
            mtsdbTime, TEST_DATA_POINTS * 1000 / mtsdbTime);
        System.out.printf("性能差异: %.1f%%%n", 
            (double)(mtsdbTime - tsdbTime) / tsdbTime * 100);
        System.out.println();
    }
    
    /**
     * 内存使用对比
     */
    private static void compareMemoryUsage() {
        System.out.println("=== 内存使用对比 ===");
        
        // 获取初始内存
        Runtime runtime = Runtime.getRuntime();
        runtime.gc();
        long initialMemory = runtime.totalMemory() - runtime.freeMemory();
        
        // 测试TimeSeriesDB内存使用
        TimeSeriesDB tsdb = new TimeSeriesDB("perf_test_tsdb_memory.db");
        for (int i = 0; i < TEST_DATA_POINTS; i++) {
            tsdb.put(System.currentTimeMillis() + i, ThreadLocalRandom.current().nextDouble(0, 100));
        }
        
        runtime.gc();
        long tsdbMemory = runtime.totalMemory() - runtime.freeMemory() - initialMemory;
        tsdb.close();
        
        // 测试MultiTypeTimeSeriesDB内存使用
        runtime.gc();
        long initialMemory2 = runtime.totalMemory() - runtime.freeMemory();
        
        MultiTypeTimeSeriesDB mtsdb = new MultiTypeTimeSeriesDB("perf_test_mtsdb_memory.db");
        for (int i = 0; i < TEST_DATA_POINTS; i++) {
            mtsdb.putDouble(System.currentTimeMillis() + i, ThreadLocalRandom.current().nextDouble(0, 100));
        }
        
        runtime.gc();
        long mtsdbMemory = runtime.totalMemory() - runtime.freeMemory() - initialMemory2;
        mtsdb.close();
        
        System.out.printf("TimeSeriesDB:     %d KB%n", tsdbMemory / 1024);
        System.out.printf("MultiTypeTSDB:   %d KB%n", mtsdbMemory / 1024);
        System.out.printf("内存差异: %.1f%%%n", 
            (double)(mtsdbMemory - tsdbMemory) / tsdbMemory * 100);
        System.out.println();
    }
}
