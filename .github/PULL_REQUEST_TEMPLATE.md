## What and why

<!-- What does this change, and why? Link an issue if there is one. -->

## How it was checked

- [ ] `python3 tools/check_imports.py app/src` and `python3 tools/check_symbols.py app/src` pass
- [ ] `Android CI` is green (lint, unit tests, assemble)
- [ ] A test covers the change (or explain why not)
- [ ] Tried on a device (device and Android version):

## Safety

- [ ] Does not widen what the agent can do without confirmation (tools, validator, deep links)
- [ ] No secrets, keys or personal data added to the repo or to logs
