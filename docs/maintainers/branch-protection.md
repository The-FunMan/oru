# Branch protection

Branches: `main` is the stable branch and `dev` is the development branch. Both are configured in the repository settings (Settings > Branches). This page holds the settings and the commands that apply them. The commands use the classic branch protection API, which replaces the whole protection of a branch on each call, so run the full body every time.

The lead applied these settings once with the commands below. This page does not state the current live state. Check it with the commands in "Verify".

## Settings for `main`

- Pull request required before merging, with 0 required approvals. This lets the owner merge their own pull request.
- Direct pushes blocked for everyone, administrators included (`enforce_admins` is true).
- Force pushes blocked. Branch deletion blocked.
- Conversation resolution required before merging.
- Required status checks: not set on the first call. Add them after the first CI run on `main`, with the second command below. The contexts are the job ids of `.github/workflows/ci.yml` that run on branches: `android`, `patcher`, `patch-api-guard`, `rust`. `dependency-review` is not required: it runs on pull requests only, and a pull request opened by the workflow token starts no pull request workflows, so requiring it would block the cut.
- Linear history: off. Recommended. The dev-to-main pull request is merged with a merge commit (see `release-stable.md`), and a linear history rule forbids merge commits.

## Settings for `dev`

- No force pushes. No branch deletion.
- No pull request requirement and no required status checks, so the owner can push directly.
- The release workflows push to `dev` only through the Cut stable release workflow (the notes commit), with the workflow token. Nothing else in `.github/workflows` pushes to a branch.

## Apply: `main`, first call (no status checks)

```sh
gh api -X PUT repos/The-FunMan/oru/branches/main/protection --input - <<'JSON'
{
  "required_status_checks": null,
  "enforce_admins": true,
  "required_pull_request_reviews": {
    "required_approving_review_count": 0,
    "dismiss_stale_reviews": false,
    "require_code_owner_reviews": false
  },
  "restrictions": null,
  "required_linear_history": false,
  "allow_force_pushes": false,
  "allow_deletions": false,
  "block_creations": false,
  "required_conversation_resolution": true,
  "lock_branch": false,
  "allow_fork_syncing": false
}
JSON
```

## Apply: `main`, after the first CI run on `main` (adds the required checks)

```sh
gh api -X PUT repos/The-FunMan/oru/branches/main/protection --input - <<'JSON'
{
  "required_status_checks": {
    "strict": false,
    "contexts": ["android", "patcher", "patch-api-guard", "rust"]
  },
  "enforce_admins": true,
  "required_pull_request_reviews": {
    "required_approving_review_count": 0,
    "dismiss_stale_reviews": false,
    "require_code_owner_reviews": false
  },
  "restrictions": null,
  "required_linear_history": false,
  "allow_force_pushes": false,
  "allow_deletions": false,
  "block_creations": false,
  "required_conversation_resolution": true,
  "lock_branch": false,
  "allow_fork_syncing": false
}
JSON
```

`strict` is false, so a pull request does not have to be up to date with `main` before it merges. Set it to true if you want that rule.

## Apply: `dev`

```sh
gh api -X PUT repos/The-FunMan/oru/branches/dev/protection --input - <<'JSON'
{
  "required_status_checks": null,
  "enforce_admins": false,
  "required_pull_request_reviews": null,
  "restrictions": null,
  "allow_force_pushes": false,
  "allow_deletions": false
}
JSON
```

To limit direct pushes on `dev` to the owner if other people get write access later, replace `"restrictions": null` with `"restrictions": {"users": ["The-FunMan"], "teams": [], "apps": []}`.

## Verify

Print the settings that matter for `main`:

```sh
gh api repos/The-FunMan/oru/branches/main/protection \
  --jq '{approvals: .required_pull_request_reviews.required_approving_review_count, admins_enforced: .enforce_admins.enabled, force_pushes: .allow_force_pushes.enabled, deletions: .allow_deletions.enabled, conversations: .required_conversation_resolution.enabled, linear: .required_linear_history.enabled, checks: .required_status_checks.contexts}'
```

Print `dev`:

```sh
gh api repos/The-FunMan/oru/branches/dev/protection \
  --jq '{force_pushes: .allow_force_pushes.enabled, deletions: .allow_deletions.enabled, pull_requests: .required_pull_request_reviews, checks: .required_status_checks}'
```

A branch with no protection returns HTTP 404 for the call. Expected results after the first call: `approvals` 0, `admins_enforced` true, `force_pushes` false, `deletions` false, `conversations` true, `linear` false, and `checks` null. After the second call, `checks` lists the four contexts.

Check in the web UI as well: Settings > Branches > the rule for `main` shows "Do not allow bypassing the above settings" checked.

## Consequence for stable releases

Stable releases are cut by a pull request from `dev` to `main`, not by a direct push to `main`. The tag is created after the merge, on the merge commit. The Cut stable release workflow does this in one run. The manual path is described in `release-stable.md`. Either way, the tag is on a commit that is on `main`, which the release workflow requires.
