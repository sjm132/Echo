package com.greate.community.controller;

import com.google.code.kaptcha.Producer;
import com.greate.community.entity.User;
import com.greate.community.service.UserService;
import com.greate.community.util.CommunityConstant;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import javax.imageio.ImageIO;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.Map;

/**
 * 处理登录注册
 */
@Controller
public class LoginController implements CommunityConstant {

    private static final Logger logger = LoggerFactory.getLogger(LoginController.class);

    @Autowired
    private UserService userService;

    @Autowired
    private Producer kaptchaProducer;

    @Value("${server.servlet.context-path}")
    private String contextPath;

    /**
     * 进入注册界面
     * @return
     */
    @GetMapping("/register")
    public String getRegisterPage() {
        return "site/register";
    }

    /**
     * 进入登录界面
     * @return
     */
    @GetMapping("/login")
    public String getLoginPage() {
        return "site/login";
    }

    /**
     * 注册用户
     * @param model
     * @param user
     * @return
     */
    @PostMapping("/register")
    public String register(Model model, User user) {
        Map<String, Object> map = userService.register(user);
        if (map == null || map.isEmpty()) {
            model.addAttribute("msg", "注册成功, 我们已经向您的邮箱发送了一封激活邮件，请尽快激活!");
            model.addAttribute("target", "/index");
            return "/site/operate-result";
        } else {
            model.addAttribute("usernameMsg", map.get("usernameMsg"));
            model.addAttribute("passwordMsg", map.get("passwordMsg"));
            model.addAttribute("emailMsg", map.get("emailMsg"));
            return "/site/register";
        }
    }

    /**
     * 激活用户
     * @param model
     * @param userId
     * @param code 激活码
     * @return
     * http://localhost:8080/echo/activation/用户id/激活码
     */
    @GetMapping("/activation/{userId}/{code}")
    public String activation(Model model, @PathVariable("userId") int userId, @PathVariable("code") String code) {
        int result = userService.activation(userId, code);
        if (result == ACTIVATION_SUCCESS) {
            model.addAttribute("msg", "激活成功, 您的账号已经可以正常使用!");
            model.addAttribute("target", "/login");
        }
        else if (result == ACTIVATION_REPEAT) {
            model.addAttribute("msg", "无效的操作, 您的账号已被激活过!");
            model.addAttribute("target", "/index");
        }
        else {
            model.addAttribute("msg", "激活失败, 您提供的激活码不正确!");
            model.addAttribute("target", "/index");
        }
        return "/site/operate-result";
    }


    /**
     * 生成验证码
     * @param response
     * @param session
     */
    @GetMapping("/kaptcha")
    public void getKaptcha(HttpServletResponse response, HttpSession session) {
        // 生成验证码
        String text = kaptchaProducer.createText(); // 生成随机字符
        System.out.println("验证码：" + text);
        BufferedImage image = kaptchaProducer.createImage(text); // 生成图片
        
        // 将验证码存入 session
        session.setAttribute("kaptcha", text);
        
        // 将图片输出给浏览器
        response.setContentType("image/png");
        try {
            ServletOutputStream os = response.getOutputStream();
            ImageIO.write(image, "png", os);
        } catch (IOException e) {
            logger.error("响应验证码失败", e.getMessage());
        }
    }

    /**
     * 用户登录
     * @param username 用户名
     * @param password 密码
     * @param code 验证码
     * @param rememberMe 是否记住我（点击记住我后，凭证的有效期延长）
     * @param model
     * @param session 从 session 中取出验证码
     * @param response
     * @return
     */
    @PostMapping("/login")
    public String login(@RequestParam("username") String username,
                        @RequestParam("password") String password,
                        @RequestParam("code") String code,
                        @RequestParam(value = "rememberMe", required = false) boolean rememberMe,
                        Model model, HttpSession session, HttpServletResponse response) {
        // 检查验证码
        String kaptcha = (String) session.getAttribute("kaptcha");
        if (StringUtils.isBlank(kaptcha) || StringUtils.isBlank(code) || !kaptcha.equalsIgnoreCase(code)) {
            model.addAttribute("codeMsg", "验证码错误");
            return "/site/login";
        }

        // 凭证过期时间（是否记住我）
        int expiredSeconds = rememberMe ? REMEMBER_EXPIRED_SECONDS : DEFAULT_EXPIRED_SECONDS;
        // 验证用户名和密码
        Map<String, Object> map = userService.login(username, password, expiredSeconds);
        if (map.containsKey("ticket")) {
            // 账号和密码均正确，则服务端会生成 ticket，浏览器通过 cookie 存储 ticket
            Cookie cookie = new Cookie("ticket", map.get("ticket").toString());
            cookie.setPath(contextPath); // cookie 有效范围
            cookie.setMaxAge(expiredSeconds);
            response.addCookie(cookie);
            return "redirect:/index";
        }
        else {
            model.addAttribute("usernameMsg", map.get("usernameMsg"));
            model.addAttribute("passwordMsg", map.get("passwordMsg"));
            return "/site/login";
        }

    }

    /**
     * 用户登出
     * @param ticket 设置凭证状态为无效
     * @return
     */
    @GetMapping("/logout")
    public String logout(@CookieValue("ticket") String ticket) {
        userService.logout(ticket);
        return "redirect:/login";
    }

}