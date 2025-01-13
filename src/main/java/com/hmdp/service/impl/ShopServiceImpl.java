package com.hmdp.service.impl;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSON;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;

import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
@Slf4j
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {

    @Resource
    private RedisTemplate<String, String> redisTemplate;

    @Override
    public Result queryById(Long id) {
        String key = CACHE_SHOP_KEY + id;
        String lockKey = "lock:shop:" + id;
        String shopJson = redisTemplate.opsForValue().get(key);
        // 判断是否存在
        if(StrUtil.isNotBlank(shopJson)){
            // 存在 直接返回
            Shop shop = JSONUtil.toBean(shopJson, Shop.class);
            log.debug("商铺{}缓存命中",shop.getId());
            return Result.ok(shop);
        }
        // 为空值
        if(shopJson!=null){
            log.debug("防止缓存穿透");
            return Result.fail("11商户不存在");
        }
        // 防止缓存击穿
        Shop shop = null;
        try {
            // 获取互斥锁
            boolean lock = getMutexLock(lockKey);
            // 判断是否获取锁
            if(!lock){
                // 否
                Thread.sleep(50);
                // 递归重试
                return queryById(id);
            }
            // 是 重建缓存
            // 查询数据库
            shop = getById(id);
            Thread.sleep(200);
            if(shop == null){
                // 将空值写入redis
                redisTemplate.opsForValue().set(key,"",CACHE_NULL_TTL, TimeUnit.SECONDS);
                return Result.fail("商户不存在");
            }
            // 存在 存入缓存
            redisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(shop),CACHE_SHOP_TTL, TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }finally {
            // 释放锁
            unlockMutex(lockKey);
        }
        return Result.ok(shop);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateShop(Shop shop) {
        String key = CACHE_SHOP_KEY+shop.getId();
        // 更新数据库
        updateById(shop);
        redisTemplate.opsForValue().set(key,JSONUtil.toJsonStr(shop));
        // 设置一个随机的过期时间 防止缓存雪崩
        redisTemplate.expire(key,CACHE_SHOP_TTL + RandomUtil.randomLong(0,10),TimeUnit.MINUTES);
        log.debug("商店{}缓存更新成功",shop.getId());
    }

    private boolean getMutexLock(String key) {
        Boolean lock = redisTemplate.opsForValue().setIfAbsent(key, "lock", 1, TimeUnit.MINUTES);
        return BooleanUtil.isTrue(lock);
    }
    private void unlockMutex(String key) {
        redisTemplate.delete(key);
    }
}
