package com.study.interview.mapper;

import org.apache.ibatis.annotations.Arg;
import org.apache.ibatis.annotations.ConstructorArgs;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface PracticeQuestionMapper {
    @Select("""
            SELECT id, question_text
            FROM practice_question
            WHERE skill_tag = #{skillTag}
            ORDER BY id
            LIMIT 1
            """)
    @ConstructorArgs({
            @Arg(column = "id", javaType = Long.class, id = true),
            @Arg(column = "question_text", javaType = String.class)
    })
    Question findFirstBySkillTag(@Param("skillTag") String skillTag);

    @Select("""
            SELECT id, question_text, scoring_points, source_url
            FROM practice_question
            WHERE id = #{questionId}
            """)
    @ConstructorArgs({
            @Arg(column = "id", javaType = Long.class, id = true),
            @Arg(column = "question_text", javaType = String.class),
            @Arg(column = "scoring_points", javaType = String.class),
            @Arg(column = "source_url", javaType = String.class)
    })
    Details findById(@Param("questionId") Long questionId);

    record Question(Long id, String questionText) {
    }

    record Details(Long id, String questionText, String scoringPoints, String sourceUrl) {
    }
}
