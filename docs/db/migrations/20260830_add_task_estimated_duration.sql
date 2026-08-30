-- Task 단위 예상 소요 시간을 저장한다.
-- null은 사용자가 시간을 정하지 않은 상태를 의미한다.

ALTER TABLE TASK
    ADD COLUMN ESTIMATED_DURATION_MINUTES INT NULL;
