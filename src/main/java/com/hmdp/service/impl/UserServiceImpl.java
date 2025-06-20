package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.http.HttpRequest;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RegexUtils;
import com.hmdp.utils.UserHolder;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result login(LoginFormDTO loginForm) {
        // 1.手机号校验
        String phone = loginForm.getPhone();
        if(RegexUtils.isPhoneInvalid(phone)){
            return Result.fail("手机号格式错误");
        }
//        // 2.校验验证码
//        Object cacheCode = session.getAttribute("code");
        String cacheCode = stringRedisTemplate.opsForValue().get(LOGIN_CODE_KEY + phone);
        String code = loginForm.getCode();
        if (cacheCode == null || !cacheCode.equals(code)) {
            //  验证码不一致 报错
            return Result.fail("验证码输入错误");
        }
        // 3.依据手机号查询用户
        User user = query().eq("phone", phone).one();
        // 4.不存在创建新用户
        if(user == null){
            user = createUserWithPhone(phone);
        }
        // 4.存在则保存用户信息到Session
//        session.setAttribute("user", BeanUtil.copyProperties(user, UserDTO.class));

        // 7.保存用户信息到 redis中
        // 7.1.随机生成token，作为登录令牌
        String token = UUID.randomUUID().toString();
        // 7.2.将User对象转为HashMap存储
        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
        Map<String, Object> userMap = BeanUtil.beanToMap(userDTO, new HashMap<>(),
                CopyOptions.create()
                        .setIgnoreNullValue(true)
                        .setFieldValueEditor((fieldName, fieldValue) -> {
                            if (fieldValue == null) {
                                return null; // 或者返回一个默认值
                            }
                            return fieldValue.toString();
                        }));
        // 7.3.存储
        String tokenKey = LOGIN_USER_KEY + token;
        stringRedisTemplate.opsForHash().putAll(tokenKey, userMap);
        // 7.4.设置token有效期
        stringRedisTemplate.expire(tokenKey, LOGIN_USER_TTL, TimeUnit.MINUTES);

        return Result.ok();
    }

    @Override
    public void logout(HttpServletRequest request) {
        // 1.获取请求头中的token
        String token = request.getHeader("authorization");
        // 2.删除redis中的token
        stringRedisTemplate.delete(LOGIN_USER_KEY + token);
        // 3.删除本地ThreadLocal保存的用户信息
        UserHolder.removeUser();
    }

    private User createUserWithPhone(String phone) {
        User user = new User();
        user.setPhone(phone);
        user.setPassword("user_"+ RandomUtil.randomNumbers(10));
        user.setNickName("user_"+ RandomUtil.randomNumbers(10));
        // 保存用户
        save(user);
        return user;
    }
}
