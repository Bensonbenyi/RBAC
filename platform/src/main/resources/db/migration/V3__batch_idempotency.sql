ALTER TABLE batch_job ADD request_key VARCHAR(80) NOT NULL;
ALTER TABLE batch_job ADD UNIQUE KEY uk_batch_request(actor_id, request_key);
