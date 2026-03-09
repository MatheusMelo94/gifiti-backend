give # Gifiti API Reference

> **For frontend engineers.** This document covers every endpoint, request/response format, authentication flow, and error handling in the Gifiti backend.

**Base URL:** `http://localhost:8080` (development) | `https://api.gifiti.app` (production)
**Content-Type:** All requests and responses use `application/json`
**Swagger UI:** Available at `/swagger-ui.html` when running locally

---

## Table of Contents

1. [Authentication Flow](#authentication-flow)
2. [Auth Endpoints](#auth-endpoints)
3. [Wishlist Endpoints](#wishlist-endpoints)
4. [Wishlist Item Endpoints](#wishlist-item-endpoints)
5. [Public Wishlist Endpoints](#public-wishlist-endpoints)
6. [Gifter Reservations Endpoints](#gifter-reservations-endpoints)
7. [Profile Endpoints](#profile-endpoints)
8. [Enums](#enums)
9. [Error Handling](#error-handling)
10. [Security Notes](#security-notes)
11. [Frontend Pages Required](#frontend-pages-required)

---

## Authentication Flow

```
1. User registers        → POST /auth/register
2. User receives email   → clicks verification link
3. Frontend calls        → POST /auth/verify-email { token }
4. User logs in          → POST /auth/login → receives accessToken + refreshToken
5. All API calls         → Authorization: Bearer <accessToken>
6. Token expires (1h)    → POST /auth/refresh { refreshToken } → new accessToken
```

### How to attach the token

```
Authorization: Bearer eyJhbGciOiJIUzI1NiJ9...
```

Every endpoint under `/api/v1/wishlists`, `/api/v1/public`, `/api/v1/reservations`, and `/api/v1/profile` requires this header. Without it, you'll get a `401 Unauthorized`.

---

## Auth Endpoints

**Base path:** `/api/v1/auth`
**Authentication:** Not required (except resend-verification)

---

### POST `/api/v1/auth/register`

Creates a new user account and sends a verification email.

**Request:**
```json
{
  "email": "jane@example.com",
  "password": "MySecureP@ss1",
  "displayName": "Jane Doe"
}
```

| Field | Type | Required | Validation |
|-------|------|----------|------------|
| `email` | string | Yes | Valid email, max 254 chars |
| `password` | string | Yes | 12-128 chars, must have: uppercase, lowercase, digit, special char (`@$!%*?&._#^()-+=`) |
| `displayName` | string | No | Max 50 chars. Derived from email if absent |

**Response:** `201 Created`
```json
{
  "id": "65f1a2b3c4d5e6f7a8b9c0d1",
  "email": "jane@example.com",
  "displayName": "Jane Doe",
  "message": "Registration successful"
}
```

**Errors:**
- `400` — Validation error (see [Error Handling](#error-handling))
- `409` — Email already registered

---

### POST `/api/v1/auth/login`

Authenticates a user and returns JWT tokens.

**Request:**
```json
{
  "email": "jane@example.com",
  "password": "MySecureP@ss1"
}
```

**Response:** `200 OK`
```json
{
  "user": {
    "id": "65f1a2b3c4d5e6f7a8b9c0d1",
    "email": "jane@example.com",
    "displayName": "Jane Doe",
    "roles": ["USER"]
  },
  "accessToken": "eyJhbGciOiJIUzI1NiJ9...",
  "refreshToken": "eyJhbGciOiJIUzI1NiJ9...",
  "expiresIn": 3600
}
```

| Response Field | Description |
|----------------|-------------|
| `accessToken` | Use in `Authorization: Bearer` header. Expires in 1 hour |
| `refreshToken` | Use to get a new access token. Expires in 7 days |
| `expiresIn` | Access token TTL in seconds |

**Errors:**
- `401` — Invalid email or password

---

### POST `/api/v1/auth/refresh`

Gets a new access token using a refresh token.

**Request:**
```json
{
  "refreshToken": "eyJhbGciOiJIUzI1NiJ9..."
}
```

**Response:** `200 OK` — Same format as login response.

**Errors:**
- `401` — Invalid or expired refresh token

---

### POST `/api/v1/auth/logout`

**Authentication: Required**

Invalidates the access token (and optionally the refresh token) server-side. Both tokens are immediately rejected on subsequent requests.

**Request:**
```
Authorization: Bearer <accessToken>
```
```json
{
  "refreshToken": "eyJhbGciOiJIUzI1NiJ9..."
}
```

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `refreshToken` | string | No | Send it to invalidate both tokens. If omitted, only the access token is blacklisted |

**Response:** `200 OK`
```json
{
  "message": "Logged out successfully"
}
```

**Frontend implementation:**
```js
// 1. Call logout endpoint
await fetch('/api/v1/auth/logout', {
  method: 'POST',
  headers: {
    'Authorization': `Bearer ${accessToken}`,
    'Content-Type': 'application/json'
  },
  body: JSON.stringify({ refreshToken })
});

// 2. Clear stored tokens (always do this, even if step 1 fails)
localStorage.removeItem('accessToken');
localStorage.removeItem('refreshToken');

// 3. Redirect to login
window.location.href = '/login';
```

---

### POST `/api/v1/auth/verify-email`

Verifies a user's email address using the token from the verification email.

**Request:**
```json
{
  "token": "550e8400-e29b-41d4-a716-446655440000"
}
```

**Response:** `200 OK`
```json
{
  "message": "Email verified successfully"
}
```

**Errors:**
- `400` — Invalid or expired token

**Frontend note:** The verification email contains a link like `{APP_BASE_URL}/verify-email?token=xxx`. Your frontend needs a `/verify-email` page that reads the `token` query param and calls this endpoint.

---

### POST `/api/v1/auth/resend-verification`

**Authentication: Required**

Resends the verification email to the logged-in user.

**Request:** Empty body

**Response:** `200 OK`
```json
{
  "message": "Verification email sent"
}
```

---

### POST `/api/v1/auth/forgot-password`

Sends a password reset email. **Always returns the same response** regardless of whether the email exists (prevents email enumeration).

**Request:**
```json
{
  "email": "jane@example.com"
}
```

**Response:** `200 OK`
```json
{
  "message": "If an account with that email exists, a password reset link has been sent"
}
```

**Frontend note:** The reset email contains a link like `{APP_BASE_URL}/reset-password?token=xxx`. Your frontend needs a `/reset-password` page.

---

### POST `/api/v1/auth/reset-password`

Resets the user's password using the token from the reset email.

**Request:**
```json
{
  "token": "550e8400-e29b-41d4-a716-446655440000",
  "newPassword": "MyNewSecureP@ss1"
}
```

| Field | Type | Required | Validation |
|-------|------|----------|------------|
| `token` | string | Yes | From reset email |
| `newPassword` | string | Yes | Same rules as registration password |

**Response:** `200 OK`
```json
{
  "message": "Password reset successfully"
}
```

**Errors:**
- `400` — Invalid/expired token or password validation failure

---

## Wishlist Endpoints

**Base path:** `/api/v1/wishlists`
**Authentication:** Required for all

---

### GET `/api/v1/wishlists`

Lists the authenticated user's wishlists with pagination and optional category filter.

**Query Parameters:**

| Param | Type | Default | Validation |
|-------|------|---------|------------|
| `page` | int | 0 | Min 0 |
| `size` | int | 20 | Min 1, Max 100 |
| `category` | string | — | One of: `BIRTHDAY`, `CHRISTMAS`, `WEDDING`, `BABY_SHOWER`, `GRADUATION`, `HOUSEWARMING`, `OTHER` |

**Example:** `GET /api/v1/wishlists?page=0&size=10&category=BIRTHDAY`

**Response:** `200 OK`
```json
{
  "wishlists": [
    {
      "id": "65f1a2b3c4d5e6f7a8b9c0d1",
      "title": "Birthday 2026",
      "description": "Things I'd love for my birthday",
      "visibility": "PUBLIC",
      "shareableId": "V1StGXR8_Z5jdHi6B-myT",
      "eventDate": "2026-06-15",
      "category": "BIRTHDAY",
      "itemCount": 5,
      "createdAt": "2026-03-08T14:30:00Z",
      "updatedAt": "2026-03-08T15:00:00Z"
    }
  ],
  "totalElements": 1,
  "totalPages": 1,
  "currentPage": 0,
  "size": 10
}
```

---

### POST `/api/v1/wishlists`

Creates a new wishlist. **Requires verified email.**

**Request:**
```json
{
  "title": "Birthday 2026",
  "description": "Things I'd love for my birthday",
  "visibility": "PUBLIC",
  "eventDate": "2026-06-15",
  "category": "BIRTHDAY"
}
```

| Field | Type | Required | Validation |
|-------|------|----------|------------|
| `title` | string | Yes | Max 100 chars |
| `description` | string | No | Max 500 chars |
| `visibility` | string | No | `PRIVATE` (default) or `PUBLIC` |
| `eventDate` | string | No | Format: `YYYY-MM-DD` |
| `category` | string | No | See [Enums](#enums) |

**Response:** `201 Created` — Returns `WishlistResponse` (same format as list above)

**Errors:**
- `400` — Validation error
- `403` — Email not verified

---

### GET `/api/v1/wishlists/{id}`

Gets a specific wishlist. Only the owner can access it.

**Response:** `200 OK` — `WishlistResponse`

**Errors:**
- `403` — Not the owner
- `404` — Not found

---

### PUT `/api/v1/wishlists/{id}`

Updates a wishlist. All fields are optional (partial update).

**Request:**
```json
{
  "title": "Updated Title",
  "category": "CHRISTMAS"
}
```

**Response:** `200 OK` — `WishlistResponse`

---

### DELETE `/api/v1/wishlists/{id}`

Deletes a wishlist **and all its items and reservations** (cascade delete).

**Response:** `204 No Content`

---

### POST `/api/v1/wishlists/{id}/rotate-link`

Generates a new shareable ID. The old link immediately stops working.

**Response:** `200 OK` — `WishlistResponse` with new `shareableId`

---

## Wishlist Item Endpoints

**Base path:** `/api/v1/wishlists/{wishlistId}/items`
**Authentication:** Required for all

---

### GET `/api/v1/wishlists/{wishlistId}/items`

Lists items in a wishlist with pagination.

**Query Parameters:**

| Param | Type | Default | Validation |
|-------|------|---------|------------|
| `page` | int | 0 | Min 0 |
| `size` | int | 20 | Min 1, Max 100 |

**Response:** `200 OK`
```json
{
  "items": [
    {
      "id": "item123",
      "wishlistId": "65f1a2b3c4d5e6f7a8b9c0d1",
      "name": "Sony WH-1000XM5 Headphones",
      "description": "Noise-cancelling, black color",
      "productLink": "https://www.amazon.com/dp/B0BX2L8PBT",
      "imageUrl": "https://images.example.com/headphones.jpg",
      "price": 349.99,
      "priority": "HIGH",
      "quantity": 1,
      "reservedQuantity": 0,
      "remainingQuantity": 1,
      "status": "AVAILABLE",
      "createdAt": "2026-03-08T14:30:00Z",
      "updatedAt": "2026-03-08T14:30:00Z"
    }
  ],
  "totalElements": 1,
  "totalPages": 1,
  "currentPage": 0,
  "size": 20
}
```

---

### POST `/api/v1/wishlists/{wishlistId}/items`

Adds an item to a wishlist.

**Request:**
```json
{
  "name": "Sony WH-1000XM5 Headphones",
  "description": "Noise-cancelling, black color",
  "productLink": "https://www.amazon.com/dp/B0BX2L8PBT",
  "imageUrl": "https://images.example.com/headphones.jpg",
  "price": 349.99,
  "priority": "HIGH",
  "quantity": 1
}
```

| Field | Type | Required | Validation |
|-------|------|----------|------------|
| `name` | string | Yes | Max 200 chars |
| `description` | string | No | Max 1000 chars |
| `productLink` | string | No | Valid `http://` or `https://` URL |
| `imageUrl` | string | No | Valid `http://` or `https://` URL |
| `price` | number | No | Positive |
| `priority` | string | No | `LOW`, `MEDIUM` (default), `HIGH` |
| `quantity` | int | No | Positive, default 1 |

**Response:** `201 Created` — `WishlistItemResponse`

---

### GET `/api/v1/wishlists/{wishlistId}/items/{itemId}`

**Response:** `200 OK` — `WishlistItemResponse`

---

### PUT `/api/v1/wishlists/{wishlistId}/items/{itemId}`

Partial update. All fields optional.

**Response:** `200 OK` — `WishlistItemResponse`

---

### DELETE `/api/v1/wishlists/{wishlistId}/items/{itemId}`

Deletes an item and its reservations.

**Response:** `204 No Content`

---

### DELETE `/api/v1/wishlists/{wishlistId}/items/{itemId}/reservation`

Owner cancels a reservation on their item (unreserves it).

**Response:** `200 OK`
```json
{
  "itemId": "item123",
  "message": "Reservation cancelled successfully",
  "reserved": false
}
```

---

## Public Wishlist Endpoints

**Base path:** `/api/v1/public/wishlists`
**Authentication:** Required (gifter must be logged in)

These are the endpoints guests/gifters use when they open a shared link.

---

### GET `/api/v1/public/wishlists/{shareableId}`

Views a shared wishlist. The `shareableId` is the NanoID from the shareable link (e.g., `V1StGXR8_Z5jdHi6B-myT`).

**Response:** `200 OK`
```json
{
  "shareableId": "V1StGXR8_Z5jdHi6B-myT",
  "title": "Birthday 2026",
  "description": "Things I'd love for my birthday",
  "ownerDisplayName": "Jane Doe",
  "eventDate": "2026-06-15",
  "itemCount": 3,
  "items": [
    {
      "id": "item123",
      "name": "Sony WH-1000XM5 Headphones",
      "description": "Noise-cancelling, black color",
      "productLink": "https://www.amazon.com/dp/B0BX2L8PBT",
      "imageUrl": "https://images.example.com/headphones.jpg",
      "price": 349.99,
      "priority": "HIGH",
      "quantity": 1,
      "reservedQuantity": 0,
      "remainingQuantity": 1,
      "status": "AVAILABLE"
    }
  ]
}
```

**Privacy:** The response does NOT include who reserved items — only the status and quantities.

**Errors:**
- `404` — Wishlist not found or not `PUBLIC`

---

### POST `/api/v1/public/wishlists/{shareableId}/items/{itemId}/reserve`

Reserves an item. **Requires verified email.**

**Request:** Empty body

**Response:** `200 OK`
```json
{
  "itemId": "item123",
  "message": "Item reserved successfully",
  "reserved": true
}
```

**Errors:**
- `404` — Item not found
- `409` — Item fully reserved, or you already reserved this item

---

## Gifter Reservations Endpoints

**Base path:** `/api/v1/reservations/mine`
**Authentication:** Required

These endpoints let a gifter manage their own reservations across all wishlists.

---

### GET `/api/v1/reservations/mine`

Lists all items the logged-in user has reserved.

**Response:** `200 OK`
```json
{
  "reservations": [
    {
      "itemId": "item123",
      "itemName": "Sony WH-1000XM5 Headphones",
      "itemImageUrl": "https://images.example.com/headphones.jpg",
      "itemPrice": 349.99,
      "wishlistTitle": "Birthday 2026",
      "wishlistShareableId": "V1StGXR8_Z5jdHi6B-myT",
      "eventDate": "2026-06-15",
      "reservedAt": "2026-03-08T14:30:00Z"
    }
  ],
  "totalCount": 1
}
```

---

### DELETE `/api/v1/reservations/mine/{itemId}`

Cancels the logged-in user's reservation on a specific item.

**Response:** `200 OK`
```json
{
  "itemId": "item123",
  "message": "Reservation cancelled successfully",
  "reserved": false
}
```

**Errors:**
- `404` — You don't have a reservation for this item

---

## Profile Endpoints

**Base path:** `/api/v1/profile`
**Authentication:** Required

---

### GET `/api/v1/profile`

**Response:** `200 OK`
```json
{
  "id": "65f1a2b3c4d5e6f7a8b9c0d1",
  "email": "jane@example.com",
  "displayName": "Jane Doe",
  "roles": ["USER"],
  "emailVerified": true,
  "createdAt": "2026-03-08T14:30:00Z"
}
```

---

### PUT `/api/v1/profile`

**Request:**
```json
{
  "displayName": "Jane D."
}
```

| Field | Type | Required | Validation |
|-------|------|----------|------------|
| `displayName` | string | No | Max 50 chars |

**Response:** `200 OK` — `ProfileResponse`

---

## Enums

Use these exact string values in requests and expect them in responses.

### Visibility
| Value | Description |
|-------|-------------|
| `PRIVATE` | Only the owner can see this wishlist |
| `PUBLIC` | Anyone with the shareable link can view it |

### WishlistCategory
| Value |
|-------|
| `BIRTHDAY` |
| `CHRISTMAS` |
| `WEDDING` |
| `BABY_SHOWER` |
| `GRADUATION` |
| `HOUSEWARMING` |
| `OTHER` |

### Priority
| Value |
|-------|
| `LOW` |
| `MEDIUM` |
| `HIGH` |

### ItemStatus
| Value | Description |
|-------|-------------|
| `AVAILABLE` | Can be reserved |
| `RESERVED` | Fully reserved (reservedQuantity = quantity) |

---

## Error Handling

All errors follow a consistent format:

```json
{
  "timestamp": "2026-03-08T14:30:00Z",
  "status": 400,
  "error": "Bad Request",
  "message": "Validation failed",
  "path": "/api/v1/auth/register",
  "correlationId": "a1b2c3d4-e5f6-7890",
  "details": [
    {
      "field": "email",
      "message": "Email is required"
    },
    {
      "field": "password",
      "message": "Password must be 12-128 characters"
    }
  ]
}
```

### HTTP Status Codes

| Code | Meaning | When |
|------|---------|------|
| `200` | OK | Successful read/update |
| `201` | Created | Resource created (register, create wishlist, create item) |
| `204` | No Content | Successful delete |
| `400` | Bad Request | Validation error, invalid enum value, malformed JSON |
| `401` | Unauthorized | Missing/invalid/expired JWT token |
| `403` | Forbidden | Email not verified, or not the resource owner |
| `404` | Not Found | Resource doesn't exist |
| `409` | Conflict | Email already registered, item already reserved |
| `500` | Internal Server Error | Unexpected server error |

### Frontend tips

- Always check for `details[]` array on `400` errors — map field names to form inputs
- On `401`, redirect to login or try refreshing the token
- On `403` with message about email verification, show a "verify your email" banner
- The `correlationId` is useful for debugging — log it or show it to users in error screens

---

## Security Notes

1. **Email verification required** for creating wishlists and reserving items. Unverified users can browse and view public wishlists.

2. **Reservation privacy** — The wishlist owner **never** sees who reserved an item. They only see that an item changed from `AVAILABLE` to `RESERVED`. The surprise is preserved.

3. **Shareable links** — Use the `shareableId` (NanoID) for public links, not the MongoDB `id`. The owner can rotate the link at any time to invalidate old ones.

4. **Password requirements** — Minimum 12 characters with uppercase, lowercase, digit, and special character.

5. **Anti-enumeration** — The forgot-password endpoint always returns the same message, so attackers can't discover which emails are registered.

6. **CORS** — The backend only accepts requests from origins configured in `CORS_ALLOWED_ORIGINS`. For local dev, `http://localhost:3000` and `http://localhost:5173` are allowed.

7. **Quantity system** — Items can have `quantity > 1`. Multiple gifters can reserve portions. The `remainingQuantity` field tells you how many are still available. When `remainingQuantity` hits 0, the status changes to `RESERVED`.

---

## Frontend Pages Required

These are the pages your frontend needs to support the full user flow:

### Auth Pages
| Page | Route | Purpose |
|------|-------|---------|
| Register | `/register` | Registration form |
| Login | `/login` | Login form |
| Verify Email | `/verify-email?token=xxx` | Reads token from URL, calls `POST /auth/verify-email` |
| Forgot Password | `/forgot-password` | Email input form |
| Reset Password | `/reset-password?token=xxx` | New password form, reads token from URL |

### Owner Pages (authenticated)
| Page | Route | Purpose |
|------|-------|---------|
| My Wishlists | `/wishlists` | List wishlists with category filter and pagination |
| Create Wishlist | `/wishlists/new` | Form to create a wishlist |
| Wishlist Detail | `/wishlists/:id` | View/edit wishlist + manage items |
| Profile | `/profile` | View/edit display name, see verification status |

### Gifter Pages (authenticated)
| Page | Route | Purpose |
|------|-------|---------|
| Public Wishlist | `/w/:shareableId` | View shared wishlist, reserve items |
| My Reservations | `/reservations` | List and cancel reservations |

### Suggested UX flows
- After register → show "check your email" screen
- After verify → redirect to login with success toast
- Unverified user tries to create wishlist → show "verify your email first" with resend button
- Share button on wishlist → copies `{FRONTEND_URL}/w/{shareableId}` to clipboard
- Rotate link → confirm dialog ("Old links will stop working. Continue?")
