package com.hmdp;

import com.hmdp.service.impl.ShopServiceImpl;
import com.hmdp.utils.RedisIdWorker;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import javax.annotation.Resource;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@SpringBootTest
class HmDianPingApplicationTests {

    @Resource
    private ShopServiceImpl shopService;

    @Resource
    private RedisIdWorker redisIdWorker;


    /**
     * 使用逻辑过期时间需要先进行缓存预热，将热点key存入redis
     */
    @Test
    void testSaveShop() {
        shopService.saveShop2Redis(1L, 10L);
    }

    private ExecutorService es = Executors.newFixedThreadPool(500);

    /**
     * 测试redis全局唯一id的生成
     * @throws InterruptedException
     * 300个线程，每个线程生成100个ID
     */
    @Test
    void testIdWorker() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(300); // 用于实现线程间协调和控制的工具。在这里，它用于允许主线程等待所有子线程完成。
        Runnable task = () -> {
            for (int i = 0; i < 100; i++) {
                long id = redisIdWorker.nextId("order");
                System.out.println("id = " + id);
            }
            latch.countDown(); // 指示主线程的一个子线程完成了任务
        };

        long begin = System.currentTimeMillis();
        for (int i = 0; i < 300; i++) {
            es.submit(task); // 模拟300个线程执行该task任务
        }
        latch.await(); // 等待300个线程完成
        long end = System.currentTimeMillis();
        System.out.println("time = " + (end - begin));
    }
}
