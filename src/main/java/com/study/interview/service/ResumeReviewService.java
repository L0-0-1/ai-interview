package com.study.interview.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.study.interview.exception.BizException;
import com.study.interview.model.Resume;
import com.study.interview.model.ResumeReview;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

@Service
public class ResumeReviewService {
    private final ObjectMapper objectMapper;
    private final ResumeService resumeService;
    private final ChatClient chatClient;
    public ResumeReviewService(ObjectMapper objectMapper, ResumeService resumeService, ChatClient chatClient) {
        this.objectMapper = objectMapper;
        this.resumeService = resumeService;
        this.chatClient = chatClient;
    }
    //根据简历内容给出结构化的评审意见
    public ResumeReview review(String resumeText) {
        if(resumeText==null||resumeText.isBlank()){
            throw new BizException(1001,"简历内容为空");
        }
        return chatClient.prompt()
                .system("你是一个简历评审员，请根据简历内容给出评审意见。")
                .user(resumeText)
                .call()
                //将结果转换为ResumeReview对象
                .entity(ResumeReview.class);
    }
    //评审并保存简历
    public Resume reviewAndSave(String resumeText){
        ResumeReview aiReview=review(resumeText);
        Resume resume=new Resume();
        String name=aiReview.name();
        if(name==null||name.trim().isEmpty()){
            name="未知";
        }
        resume.setName(name);
        resume.setScore(aiReview.score());
        resume.setSummary(aiReview.summary());
        //简历内容直接用传入的参数，防止ai复述后产生偏差
        resume.setResumeText(resumeText);
        try {
        resume.setReviewJson(objectMapper.writeValueAsString(aiReview));
        } catch (Exception e) {
            throw new BizException(1003,"简历评审失败");
        }
        //由于插入是原子操作，不需要用事务
        resumeService.save(resume);
        return resume;
    }
}
