package com.study.interview.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.study.interview.common.Result;
import com.study.interview.model.Resume;
import com.study.interview.service.ResumeService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/resume")
public class ResumeController {
    private final ResumeService resumeService;
    public ResumeController(ResumeService resumeService) {
        this.resumeService = resumeService;
    }
    //分页查询简历历史
    @GetMapping("/history")
    public Result<Page<Resume>> getResumeHistory(@RequestParam int current, @RequestParam int size) {
        return Result.success(resumeService.page(current, size));
    }
    //按id查询简历
    @GetMapping("/{id}")
    public Result<Resume> getResumeById(@PathVariable Long id) {
        return Result.success(resumeService.getById(id));
    }
}
