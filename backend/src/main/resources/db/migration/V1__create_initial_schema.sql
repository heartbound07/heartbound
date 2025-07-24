CREATE TABLE IF NOT EXISTS public.users (
    id VARCHAR(255) PRIMARY KEY,
    username VARCHAR(255),
    discriminator VARCHAR(255),
    avatar VARCHAR(255),
    email VARCHAR(255),
    password VARCHAR(255),
    discord_avatar_url VARCHAR(255),
    display_name VARCHAR(255),
    pronouns VARCHAR(255),
    about TEXT,
    banner_color VARCHAR(255),
    banner_url VARCHAR(255),
    credits INTEGER DEFAULT 0,
    level INTEGER DEFAULT 1,
    experience INTEGER DEFAULT 0,
    message_count BIGINT DEFAULT 0,
    fish_caught_count INTEGER DEFAULT 0,
    fish_caught_since_limit INTEGER DEFAULT 0,
    fishing_limit_cooldown_until TIMESTAMP,
    messages_today INTEGER DEFAULT 0,
    messages_this_week INTEGER DEFAULT 0,
    messages_this_two_weeks INTEGER DEFAULT 0,
    last_daily_reset TIMESTAMP,
    last_weekly_reset TIMESTAMP,
    last_bi_weekly_reset TIMESTAMP,
    voice_time_minutes_total INTEGER DEFAULT 0,
    voice_time_minutes_today INTEGER DEFAULT 0,
    voice_time_minutes_this_week INTEGER DEFAULT 0,
    voice_time_minutes_this_two_weeks INTEGER DEFAULT 0,
    voice_rank INTEGER,
    last_voice_daily_reset TIMESTAMP,
    last_voice_weekly_reset TIMESTAMP,
    last_voice_bi_weekly_reset TIMESTAMP,
    daily_streak INTEGER DEFAULT 0,
    last_daily_claim TIMESTAMP,
    prisoned_at TIMESTAMP,
    prison_release_at TIMESTAMP,
    selected_age_role_id VARCHAR(255),
    selected_gender_role_id VARCHAR(255),
    selected_rank_role_id VARCHAR(255),
    selected_region_role_id VARCHAR(255),
    active BOOLEAN DEFAULT TRUE,
    is_banned BOOLEAN DEFAULT FALSE,
    equipped_user_color_id UUID,
    equipped_listing_id UUID,
    equipped_accent_id UUID,
    equipped_badge_id UUID,
    equipped_fishing_rod_id UUID
);

CREATE TABLE IF NOT EXISTS public.shop_items (
    id UUID PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    description VARCHAR(500),
    price INTEGER NOT NULL,
    category VARCHAR(255) NOT NULL,
    image_url VARCHAR(255),
    rarity VARCHAR(255) DEFAULT 'COMMON' NOT NULL,
    is_active BOOLEAN DEFAULT TRUE NOT NULL,
    required_role VARCHAR(255),
    is_featured BOOLEAN DEFAULT FALSE NOT NULL,
    is_daily BOOLEAN DEFAULT FALSE NOT NULL,
    created_at TIMESTAMP,
    updated_at TIMESTAMP,
    expires_at TIMESTAMP,
    discord_role_id VARCHAR(255),
    thumbnail_url VARCHAR(255),
    fishing_rod_multiplier DOUBLE PRECISION,
    gradient_end_color VARCHAR(255),
    max_copies INTEGER,
    copies_sold INTEGER DEFAULT 0
);

CREATE TABLE IF NOT EXISTS public.item_instances (
    id UUID PRIMARY KEY,
    owner_id VARCHAR(255) NOT NULL,
    base_item_id UUID NOT NULL,
    serial_number BIGINT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_item_instances_owner FOREIGN KEY (owner_id) REFERENCES public.users(id),
    CONSTRAINT fk_item_instances_base_item FOREIGN KEY (base_item_id) REFERENCES public.shop_items(id)
);

CREATE TABLE IF NOT EXISTS public.user_roles (
    user_id VARCHAR(255) NOT NULL,
    role VARCHAR(255),
    CONSTRAINT fk_user_roles_user FOREIGN KEY (user_id) REFERENCES public.users(id)
);

