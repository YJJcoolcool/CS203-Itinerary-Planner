# Authenticated endpoint guidelines

## Core rule

Clerk authenticates a request, but the application owns its data model. All
business logic, service methods, and database queries must use the
application's internal `User.id` (`Long`), not Clerk's `user_...` identifier.

```text
Clerk bearer token
  -> Spring Security validates JWT
  -> JWT subject (`sub`) is the Clerk user ID
  -> users.clerk_user_id lookup
  -> users.id (internal ID)
  -> service and repository calls
```

The Clerk ID is an external identity lookup key. It must not spread into trip,
activity, itinerary, or other business-domain services.

## Controller responsibilities

Controllers are the boundary between Clerk authentication and application
logic. A protected controller receives the verified JWT and resolves the local
user once.

```java
@PostMapping("/api/trips")
public Trip createTrip(
        @AuthenticationPrincipal Jwt jwt,
        @RequestBody CreateTripRequest request) {

    Long internalUserId = userService
            .getUserByClerkId(jwt.getSubject())
            .getId();

    return tripService.createTrip(internalUserId, request);
}
```

`@AuthenticationPrincipal Jwt jwt` is supplied by Spring Security only after a
valid Clerk bearer token has been verified. `jwt.getSubject()` is Clerk's user
ID from the JWT `sub` claim.

Do not manually decode JWT strings in a controller. Do not use a Clerk user ID
after this controller-boundary lookup.

## Service and repository responsibilities

Service APIs accept the internal user ID only:

```java
public Trip createTrip(Long ownerId, CreateTripRequest request)
public List<Trip> getTripsForUser(Long userId)
public Trip updateTrip(Long userId, Long tripId, UpdateTripRequest request)
public void deleteTrip(Long userId, Long tripId)
```

Repository queries must include the internal owner/member ID whenever the
operation accesses user-owned data:

```java
Optional<Trip> findByIdAndOwnerId(Long tripId, Long ownerId);
List<Trip> findAllByOwnerId(Long ownerId);
```

This prevents a valid user from reading or changing another user's records by
changing an ID in a URL.

## Never trust identity from client input

Do not add either of these fields to request bodies for user-owned endpoints:

```json
{ "userId": 42 }
```

```json
{ "clerkUserId": "user_abc" }
```

Both values can be changed by a caller. The current authenticated user always
comes from the verified JWT, not from a path parameter, query parameter, or
JSON body.

It is acceptable for an administrator-only endpoint to target another internal
user ID, but it must first enforce an explicit authorization/role check.

## Frontend request contract

The React frontend obtains a short-lived Clerk session token and sends it to
the backend:

```ts
const token = await getToken()

await fetch(`${apiUrl}/api/trips`, {
  method: 'POST',
  headers: {
    Authorization: `Bearer ${token}`,
    'Content-Type': 'application/json',
  },
  body: JSON.stringify(tripData),
})
```

The frontend does not send an identity field to determine ownership.

## Endpoint checklist

- Is the endpoint protected by Spring Security?
- Does the controller use `@AuthenticationPrincipal Jwt` (or an equivalent
  verified authentication principal)?
- Does it resolve `jwt.getSubject()` to the local `User` exactly once?
- Do service methods accept only the internal user ID?
- Do repository queries constrain results by that internal user ID?
- Does the request body omit `userId` and `clerkUserId`?
- Does a request with no or an invalid bearer token return `401 Unauthorized`?

## Endpoint coverage

Controllers never read the token themselves. They call `UserService`
(`getCurrentUser`, `getCurrentUserId`, `findCurrentUser`,
`findOrCreateCurrentUser`), which returns 401 when there is no token.

| Endpoints | Who is allowed |
| --- | --- |
| `/api/users/me` | The logged-in user (their own profile only) |
| `GET /api/trips`, `POST /api/trips` | The logged-in user (lists their trips; becomes owner of new trips) |
| `/api/trips/{tripId}/**` (trip, members, activities) | Owner or members of that trip, else 403 |
| `/api/trips/{tripId}/invitations`, `/api/users/me/invitations` | Trip owner / the invited user |
| `/api/locations/**`, `/api/geo/**` | Any logged-in user. Locations are a shared catalog with no owner, so no user id is passed |

## Email claim (required for trip invitations)

Clerk's default session token has no email. In the Clerk dashboard go to
Sessions → Customize session token and add:

```json
{ "email": "{{user.primary_email_address}}" }
```

`POST /api/users/me` reads this claim and stores it on `User.email`, which is
how owners look up invitees. Without it, `email` stays null and the user cannot
be invited.
