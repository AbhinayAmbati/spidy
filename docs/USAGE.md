# Using Spidey

Spidey is an on-device Android agent. You describe a task in plain language and it drives
your phone through Android's official APIs until the task is done, or until it needs your
approval.

This guide covers setup, how the agent works, how to phrase tasks, what each tool does, and
what to do when something fails.

---

## 1. Setup

Do these once.

**1. Install the APK.** Allow installing from the app you are installing from:
*Settings → Apps → Special app access → Install unknown apps* → pick Chrome / Files / Drive
→ **Allow**. Play Protect will warn that it does not recognise the developer — expected for a
new signing key. Tap *More details → Install anyway*.

**2. Add an AI provider.** Open **Settings → Provider**, pick one (OpenAI, Anthropic, Google
Gemini, Sarvam AI, OpenRouter, or any OpenAI-compatible endpoint), paste your API key and
press **Test connection**. The key is encrypted on-device and sent only to the provider you
chose.

Choose a model that supports **tool calling** — that is what lets the agent act rather than
chat. Every current model from OpenAI, Anthropic and Google does.

**3. Turn on Accessibility.** *Settings → Accessibility → Spidey → On*. This is what lets
Spidey see and touch the screen. It is required; nothing works without it.

**4. Turn on Notifications (optional).** *Settings → Notifications → Notification access →
Spidey*. This shows progress while a task runs, and lets the agent read notifications.

**After every update, turn accessibility off and back on once.** Android caches the previous
declaration, so it will not re-bind on its own.

---

## 2. How the agent works

Spidey runs a loop. Every turn it does exactly four things:

1. **Read** the current screen — which app is in front, and a short list of the elements on
   it.
2. **Decide** the single next action that makes the most progress toward your goal.
3. **Act** — call exactly one tool.
4. **Verify** on the next screen. If the screen did not change, the action did not work, and
   it takes a different route.

Three things follow from this:

- **It never runs code.** The model can only call the fixed set of tools below. Every action
  passes a validator first, so an invented tool name can never run.
- **It cannot get stuck in a loop.** An action identical to one that already ran and left the
  screen unchanged is refused, and the agent is told to try something else. If it keeps
  insisting, the task stops with an explanation rather than spinning.
- **Sensitive actions wait for you.** Anything that sends, pays, deletes, calls, installs or
  confirms pauses and asks for an explicit **Allow**.
- **Stop halts it immediately, wherever it is.** Pressing Stop closes a gate that every
  device action checks before it runs, so nothing new begins — and there is a **Stop** button
  on the notification as well, so you do not have to find the app to halt a task that is
  driving your phone.

---

## 3. Phrasing a task

Be specific about **what** and **where**. The agent can see the screen, so you do not need to
describe the steps — just the outcome.

Good:

> Open Chrome and search for Kubernetes tutorials

> Open YouTube and play the first result for "Karan Aujla"

> Open Settings and turn on Wi-Fi

> What are my latest notifications?

Weak, and why:

> Do the thing I asked earlier — no memory of previous tasks; state it in full.

> Open Chrome, tap the address bar, type Kubernetes, then press enter — you are describing
> steps the agent can work out, and a wrong step will derail it.

> Make my phone faster — not an observable outcome.

**If a task has a decision only you can make**, the agent will ask (it has an `ask_user`
tool). Answering is better than guessing.

---

## 4. The tools, and when the agent uses each

| Tool | What it does | Notes |
| --- | --- | --- |
| `launch_url` | Opens a URL | The fastest route to anything on the web — **including search** |
| `open_app` | Opens an installed app | Does nothing if the app is already on screen |
| `click` | Taps an element | Target by visible text, then content description, then id, then key |
| `long_click` | Long-presses an element | Same targeting as `click` |
| `type_text` | Types into a field | Tap the field first so it is focused; only `editable` fields accept text |
| `press_back` | Goes back one screen | For leaving a screen you just opened |
| `press_home` | Returns to the home screen | Usually before opening a different app |
| `scroll` | Moves within the current screen | `forward` = down, `backward` = up; reveals what is not yet listed |
| `swipe` | A directional gesture | Only when `scroll` does not work |
| `read_screen` | Re-reads the screen | After it changed on its own |
| `find_text` | Checks whether text is on screen | Does not tap anything |
| `wait` | Waits up to 5 s | When a screen is still loading |
| `get_notifications` | Lists recent notifications | Needs notification access |
| `ask_user` | Asks you a question | When a decision is genuinely yours |
| `finish` | Ends the task with a summary | The only way a task completes |

### Search

**Searching is the most common task, and the agent is told to do it in one call.** Rather than
opening a browser and driving its interface — which means finding the address bar, tapping it,
typing, and finding the search button — it opens a search URL directly:

