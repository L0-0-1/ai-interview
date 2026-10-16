package com.study.interview.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.study.interview.exception.BizException;
import com.study.interview.mapper.ResumeMapper;
import com.study.interview.model.Resume;
import org.springframework.stereotype.Service;
//用来实现简历的数据库交互业务逻辑
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
        //通过分页器实现分页查询
        LambdaQueryWrapper<Resume> wrapper=new LambdaQueryWrapper<>();
        wrapper.orderByDesc(Resume::getScore);
        wrapper.orderByDesc(Resume::getId);
        return resumeMapper.selectPage(pageParam, wrapper);
    }
    //保存插入简历
    public Boolean save(Resume resume){
        int row= resumeMapper.insert(resume);
        if(row==0){
            throw new BizException(1002,"简历保存失败");
        }
        return row==1;
    }
    //根据Id更新简历分数
    public void updateScore(Long id, Integer score){
        Resume temp=new Resume();
        temp.setId(id);
        temp.setScore(score);
        int row= resumeMapper.updateById(temp);
        if(row==0){
            throw new BizException(1003,"简历更新失败");
        }
    }
    //根据id删除简历
    public void removeById(Long id){
        int row= resumeMapper.deleteById(id);
        if(row==0){
            throw new BizException(1004,"简历删除失败");
        }
    }
}
