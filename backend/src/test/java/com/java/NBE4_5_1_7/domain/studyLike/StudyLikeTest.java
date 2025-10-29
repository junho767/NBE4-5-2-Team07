package com.java.NBE4_5_1_7.domain.studyLike;

import com.java.NBE4_5_1_7.domain.member.entity.Member;
import com.java.NBE4_5_1_7.domain.member.entity.Role;
import com.java.NBE4_5_1_7.domain.member.entity.SubscriptionPlan;
import com.java.NBE4_5_1_7.domain.member.service.MemberService;
import com.java.NBE4_5_1_7.domain.study.entity.StudyContent;
import com.java.NBE4_5_1_7.domain.study.entity.StudyMemo;
import com.java.NBE4_5_1_7.domain.study.entity.StudyMemoLike;
import com.java.NBE4_5_1_7.domain.study.repository.StudyMemoLikeRepository;
import com.java.NBE4_5_1_7.domain.study.repository.StudyMemoRepository;
import com.java.NBE4_5_1_7.domain.study.service.StudyMemoLikeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class StudyLikeTest {

    @Mock
    private StudyMemoLikeRepository studyMemoLikeRepository;

    @Mock
    private StudyMemoRepository studyMemoRepository;

    @Mock
    private MemberService memberService;

    @Mock
    private RedissonClient redissonClient;

    @InjectMocks
    private StudyMemoLikeService studyMemoLikeService;

    private List<Member> testMembers;
    private StudyMemo testStudyMemo;
    private StudyContent testStudyContent;

    @BeforeEach
    void setUp() {
        // 테스트용 Member 10명 생성
        testMembers = new ArrayList<>();
        for (int i = 1; i <= 10; i++) {
            Member member = Member.builder()
                    .id((long) i)
                    .username("testuser" + i)
                    .nickname("테스트유저" + i)
                    .role(Role.USER)
                    .subscriptionPlan(SubscriptionPlan.FREE)
                    .subscribeEndDate(LocalDateTime.now())
                    .build();
            testMembers.add(member);
        }

        // 테스트용 StudyContent 생성
        testStudyContent = new StudyContent();
        testStudyContent.setStudy_content_id(1L);

        // 테스트용 StudyMemo 생성
        testStudyMemo = new StudyMemo("테스트 메모", testStudyContent, testMembers.get(0), true);
    }

    @Test
    @DisplayName("분산 락 적용 - 10명이 동시에 좋아요 추가/취소")
    void testDistributedLockWith10Users() throws InterruptedException {
        List<StudyMemoLike> likeList = new ArrayList<>();
        ThreadLocal<Member> currentMember = new ThreadLocal<>();
        
        setupMocks(likeList, currentMember);
        ExecutorService executor = Executors.newFixedThreadPool(10);
        
        executeConcurrentLikes(executor, currentMember);
        int afterAdd = likeList.size();
        
        executeConcurrentLikes(executor, currentMember);
        int afterCancel = likeList.size();
        
        executor.shutdown();
        System.out.println("[분산 락 테스트] 10명 추가: " + afterAdd + "개 | 10명 취소: " + afterCancel + "개");
        
        assertThat(afterAdd).isEqualTo(10);
        assertThat(afterCancel).isEqualTo(0);
    }
    
    private void setupMocks(List<StudyMemoLike> likeList, ThreadLocal<Member> member) throws InterruptedException {
        when(studyMemoRepository.findById(anyLong())).thenReturn(Optional.of(testStudyMemo));
        when(memberService.getMemberFromRq()).thenAnswer(inv -> member.get());
        
        RLock lock = mock(RLock.class);
        when(redissonClient.getLock(anyString())).thenReturn(lock);
        when(lock.tryLock(anyLong(), anyLong(), any())).thenReturn(true);
        when(lock.isHeldByCurrentThread()).thenReturn(true);
        
        when(studyMemoLikeRepository.findByMemberAndStudyMemo(any(), eq(testStudyMemo))).thenAnswer(inv -> {
            synchronized (likeList) {
                return likeList.stream()
                    .filter(like -> like.getMember().getId().equals(((Member)inv.getArgument(0)).getId()))
                    .findFirst();
            }
        });
        
        when(studyMemoLikeRepository.save(any(StudyMemoLike.class))).thenAnswer(inv -> {
            synchronized (likeList) { likeList.add(inv.getArgument(0)); }
            return inv.getArgument(0);
        });
        
        doAnswer(inv -> {
            synchronized (likeList) {
                likeList.removeIf(l -> l.getMember().getId().equals(((StudyMemoLike)inv.getArgument(0)).getMember().getId()));
            }
            return null;
        }).when(studyMemoLikeRepository).delete(any());
    }
    
    private void executeConcurrentLikes(ExecutorService executor, ThreadLocal<Member> member) throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(10);
        for (int i = 0; i < 10; i++) {
            int idx = i;
            executor.submit(() -> {
                try {
                    member.set(testMembers.get(idx));
                    studyMemoLikeService.memoLike(1L);
                } finally {
                    member.remove();
                    latch.countDown();
                }
            });
        }
        latch.await();
    }
}
