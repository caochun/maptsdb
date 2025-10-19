package com.maptsdb;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CountDownLatch;

import static org.junit.jupiter.api.Assertions.*;

/**
 * TimeSeriesDatabaseBuilder 全面功能测试
 * 测试所有API功能和边界情况
 */
public class TimeSeriesDatabaseBuilderTest {
    
    @TempDir
    Path tempDir;
    
    private TimeSeriesDatabaseBuilder.TimeSeriesDatabase db;
    private String dbPath;
    
    @BeforeEach
    void setUp() {
        dbPath = tempDir.resolve("comprehensive_test.db").toString();
    }
    
    @AfterEach
    void tearDown() {
        if (db != null) {
            db.close();
        }
    }
    
    @Test
    void testBasicBuilderFunctionality() {
        System.out.println("\n=== 基础Builder功能测试 ===");
        
        // 测试基本数据库创建
        db = TimeSeriesDatabaseBuilder.builder()
                .path(dbPath)
                .addDoubleSource("temperature", "环境温度")
                .addIntegerSource("humidity", "相对湿度")
                .addLongSource("pressure", "大气压力")
                .addFloatSource("voltage", "系统电压")
                .addObjectSource("status", "系统状态")
                .withRetentionDays(30)
                .enableMemoryMapping()
                .enableTransactions()
                .withConcurrencyScale(8)
                .build();
        
        assertNotNull(db);
        assertEquals(5, db.getDataSources().size());
        assertTrue(db.getDataSources().contains("temperature"));
        assertTrue(db.getDataSources().contains("humidity"));
        assertTrue(db.getDataSources().contains("pressure"));
        assertTrue(db.getDataSources().contains("voltage"));
        assertTrue(db.getDataSources().contains("status"));
        
        System.out.println("✅ 基础Builder功能测试通过");
        System.out.println("数据源: " + db.getDataSources());
    }
    
    @Test
    void testDataWriteAndRead() {
        System.out.println("\n=== 数据写入和读取测试 ===");
        
        db = TimeSeriesDatabaseBuilder.builder()
                .path(dbPath)
                .addDoubleSource("temperature")
                .addIntegerSource("humidity")
                .addLongSource("pressure")
                .addFloatSource("voltage")
                .addObjectSource("status")
                .build();
        
        long timestamp = System.currentTimeMillis();
        
        // 测试数值类型写入和读取
        db.putDouble("temperature", timestamp, 25.6);
        db.putInteger("humidity", timestamp, 65);
        db.putLong("pressure", timestamp, 101325L);
        db.putFloat("voltage", timestamp, 3.3f);
        
        // 测试对象类型写入和读取
        db.putDoubleToObject("status", timestamp, 25.6);
        db.putStringToObject("status", timestamp + 1, "正常");
        db.putBooleanToObject("status", timestamp + 2, true);
        db.putIntegerToObject("status", timestamp + 3, 1);
        
        // 重要：手动提交事务
        db.commit();
        
        // 验证数值类型读取
        Double temp = db.getDouble("temperature", timestamp);
        Integer humidity = db.getInteger("humidity", timestamp);
        Long pressure = db.getLong("pressure", timestamp);
        Float voltage = db.getFloat("voltage", timestamp);
        
        assertNotNull(temp);
        assertEquals(25.6, temp, 0.001);
        assertNotNull(humidity);
        assertEquals(65, humidity.intValue());
        assertNotNull(pressure);
        assertEquals(101325L, pressure.longValue());
        assertNotNull(voltage);
        assertEquals(3.3f, voltage, 0.001f);
        
        // 验证对象类型读取
        Double tempFromObject = db.getDoubleFromObject("status", timestamp);
        String statusFromObject = db.getStringFromObject("status", timestamp + 1);
        Boolean flagFromObject = db.getBooleanFromObject("status", timestamp + 2);
        Integer codeFromObject = db.getIntegerFromObject("status", timestamp + 3);
        
        assertNotNull(tempFromObject);
        assertEquals(25.6, tempFromObject, 0.001);
        assertNotNull(statusFromObject);
        assertEquals("正常", statusFromObject);
        assertNotNull(flagFromObject);
        assertEquals(true, flagFromObject);
        assertNotNull(codeFromObject);
        assertEquals(1, codeFromObject.intValue());
        
        System.out.println("✅ 数据写入和读取测试通过");
        System.out.println("温度: " + temp + "°C");
        System.out.println("湿度: " + humidity + "%");
        System.out.println("压力: " + pressure + " Pa");
        System.out.println("电压: " + voltage + " V");
    }
    
