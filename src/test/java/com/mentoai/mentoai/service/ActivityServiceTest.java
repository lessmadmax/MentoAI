package com.mentoai.mentoai.service;

import com.mentoai.mentoai.controller.dto.ActivityUpsertRequest;
import com.mentoai.mentoai.entity.ActivityEntity;
import com.mentoai.mentoai.entity.ActivityEntity.ActivityType;
import com.mentoai.mentoai.entity.ActivityEntity.ActivityStatus;
import com.mentoai.mentoai.repository.ActivityRepository;
import com.mentoai.mentoai.repository.TagRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ActivityServiceTest {

    @Mock
    private ActivityRepository activityRepository;

    @Mock
    private TagRepository tagRepository;

    @Mock
    private NotificationService notificationService;

    @Mock
    private ActivityRoleMatchService activityRoleMatchService;

    @Mock
    private UserProfileService userProfileService;

    @InjectMocks
    private ActivityService activityService;

    private ActivityEntity testActivity;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        
        // 테스트용 활동 데이터 생성
        testActivity = new ActivityEntity();
        testActivity.setId(1L);
        testActivity.setTitle("테스트 활동");
        testActivity.setContent("테스트 내용입니다.");
        testActivity.setType(ActivityType.STUDY);
        testActivity.setOrganizer("테스트 주최자");
        testActivity.setIsCampus(true);
        testActivity.setStatus(ActivityStatus.OPEN);
    }

    @Test
    @DisplayName("활동 생성 테스트")
    void createActivity_Success() {
        // Given
        ActivityUpsertRequest request = sampleRequest();
        when(activityRepository.save(any(ActivityEntity.class))).thenReturn(testActivity);
        when(notificationService.createNewActivityNotification(any(ActivityEntity.class)))
                .thenReturn(java.util.concurrent.CompletableFuture.completedFuture(null));
        doNothing().when(activityRoleMatchService).indexActivity(any(ActivityEntity.class));

        // When
        ActivityEntity result = activityService.createActivity(request);

        // Then
        assertNotNull(result);
        assertEquals("테스트 활동", result.getTitle());
        assertEquals(ActivityType.STUDY, result.getType());
        verify(activityRepository, times(1)).save(any(ActivityEntity.class));
    }

    @Test
    @DisplayName("활동 ID로 조회 테스트 - 성공")
    void getActivity_Success() {
        // Given
        when(activityRepository.findById(1L)).thenReturn(Optional.of(testActivity));

        // When
        Optional<ActivityEntity> result = activityService.getActivity(1L);

        // Then
        assertTrue(result.isPresent());
        assertEquals("테스트 활동", result.get().getTitle());
        verify(activityRepository, times(1)).findById(1L);
    }

    @Test
    @DisplayName("활동 ID로 조회 테스트 - 실패")
    void getActivity_NotFound() {
        // Given
        when(activityRepository.findById(999L)).thenReturn(Optional.empty());

        // When
        Optional<ActivityEntity> result = activityService.getActivity(999L);

        // Then
        assertFalse(result.isPresent());
        verify(activityRepository, times(1)).findById(999L);
    }

    @Test
    @DisplayName("활동 목록 조회 테스트")
    void getActivities_Success() {
        // Given
        List<ActivityEntity> activities = Arrays.asList(testActivity);
        Page<ActivityEntity> page = new PageImpl<>(activities);
        
        when(activityRepository.search(
                any(), any(), any(), any(), any(), any(), any(), any(Pageable.class)))
                .thenReturn(page);

        // When
        Page<ActivityEntity> result = activityService.getActivities(
            null, null, null, null, null, null, null, 0, 20, "createdAt", "desc");

        // Then
        assertNotNull(result);
        assertEquals(1, result.getContent().size());
        assertEquals("테스트 활동", result.getContent().get(0).getTitle());
    }

    @Test
    @DisplayName("활동 수정 테스트 - 성공")
    void updateActivity_Success() {
        // Given
        ActivityUpsertRequest updateRequest = new ActivityUpsertRequest(
                "수정된 활동",
                "요약",
                "수정된 내용",
                ActivityType.CONTEST.name(),
                "수정된 주최자",
                "서울",
                "https://example.com",
                false,
                ActivityStatus.CLOSED.name(),
                null,
                null,
                null
        );

        when(activityRepository.findById(1L)).thenReturn(Optional.of(testActivity));
        when(activityRepository.save(any(ActivityEntity.class))).thenReturn(testActivity);
        doNothing().when(activityRoleMatchService).indexActivity(any(ActivityEntity.class));

        // When
        Optional<ActivityEntity> result = activityService.updateActivity(1L, updateRequest);

        // Then
        assertTrue(result.isPresent());
        assertEquals("수정된 활동", result.get().getTitle());
        verify(activityRepository, times(1)).findById(1L);
        verify(activityRepository, times(1)).save(any(ActivityEntity.class));
    }

    @Test
    @DisplayName("활동 삭제 테스트 - 성공")
    void deleteActivity_Success() {
        // Given
        when(activityRepository.existsById(1L)).thenReturn(true);
        doNothing().when(activityRoleMatchService).deleteActivityVector(1L);
        doNothing().when(activityRepository).deleteById(1L);

        // When
        boolean result = activityService.deleteActivity(1L);

        // Then
        assertTrue(result);
        verify(activityRepository, times(1)).existsById(1L);
        verify(activityRepository, times(1)).deleteById(1L);
    }

    @Test
    @DisplayName("활동 삭제 테스트 - 실패 (존재하지 않는 ID)")
    void deleteActivity_NotFound() {
        // Given
        when(activityRepository.existsById(999L)).thenReturn(false);

        // When
        boolean result = activityService.deleteActivity(999L);

        // Then
        assertFalse(result);
        verify(activityRepository, times(1)).existsById(999L);
        verify(activityRepository, never()).deleteById(999L);
    }

    @Test
    @DisplayName("활성 활동 목록 조회 테스트")
    void getActiveActivities_Success() {
        // Given
        List<ActivityEntity> activeActivities = Arrays.asList(testActivity);
        when(activityRepository.findByStatus(ActivityStatus.OPEN))
            .thenReturn(activeActivities);

        // When
        List<ActivityEntity> result = activityService.getActiveActivities();

        // Then
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals(ActivityStatus.OPEN, result.get(0).getStatus());
        verify(activityRepository, times(1)).findByStatus(ActivityStatus.OPEN);
    }

    @Test
    @DisplayName("캠퍼스 활동 조회 테스트")
    void getCampusActivities_Success() {
        // Given
        List<ActivityEntity> campusActivities = Arrays.asList(testActivity);
        when(activityRepository.findByIsCampus(true))
            .thenReturn(campusActivities);

        // When
        List<ActivityEntity> result = activityService.getCampusActivities(true);

        // Then
        assertNotNull(result);
        assertEquals(1, result.size());
        assertTrue(result.get(0).getIsCampus());
        verify(activityRepository, times(1)).findByIsCampus(true);
    }

    private ActivityUpsertRequest sampleRequest() {
        return new ActivityUpsertRequest(
                "테스트 활동",
                "요약",
                "테스트 내용입니다.",
                ActivityType.STUDY.name(),
                "테스트 주최자",
                "서울",
                "https://example.com",
                true,
                ActivityStatus.OPEN.name(),
                null,
                null,
                null
        );
    }
}

