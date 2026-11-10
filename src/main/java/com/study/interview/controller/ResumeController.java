package com.study.interview.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.study.interview.common.Result;
import com.study.interview.model.Resume;
import com.study.interview.service.ResumeService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;


@RestController
@RequestMapping("/api/resume")
public class ResumeController {
    private final ResumeService resumeService;
    public ResumeController(ResumeService resumeService) {
        this.resumeService = resumeService;
    }
    //分页查询简历历史
    // minScore 可选：不传 = 不按分数筛选；传了 = score >= minScore
    @GetMapping("/history")
    public Result<Page<Resume>> getResumeHistory(@RequestParam int page, @RequestParam int size,
                                                 @RequestParam(required = false) Integer minScore) {
        return Result.success(resumeService.page(page, size, minScore));
    }
    //按id查询简历
    @GetMapping("/{id}")
    public Result<Resume> getResumeById(@PathVariable Long id) {
        return Result.success(resumeService.getById(id));
    }
}