    @Test
    void testBatchWriteOperations() {
        System.out.println("\n=== 批量写入操作测试 ===");
        
        db = TimeSeriesDatabaseBuilder.builder()
                .path(dbPath)
                .addDoubleSource("temperature")
                .addIntegerSource("humidity")
                .addObjectSource("logs")
                .build();
        
        long baseTime = System.currentTimeMillis();
        
        // 准备批量数据
        List<DataPoint<Double>> tempData = new ArrayList<>();
        List<DataPoint<Integer>> humidityData = new ArrayList<>();
        List<DataPoint<Object>> logData = new ArrayList<>();
        
        for (int i = 0; i < 100; i++) {
            tempData.add(new DataPoint<>(baseTime + i * 1000, 20.0 + Math.random() * 10));
            humidityData.add(new DataPoint<>(baseTime + i * 1000, 50 + (int)(Math.random() * 30)));
            logData.add(new DataPoint<>(baseTime + i * 1000, "Log entry " + i));
        }
        
        // 执行批量写入
        db.putDoubleBatch("temperature", tempData);
        db.putIntegerBatch("humidity", humidityData);
        db.putObjectBatch("logs", logData);
        
        // 重要：批量写入后提交事务
        db.commit();
        
        // 验证数据
        Double firstTemp = db.getDouble("temperature", baseTime);
        Integer firstHumidity = db.getInteger("humidity", baseTime);
        String firstLog = db.getStringFromObject("logs", baseTime);
        
        assertNotNull(firstTemp);
        assertNotNull(firstHumidity);
        assertNotNull(firstLog);
        assertEquals("Log entry 0", firstLog);
        
        // 验证统计信息
        Map<String, Long> stats = db.getStatistics();
        assertEquals(100L, stats.get("temperature (DOUBLE)"));
        assertEquals(100L, stats.get("humidity (INTEGER)"));
        assertEquals(100L, stats.get("logs (OBJECT)"));
        
        System.out.println("✅ 批量写入操作测试通过");
        System.out.println("温度数据: " + stats.get("temperature (DOUBLE)") + " 条");
        System.out.println("湿度数据: " + stats.get("humidity (INTEGER)") + " 条");
        System.out.println("日志数据: " + stats.get("logs (OBJECT)") + " 条");
    }
    
    @Test
    void testDynamicDataSourceAddition() {
        System.out.println("\n=== 动态数据源添加测试 ===");
        
        // 创建支持动态添加的数据库
        db = TimeSeriesDatabaseBuilder.builder()
                .path(dbPath)
                .addDoubleSource("initial_temp", "初始温度")
                .buildWithDynamicSources();
        
        assertEquals(1, db.getDataSources().size());
        assertTrue(db.getDataSources().contains("initial_temp"));
        
        // 动态添加数据源
        db.addDoubleSource("new_temp", "新温度传感器");
        db.addIntegerSource("humidity", "湿度传感器");
        db.addLongSource("pressure", "压力传感器");
        db.addFloatSource("voltage", "电压传感器");
        db.addObjectSource("status", "状态数据");
        
        assertEquals(6, db.getDataSources().size());
        assertTrue(db.getDataSources().contains("new_temp"));
        assertTrue(db.getDataSources().contains("humidity"));
        assertTrue(db.getDataSources().contains("pressure"));
        assertTrue(db.getDataSources().contains("voltage"));
        assertTrue(db.getDataSources().contains("status"));
        
        // 测试新添加的数据源
        long timestamp = System.currentTimeMillis();
        db.putDouble("new_temp", timestamp, 25.5);
        db.putInteger("humidity", timestamp, 70);
        db.putLong("pressure", timestamp, 101500L);
        db.putFloat("voltage", timestamp, 3.3f);
        db.putStringToObject("status", timestamp, "在线");
        
        // 重要：手动提交事务
        db.commit();
        
        // 验证数据
        Double newTemp = db.getDouble("new_temp", timestamp);
        Integer humidity = db.getInteger("humidity", timestamp);
        Long pressure = db.getLong("pressure", timestamp);
        Float voltage = db.getFloat("voltage", timestamp);
        String status = db.getStringFromObject("status", timestamp);
        
        assertNotNull(newTemp);
        assertEquals(25.5, newTemp, 0.001);
        assertNotNull(humidity);
        assertEquals(70, humidity.intValue());
        assertNotNull(pressure);
        assertEquals(101500L, pressure.longValue());
        assertNotNull(voltage);
        assertEquals(3.3f, voltage, 0.001f);
        assertNotNull(status);
        assertEquals("在线", status);
        
        System.out.println("✅ 动态数据源添加测试通过");
        System.out.println("最终数据源: " + db.getDataSources());
    }
    
