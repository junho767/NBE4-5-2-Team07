package com.java.NBE4_5_1_7.domain.study.repository;

import com.java.NBE4_5_1_7.domain.member.entity.Member;
import com.java.NBE4_5_1_7.domain.study.entity.StudyMemo;
import com.java.NBE4_5_1_7.domain.study.entity.StudyMemoLike;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface StudyMemoLikeRepository extends JpaRepository<StudyMemoLike, Integer> {
    int countByStudyMemoId(Long studyMemoId);
    
    // 특정 멤버의 특정 메모에 대한 좋아요 조회 (동시성 제어용)
    Optional<StudyMemoLike> findByMemberAndStudyMemo(Member member, StudyMemo studyMemo);
}
