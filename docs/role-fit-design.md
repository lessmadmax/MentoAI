## 직무 · 활동 매핑 및 전공 구분 설계 초안

| 목적 | 설계안 |
| --- | --- |
| 공고 ↔ 직무 매핑 | 새 테이블 `activity_target_roles`<br>`activity_id (FK, activities)`<br>`role_id (FK, target_roles)`<br>`similarity_score double precision` (Qdrant 유사도 저장)<br>`matched_requirements jsonb` (필수 전공/스킬 충족 여부 기록)<br>`matched_preferences jsonb` (우대 자격증/경험/전공 기록)<br>`created_at / updated_at timestamptz` |
| 필수 전공 분리 | 기존 `target_role_major_mapping`을 `target_role_required_majors` と `target_role_preferred_majors` 두 테이블로 분리하거나 `is_required boolean` 컬럼 추가<br>→ 필수 전공은 점수 계산 시 미충족 시 큰 패널티, 우대 전공은 보너스 |
| 우대 요건 구조화 | `target_role_preferred_experiences` 테이블 신설 (role_id, experience_type, weight)<br>기존 `target_role_bonus_skills`, `target_role_recommended_certs`는 그대로 사용 |
| Qdrant Payload 표준 | 포인트 payload 예시:<br>`{ "activityId": 123, "roleHints": ["backend", "spring"], "ingestedAt": "..." }`<br>→ 검색 후 `activity_target_roles`에 집계시 사용 |

### 처리 흐름
1. 공고 저장 이후 `GeminiService.generateEmbedding(activityText)` → `QdrantClient.upsertActivityVectors`.
2. target role 임베딩과 공고 임베딩을 비교해 topK 후보를 `activity_target_roles`에 upsert.
3. `matched_requirements`/`matched_preferences`에는 전처리 엔진(LLM or rule) 결과를 JSON으로 저장해 재계산 없이 재사용.

## 추천 및 알림 로직 전환 가이드

1. **후보 검색 단계**  
   - `RecommendService.retrieveRelevantActivities`와 `ActivityService.getPersonalizedActivities`는 `targetRoleId` 필수 입력.  
   - `QdrantClient.searchByEmbedding(roleEmbedding, topK, filters)` 결과의 activityId로 RDB 조회.  
   - `activity_target_roles`에 적재된 `similarity_score`, `matched_requirements` 데이터를 우선 사용.

2. **점수 계산**  
   - 기준 점수 = `similarity_score * 70%`.  
   - 필수 전공 미충족 시 즉시 탈락 또는 큰 패널티 (-50).  
   - 필수 스킬·경험 매칭 비율 = `matched_requirements.requiredSkillsMatched / total` → 20%.  
   - 우대 항목(전공/자격증/경험/스킬) 충족 개수 * 가중치로 최대 10% 보너스.  
   - 총점 0~100으로 normalize, 최소 임계값(예: 60) 미만 활동은 `/activities` 맞춤 결과에서 제외.

3. **엔드포인트 영향**  
   - `/recommend`: 관심사 로직 제거 → `targetRoleId` 기반으로만 후보 생성.  
   - `/activities?userId=`: 동일 점수 로직 재사용, 페이지네이션만 적용.  
   - `/users/{id}/interests` API 및 관련 엔티티는 단계적으로 삭제 후보.

4. **알림 제거**  
   - `NotificationService#createInterestMatchNotifications` 등 관심사 기반 메서드 비활성화.  
   - 향후 필요 시 “직무 기반 활동 알림”을 새로운 규칙으로 다시 설계.

5. **운영 가드레일**  
   - 벡터 싱크 실패 시 `activity_target_roles.sync_status`로 재처리.  
   - Role 정의 갱신 시 관련 매핑 재생성 배치 필요.


