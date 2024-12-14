package com.hmdp.service.impl;

import com.hmdp.dto.Result;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.UserHolder;
import org.springframework.aop.framework.AopContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
//@Transactional
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {
    @Resource
    private ISeckillVoucherService seckillVoucherService;
    @Resource
    private RedisIdWorker redisIdWorker; // 自定义的redis全局id生成器

    /**
     * 抢购特价券
     * @param voucherId
     * @return
     */
    @Override
//    @Transactional
    public Result seckillVoucher(Long voucherId) {
        // 1. 查询优惠券
        SeckillVoucher voucher = seckillVoucherService.getById(voucherId);// 查询特价券

        // 2. 判断秒杀是否开始
        if (voucher.getBeginTime().isAfter(LocalDateTime.now())) {
            // 尚未开始
            return Result.fail("秒杀尚未开始");
        }

        // 3. 判断秒杀是否已经结束
        if (voucher.getEndTime().isBefore(LocalDateTime.now())) {
            // 秒杀结束
            return Result.fail("秒杀已经结束");
        }

        // 4. 判断库存是否充足
        if (voucher.getStock() < 1) {
            // 库存不足
            return Result.fail("库存不足");
        }
        Long userId = UserHolder.getUser().getId();

        // 8. 一人一单创建订单 返回订单id
        // 对userId加锁
        // intern() 这个方法是从常量池中拿到数据，如果我们直接使用userId.toString() 他拿到的对象实际上是不同的对象，new出来的对象，我们使用锁必须保证锁必须是同一把，所以我们需要使用intern()方法
        // 确保事务提交后释放锁
        synchronized (userId.toString().intern()) { // 对相同的userId进行加锁
//            return  createVoucherOrder(voucherId); // 这样spring的事务代理就失效了
            // 获取代理对象(事务)
            IVoucherOrderService proxy = (IVoucherOrderService)AopContext.currentProxy(); // 拿到代理对象
            return proxy.createVoucherOrder(voucherId); // 这样spring的事务代理就有效了
        }
    }

    /**
     * 实现一人一单，创建订单
     * @param voucherId 返回的订单id
     * @return
     * 在方法上加synchronized，锁的对象是this，因此不建议加到方法上
     */
    @Transactional
//    public synchronized Result createVoucherOrder(Long voucherId) {
    public Result createVoucherOrder(Long voucherId) {
        // 5. 一人一单
        Long userId = UserHolder.getUser().getId();

        // 在这里加锁也是不合适的，写在这里，锁在执行完这些代码块后就会被释放，其他线程就会进来，这时可能创建订单的数据库操作还未完成，spring管理的该事务方法也还未提交，其他线程又因为锁的释放进来了，因此这样也不行
        // intern() 这个方法是从常量池中拿到数据，如果我们直接使用userId.toString() 他拿到的对象实际上是不同的对象，new出来的对象，我们使用锁必须保证锁必须是同一把，所以我们需要使用intern()方法
//        synchronized (userId.toString().intern()) { // 对相同的userId进行加锁
            // 5.1 查询订单
            int count = query().eq("user_id", userId).eq("voucher_id", voucherId).count();
            if (count > 0) {
                // 用户购买过了
                return Result.fail("用户已经购买过了，每个用户仅限一单");
            }

            // 6. 扣减库存
            // 有优惠券超卖问题
//        boolean success = seckillVoucherService.update()
//                .setSql("stock = stock - 1")
//                .eq("voucher_id", voucherId).update();

            // 优化：使用乐观锁，判断库存相等，在修改时判断库存是否和刚开始查到的值一样，如果一样认为此前没有别人操作，执行修改库存
            // 失败几率过高，如100个人中只有1个人能扣减成功，其他的人在处理时，他们在扣减时，库存已经被修改过了，所以此时其他线程都会失败
//        boolean success = seckillVoucherService.update()
//                .setSql("stock = stock - 1")
//                .eq("voucher_id", voucherId).eq("stock", voucher.getStock())
//                .update();

            // 优化：使用乐观锁，判断库存>0，在修改时判断库存是否>0，如果>0则认为可以操作，执行修改库存
            boolean success = seckillVoucherService.update()
                    .setSql("stock = stock - 1")
                    .eq("voucher_id", voucherId).gt("stock", 0) // >0
                    .update();

            if (!success) { // 扣减库存失败
                return Result.fail("库存不足");
            }


            // 7. 创建订单
            VoucherOrder voucherOrder = new VoucherOrder();
            // 7.1 订单id
            long orderId = redisIdWorker.nextId("order");
            voucherOrder.setId(orderId);
            // 7.2 用户id
            voucherOrder.setUserId(userId);
            // 7.3 代金券id
            voucherOrder.setVoucherId(voucherId);
            // 7.4 创建订单
            save(voucherOrder);

            // 8. 返回订单id
            return Result.ok(orderId);
//        }
    }
}