CREATE TABLE IF NOT EXISTS public.user_prison_roles (
    user_id VARCHAR(255) NOT NULL,
    role_id VARCHAR(255),
    CONSTRAINT fk_user_prison_roles_user FOREIGN KEY (user_id) REFERENCES public.users(id)
);

CREATE TABLE IF NOT EXISTS public.trades (
    id BIGSERIAL PRIMARY KEY,
    initiator_id VARCHAR(255) NOT NULL,
    receiver_id VARCHAR(255) NOT NULL,
    status VARCHAR(255) NOT NULL DEFAULT 'PENDING',
    initiator_locked BOOLEAN DEFAULT FALSE,
    receiver_locked BOOLEAN DEFAULT FALSE,
    initiator_accepted BOOLEAN DEFAULT FALSE,
    receiver_accepted BOOLEAN DEFAULT FALSE,
    expires_at TIMESTAMP WITH TIME ZONE,
    discord_message_id VARCHAR(255),
    discord_channel_id VARCHAR(255),
    created_at TIMESTAMP WITH TIME ZONE,
    updated_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT fk_trades_initiator FOREIGN KEY (initiator_id) REFERENCES public.users(id),
    CONSTRAINT fk_trades_receiver FOREIGN KEY (receiver_id) REFERENCES public.users(id)
);

CREATE TABLE IF NOT EXISTS public.trade_items (
    id BIGSERIAL PRIMARY KEY,
    trade_id BIGINT NOT NULL,
    item_instance_id UUID NOT NULL,
    CONSTRAINT fk_trade_items_trade FOREIGN KEY (trade_id) REFERENCES public.trades(id),
    CONSTRAINT fk_trade_items_item_instance FOREIGN KEY (item_instance_id) REFERENCES public.item_instances(id),
    CONSTRAINT uk_trade_items_item_instance_id UNIQUE (item_instance_id)
);

