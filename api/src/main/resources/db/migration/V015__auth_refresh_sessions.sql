-- V015__auth_refresh_sessions.sql
-- Stateful refresh-session rotation + token-family revocation.

CREATE TABLE public.user_refresh_sessions (
    id uuid NOT NULL,
    user_id uuid NOT NULL,
    family_id uuid NOT NULL,
    parent_session_id uuid,
    token_hash character varying(128) NOT NULL,
    issued_at timestamp with time zone DEFAULT now() NOT NULL,
    expires_at timestamp with time zone NOT NULL,
    revoked_at timestamp with time zone,
    revoked_reason character varying(120),
    replaced_by_session_id uuid
);

ALTER TABLE ONLY public.user_refresh_sessions
    ADD CONSTRAINT pk_user_refresh_sessions PRIMARY KEY (id);

ALTER TABLE ONLY public.user_refresh_sessions
    ADD CONSTRAINT fk_user_refresh_sessions_user FOREIGN KEY (user_id) REFERENCES public.users(id) ON DELETE CASCADE;

CREATE UNIQUE INDEX uq_user_refresh_sessions_token_hash ON public.user_refresh_sessions USING btree (token_hash);
CREATE INDEX idx_user_refresh_sessions_user ON public.user_refresh_sessions USING btree (user_id);
CREATE INDEX idx_user_refresh_sessions_family ON public.user_refresh_sessions USING btree (family_id);
CREATE INDEX idx_user_refresh_sessions_expires ON public.user_refresh_sessions USING btree (expires_at);
