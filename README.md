# service-cp-crime-defendant-details

Implements the [CP Crime Defendant Details API](https://github.com/hmcts/api-cp-crime-defendant-details) —
exposes defendant identity lookups by case for the Common Platform (CP).

- **API contract:** `api-cp-crime-defendant-details` (depends on published artefact
  `uk.gov.hmcts.cp:api-cp-crime-defendant-details:1.0.0` — see `build.gradle`).
- **Upstream backend:** the CP backend, reachable via `${CP_BACKEND_URL}` (see
  `application.yaml`). No database — this is a stateless read/pass-through service,
  matching `service-cp-crime-hearing`. The exact upstream query path/contract is not
  yet wired — an open item for the implementation stage.
- **Downstream consumers:** not yet confirmed — flagging as an open item rather than
  guessing.
- **Owning team:** `@hmcts/api-marketplace` (`maintain`), `@hmcts/api-marketplace-admin` (`admin`).
- **Support model / escalation:** API Marketplace product team, in-hours support only,
  escalate via Slack `#api-marketplace-support`.

## New team member setup

Anyone newly added to the owning team should verify push access once:

```bash
gh auth login                                          # if not already authenticated
git clone git@github.com:hmcts/service-cp-crime-defendant-details.git
cd service-cp-crime-defendant-details
git checkout -b smoke/access-check
git commit --allow-empty -m "chore: verify push access"
git push -u origin smoke/access-check
git push origin --delete smoke/access-check             # clean up the throwaway branch
```

If the push is rejected with a permissions error, check team membership of
`@hmcts/api-marketplace` / `@hmcts/api-marketplace-admin` before assuming a tooling problem.

---

## Documentation

This repo is built from the HMCTS Spring Boot service template — for generic setup,
build commands, static analysis, and implementation pattern guides, see the
[service-hmcts-crime-springboot-template README](https://github.com/hmcts/service-hmcts-crime-springboot-template/blob/main/README.md)
and its [supporting docs](https://github.com/hmcts/service-hmcts-crime-springboot-template/tree/main/docs).

Repo-specific documentation: [Logging](docs/Logging.md).

### Contribute to This Repository

Contributions are welcome! Please see the [CONTRIBUTING.md](.github/CONTRIBUTING.md) file for guidelines.

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details