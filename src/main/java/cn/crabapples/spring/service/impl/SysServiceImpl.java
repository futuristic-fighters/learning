package cn.crabapples.spring.service.impl;

import cn.crabapples.spring.common.ApplicationException;
import cn.crabapples.spring.common.utils.AesUtils;
import cn.crabapples.spring.entity.User;
import cn.crabapples.spring.form.UserForm;
import cn.crabapples.spring.service.SysService;
import cn.crabapples.spring.service.UserService;
import org.apache.commons.codec.digest.DigestUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.CacheConfig;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import java.util.concurrent.TimeUnit;

/**
 * TODO 系统相关服务实现类
 *
 * @author Mr.He
 * 2020/1/28 23:23
 * e-mail crabapples.cn@gmail.com
 * qq 294046317
 * pc-name 29404
 */
@Service
@CacheConfig(cacheNames = "user:")
public class SysServiceImpl implements SysService {

    @Value("${crabapples.aesKey}")
    private String aesKey;
    private static final Logger logger = LoggerFactory.getLogger(SysServiceImpl.class);
    @Value("${crabapples.token}")
    private String TOKEN = "crabapples:token:";
    private final UserService userService;
    private final StringRedisTemplate stringRedisTemplate;

    public SysServiceImpl(UserService userService, StringRedisTemplate stringRedisTemplate) {
        this.userService = userService;
        this.stringRedisTemplate = stringRedisTemplate;
    }

    /**
     * Cacheable
     *  key: redis中key的值
     *  value: redis中key的前缀
     *  例: userLogin::${#p0.username}
     */
    @Override
//    @Cacheable(key = "#p0.username")
    public String login(UserForm form) {
        try {
            String username = form.getUsername();
            String password = form.getPassword();
            for (int i = 0; i < 100; i++) {
                password = DigestUtils.md5Hex(password);
            }
            password = AesUtils.doFinal(aesKey, password, Cipher.ENCRYPT_MODE);
            logger.info("开始登录->用户名:[{}],密码:[{}]", username, password);
            User user = userService.findByUsernameAndPasswordAndStatusNotAndDelFlagNot(username, password, 1,1).orElse(null);
            if(user == null){
                throw new ApplicationException("用户名或密码错");
            }
            String token = DigestUtils.md5Hex(user.getId() + System.currentTimeMillis()).toUpperCase();
            String tokenKey = TOKEN + user.getId();
            logger.info("登录成功->token:[{}],", token);
            Boolean aBoolean = stringRedisTemplate.opsForValue().setIfAbsent(tokenKey,token, 10, TimeUnit.MINUTES);
            if(null == aBoolean){
                aBoolean = false;
            }
            return aBoolean ? token : stringRedisTemplate.opsForValue().get(tokenKey);
        } catch (Exception e) {
            logger.warn("登录失败:[{}]", e.getMessage(), e);
            throw new ApplicationException("登录失败:" + e.getMessage());
        }
    }
}
