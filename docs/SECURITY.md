# Security and privacy

What Spidey can do, what it deliberately cannot, where your data goes, and what is not
protected. Written to be read by someone deciding whether to install it — not as marketing.

## The threat this app is built around

Spidey holds an accessibility service. That API can read the contents of any screen and
synthesise touches, which is exactly what a banking trojan wants, so the design assumes the
model driving it may be wrong, compromised, or adversarial.

The model is treated as **untrusted input**. It cannot run code, shell commands, or reach the
filesystem. The entire surface it can influence is a fixed set of typed actions, each of which
is validated against a registered tool before anything touches the device.

## What the agent can and cannot do

**Can:** open apps, tap and long-press elements it can see, type into editable fields, scroll,
swipe, press back and home, open http/https URLs, read notifications if you grant it, read the
screen, and ask you a question.

**Cannot:** execute code, run shell commands, read or write files, send messages, place calls,
install or uninstall apps, force-stop other apps, or read screens marked `FLAG_SECURE` — so
banking apps and password managers are invisible to it.

Two refusals are worth knowing because they are deliberate and permanent:

- **Messaging deep links are refused.** `wa.me`, `t.me`, `sms:`, `tel:` and similar open an app
  with the message already composed. A message must go through the app's own interface, where
  it is visible before it is sent.
- **Sensitive taps are confirmed.** Tapping a control labelled send, pay, delete, uninstall,
  subscribe, call and similar pauses for your approval, even though the tap tool itself is
  generic.

## Where your data goes

**To your provider, and nowhere else.** Your prompts and the text of the screen the agent is
looking at are sent to whichever AI provider you configure. There is no Spidey server. If you
run your own endpoint, nothing leaves your network.

The screen is not sent as an image or a raw tree. It is reduced to a bounded list of labelled,
interactive elements — up to 120 — with the app's name. What is not listed is not sent.

**Nothing is logged.** There are no `Log` calls in the app at all, so prompts, screen content
and API keys cannot reach logcat.

## What is stored, and where

| What | Where | Protected by |
| --- | --- | --- |
| API keys | EncryptedSharedPreferences | AES-256, key in the Android Keystore |
| Task history | Local database | Device storage only |
| Long-term memory | Local DataStore | Device storage only |
| Settings | Local DataStore | Device storage only |

Cloud backup and device transfer are **both disabled entirely** — `allowBackup="false"`, and the
extraction rules exclude preferences and databases. Nothing is copied off the device by the
platform.

API keys are never displayed in full. The UI shows only the last four characters, and the key
screen is marked `FLAG_SECURE`, so it is excluded from screenshots, the recents thumbnail and
screen recording.

## Network

**No cleartext, ever.** `usesCleartextTraffic` is off and the network security config forbids it
for every domain, with no exception list. A configured endpoint that is not `https://` is
refused with a reason rather than failing opaquely.

There is deliberately **no certificate pinning**. The endpoint is user-supplied and can be any
provider, a gateway, or a self-hosted server, so there is no fixed certificate to pin. Trust
rests on the system trust store and on TLS.

## What this app does not protect against

Being honest about the boundaries is more useful than a longer list of features.

- **A rooted or compromised device.** Keystore-backed encryption protects data at rest from
  another app; it does not protect against an attacker with root.
- **A malicious provider.** Whatever endpoint you configure receives your prompts and screen
  text. Spidey cannot know what it does with them.
- **A malicious model response.** The validator, the confirmation gate and the refusal rules
  constrain what an action can be. They cannot stop a model from choosing a legitimate-looking
  action that is simply wrong — tapping the wrong button on the right screen. That is why
  sensitive labels require confirmation, and why the agent shows its steps as it works.
- **Screen content while the agent runs.** While a task is active, the accessibility service
  can see whatever is on screen, including anything you have not marked `FLAG_SECURE`. Stop the
  task before opening something private.
- **An unlocked bootloader.** AICore and some platform security features refuse to run, and the
  guarantee that the app binary is the one that was published is weakened.
- **Physical access to an unlocked phone.**

## Permissions, and why each is needed

| Permission | Why | Optional |
| --- | --- | --- |
| Accessibility service | To see and control the screen. The core of the app. | No |
| `POST_NOTIFICATIONS` | To show the agent's progress while it works | No |
| `SYSTEM_ALERT_WINDOW` | The status pill over other apps | Yes |
| Notification access | To read notifications when a task asks | Yes |
| `INTERNET` | To reach your AI provider | No |

`SYSTEM_ALERT_WINDOW` is the only permission granted outside the app, and the pill it draws is
non-focusable and non-touchable — it can never become the window the agent is reading, and it
can never intercept a tap the agent needs to make.

## Reporting

If you find a way to make Spidey do something on this list says it cannot, that is a security
bug and worth reporting before it is exploited.
