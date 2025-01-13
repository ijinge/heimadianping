package com.hmdp.service.impl;

import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.entity.ShopType;
import com.hmdp.mapper.ShopTypeMapper;
import com.hmdp.service.IShopTypeService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

import java.util.ArrayList;
import java.util.List;

import static com.hmdp.utils.RedisConstants.SHOP_TYPE_LIST_KEY;

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
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {
    @Resource
    private RedisTemplate<String, String> redisTemplate;

    @Override
    public List<ShopType> queryTypeList() {
        String key = SHOP_TYPE_LIST_KEY;
        String shopTypeJson = redisTemplate.opsForValue().get(key);
        List<ShopType> typeList = null;
        // 命中
        if(StrUtil.isNotBlank(shopTypeJson)){
            typeList = JSONUtil.toList(shopTypeJson, ShopType.class);
            log.debug("商品列表缓存命中{}", JSONUtil.toJsonStr(typeList));
            return typeList;
        }
        // 未命中
        typeList = query().orderByAsc("sort").list();
        redisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(typeList));
        return typeList;
    }
}
