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

## Current user endpoints

`/api/users/me` is the only current place that intentionally uses Clerk's ID.
It creates or retrieves the local user through `clerkUserId`; its response
contains the internal `id` used by the rest of the application.

When the Trip entity is merged, restore the pending `User` ↔ `Trip`
relationship and keep all trip ownership queries based on `User.id`.
