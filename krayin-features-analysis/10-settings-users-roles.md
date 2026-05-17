# Settings — Users, Roles, Groups, and View Permissions

## User Stories

### Users
- As an admin I can list all users.
- As an admin I can create a new user with name, email, password, role, groups, status, and view permission.
- As an admin I can edit a user's details (including password change).
- As an admin I can search users by keyword.
- As an admin I can activate/deactivate users.
- As an admin I can mass-activate or mass-deactivate users.
- As an admin I can delete a user (cannot delete the last user or yourself).
- As an admin I can mass-delete users (cannot delete self).
- When a user is created, they receive an email notification with their credentials.

### Roles
- As an admin I can list all roles.
- As an admin I can create a role with a permission type (all permissions or custom) and select specific permissions.
- As an admin I can edit a role's permissions.
- As an admin I can delete a role (blocked if role has assigned users or is the last role or is the current user's role).

### Groups
- As an admin I can list all groups.
- As an admin I can create a group with a name and description.
- As an admin I can edit a group.
- As an admin I can delete a group.

### View Permissions
- Each user has a `view_permission` field controlling which records they see:
  - `global` — all records
  - `group` — records from all members of their groups
  - `individual` — only their own records

---

## API Endpoints

### Users

| Method | Route name | Path | Description |
|--------|-----------|------|-------------|
| GET | admin.settings.users.index | `GET /settings/users` | List (DataGrid) |
| POST | admin.settings.users.store | `POST /settings/users/create` | Create user |
| GET | admin.settings.users.edit | `GET /settings/users/edit/{id?}` | Get user data (JSON) |
| PUT | admin.settings.users.update | `PUT /settings/users/edit/{id}` | Update user |
| GET | admin.settings.users.search | `GET /settings/users/search` | Autocomplete search |
| DELETE | admin.settings.users.delete | `DELETE /settings/users/{id}` | Delete user |
| POST | admin.settings.users.mass_update | `POST /settings/users/mass-update` | Mass activate/deactivate |
| POST | admin.settings.users.mass_delete | `POST /settings/users/mass-destroy` | Mass delete |

### Roles

| Method | Route name | Path | Description |
|--------|-----------|------|-------------|
| GET | admin.settings.roles.index | `GET /settings/roles` | List (DataGrid) |
| GET | admin.settings.roles.create | `GET /settings/roles/create` | Create form |
| POST | admin.settings.roles.store | `POST /settings/roles/create` | Create role |
| GET | admin.settings.roles.edit | `GET /settings/roles/edit/{id}` | Edit form |
| PUT | admin.settings.roles.update | `PUT /settings/roles/edit/{id}` | Update role |
| DELETE | admin.settings.roles.delete | `DELETE /settings/roles/{id}` | Delete role |

### Groups

| Method | Route name | Path | Description |
|--------|-----------|------|-------------|
| GET | admin.settings.groups.index | `GET /settings/groups` | List (DataGrid) |
| POST | admin.settings.groups.store | `POST /settings/groups/create` | Create group |
| GET | admin.settings.groups.edit | `GET /settings/groups/edit/{id}` | Edit form |
| PUT | admin.settings.groups.update | `PUT /settings/groups/edit/{id}` | Update group |
| DELETE | admin.settings.groups.delete | `DELETE /settings/groups/{id}` | Delete group |

---

### Request Bodies

**POST /settings/users/create:**
```
name              string   required
email             string   required  unique:users,email
password          string   nullable
confirm_password  string   nullable  required_with:password, must match
role_id           int      required  FK → roles
status            boolean  optional  in: 0, 1
view_permission   string   optional  in: global, group, individual
groups            int[]    optional  group IDs
```
Password is bcrypt-hashed before storage. If no password provided, user account has no password until they set one.

**PUT /settings/users/edit/{id}:**
```
name              string   required
email             string   required  unique excluding self
password          string   nullable  min 6
confirm_password  nullable required_with:password
role_id           int      required  exists:roles
status            boolean  nullable
view_permission   string   required  in: global, group, individual
groups            int[]    optional
```
Logged-in user cannot change their own status to inactive.

**POST /settings/users/mass-update:**
```
indices  int[]  required  user IDs (self excluded)
value    int    required  0 or 1 (status)
```

**POST /settings/roles/create:**
```
name             string   required
description      string   required
permission_type  string   required  in: all, custom
permissions      array    required if permission_type = custom  array of permission key strings
```

**PUT /settings/roles/edit/{id}:**
```
name             string   required
description      string   required
permission_type  string   required  in: all, custom
permissions      array    required_if:permission_type,custom
```
If `permissions` absent, stored as empty array.

**POST /settings/groups/create:**
```
name         string   required
description  string   optional
```

---

## DB Schema

### `users`

(Full schema in `01-auth.md` — see there for all columns.)

Key columns for this section:
- `role_id` — FK → roles
- `view_permission` — global / group / individual
- `status` — 0 = inactive, 1 = active
- `image` — profile photo path

### `roles`

| Column | Type | Nullable | Default | Notes |
|--------|------|----------|---------|-------|
| id | int unsigned | no | auto | PK |
| name | varchar | no | | |
| description | varchar | yes | | |
| permission_type | varchar | no | | `all` or `custom` |
| permissions | json | yes | | array of permission key strings |
| created_at | timestamp | | | |
| updated_at | timestamp | | | |

### `groups`

| Column | Type | Nullable | Notes |
|--------|------|----------|-------|
| id | int unsigned | no | PK |
| name | varchar | no | unique |
| description | varchar | yes | |
| created_at | timestamp | | |
| updated_at | timestamp | | |

### `user_groups`

| Column | Type | Notes |
|--------|------|-------|
| group_id | int unsigned | FK → groups ON DELETE CASCADE |
| user_id | int unsigned | FK → users ON DELETE CASCADE |

---

## View Permission Scoping Logic

`bouncer()->getAuthorizedUserIds()` returns:

| view_permission | Returns |
|----------------|---------|
| `global` | `null` (no filter — sees all) |
| `group` | Array of user IDs who are in any of the current user's groups |
| `individual` | `[currentUserId]` |

When `null` is returned, no `whereIn` filter is applied (global access). When an array is returned, queries filter `{entity}.user_id IN (...)`.

This is applied to: leads, persons, organizations, quotes, and email-associated leads.

---

## Business Rules

1. **Cannot delete last user:** If only 1 user exists, delete returns 400.
2. **Cannot delete self:** Self-deletion is blocked (both individual and mass delete).
3. **Cannot delete role in use:** If role has assigned users, delete returns 400.
4. **Cannot delete last role:** Returns 400.
5. **Cannot delete current user's role:** Returns 400.
6. **Status forced active for self:** Admin cannot deactivate their own account via update (status is forced true for self).
7. **New user email:** `UserCreatedNotification` is sent via `Mail::queue` when creating a user. Errors are caught and reported but don't fail the request.
8. **Password bcrypt:** Stored hashed; comparison uses `Hash::check`.
9. **Groups sync:** `admin->groups()->sync($data['groups'] ?? [])` — replaces group memberships on create/update.
10. **Events fired:** `settings.user.create/update/delete.before/after`.

---

## Permissions

| Action | ACL Key |
|--------|---------|
| View users list | `settings.user.users` |
| Create user | `settings.user.users.create` |
| Edit user | `settings.user.users.edit` |
| Mass update users | `settings.user.users.edit` |
| Delete user | `settings.user.users.delete` |
| Mass delete users | `settings.user.users.delete` |
| View roles list | `settings.user.roles` |
| Create role | `settings.user.roles.create` |
| Edit role | `settings.user.roles.edit` |
| Delete role | `settings.user.roles.delete` |
| View groups list | `settings.user.groups` |
| Create group | `settings.user.groups.create` |
| Edit group | `settings.user.groups.edit` |
| Delete group | `settings.user.groups.delete` |
