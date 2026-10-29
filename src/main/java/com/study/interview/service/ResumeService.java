package com.study.interview.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.study.interview.exception.BizException;
import com.study.interview.mapper.ResumeMapper;
import com.study.interview.model.Resume;
import org.springframework.http.HttpStatus;
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
            // 资源不存在 → 1002 + HTTP 404。"参数填错"和"查无此人"不能共用一个码：
            // 1001 只留给参数问题，404 让请求方一眼看出是"没有这个资源"而不是"我传错了"
            throw new BizException(1002, HttpStatus.NOT_FOUND, "简历不存在");
        }
        return resume;
    }
    //简历分页查询
    public Page<Resume> page(int current, int size){
        // 参数闸（校验属于业务，放 Service 不放 Controller，与评审接口 isBlank 校验同款边界）：
        // 负数/0/超大 size 传进去，MyBatis-Plus 会在 SQL 层报错，最后被兜底吞成 500"服务器内部错误"——
        // 明明是请求方填错，却报成服务器故障。这里先拦成 1001 参数问题（400）
        if (current < 1 || size < 1 || size > 100) {
            throw new BizException(1001, "分页参数不合法：page 从 1 起，size 在 1-100 之间");
        }
        Page<Resume> pageParam = new Page<>(current, size);
        //通过分页器实现分页查询
        LambdaQueryWrapper<Resume> wrapper=new LambdaQueryWrapper<>();
        wrapper.orderByDesc(Resume::getScore);
        wrapper.orderByDesc(Resume::getId);
        return resumeMapper.selectPage(pageParam, wrapper);
    }
    //保存插入简历
    public void save(Resume resume){
        int row= resumeMapper.insert(resume);
        if(row==0){
            // 服务端问题 → 2001 + HTTP 500：insert 要么成功返回 1、要么抛异常，返回 0 只可能是库层故障
            throw new BizException(2001, HttpStatus.INTERNAL_SERVER_ERROR, "简历保存失败");
        }
    }
    //根据Id更新简历分数
    public void updateScore(Long id, Integer score){
        // 先查存在性，不靠"受影响行数"判断失败——这是 MySQL 的一个坑：
        // 默认不开启 CLIENT_FOUND_ROWS，UPDATE 把分数改成相同的值时受影响行数为 0，
        // 会误报"更新失败"。updateById 本身的故障是抛异常（走全局兜底），不用判返回值
        if (resumeMapper.selectById(id) == null) {
            throw new BizException(1002, HttpStatus.NOT_FOUND, "简历不存在");
        }
        Resume temp=new Resume();
        temp.setId(id);
        temp.setScore(score);
        resumeMapper.updateById(temp);
    }
    //根据id删除简历
    public void removeById(Long id){
        int row= resumeMapper.deleteById(id);
        if(row==0){
            // 删除不存在的行受影响行数才是 0（删除过程中的故障是抛异常，不走这个分支），
            // 所以这里语义是"不存在"而非"删除失败" → 1002 + 404
            throw new BizException(1002, HttpStatus.NOT_FOUND, "简历不存在");
        }
    }
}
