package com.study.interview.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.study.interview.exception.BizException;
import com.study.interview.mapper.ResumeMapper;
import com.study.interview.model.Resume;
import org.springframework.stereotype.Service;

@Service
public class ResumeService {
    private final ResumeMapper resumeMapper;
    public ResumeService(ResumeMapper resumeMapper) {
        this.resumeMapper = resumeMapper;
    }
    //按照id查询简历
    public Resume getById(Long id){
        Resume resume= resumeMapper.selectById(id);
        if(resume==null){
            throw new BizException(1001,"简历不存在");
        }
        return resume;
    }
    //简历分页查询
    public Page<Resume> page(int current, int size){
        Page<Resume> pageParam = new Page<>(current, size);
        return resumeMapper.selectPage(pageParam, null);
    }
}
