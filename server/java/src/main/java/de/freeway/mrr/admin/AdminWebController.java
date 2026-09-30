package de.freeway.mrr.admin;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class AdminWebController {

    @GetMapping("/admin")
    public String dashboard() {
        return "forward:/admin/index.html";
    }

    @GetMapping("/admin/login")
    public String login() {
        return "forward:/admin/login.html";
    }
}