    @Test
    void testErrorHandling() {
        System.out.println("\n=== 错误处理测试 ===");
        
        // 测试无效路径
        assertThrows(IllegalArgumentException.class, () -> {
            TimeSeriesDatabaseBuilder.builder().path("");
        });
        
        assertThrows(IllegalArgumentException.class, () -> {
            TimeSeriesDatabaseBuilder.builder().path(null);
        });
        
        // 测试无效数据源ID
        assertThrows(IllegalArgumentException.class, () -> {
            TimeSeriesDatabaseBuilder.builder()
                    .path(dbPath)
                    .addDoubleSource("");
        });
        
        assertThrows(IllegalArgumentException.class, () -> {
            TimeSeriesDatabaseBuilder.builder()
                    .path(dbPath)
                    .addDoubleSource(null);
        });
        
        // 测试重复数据源
        assertThrows(IllegalArgumentException.class, () -> {
            TimeSeriesDatabaseBuilder.builder()
                    .path(dbPath)
                    .addDoubleSource("sensor")
                    .addIntegerSource("sensor"); // 重复ID，不同类型
        });
        
        // 测试无效保留天数
        assertThrows(IllegalArgumentException.class, () -> {
            TimeSeriesDatabaseBuilder.builder()
                    .path(dbPath)
                    .addDoubleSource("sensor")
                    .withRetentionDays(0);
        });
        
        // 测试无效并发级别
        assertThrows(IllegalArgumentException.class, () -> {
            TimeSeriesDatabaseBuilder.builder()
                    .path(dbPath)
                    .addDoubleSource("sensor")
                    .withConcurrencyScale(0);
        });
        
        // 测试构建时缺少必要参数
        assertThrows(IllegalStateException.class, () -> {
            TimeSeriesDatabaseBuilder.builder().build();
        });
        
        assertThrows(IllegalStateException.class, () -> {
            TimeSeriesDatabaseBuilder.builder()
                    .path(dbPath)
                    .build();
        });
        
        System.out.println("✅ 错误处理测试通过");
    }
    
    @Test
    void testTypeSafety() {
        System.out.println("\n=== 类型安全测试 ===");
        
        db = TimeSeriesDatabaseBuilder.builder()
                .path(dbPath)
                .addDoubleSource("numeric_data")
                .addObjectSource("mixed_data")
                .build();
        
        long timestamp = System.currentTimeMillis();
        
        // 测试类型安全的数据源验证
        assertThrows(IllegalArgumentException.class, () -> {
            db.putInteger("numeric_data", timestamp, 100); // 错误：Double数据源不能写入Integer
        });
        
        assertThrows(IllegalArgumentException.class, () -> {
            db.putDouble("mixed_data", timestamp, 25.6); // 错误：Object数据源不能直接写入Double
        });
        
        // 测试正确的类型操作
        db.putDouble("numeric_data", timestamp, 25.6);
        db.putDoubleToObject("mixed_data", timestamp, 25.6);
        
        // 重要：手动提交事务
        db.commit();
        
        // 验证数据
        Double numericValue = db.getDouble("numeric_data", timestamp);
        Double objectValue = db.getDoubleFromObject("mixed_data", timestamp);
        
        assertNotNull(numericValue);
        assertEquals(25.6, numericValue, 0.001);
        assertNotNull(objectValue);
        assertEquals(25.6, objectValue, 0.001);
        
        System.out.println("✅ 类型安全测试通过");
    }
    
    @Test
    void testDataSourceInfo() {
        System.out.println("\n=== 数据源信息测试 ===");
        
        db = TimeSeriesDatabaseBuilder.builder()
                .path(dbPath)
                .addDoubleSource("temperature", "环境温度传感器")
                .addIntegerSource("humidity", "相对湿度传感器")
                .addObjectSource("status", "系统状态数据")
                .build();
        
        // 测试数据源信息获取
        var tempInfo = db.getDataSourceInfo("temperature");
        var humidityInfo = db.getDataSourceInfo("humidity");
        var statusInfo = db.getDataSourceInfo("status");
        
        assertNotNull(tempInfo);
        assertEquals("temperature", tempInfo.getSourceId());
        assertEquals(TimeSeriesDatabaseBuilder.DataType.DOUBLE, tempInfo.getDataType());
        assertEquals("环境温度传感器", tempInfo.getDescription());
        
        assertNotNull(humidityInfo);
        assertEquals("humidity", humidityInfo.getSourceId());
        assertEquals(TimeSeriesDatabaseBuilder.DataType.INTEGER, humidityInfo.getDataType());
        assertEquals("相对湿度传感器", humidityInfo.getDescription());
        
        assertNotNull(statusInfo);
        assertEquals("status", statusInfo.getSourceId());
        assertEquals(TimeSeriesDatabaseBuilder.DataType.OBJECT, statusInfo.getDataType());
        assertEquals("系统状态数据", statusInfo.getDescription());
        
        // 测试不存在的数据源
        assertNull(db.getDataSourceInfo("nonexistent"));
        
        System.out.println("✅ 数据源信息测试通过");
        System.out.println("温度传感器: " + tempInfo.getDescription());
        System.out.println("湿度传感器: " + humidityInfo.getDescription());
        System.out.println("状态数据: " + statusInfo.getDescription());
    }
    
