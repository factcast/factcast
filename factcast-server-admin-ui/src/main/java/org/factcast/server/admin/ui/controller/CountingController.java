package org.factcast.server.admin.ui.controller;

import static org.springframework.http.HttpStatus.NOT_FOUND;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Controller
@RequestMapping("/admin/counting")
public class CountingController {

  private final Map<UUID, LocalDateTime> jobs = new HashMap<>();

  @GetMapping
  public String countingMainPage() {
    return "admin/counting/main";
  }

  @PostMapping("/jobs")
  public String submitCountingJob(RedirectAttributes redirectAttributes) {
    UUID id = UUID.randomUUID();
    jobs.put(id, LocalDateTime.now());
    log.info("New job with id {}", id);
    redirectAttributes.addAttribute("id", id);
    return "redirect:jobs/{id}";
  }

  @GetMapping("/jobs/{id}")
  public String getJobStatus(
      @PathVariable UUID id, Model model, RedirectAttributes redirectAttributes) {

    var status = computeJobStatus(id);

    if (status.equals("done")) {
      redirectAttributes.addAttribute("id", id);
      return "redirect:/admin/counting/jobs/{id}/result";
    }

    model.addAttribute("id", id);
    model.addAttribute("status", status);

    return "admin/counting/jobStatus";
  }

  @GetMapping("/jobs/{id}/result")
  public String getJobResult(@PathVariable UUID id, Model model) {

    var status = computeJobStatus(id);
    if (!status.equals("done")) {
      throw new ResponseStatusException(NOT_FOUND, "Unknown job or job not finished");
    }

    model.addAttribute("id", id);
    model.addAttribute("result", jobs.get(id).getSecond());

    return "admin/counting/jobResult";
  }

  private String computeJobStatus(UUID id) {
    if (id == null || !jobs.containsKey(id)) {
      return "unknown";
    }
    if (jobs.get(id).plusSeconds(5).isBefore(LocalDateTime.now())) {
      return "done";
    }
    return "running";
  }
}
