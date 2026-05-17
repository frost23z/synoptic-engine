# Dashboard

## User Stories

- As a user I can view an overview dashboard with key metrics for a date range.
- As a user I can see total won revenue and total lost revenue with percentage change vs previous period.
- As a user I can see total leads, average lead value, average leads per day, total quotations, total persons, and total organizations with trends.
- As a user I can view total leads over time (chart), won leads over time, and lost leads over time.
- As a user I can see revenue broken down by lead source.
- As a user I can see revenue broken down by lead type.
- As a user I can see open leads grouped by pipeline stage.
- As a user I can see the top 5 best-selling products by revenue.
- As a user I can see the top 5 persons by revenue.
- As a user I can change the date range filter to see metrics for a custom period.

---

## API Endpoints

| Method | Route | Path | Description |
|--------|-------|------|-------------|
| GET | admin.dashboard.index | `GET /dashboard` | Dashboard HTML page |
| GET | admin.dashboard.stats | `GET /dashboard/stats` | Fetch stats JSON |

### GET /dashboard/stats

Query param `type` selects the stat group. Returns `{ statistics: ..., date_range: "01 Jan - 31 Jan" }`.

| `type` value | Data returned |
|-------------|---------------|
| `over-all` | 6 KPI metrics with current/previous/progress |
| `revenue-stats` | Won and lost revenue totals with progress |
| `total-leads` | 3 time-series charts: all / won / lost leads over time |
| `revenue-by-sources` | Won lead value grouped by lead source |
| `revenue-by-types` | Won lead value grouped by lead type |
| `top-selling-products` | Top 5 products by won lead revenue |
| `top-persons` | Top 5 persons by won lead revenue |
| `open-leads-by-states` | Count of open leads (not won/lost) by stage |

---

## Data Structures

### over-all (getOverAllStats)

```json
{
  "total_leads": {
    "previous": 45,
    "current": 60,
    "progress": 33.3
  },
  "average_lead_value": {
    "previous": 1200.00,
    "current": 1500.00,
    "formatted_total": "$1,500.00",
    "progress": 25.0
  },
  "average_leads_per_day": {
    "previous": 1.5,
    "current": 2.0,
    "progress": 33.3
  },
  "total_quotations": {
    "previous": 12,
    "current": 18,
    "progress": 50.0
  },
  "total_persons": { ... },
  "total_organizations": { ... }
}
```

### revenue-stats (getRevenueStats)

```json
{
  "total_won_revenue": {
    "previous": 50000.00,
    "current": 75000.00,
    "formatted_total": "$75,000.00",
    "progress": 50.0
  },
  "total_lost_revenue": { ... }
}
```

### total-leads (getTotalLeadsStats)

```json
{
  "all": {
    "over_time": [
      { "label": "Jan 01", "count": 3, "total": 4500.00 },
      ...
    ]
  },
  "won": {
    "over_time": [ ... ]
  },
  "lost": {
    "over_time": [ ... ]
  }
}
```

The `over_time` array uses `created_at` for all leads and won leads, `closed_at` for won/lost.

### revenue-by-sources / revenue-by-types

```json
[
  { "name": "Cold Call", "total": 25000.00 },
  { "name": "Web", "total": 15000.00 }
]
```

### open-leads-by-states

```json
[
  { "name": "New", "total": 23 },
  { "name": "In Discussion", "total": 15 }
]
```
Stages with `code = 'won'` or `code = 'lost'` are excluded.

### top-selling-products

```json
[
  { "name": "Widget Pro", "revenue": 45000.00 },
  ...
]
```
Top 5 by `SUM(lead_value)` for won leads containing that product.

### top-persons

```json
[
  { "name": "John Doe", "revenue": 30000.00 },
  ...
]
```
Top 5 persons by `SUM(lead_value)` for won leads.

---

## Date Range Logic

The `AbstractReporting` base class resolves start/end dates from request params (`start_date`, `end_date`). If not provided, defaults to the current month.

For "previous period" comparison, the same duration is used, shifted back in time.

### Auto Period Selection for Charts

The `Lead` reporting class selects the chart grouping automatically:

| Date range | Period |
|-----------|--------|
| > 3 years | year |
| > 6 months | month |
| > 60 days | week |
| Otherwise | day |

---

## Business Logic

1. **Won leads:** Identified by `lead_pipeline_stage_id IN (stages where code = 'won')`.
2. **Lost leads:** Identified by `lead_pipeline_stage_id IN (stages where code = 'lost')`.
3. **Open leads:** All leads NOT in won or lost stages.
4. **Revenue = lead_value:** Sum of `lead_value` for won leads in the date range.
5. **Quotations:** Count of quotes in the date range.
6. **Average leads per day:** `count / diffInDays(start, end)`; returns 0 if range = 0 days.
7. **Progress %:** `(current - previous) / previous * 100`; returns 0 if previous = 0.

---

## Permissions

| Access | ACL Key |
|--------|---------|
| View dashboard | `dashboard` |
