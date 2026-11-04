# Stage 3: real PIN authorization on the Android emulator

This is a new non-biometric authentication job targeting only synthetic,
disposable emulator folders. It must never import a user's password or
inject an authorization capability directly.

## Verifiable integration

- Uses the existing ClassicPasswordMethodDialog (select PIN, not biometrics).
- Uses the production SecurityDialog keypad to enter/confirm fixture PIN 1357.
- Uses the real photo credential snapshot and ensures its SHA-1 matches the
  only-ever-in-memory fixture secret. The PIN itself is not logged or exported.
- Sets up two protected test source records using config.addFolderProtection:
  P uses the freshly configured PIN identity and P2 uses a different synthetic
  PIN 2468. Seeding protected test folder records is NOT a privilege grant.
- Attempts the profile without a token, checking that both P and P2 are absent.
- Enters a wrong real UI PIN then cancels; the process capability must stay null.
- Enters the correct real UI PIN, then answers the real source-binding consent
  and enters the second source PIN through SecurityDialog.
- Asserts both protected sources become visible ONLY while the actual capability
  is active. Revokes through the production session manager, and asserts
  protected media disappear again.
- Cleans all synthetic folder locks, profile records, credential and capability.

## Limitations

This PIN integration does not cover lock expiry over real time, Favorites
with a separate credential, changing credentials mid-scan, automatic
re-authentication after Activity recreation, nor different per-folder PIN
combinations beyond two synthetic source locks. Those need their own scenarios.

The large-media and 15-sort suites remain separate. A smoke pass does not
imply PIN success, and a PIN test that is skipped under another suite is never
reported as having passed.
