package com.study.interview.controller;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.study.interview.mapper.ResumeMapper;
import com.study.interview.model.Resume;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/db/Test")
public class dbTestController {
    ResumeMapper resumeMapper;
    public dbTestController(ResumeMapper resumeMapper) {
        this.resumeMapper = resumeMapper;
    }
    // 根据 id 获取简历
    @GetMapping("/resume/{id}")
    public Resume byId(@PathVariable Long id){
        return resumeMapper.selectById(id);
    }
    // 获取所有简历
    @GetMapping("/resumes")
    public List<Resume> resume(){
        return resumeMapper.selectList(Wrappers.lambdaQuery());
    }
}
