CREATE TABLE public.organization_tags (
    organization_id uuid NOT NULL,
    tag_id uuid NOT NULL
);

ALTER TABLE ONLY public.organization_tags
    ADD CONSTRAINT pk_organization_tags PRIMARY KEY (organization_id, tag_id);

ALTER TABLE ONLY public.organization_tags
    ADD CONSTRAINT fk_organization_tags_organization FOREIGN KEY (organization_id) REFERENCES public.organizations(id) ON DELETE CASCADE;

ALTER TABLE ONLY public.organization_tags
    ADD CONSTRAINT fk_organization_tags_tag FOREIGN KEY (tag_id) REFERENCES public.tags(id) ON DELETE CASCADE;
