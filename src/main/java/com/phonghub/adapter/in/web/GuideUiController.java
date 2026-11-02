package com.phonghub.adapter.in.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/** Trang hướng dẫn sử dụng theo vai trò; nội dung nằm ở template, chỉ cần đã đăng nhập. */
@Controller
public class GuideUiController {

    @GetMapping("/guide")
    public String guide() {
        return "guide/index";
    }
}
