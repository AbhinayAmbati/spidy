# Security policy

## Reporting a vulnerability

Please do **not** open a public issue for a security problem. Use GitHub's private
vulnerability reporting: open the **Security** tab of this repository and choose
**Report a vulnerability**. Include the app version, the device and Android version, and the
steps to reproduce.

You can expect an acknowledgement, and an update when a fix is planned. This is a small
project; please allow reasonable time before disclosing publicly.

## Supported versions

Only the latest release and the current `main` branch receive fixes.

## Security posture

Spidey runs an Android accessibility service, which can read the screen and perform touches.
The design assumes the model driving it can be wrong or adversarial:

- The model can only choose from a fixed set of typed tools; it cannot run code or shell
  commands or touch the filesystem.
- Every action is validated, and sensitive actions (send, pay, delete, call, install, confirm)
  wait for the user's approval.
- Messaging and calling deep links are refused; screens marked `FLAG_SECURE` are not readable.
- In on-device mode the model runs locally, so task text and screen contents are not sent to a
  server. The Needle and Whistle models are downloaded over HTTPS and checked against a pinned
  SHA-256 before use.
- If a cloud provider is configured in a build that still has one, it receives a summarised
  screen listing and your task text; keys are stored with Android's encrypted preferences.

The full description, including what is not protected, is in
[docs/SECURITY.md](docs/SECURITY.md).
