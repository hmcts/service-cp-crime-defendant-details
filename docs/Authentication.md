# Authentication

This service validates Microsoft Entra **app-only** (client credentials) access tokens on every
request, with an enumerated list of exempt infrastructure endpoints.

> **Before this is deployed anywhere, `AUTH_TENANT_ID` and `AUTH_AUDIENCE` must be set in that
> environment's deployment configuration.** They have no defaults. With `AUTH_MODE` at its default
> of `ENFORCE`, the service will fail to start without them. That is deliberate — see
> [Why startup fails rather than degrades](#why-startup-fails-rather-than-degrades).

## Why validate in the application at all

APIM's `validate-jwt` policy already rejects bad tokens arriving from the internet. This duplicates
the gateway **on purpose**, because it covers the paths the gateway never sees: in-cluster callers,
a `kubectl port-forward`, and a misrouted ingress. "APIM validates it" is not an answer to a missing
in-application check — the pod is reachable inside the cluster without going through APIM at all.

## Configuration

| Variable | Default | Notes |
|---|---|---|
| `AUTH_MODE` | `ENFORCE` | `OFF`, `OBSERVE` or `ENFORCE`. See below. |
| `AUTH_TENANT_ID` | *(none)* | The tenant that **issues** the tokens. Not necessarily the tenant hosting the service — the hosting tenant's directory id rejects every token. |
| `AUTH_AUDIENCE` | *(none)* | This API's own audience. |
| `AUTH_REQUIRED_ROLE` | `DefendantDetails.Read` | The application role a caller must hold. |
| `AUTH_ISSUER` | derived from the tenant | Override only if you have a reason to. |
| `AUTH_JWKS_URI` | derived from the tenant | As above. |
| `AUTH_CLOCK_SKEW_SECONDS` | `60` | Capped at 300. A larger skew turns `exp` into a no-op. |
| `AUTH_JWKS_CACHE_TTL_SECONDS` | `300` | So verification costs no outbound call per request. |

### The three modes

- **`OFF`** — no validation, no caller identity. Provides no protection.
- **`OBSERVE`** — tokens are fully validated and every failure is logged and counted, but **no
  request is rejected**. A diagnostic for finding broken clients before enforcing. Provides no
  protection.
- **`ENFORCE`** — validated, and invalid requests rejected.

`OFF` and `OBSERVE` are **rejected at startup** unless the `local` or `test` Spring profile is
active. A deployed pod carries neither, so it cannot be put into a non-enforcing mode by an
environment variable alone. Treat any deployed environment running one as an incident, not a
configuration preference.

### Why startup fails rather than degrades

A blank audience must never be read as "accept any audience". The `aud` check is the only thing that
rejects a genuine, correctly signed, unexpired token minted for a **different** resource — a
Microsoft Graph token, or a sibling CP API's.

There is deliberately **no default** for the tenant or the audience. A non-blank default belonging to
one environment would be worse than a blank one: the service would start everywhere and reject every
token in all the other environments, which looks like a client problem rather than a configuration
one.

## What is checked

| Claim | Rule |
|---|---|
| `aud` | Equals this API's audience |
| `iss` | **Exact** string match — never a prefix or `contains` |
| `exp` | **Required**, and in the future within the configured skew |
| `nbf` | Checked when present |
| `tid` | Exact match against the issuing tenant (defence in depth; `iss` already contains it) |
| `ver` | `2.0` |
| `azp` | The caller's identity. Must parse as a UUID |
| `roles` | Present, non-empty, and containing `AUTH_REQUIRED_ROLE` |
| `scp` | **Prohibited** — its presence means a delegated user token |

The signature algorithm is **pinned to RS256 in the verifier's own configuration**. It is not read
from the token header, and it cannot be inferred from the key set — Entra's JWKS entries carry no
`alg`. Because the key selector is built for exactly one algorithm against the configured JWKS, a
token declaring anything else finds no candidate key and fails before verification is attempted.
That makes `alg: none`, algorithm confusion, and key material supplied in the token header
(`jku` / `jwk` / `x5u`) structurally impossible rather than separately defended.

### Two things that are easy to get wrong

- **The identity is `azp`, not `oid` or `sub`.** `azp` is the calling application's client id;
  `oid`/`sub` is that application's service principal object id in the tenant. Seeding a client
  registry with `oid` produces a signature-valid token that then 403s or 404s — a confusing failure,
  because nothing is wrong with the token.
- **App-only is proven by `sub == oid`, never by `idtyp`.** Entra omits `idtyp` unless it is
  explicitly enabled as an optional claim on the app registration, so requiring it would reject all
  legitimate traffic. There is a regression test asserting a token without `idtyp` is accepted;
  do not "harden" it away.

## Exempt endpoints

Enumerated in `ExemptPaths` and matched **exactly**, never by prefix:

```
/                            root, serves nothing
/actuator/health             probes
/actuator/health/liveness    probes
/actuator/health/readiness   probes
/actuator/info               build and git metadata
/actuator/prometheus         in-cluster scrape
```


## Errors

401 means "we do not know who you are"; 403 means "we know, and you may not do this". A token that
fails signature, claim or app-only checks yields no trustworthy identity, so it is 401. A token that
verifies but lacks the required role identifies its caller, so it is 403.

Responses carry an RFC 6750 `WWW-Authenticate` challenge (`invalid_token`, `insufficient_scope`) and
an `ErrorResponse` body. Both carry a **coarse reason only** — never claim values and never the
token. `TokenValidationException` deliberately does not wrap the underlying library exception,
because those messages embed values taken from the token.

## Metrics

Scraped from `/actuator/prometheus`:

| Counter | Meaning |
|---|---|
| `auth_tokens_accepted_total` | Validated successfully |
| `auth_tokens_rejected_total{reason}` | Rejected, and the request refused |
| `auth_tokens_would_reject_total{reason}` | What `OBSERVE` **would** have rejected, having served it anyway |

The last is separate on purpose: conflating it with rejections would make a non-enforcing
environment look protected on a dashboard.

## Running locally

Token validation is on by default, so a local run needs either real values or an explicit opt-out:

```bash
SPRING_PROFILES_ACTIVE=local AUTH_MODE=OFF ./gradlew bootRun
```

`AUTH_MODE=OFF` alone is not enough — without the `local` profile the service refuses to start.

## Entra prerequisites — code cannot compensate for these

| Item | Owner |
|---|---|
| App registration exposing this API's audience, per environment | Entra / platform admin |
| The application role **declared and assigned, with admin consent** | Entra / platform admin |
| `requestedAccessTokenVersion` pinned to `2` | Entra admin |
| Per-environment tenant and audience values wired into the deployment config | Deployment |

A **declared** role is not an **assigned** one. A role declared without admin consent produces a
token that looks entirely correct but silently omits `roles`, and this service will return 403.
Verify by minting a token, not by reading the portal.

Clients request a token with:

```
POST https://login.microsoftonline.com/{tenant}/oauth2/v2.0/token
grant_type=client_credentials
scope=<this API's audience>/.default
```

Not a Graph scope, and not another CP API's audience. A token minted with a sibling API's GUID is
*correctly* rejected — do not "fix" that by widening the audience.

## The conformance suite

There is deliberately no shared authentication library across CP services. The agreed model is a
common standard plus a **conformance suite in each repo** proving the service meets it, which makes
the tests the contract — a service that validates correctly but cannot demonstrate it is not
conformant, because the next refactor has nothing to fail against.

Integration tests run with enforcement **on**, supplying the signing key in-process as the
application's JWKS. Running them with validation disabled would stop them covering the
authentication path at all — a quiet and common regression.
