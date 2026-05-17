# Authentication

## User Stories

- As a user I can log in with email and password.
- As a user I can choose "remember me" to keep a long-lived session.
- As a user I can log out.
- As a user I can request a password-reset email if I forget my password.
- As a user I can follow a token link from the reset email and set a new password.
- As a user I can edit my own account (name, email, password, profile image).

## API Endpoints

All routes are under the `admin` prefix (the system is a traditional Laravel web app, not a JSON API at this layer, but most actions return JSON when called via AJAX).

### Session (Login/Logout)

| Method | Path | Description |
|--------|------|-------------|
| GET | `/login` | Show login form |
| POST | `/login` | Authenticate user |
| DELETE | `/logout` | Destroy session |

**POST /login request body:**
```
email        string  required  valid email
password     string  required
remember     boolean optional  sets remember-me cookie
```

**POST /login business rules:**
1. Attempts `auth()->guard('user')->attempt(['email', 'password'], remember)`.
2. If credentials fail → redirect back with error.
3. If user `status == 0` → logs them out immediately, shows activation warning.
4. If user lacks `dashboard` permission AND has no accessible menu items → logs out with "no permission" error.
5. On success → redirect to intended URL (if accessible) or first available menu item.

### Forgot Password

| Method | Path | Description |
|--------|------|-------------|
| GET | `/forget-password` | Show forgot-password form |
| POST | `/forget-password` | Send reset link email |

**POST /forget-password request body:**
```
email  string  required  valid email
```

Uses the `users` password broker. Sends a `UserResetPassword` notification.

### Reset Password

| Method | Path | Description |
|--------|------|-------------|
| GET | `/reset-password/{token}` | Show reset form (token + email as query param) |
| POST | `/reset-password` | Set new password |

**POST /reset-password request body:**
```
token                 string  required
email                 string  required  valid email
password              string  required  min 6 chars
password_confirmation string  required  must match password
```

On success: logs the user in and redirects to dashboard.

### Account Management

| Method | Path | Description |
|--------|------|-------------|
| GET | `/account/edit` | Show account edit form |
| PUT | `/account/update` | Update own account |

**PUT /account/update request body:**
```
name                 string    required
email                string    required, unique (excluding self), valid email
current_password     string    required, min 6
password             string    nullable, min 6
password_confirmation string   required if password present, must match
image                file[]    nullable, mimes: bmp, jpeg, jpg, png, webp
```

**Business rules:**
- `current_password` is verified with `Hash::check` — if wrong, redirected back with warning.
- `role_id` and `view_permission` are silently ignored to prevent privilege escalation.
- If `isPasswordChanged`, fires `user.account.update-password` event.
- Profile image stored at `admins/{userId}`.

## DB Schema

### `users` table

| Column | Type | Nullable | Default | Notes |
|--------|------|----------|---------|-------|
| id | int unsigned | no | auto | PK |
| name | varchar | no | | |
| email | varchar | no | | unique |
| password | varchar | yes | | bcrypt hash |
| status | tinyint(1) | no | 0 | 1 = active |
| view_permission | varchar | yes | global | global / group / individual |
| role_id | int unsigned | no | | FK → roles |
| image | varchar | yes | | file path |
| remember_token | varchar | yes | | |
| created_at | timestamp | yes | | |
| updated_at | timestamp | yes | | |

### `user_password_resets` table

| Column | Type | Notes |
|--------|------|-------|
| email | varchar | indexed |
| token | varchar | hashed reset token |
| created_at | timestamp | |

## Permissions

No ACL check on login/logout/forgot-password/reset (middleware `withoutMiddleware(['user'])`).

Account edit/update requires only being authenticated (no specific ACL key).
