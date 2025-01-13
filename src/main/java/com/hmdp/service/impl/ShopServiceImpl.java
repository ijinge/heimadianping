package com.hmdp.service.impl;

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

import static com.hmdp.utils.RedisConstants.CACHE_NULL_TTL;
import static com.hmdp.utils.RedisConstants.CACHE_SHOP_KEY;

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
        String key = CACHE_SHOP_KEY+id;
        String shopJson = redisTemplate.opsForValue().get(key);
        // 判断是否存在
        if(StrUtil.isNotBlank(shopJson)){
            // 存在 直接返回
            Shop shop = JSONUtil.toBean(shopJson, Shop.class);
            log.debug("商铺{}缓存命中",shop.toString());
            return Result.ok(shop);
        }
        // 为空值
        if(shopJson!=null){
            log.debug("防止缓存穿透");
            return Result.fail("11商户不存在");
        }
        // 不存在 查询数据库
        Shop shop = getById(id);
        if(shop == null){
            // 将空值写入redis
            redisTemplate.opsForValue().set(key,"",CACHE_NULL_TTL, TimeUnit.SECONDS);
            return Result.fail("商户不存在");
        }
        // 存在 存入缓存
        redisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(shop));
        return Result.ok(shop);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateShop(Shop shop) {
        String key = CACHE_SHOP_KEY+shop.getId();
        // 更新数据库
        updateById(shop);
        redisTemplate.opsForValue().set(key,JSONUtil.toJsonStr(shop));
        log.debug("商店{}缓存更新成功",shop.getId());
    }
}
