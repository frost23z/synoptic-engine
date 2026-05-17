# Settings — Pipelines and Stages

## User Stories

- As a user I can view a list of all pipelines.
- As a user I can create a new pipeline with a name and stages.
- As a user I can mark a pipeline as the default.
- As a user I can edit a pipeline: rename it, change stages, add/remove stages, set rotten_days.
- As a user I can reorder pipeline stages by dragging.
- As a user I can delete a pipeline (unless it is the default; leads are migrated to default pipeline).
- As a user I can configure a "rotten days" threshold per pipeline (leads older than this are marked rotten).

---

## API Endpoints

| Method | Route name | Path | Description |
|--------|-----------|------|-------------|
| GET | admin.settings.pipelines.index | `GET /settings/pipelines` | List (DataGrid) |
| GET | admin.settings.pipelines.create | `GET /settings/pipelines/create` | Create form |
| POST | admin.settings.pipelines.store | `POST /settings/pipelines/create` | Create pipeline |
| GET | admin.settings.pipelines.edit | `GET /settings/pipelines/edit/{id?}` | Edit form |
| POST | admin.settings.pipelines.update | `POST /settings/pipelines/edit/{id}` | Update pipeline (note: POST not PUT) |
| DELETE | admin.settings.pipelines.delete | `DELETE /settings/pipelines/{id}` | Delete pipeline |

---

### Request Bodies

**POST /settings/pipelines/create (PipelineForm):**
```
name         string   required
is_default   boolean  optional  (checkbox presence = 1, absence = 0)
rotten_days  int      optional
stages       array    required
stages[stage_ID].name        string  required
stages[stage_ID].code        string  required  (e.g. 'new', 'won', 'lost')
stages[stage_ID].probability int     optional  0–100
stages[stage_ID].sort_order  int     optional
```

For new stages, use a key like `stages[stage_1]`, `stages[stage_2]`.

**POST /settings/pipelines/edit/{id}:**
Same fields. Existing stages use their database ID as key; new stages use `stage_X` prefix.

On update:
- Stages no longer in the payload are deleted.
- Any lead in a deleted stage is moved to the pipeline's first remaining stage.
- If a stage is deleted and leads were in it, they are reassigned.

---

## DB Schema

### `lead_pipelines`

| Column | Type | Nullable | Default | Notes |
|--------|------|----------|---------|-------|
| id | int unsigned | no | auto | PK |
| name | varchar | no | | unique |
| is_default | tinyint | no | 0 | only one pipeline should be default |
| rotten_days | int | yes | | days before lead is considered rotten |
| created_at | timestamp | | | |
| updated_at | timestamp | | | |

### `lead_pipeline_stages`

| Column | Type | Nullable | Default | Notes |
|--------|------|----------|---------|-------|
| id | int unsigned | no | auto | PK |
| name | varchar | | | stage display name |
| code | varchar | | | machine code; `won` and `lost` are terminal |
| probability | int | no | 0 | 0–100 |
| sort_order | int | no | 0 | display order |
| lead_stage_id | int unsigned | no | | FK → lead_stages (global template) |
| lead_pipeline_id | int unsigned | no | | FK → lead_pipelines ON DELETE CASCADE |

### `lead_stages` (global template)

| Column | Type | Notes |
|--------|------|-------|
| id | int unsigned | PK |
| name | varchar | |
| code | varchar | special: `won`, `lost` |
| sort_order | int | |

---

## Business Rules

1. **Only one default pipeline:** On create/update with `is_default = 1`, all other pipelines are set to `is_default = 0`.
2. **Cannot delete default pipeline:** Returns 400 if `pipeline->is_default` is true.
3. **Default pipeline fallback:** `getDefaultPipeline()` finds the first pipeline where `is_default = 1`; falls back to `first()` if none.
4. **Lead migration on pipeline delete:** All leads in the deleted pipeline are moved to the default pipeline's first stage.
5. **Lead migration on stage delete:** Leads in a deleted stage within an updated pipeline are moved to the pipeline's first remaining stage.
6. **Stage codes `won` and `lost` are terminal:** When a lead is moved to these stages, `closed_at` is set to now. Moving away clears it.
7. **Rotten leads:** Computed at query time as `DATEDIFF(NOW(), leads.created_at) >= lead_pipelines.rotten_days`. Won/lost leads are excluded.
8. **Events fired:** `settings.pipeline.create.before/after`, `settings.pipeline.update.before/after`, `settings.pipeline.delete.before/after`.

---

## Permissions

| Action | ACL Key |
|--------|---------|
| View list | `settings.lead.pipelines` |
| Create | `settings.lead.pipelines.create` |
| Edit | `settings.lead.pipelines.edit` |
| Delete | `settings.lead.pipelines.delete` |
