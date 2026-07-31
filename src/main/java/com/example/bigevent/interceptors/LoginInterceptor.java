package com.example.bigevent.interceptors;

import com.example.bigevent.util.JwtUtil;
import com.example.bigevent.util.ThreadLocalUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Map;
//用这个拦截器还要配合webmvc
@Slf4j
@Component
    public class LoginInterceptor implements HandlerInterceptor {
       @Autowired
       private StringRedisTemplate stringRedisTemplate;

        @Override
        public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
            //令牌验证
            String token = request.getHeader("Authorization");
            String uri = request.getRequestURI();
            log.debug("=== 请求信息 === 路径: {}, Token: {}", uri, token != null ? "有" : "无");
            //验证token
            try {
                Map<String,Object> claims = JwtUtil.parseToken(token);
                String requestURI = request.getRequestURI();
                log.debug("拦截器拦截的请求: {}", requestURI);

                //把业务数据存储到ThreadLocal中
                ThreadLocalUtil.set(claims);
                ValueOperations<String, String> operations = stringRedisTemplate.opsForValue();
                String redisToken = operations.get(token);
                if (redisToken==null){
                    //说明token已经失效了
                    throw new RuntimeException();
                }
                return true;
            } catch (Exception e) {
                //http响应状态码为401
                response.setStatus(401);
                String requestURI = request.getRequestURI();


                //不放行
                return false;
            }
        }
    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        //清空ThreadLocal中的数据，防止内存泄露
        ThreadLocalUtil.remove();
    }


}
