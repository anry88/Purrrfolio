-- The referred player always receives the referral bonus. The referrer reward
-- can be capped independently (10 rewarded referrals per calendar month).
ALTER TABLE referral_rewards
    ADD COLUMN referrer_rewarded BOOLEAN NOT NULL DEFAULT TRUE;