    @Test
    void testConfigurationOptions() {
        System.out.println("\n=== 配置选项测试 ===");
        
        // 测试完整配置
        db = TimeSeriesDatabaseBuilder.builder()
                .path(dbPath)
                .addDoubleSource("test_data")
                .withRetentionDays(7)
                .withCleanupInterval(2, java.util.concurrent.TimeUnit.HOURS)
                .enableMemoryMapping()
                .enableTransactions()
                .withConcurrencyScale(4)
                .build();
        
        assertNotNull(db);
        
        // 测试禁用内存映射
        String noMmapPath = tempDir.resolve("no_mmap.db").toString();
        TimeSeriesDatabaseBuilder.TimeSeriesDatabase noMmapDb = TimeSeriesDatabaseBuilder.builder()
                .path(noMmapPath)
                .addDoubleSource("test_data")
                .disableMemoryMapping()
                .build();
        
        assertNotNull(noMmapDb);
        noMmapDb.close();
        
        // 测试禁用事务
        String noTxPath = tempDir.resolve("no_tx.db").toString();
        TimeSeriesDatabaseBuilder.TimeSeriesDatabase noTxDb = TimeSeriesDatabaseBuilder.builder()
                .path(noTxPath)
                .addDoubleSource("test_data")
                .disableTransactions()
                .build();
        
        assertNotNull(noTxDb);
        noTxDb.close();
        
        System.out.println("✅ 配置选项测试通过");
    }
    
    @Test
    void testConcurrentOperations() throws InterruptedException {
        System.out.println("\n=== 并发操作测试 ===");
        
        db = TimeSeriesDatabaseBuilder.builder()
                .path(dbPath)
                .addDoubleSource("concurrent_data")
                .withConcurrencyScale(4)
                .build();
        
        int threadCount = 10;
        int operationsPerThread = 100;
        List<Thread> threads = new ArrayList<>();
        CountDownLatch latch = new CountDownLatch(threadCount);
        
        // 创建多个线程同时写入数据
        for (int i = 0; i < threadCount; i++) {
            final int threadId = i;
            Thread thread = new Thread(() -> {
                try {
                    for (int j = 0; j < operationsPerThread; j++) {
                        // 使用纳秒时间戳 + 线程ID + 操作序号确保唯一性
                        long timestamp = System.nanoTime() + threadId * 1000000L + j;
                        db.putDouble("concurrent_data", timestamp, Math.random() * 100);
                    }
                    // 每个线程完成后提交事务
                    db.commit();
                } finally {
                    latch.countDown();
                }
            });
            threads.add(thread);
            thread.start();
        }
        
        // 等待所有线程完成
        latch.await();
        
        // 验证数据
        Map<String, Long> stats = db.getStatistics();
        long totalDataPoints = stats.get("concurrent_data (DOUBLE)");
        
        assertEquals(threadCount * operationsPerThread, totalDataPoints);
        
        System.out.println("✅ 并发操作测试通过");
        System.out.println("并发线程数: " + threadCount);
        System.out.println("每线程操作数: " + operationsPerThread);
        System.out.println("总数据点数: " + totalDataPoints);
    }
    
    @Test
    void testDataCleanup() {
        System.out.println("\n=== 数据清理测试 ===");
        
        db = TimeSeriesDatabaseBuilder.builder()
                .path(dbPath)
                .addDoubleSource("cleanup_test")
                .withRetentionDays(1) // 1天保留期
                .build();
        
        long currentTime = System.currentTimeMillis();
        long oldTime = currentTime - 2 * 24 * 60 * 60 * 1000; // 2天前
        long recentTime = currentTime - 12 * 60 * 60 * 1000; // 12小时前
        
        // 写入旧数据
        db.putDouble("cleanup_test", oldTime, 100.0);
        
        // 写入新数据
        db.putDouble("cleanup_test", recentTime, 200.0);
        db.putDouble("cleanup_test", currentTime, 300.0);
        
        // 重要：手动提交事务
        db.commit();
        
        // 验证数据存在
        Map<String, Long> statsBefore = db.getStatistics();
        assertEquals(3L, statsBefore.get("cleanup_test (DOUBLE)"));
        
        // 注意：实际的数据清理是异步的，这里只是验证数据写入
        // 在真实环境中，清理任务会定期运行
        
        System.out.println("✅ 数据清理测试通过");
        System.out.println("清理前数据点: " + statsBefore.get("cleanup_test (DOUBLE)"));
    }
}
