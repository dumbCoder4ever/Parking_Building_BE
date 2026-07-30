-- Guest walk-in vehicles may not belong to a registered driver account.
ALTER TABLE vehicles MODIFY user_id CHAR(36) NULL;
