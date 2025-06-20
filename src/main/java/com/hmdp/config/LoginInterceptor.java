package com.hmdp.config;

import com.hmdp.dto.UserDTO;
import com.hmdp.utils.UserHolder;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

public class LoginInterceptor implements HandlerInterceptor {
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 1.获取session
        HttpSession session = request.getSession();
        // 2.获取session中的用户信息
        Object user = session.getAttribute("user");
        if(user == null){
            // 3.不存在
            response.setStatus(401);
            return false;
        }
        // 存在，保存用户信息到ThreadLocal
        UserHolder.saveUser((UserDTO) user);
        // 放行
        return true;
    }
}
