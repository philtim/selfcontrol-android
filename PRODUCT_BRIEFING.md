# FocusTime — Product Briefing

## What FocusTime Does

FocusTime is an Android app that blocks distracting apps and websites during timed focus sessions. Users select the apps and URLs they want blocked, set a duration (30 minutes to 8 hours), and start a session. Once active, FocusTime enforces the block through three layers:

1. **App blocking** — a foreground service detects when a blocked app is opened and immediately covers it with a full-screen overlay.
2. **Website blocking** — a local VPN intercepts DNS queries and null-routes blocked domains (including wildcard patterns like `*.youtube.com`), making sites unreachable in any browser.
3. **Tamper protection** — Device Admin prevents uninstalling the app, sessions survive device reboots, and early unlock requires a master password that is meant to be held by an accountability partner, not the user themselves.

When the session ends, users are rewarded with a confetti celebration screen showing how long they stayed focused.

---

## Target Audience

**Primary:** Knowledge workers, students, and freelancers (18–35) who know their phone is their biggest productivity leak but lack the willpower to simply "put it down." They have tried screen-time tools built into Android and found them too easy to dismiss.

**Secondary:** People in accountability relationships — study groups, coaching clients, couples — where one person holds the master password for the other. This turns focus from a solo willpower exercise into a shared commitment.

**Psychographic:** Self-improvement-minded individuals who treat focus as a skill to train, not a personality trait they either have or lack.

---

## Competitive Landscape & Differentiation

| Capability | Android Digital Wellbeing | AppBlock / Stay Focused | Freedom | **FocusTime** |
|---|---|---|---|---|
| App blocking | Basic timers | Yes | Yes | Yes |
| DNS-level website blocking | No | No | Server-side VPN | **Local VPN, no data leaves device** |
| Wildcard domain blocking | No | No | Limited | **Yes (`*.domain.com`)** |
| Survives device reboot | No | Partial | Yes | **Yes** |
| Uninstall protection | No | Paid tier | No | **Yes (Device Admin)** |
| Accountability partner model | No | No | No | **Yes (master password handoff)** |
| Subscription required | Free | Freemium | $8.99/mo | **Free, no data collection** |
| Celebration / gamification | No | Basic streaks | No | **Confetti + session stats** |

Most blockers treat the user as a rational agent who just needs a gentle nudge. FocusTime treats the user as someone who will actively try to break out — and makes that extremely hard.

---

## Unique Selling Proposition (USP)

> **FocusTime is the app blocker you genuinely cannot cheat.**
>
> It blocks apps at the OS level, blocks websites at the DNS level, prevents its own uninstallation, survives reboots, and locks the kill-switch behind a password someone else holds. No subscription. No data collection. Just an ironclad two-hour wall between you and Instagram.

---

## Why Users Should Choose FocusTime

1. **It actually works.** Other blockers have a dismiss button, a "just this once" override, or can be uninstalled in 10 seconds. FocusTime has none of these escape hatches during an active session.

2. **Accountability built in, not bolted on.** The master password is designed to be given to a friend, partner, or coach. This makes focus a two-person contract — which behavioral research shows is far more effective than self-regulation alone.

3. **Privacy-first architecture.** The VPN runs locally. DNS queries are intercepted on-device. Zero data is sent to any server. Users get the blocking power of a commercial VPN service with none of the privacy trade-offs.

4. **Zero cost, zero friction.** No subscription tiers, no ads, no account creation. Pick apps, set a timer, start.

5. **Positive reinforcement loop.** The confetti celebration and session stats at the end of each focus block create a micro-reward that makes the next session easier to start. Motivational quotes during the session reduce the frustration of being blocked.

---

## How Users Gain a New Level of Focus

FocusTime shifts the economics of distraction. Without it, checking Instagram costs zero effort — just tap the icon. With FocusTime active, the cost becomes: find the person who has your password, convince them to unlock it early, enter the password, and deal with the social friction of admitting you caved. That cost is high enough that most users simply don't bother and return to their work.

Over time, this creates a habit loop:

1. **Cue** — Start of a work block → open FocusTime, start session.
2. **Routine** — Phone becomes a tool, not a distraction source, for the chosen duration.
3. **Reward** — Confetti, completion stats, and the intrinsic satisfaction of uninterrupted deep work.

After a few weeks of consistent use, users report that starting a focus session feels as automatic as putting on headphones. The app is training a reflex, not just enforcing a rule.

---

## One-Line Summary

**FocusTime: The focus app built for people who don't trust themselves — and are smart enough to know it.**
