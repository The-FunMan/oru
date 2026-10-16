# Upstream features not ported to Oru

This is the list of upstream Morphe Manager features that Oru does not have. It is the list the upstream sync (`chores.md`, 4) works from. Each line says whether the feature is to be ported, not ported, or skipped on purpose.

| Feature | Status |
| --- | --- |
| Sorting, filtering and grouping sheet on Home | not ported |
| Sharing patched APKs | skipped on purpose |
| Patch update ignore | not ported |
| Source management dialogs | not ported |
| Root mount fixes | ported |
| File picker, newest first | ported |
| Completion sound | ported as the default notification sound, no custom sound files |
| Source pictures cache | not ported |
| Dialog and sheet width and icon tweaks | not ported |

Notes:

- "Skipped on purpose" means the feature conflicts with Oru's rules (`README.md`, principles). Sharing patched APKs is not part of Oru.
- "To port" means the item is next on the cherry-pick list. Port it as its own pull request, with the upstream commit named in the description.
- Adding an item to the list, or changing its status, is done in a pull request to `dev`.
