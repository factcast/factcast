package org.factcast.server.admin.ui.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/admin/counting")
public class CountingController {

  @GetMapping
  public String countingMainPage() {
    return "countingMainPage";
  }
  // POST /counting/jobs
  // GET  /counting/jobs/123 -> status, when done: redirect; otherwise refresh every 20 seconds
  // GET  /counting/jobs/123/result
}