- Web: `https://www.google.com/search?q=your+terms`
- YouTube: `https://www.youtube.com/results?search_query=your+terms`
- Maps: `https://www.google.com/maps/search/your+terms`

So *"open Chrome and search for Kubernetes"* should take one step, not fifteen, and it will
not need to tap a new-tab button.

If you want the agent to use a specific app's own search instead, say so — for example
*"in the YouTube app, search for …"*.

### Sending a message or calling

`launch_url` is for the web, and it will **not** open messaging links. URLs that launch a
messaging app with the message already written — `wa.me`, `t.me`, `api.whatsapp.com` — and
the `sms:`, `tel:` and `mailto:` schemes are all refused, and the agent is told why.

To send a message or place a call, the agent opens the app and works through its interface.
That is deliberate: the message stays visible before it is sent, and the send itself is one of
the actions that pauses for your **Allow**.

### Back, home and scrolling

These three are what agents get wrong most often, so the agent is given explicit rules:

- **`press_back` is for leaving a screen you just opened.** It is not for looking around, and
  not for undoing something from several steps ago. Wandering with Back is the main cause of
  long, thrashing runs.
- **`press_home` is for switching apps**, not for abandoning a task.
- **`scroll` moves within the screen you are already on.** It targets the largest scrollable
  region, and falls back to an actual drag gesture if the app ignores the scroll action.
  Scroll to *reveal* something that is not in the list, then look at the new list.
- The agent must not go back and forth between the same two screens, and must not reopen an
  app it has just left.

### Targeting an element

Each element in the screen listing looks like:

```
  [a3f2c1] "Search" <EditText> (clickable,editable) id=search_box
```

- `[a3f2c1]` is a **key**: a short, stable identifier for that element.
- `"Search"` is the visible text, or the content description when there is no text.
- `<EditText>` is the widget type — only `editable` widgets accept typing.
- `(clickable,editable)` are the flags that matter: only `clickable` elements can be tapped.
- `id=search_box` is the shortened resource id.

The agent is told to target by **visible text first**, because it is the most human-readable
and the most stable. Keys are next. Keys come from the latest listing only — if the screen has
changed, the agent re-reads it before acting.

---

## 5. Worked examples

**Search the web** — one step.

> Open Chrome and search for Kubernetes tutorials

`launch_url https://www.google.com/search?q=Kubernetes+tutorials` → done.

**Play something on YouTube** — one or two steps.

> Open YouTube and play the first result for "Karan Aujla"

`launch_url https://www.youtube.com/results?search_query=Karan+Aujla`, then `click` the first
result.

**Change a setting** — a few steps, because settings screens have no shortcut.

> Open Settings and turn on Wi-Fi

`open_app com.android.settings` → `click` "Network & internet" → `click` the Wi-Fi switch.

**Read notifications.**

> What are my latest notifications?

`get_notifications` → `finish`.

---

## 6. When something fails

Open **History** and tap the task. Every step is listed, and a failed step shows **why**
underneath it. That line is the most useful thing to share.

| What you see | What it means | What to do |
| --- | --- | --- |
| "Accessibility access is off" | The service is not running | Settings → Accessibility → Spidey → off, then on |
| "The API key was rejected" | Wrong or expired key | Re-enter it under Settings → Provider, press Test connection |
| "The model kept replying with text instead of acting" | The model does not support tool calling | Pick a different model |
| "I kept repeating the same action and the screen never changed" | The agent could not make progress | Name the exact element you want tapped, or rephrase |
| "Could not find … on screen. The screen may have changed" | The element moved or went away | Usually transient — the agent re-reads and retries |
| "No installed app matches …" | The app name did not resolve | Use the exact app name, or its package name |
| A task runs many steps for something simple | The direct route was not taken | Say the outcome plainly, e.g. "search the web for …" |
| "Nothing on screen has changed for the last N steps" | The agent was making no progress | Name the exact element it should use, or rephrase |
| "I used X N times in a row without finishing" | The agent was hammering one tool | Name the element, or rephrase; it usually means what it needs is not on screen |
| A message was not sent | Messaging links are refused by design | Ask it to open the messaging app and send from there |
| The task kept going after Stop | Fixed — Stop now halts immediately | Update to v0.1.12 or later |

---

## 7. Limits

- **No screenshots.** Spidey reads the accessibility tree, not pixels. Apps that expose
  nothing useful to accessibility are out of reach.
- **Android restrictions are respected, not bypassed.** It cannot force-stop other apps, tap
  system dialogs it is not allowed to touch, or read screens a secure flag protects.
- **It is only as good as the model.** A weak model makes poor choices about which element to
  tap. Use one that supports tool calling.
- **Screen layouts differ between devices and app versions.** A step that works on one phone
  may need a different element on another.
- **Terms of Service are a template** and have not been reviewed by a lawyer.
