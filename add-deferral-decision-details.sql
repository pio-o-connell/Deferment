-- Apply once to an existing MySQL or PostgreSQL database after backing it up.
ALTER TABLE deferrals
    ADD COLUMN Decision_Lect_ID VARCHAR(10) DEFAULT NULL,
    ADD COLUMN Rejection_Reason VARCHAR(1000) DEFAULT NULL;