CREATE TABLE IF NOT EXISTS public.achievements (
    id BIGSERIAL PRIMARY KEY,
    achievement_key VARCHAR(100) NOT NULL,
    name VARCHAR(200) NOT NULL,
    description TEXT NOT NULL,
    achievement_type VARCHAR(255) NOT NULL,
    xp_reward INT NOT NULL,
    requirement_value INT NOT NULL,
    requirement_description VARCHAR(500),
    icon_url VARCHAR(255),
    badge_color VARCHAR(20),
    rarity VARCHAR(20) DEFAULT 'common',
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_achievement_type ON public.achievements(achievement_type);
CREATE INDEX IF NOT EXISTS idx_achievement_active ON public.achievements(active);
CREATE UNIQUE INDEX IF NOT EXISTS idx_achievement_key ON public.achievements(achievement_key);

CREATE TABLE IF NOT EXISTS public.audits (
    id UUID PRIMARY KEY,
    "timestamp" TIMESTAMP NOT NULL,
    user_id TEXT NOT NULL,
    action TEXT NOT NULL,
    entity_type TEXT,
    entity_id TEXT,
    description TEXT,
    ip_address TEXT,
    user_agent TEXT,
    session_id TEXT,
    details TEXT,
    severity VARCHAR(20) DEFAULT 'INFO',
    category VARCHAR(50) DEFAULT 'SYSTEM',
    source TEXT
);

CREATE INDEX IF NOT EXISTS idx_audit_user_id ON public.audits(user_id);
CREATE INDEX IF NOT EXISTS idx_audit_timestamp ON public.audits("timestamp");
CREATE INDEX IF NOT EXISTS idx_audit_action ON public.audits(action);
CREATE INDEX IF NOT EXISTS idx_audit_entity_type ON public.audits(entity_type);
CREATE INDEX IF NOT EXISTS idx_audit_entity_id ON public.audits(entity_id);

CREATE TABLE IF NOT EXISTS public.blacklist_entries (
    id BIGSERIAL PRIMARY KEY,
    user1_id VARCHAR(255) NOT NULL,
    user2_id VARCHAR(255) NOT NULL,
    reason VARCHAR(255),
    created_at TIMESTAMP NOT NULL,
    CONSTRAINT uk_blacklist_entries_user1_id_user2_id UNIQUE (user1_id, user2_id)
);

CREATE INDEX IF NOT EXISTS idx_blacklist_user1 ON public.blacklist_entries(user1_id);
CREATE INDEX IF NOT EXISTS idx_blacklist_user2 ON public.blacklist_entries(user2_id);
CREATE INDEX IF NOT EXISTS idx_blacklist_pair ON public.blacklist_entries(user1_id, user2_id);
CREATE INDEX IF NOT EXISTS idx_blacklist_created ON public.blacklist_entries(created_at);

-- Case Items
CREATE TABLE IF NOT EXISTS public.case_items (
    id UUID PRIMARY KEY,
    case_id UUID NOT NULL,
    contained_item_id UUID NOT NULL,
    drop_rate INTEGER NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_case_item_case_id FOREIGN KEY (case_id) REFERENCES public.shop_items(id),
    CONSTRAINT fk_case_item_contained_item_id FOREIGN KEY (contained_item_id) REFERENCES public.shop_items(id)
);
CREATE INDEX IF NOT EXISTS idx_case_item_case_id ON public.case_items(case_id);
CREATE INDEX IF NOT EXISTS idx_case_item_contained_item_id ON public.case_items(contained_item_id);
CREATE INDEX IF NOT EXISTS idx_case_item_drop_rate ON public.case_items(drop_rate);

-- Challenge Participants
CREATE TABLE IF NOT EXISTS public.challenge_participants (
    id BIGSERIAL PRIMARY KEY,
    user_id VARCHAR(255) NOT NULL,
    team_id VARCHAR(255) NOT NULL,
    team_name VARCHAR(255) NOT NULL,
    message_count BIGINT NOT NULL DEFAULT 0,
    challenge_period VARCHAR(255) NOT NULL
);

-- Counting Game
CREATE TABLE IF NOT EXISTS public.counting_game_state (
    id BIGINT PRIMARY KEY,
    current_count INTEGER DEFAULT 0,
    last_user_id VARCHAR(255),
    last_correct_message_id VARCHAR(255),
    total_resets BIGINT DEFAULT 0,
    highest_count INTEGER DEFAULT 0,
    save_cost INTEGER DEFAULT 200,
    restart_delay_until TIMESTAMP,
    last_failed_count INTEGER
);
CREATE TABLE IF NOT EXISTS public.counting_user_data (
    user_id VARCHAR(255) PRIMARY KEY,
    lives_remaining INTEGER,
    timeout_level INTEGER DEFAULT 0,
    timeout_expiry TIMESTAMP,
    total_correct_counts BIGINT DEFAULT 0,
    total_mistakes BIGINT DEFAULT 0,
    best_count INTEGER DEFAULT 0
);

-- Daily Stats
CREATE TABLE IF NOT EXISTS public.daily_message_stats (
    id BIGSERIAL PRIMARY KEY,
    user_id VARCHAR(255) NOT NULL,
    date DATE NOT NULL,
    message_count BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_daily_message_stats_user FOREIGN KEY (user_id) REFERENCES public.users(id),
    CONSTRAINT uk_daily_message_stats_user_date UNIQUE (user_id, date)
);
CREATE TABLE IF NOT EXISTS public.daily_voice_activity_stats (
    id BIGSERIAL PRIMARY KEY,
    user_id VARCHAR(255) NOT NULL,
    date DATE NOT NULL,
    voice_minutes BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_daily_voice_activity_stats_user FOREIGN KEY (user_id) REFERENCES public.users(id),
    CONSTRAINT uk_daily_voice_activity_stats_user_date UNIQUE (user_id, date)
);

-- Discord Bot Settings
CREATE TABLE IF NOT EXISTS public.discord_bot_settings (
    id BIGINT PRIMARY KEY,
    activity_enabled BOOLEAN,
    credits_to_award INTEGER,
    message_threshold INTEGER,
    time_window_minutes INTEGER,
    cooldown_seconds INTEGER,
    min_message_length INTEGER,
    leveling_enabled BOOLEAN,
    xp_to_award INTEGER,
    base_xp INTEGER,
    level_multiplier INTEGER,
    level_exponent INTEGER,
    level_factor INTEGER,
    credits_per_level INTEGER,
    level5role_id VARCHAR(255),
    level15role_id VARCHAR(255),
    level30role_id VARCHAR(255),
    level40role_id VARCHAR(255),
    level50role_id VARCHAR(255),
    level70role_id VARCHAR(255),
    level100role_id VARCHAR(255),
    starter_role_id VARCHAR(255),
    role_multipliers TEXT,
    role_multipliers_enabled BOOLEAN,
    inactivity_channel_id VARCHAR(255),
    counting_game_enabled BOOLEAN,
    counting_channel_id VARCHAR(255),
    counting_timeout_role_id VARCHAR(255),
    credits_per_count INTEGER,
    counting_lives INTEGER,
    auto_slowmode_enabled BOOLEAN,
    slowmode_channel_ids TEXT,
    activity_threshold INTEGER,
    slowmode_time_window INTEGER,
    slowmode_duration INTEGER,
    slowmode_cooldown INTEGER,
    credit_drop_enabled BOOLEAN,
    credit_drop_channel_id VARCHAR(255),
    credit_drop_min_amount INTEGER,
    credit_drop_max_amount INTEGER,
    age15role_id VARCHAR(255),
    age16to17role_id VARCHAR(255),
    age18plus_role_id VARCHAR(255),
    gender_she_her_role_id VARCHAR(255),
    gender_he_him_role_id VARCHAR(255),
    gender_ask_role_id VARCHAR(255),
    rank_iron_role_id VARCHAR(255),
    rank_bronze_role_id VARCHAR(255),
    rank_silver_role_id VARCHAR(255),
    rank_gold_role_id VARCHAR(255),
    rank_platinum_role_id VARCHAR(255),
    rank_diamond_role_id VARCHAR(255),
    rank_ascendant_role_id VARCHAR(255),
    rank_immortal_role_id VARCHAR(255),
    rank_radiant_role_id VARCHAR(255),
    age_roles_thumbnail_url VARCHAR(255),
    gender_roles_thumbnail_url VARCHAR(255),
    rank_roles_thumbnail_url VARCHAR(255),
    region_na_role_id VARCHAR(255),
    region_eu_role_id VARCHAR(255),
    region_sa_role_id VARCHAR(255),
    region_ap_role_id VARCHAR(255),
    region_oce_role_id VARCHAR(255),
    region_roles_thumbnail_url VARCHAR(255)
);

-- Giveaways
CREATE TABLE IF NOT EXISTS public.giveaways (
    id UUID PRIMARY KEY,
    host_user_id VARCHAR(255) NOT NULL,
    host_username VARCHAR(255) NOT NULL,
    prize VARCHAR(255) NOT NULL,
    number_of_winners INTEGER NOT NULL,
    end_date TIMESTAMP NOT NULL,
    channel_id VARCHAR(255) NOT NULL,
    message_id VARCHAR(255) NOT NULL,
    boosters_only BOOLEAN DEFAULT FALSE,
    level_restricted BOOLEAN DEFAULT FALSE,
    no_restrictions BOOLEAN DEFAULT FALSE,
    max_entries_per_user INTEGER DEFAULT 0,
    entry_price INTEGER DEFAULT 0,
    status VARCHAR(255) DEFAULT 'ACTIVE',
    created_at TIMESTAMP NOT NULL,
    completed_at TIMESTAMP
);
CREATE TABLE IF NOT EXISTS public.giveaway_entries (
    id UUID PRIMARY KEY,
    giveaway_id UUID NOT NULL,
    user_id VARCHAR(255) NOT NULL,
    username VARCHAR(255) NOT NULL,
    entry_number INTEGER NOT NULL,
    credits_paid INTEGER NOT NULL,
    entry_date TIMESTAMP NOT NULL,
    CONSTRAINT fk_giveaway_entries_giveaway FOREIGN KEY (giveaway_id) REFERENCES public.giveaways(id)
);

-- LFG
CREATE TABLE IF NOT EXISTS public.lfg_parties (
    id UUID PRIMARY KEY,
    leader_id VARCHAR(255) NOT NULL,
    game VARCHAR(255) NOT NULL,
    title VARCHAR(255) NOT NULL,
    description TEXT,
    req_rank VARCHAR(255) NOT NULL,
    req_region VARCHAR(255) NOT NULL,
    req_invite_only BOOLEAN NOT NULL,
    expires_in INTEGER NOT NULL,
    max_players INTEGER NOT NULL,
    status VARCHAR(255) NOT NULL DEFAULT 'open',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    match_type VARCHAR(255) NOT NULL,
    game_mode VARCHAR(255) NOT NULL,
    team_size VARCHAR(255) NOT NULL,
    voice_preference VARCHAR(255) NOT NULL,
    age_restriction VARCHAR(255) NOT NULL,
    discord_channel_id VARCHAR(255),
    discord_invite_url VARCHAR(255),
    discord_announcement_message_id VARCHAR(255)
);
CREATE TABLE IF NOT EXISTS public.lfg_party_participants (
    lfg_party_id UUID NOT NULL,
    participant_id VARCHAR(255) NOT NULL,
    CONSTRAINT fk_lfg_party_participants_party FOREIGN KEY (lfg_party_id) REFERENCES public.lfg_parties(id)
);
CREATE TABLE IF NOT EXISTS public.lfg_party_invited_users (
    party_id UUID NOT NULL,
    invited_user_id VARCHAR(255) NOT NULL,
    CONSTRAINT fk_lfg_party_invited_users_party FOREIGN KEY (party_id) REFERENCES public.lfg_parties(id)
);
CREATE TABLE IF NOT EXISTS public.lfg_party_join_requests (
    party_id UUID NOT NULL,
    requesting_user_id VARCHAR(255) NOT NULL,
    CONSTRAINT fk_lfg_party_join_requests_party FOREIGN KEY (party_id) REFERENCES public.lfg_parties(id)
);

-- Matchmaking
CREATE TABLE IF NOT EXISTS public.match_queue_users (
    id BIGSERIAL PRIMARY KEY,
    user_id VARCHAR(255) NOT NULL UNIQUE,
    age INTEGER NOT NULL,
    region VARCHAR(255) NOT NULL,
    rank VARCHAR(255) NOT NULL,
    gender VARCHAR(255) NOT NULL,
    queued_at TIMESTAMP NOT NULL,
    in_queue BOOLEAN NOT NULL DEFAULT TRUE
);
CREATE INDEX IF NOT EXISTS idx_queue_user_id ON public.match_queue_users(user_id);
CREATE INDEX IF NOT EXISTS idx_queue_in_queue ON public.match_queue_users(in_queue);
CREATE INDEX IF NOT EXISTS idx_queue_queued_at ON public.match_queue_users(queued_at);
CREATE INDEX IF NOT EXISTS idx_queue_region ON public.match_queue_users(region);
CREATE INDEX IF NOT EXISTS idx_queue_rank ON public.match_queue_users(rank);
CREATE INDEX IF NOT EXISTS idx_queue_active_users ON public.match_queue_users(in_queue, queued_at);

-- Pairings
CREATE TABLE IF NOT EXISTS public.pairings (
    id BIGSERIAL PRIMARY KEY,
    user1_id VARCHAR(255) NOT NULL,
    user2_id VARCHAR(255) NOT NULL,
    discord_channel_id BIGINT,
    discord_channel_name VARCHAR(255),
    discord_leaderboard_message_id VARCHAR(255),
    matched_at TIMESTAMP NOT NULL,
    message_count INTEGER NOT NULL DEFAULT 0,
    user1_message_count INTEGER NOT NULL DEFAULT 0,
    user2_message_count INTEGER NOT NULL DEFAULT 0,
    voice_time_minutes INTEGER NOT NULL DEFAULT 0,
    current_voice_session_start TIMESTAMP,
    word_count INTEGER NOT NULL DEFAULT 0,
    emoji_count INTEGER NOT NULL DEFAULT 0,
    active_days INTEGER NOT NULL DEFAULT 0,
    compatibility_score INTEGER NOT NULL,
    breakup_initiator_id VARCHAR(255),
    breakup_reason TEXT,
    breakup_timestamp TIMESTAMP,
    mutual_breakup BOOLEAN NOT NULL DEFAULT FALSE,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    blacklisted BOOLEAN NOT NULL DEFAULT FALSE,
    user1_age INTEGER,
    user1_gender VARCHAR(255),
    user1_region VARCHAR(255),
    user1_rank VARCHAR(255),
    user2_age INTEGER,
    user2_gender VARCHAR(255),
    user2_region VARCHAR(255),
    user2_rank VARCHAR(255)
);
CREATE INDEX IF NOT EXISTS idx_user1_id ON public.pairings(user1_id);
CREATE INDEX IF NOT EXISTS idx_user2_id ON public.pairings(user2_id);
CREATE INDEX IF NOT EXISTS idx_active ON public.pairings(active);
CREATE INDEX IF NOT EXISTS idx_user1_user2 ON public.pairings(user1_id, user2_id);
CREATE INDEX IF NOT EXISTS idx_discord_channel ON public.pairings(discord_channel_id);

CREATE TABLE IF NOT EXISTS public.pair_achievements (
    id BIGSERIAL PRIMARY KEY,
    pairing_id BIGINT NOT NULL,
    achievement_id BIGINT NOT NULL,
    unlocked_at TIMESTAMP NOT NULL,
    progress_value INTEGER,
    xp_awarded INTEGER NOT NULL,
    notified BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_pair_achievements_pairing FOREIGN KEY (pairing_id) REFERENCES public.pairings(id),
    CONSTRAINT fk_pair_achievements_achievement FOREIGN KEY (achievement_id) REFERENCES public.achievements(id),
    CONSTRAINT uk_pair_achievements_pairing_achievement UNIQUE (pairing_id, achievement_id)
);
CREATE INDEX IF NOT EXISTS idx_pair_achievement_pairing ON public.pair_achievements(pairing_id);
CREATE INDEX IF NOT EXISTS idx_pair_achievement_achievement ON public.pair_achievements(achievement_id);
CREATE INDEX IF NOT EXISTS idx_pair_achievement_unlocked ON public.pair_achievements(unlocked_at);
CREATE INDEX IF NOT EXISTS idx_pair_achievement_pair_unlocked ON public.pair_achievements(pairing_id, unlocked_at);

CREATE TABLE IF NOT EXISTS public.pair_levels (
    id BIGSERIAL PRIMARY KEY,
    pairing_id BIGINT NOT NULL UNIQUE,
    current_level INTEGER NOT NULL DEFAULT 1,
    total_xp INTEGER NOT NULL DEFAULT 0,
    current_level_xp INTEGER NOT NULL DEFAULT 0,
    next_level_xp INTEGER NOT NULL DEFAULT 100,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_pair_levels_pairing FOREIGN KEY (pairing_id) REFERENCES public.pairings(id)
);
CREATE INDEX IF NOT EXISTS idx_pair_level_pairing_id ON public.pair_levels(pairing_id);
CREATE INDEX IF NOT EXISTS idx_pair_level_current_level ON public.pair_levels(current_level);
CREATE INDEX IF NOT EXISTS idx_pair_level_total_xp ON public.pair_levels(total_xp);

CREATE TABLE IF NOT EXISTS public.voice_streaks (
    id BIGSERIAL PRIMARY KEY,
    pairing_id BIGINT NOT NULL,
    streak_date DATE NOT NULL,
    voice_minutes INTEGER NOT NULL,
    streak_count INTEGER NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_voice_streaks_pairing FOREIGN KEY (pairing_id) REFERENCES public.pairings(id),
    CONSTRAINT uk_voice_streaks_pairing_date UNIQUE (pairing_id, streak_date)
);
CREATE INDEX IF NOT EXISTS idx_voice_streak_pairing ON public.voice_streaks(pairing_id);
CREATE INDEX IF NOT EXISTS idx_voice_streak_date ON public.voice_streaks(streak_date);
CREATE INDEX IF NOT EXISTS idx_voice_streak_active ON public.voice_streaks(active);

-- Pending Data
CREATE TABLE IF NOT EXISTS public.pending_prisons (
    discord_user_id VARCHAR(255) PRIMARY KEY,
    prisoned_at TIMESTAMP,
    prison_release_at TIMESTAMP,
    updated_at TIMESTAMP
);
CREATE TABLE IF NOT EXISTS public.pending_prison_original_roles (
    discord_user_id VARCHAR(255) NOT NULL,
    role_id VARCHAR(255),
    CONSTRAINT fk_pending_prison_original_roles_user FOREIGN KEY (discord_user_id) REFERENCES public.pending_prisons(discord_user_id)
);
CREATE INDEX IF NOT EXISTS idx_pending_prison_updated_at ON public.pending_prisons(updated_at);

CREATE TABLE IF NOT EXISTS public.pending_role_selections (
    discord_user_id VARCHAR(255) PRIMARY KEY,
    selected_age_role_id VARCHAR(255),
    selected_gender_role_id VARCHAR(255),
    selected_rank_role_id VARCHAR(255),
    selected_region_role_id VARCHAR(255),
    updated_at TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_pending_updated_at ON public.pending_role_selections(updated_at);

-- Roll Audits
CREATE TABLE IF NOT EXISTS public.roll_audits (
    id UUID PRIMARY KEY,
    user_id VARCHAR(255) NOT NULL,
    case_id UUID NOT NULL,
    case_name VARCHAR(255) NOT NULL,
    won_item_id UUID NOT NULL,
    won_item_name VARCHAR(255) NOT NULL,
    roll_value INTEGER NOT NULL,
    roll_seed_hash VARCHAR(255) NOT NULL,
    drop_rate INTEGER NOT NULL,
    total_drop_rates INTEGER NOT NULL,
    case_items_count INTEGER NOT NULL,
    already_owned BOOLEAN NOT NULL,
    client_ip VARCHAR(255),
    user_agent VARCHAR(500),
    session_id VARCHAR(255),
    roll_timestamp TIMESTAMP NOT NULL,
    processing_time_ms BIGINT,
    user_credits_before INTEGER NOT NULL,
    user_credits_after INTEGER NOT NULL,
    verification_status VARCHAR(255) DEFAULT 'PENDING',
    anomaly_flags VARCHAR(500),
    statistical_hash VARCHAR(255)
);
CREATE INDEX IF NOT EXISTS idx_roll_audit_user_id ON public.roll_audits(user_id);
CREATE INDEX IF NOT EXISTS idx_roll_audit_case_id ON public.roll_audits(case_id);
CREATE INDEX IF NOT EXISTS idx_roll_audit_timestamp ON public.roll_audits(roll_timestamp);
CREATE INDEX IF NOT EXISTS idx_roll_audit_session ON public.roll_audits(session_id);

-- User Inventory
CREATE TABLE IF NOT EXISTS public.user_inventory (
    user_id VARCHAR(255) NOT NULL,
    item_id UUID NOT NULL,
    CONSTRAINT fk_user_inventory_user FOREIGN KEY (user_id) REFERENCES public.users(id),
    CONSTRAINT fk_user_inventory_item FOREIGN KEY (item_id) REFERENCES public.shop_items(id),
    PRIMARY KEY (user_id, item_id)
);
CREATE TABLE IF NOT EXISTS public.user_inventory_items (
    id UUID PRIMARY KEY,
    user_id VARCHAR(255) NOT NULL,
    item_id UUID NOT NULL,
    quantity INTEGER NOT NULL DEFAULT 1,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_user_inventory_items_user FOREIGN KEY (user_id) REFERENCES public.users(id),
    CONSTRAINT fk_user_inventory_items_item FOREIGN KEY (item_id) REFERENCES public.shop_items(id),
    CONSTRAINT uk_user_inventory_user_item UNIQUE (user_id, item_id)
);
CREATE INDEX IF NOT EXISTS idx_user_inventory_user_id ON public.user_inventory_items(user_id);
CREATE INDEX IF NOT EXISTS idx_user_inventory_item_id ON public.user_inventory_items(item_id);
CREATE INDEX IF NOT EXISTS idx_user_inventory_user_item ON public.user_inventory_items(user_id, item_id); 