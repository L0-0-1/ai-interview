package com.study.interview.controller;

import com.study.interview.common.Result;
import com.study.interview.model.Resume;
import com.study.interview.model.ResumeReview;
import com.study.interview.model.ResumeText;
import com.study.interview.service.ResumeReviewService;
import org.springframework.web.bind.annotation.*;

import java.awt.*;

@RestController
@RequestMapping("/api/resume")
public class ResumeReviewController {
    private final ResumeReviewService resumeReviewService;
    public ResumeReviewController(ResumeReviewService resumeReviewService) {
        this.resumeReviewService = resumeReviewService;
    }
    // 评审简历，只评审不落库
    @PostMapping("/review")
    public Result<ResumeReview> review(@RequestBody ResumeText resumeText) {
        ResumeReview resumeReview = resumeReviewService.review(resumeText.getText());
        return Result.success(resumeReview);
    }
    // 评审简历并保存
    @PostMapping("/save")
    public Result<Resume> reviewAndSave(@RequestBody ResumeText resumeText) {
        Resume resume = resumeReviewService.reviewAndSave(resumeText.getText());
        return Result.success(resume);
    }
}
