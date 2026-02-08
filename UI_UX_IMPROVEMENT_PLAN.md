# FocusTime UI/UX Improvement Plan

**Prepared by:** Senior UI Designer + Senior UX Designer
**Date:** February 2026
**App:** FocusTime by T7Lab (`com.t7lab.focustime`)

---

## Executive Summary

FocusTime has a solid functional foundation built with Material 3 and Jetpack Compose. However, the current UI/UX falls short of the polish, delight, and friction-reduction that top-tier focus apps (Forest, Focus Keeper, Goodtime) deliver and that users now expect in 2026. This plan identifies **7 high-impact improvement areas** across 28 specific changes, organized by priority.

---

## 1. ONBOARDING & PERMISSION FLOW (Critical - UX)

### Current State
`MainActivity.kt` fires **all permission requests simultaneously on first launch** (lines 98-122): notification, usage stats, and overlay permissions are requested in rapid succession. Usage stats and overlay permissions launch raw system settings intents with no context. There is no onboarding, no explanation of what the app does, and no graceful handling of denied permissions.

### Problems
- Users are bombarded with 2-3 system settings screens immediately on first open
- No explanation of *why* each permission is needed or *what benefit it provides*
- If any permission is denied, the app gives no feedback or fallback guidance
- Violates the #1 onboarding principle of 2025-2026: **"deliver value first, ask for permissions later"**
- No first-run experience whatsoever

### Recommendations

**1.1 — Add a 3-screen onboarding flow (first launch only)**

| Screen | Content |
|---|---|
| **Welcome** | App name, tagline ("Take control of your focus"), hero illustration of someone relaxed with phone blocked. Single "Get Started" button |
| **How It Works** | 3-step explainer: (1) Pick apps/URLs to block, (2) Set duration, (3) Stay focused. Animated illustrations |
| **Permissions** | Explain each permission with benefit-driven copy, request them one at a time contextually (see 1.2) |

Store `onboarding_complete` in DataStore to skip on subsequent launches.

**1.2 — Contextual, just-in-time permission requests**

Instead of requesting everything at launch, request permissions when the user first needs them:

| Permission | When to Request | Pre-Permission Copy |
|---|---|---|
| **Usage Stats** | When user taps "Start Focus" for the first time with apps in the blocklist | "FocusTime needs Usage Access to detect when you open blocked apps and redirect you back" |
| **Overlay** | Same moment as above | "Display over other apps lets FocusTime show a 'blocked' screen when you try to open a distracted app" |
| **VPN** | When user taps "Start Focus" with URLs in the blocklist | "VPN permission lets FocusTime filter blocked websites. No data is collected or sent anywhere." |
| **Notifications** | After first session starts successfully | "Get notified about your remaining focus time and when sessions end" |
| **Device Admin** | From Settings, with explanation | "Prevents the app from being uninstalled during a session for maximum accountability" |

**1.3 — Permission status dashboard**

Add a permissions section to Settings showing the status of each required permission with a toggle/button to grant missing ones. Color-code: green (granted), amber (not yet asked), red (denied).

**Files to modify:** `MainActivity.kt`, new `ui/onboarding/OnboardingScreen.kt`, `ui/settings/SettingsScreen.kt`

---

## 2. HOME SCREEN REDESIGN (High Priority - UI + UX)

### Current State
The home screen (`HomeScreen.kt`) has two states — inactive and active session — both rendered as a vertical scroll of cards. The inactive state shows Blocked Apps card, Blocked URLs card, Duration picker card, and Start button. The active state shows a circular timer, blocked items list, and unlock button.

### Problems
- **No visual hierarchy or emotional impact**: Cards are uniformly styled, equally weighted. Nothing draws the eye to the primary action
- **Timer is visually weak**: The circular progress indicator at 180dp is functional but uninspired. Top focus apps use the timer as the hero element
- **No celebration or completion feedback**: Session end is silent — no animation, sound, or congratulatory message
- **Start button buried at bottom**: The most important action requires scrolling past 3 cards
- **Empty states are plain text**: "No apps added yet" feels lifeless

### Recommendations

**2.1 — Redesign inactive state with clear visual hierarchy**

```
┌──────────────────────────────────────────┐
│  LargeTopAppBar: "FocusTime"    [⚙]     │
│                                           │
│  ┌─────────────────────────────────────┐ │
│  │  🎯 START FOCUS (Hero Button)       │ │
│  │  Big, rounded, primary color FAB    │ │
│  │  with ripple animation              │ │
│  │  Disabled state: "Add items first"  │ │
│  └─────────────────────────────────────┘ │
│                                           │
│  ⏱ Duration: [30m] [1h] [2h] [4h] [+]   │
│                                           │
│  ┌───────────────────────────────────┐   │
│  │ 📱 Apps (3)              [+ Add]  │   │
│  │ [Instagram ✕] [TikTok ✕] [YT ✕]  │   │
│  └───────────────────────────────────┘   │
│                                           │
│  ┌───────────────────────────────────┐   │
│  │ 🌐 URLs (1)              [+ Add]  │   │
│  │ [*.youtube.com ✕]                  │   │
│  └───────────────────────────────────┘   │
│                                           │
└──────────────────────────────────────────┘
```

Key changes:
- **Move the Start button to the top** as the hero element — a large, visually prominent rounded button with a subtle pulse animation when enabled
- **Inline the duration picker** directly below Start, not inside a card
- **Collapse Apps/URLs sections** into compact cards with counts in headers
- **Animate empty states**: Show a friendly illustration or Lottie animation instead of plain text

**2.2 — Redesign active session with immersive timer**

```
┌──────────────────────────────────────────┐
│  MediumTopAppBar: "Focus Active"         │
│                                           │
│  ┌─────────────────────────────────────┐ │
│  │         (full-width card)            │ │
│  │                                       │ │
│  │      ╭──────────────────╮            │ │
│  │      │    1:24:36       │            │ │
│  │      │   remaining      │            │ │
│  │      ╰──────────────────╯            │ │
│  │      ━━━━━━━━━━━━━░░░░░  (linear)   │ │
│  │                                       │ │
│  │  Blocking 3 apps, 1 URL              │ │
│  └─────────────────────────────────────┘ │
│                                           │
│  ┌───────────────────────────────────┐   │
│  │ Motivational quote (rotating)     │   │
│  └───────────────────────────────────┘   │
│                                           │
│  [ 🔓 Unlock with Password ]             │
│                                           │
└──────────────────────────────────────────┘
```

Key changes:
- **Larger, bolder timer typography** — use `displayLarge` with `FontWeight.Bold`
- **Add a linear progress bar** in addition to the circular one for at-a-glance progress
- **Add motivational content** — rotating quotes/tips to fill the screen with positive reinforcement
- **Show a summary** ("Blocking 3 apps, 1 URL") instead of listing every chip
- **Push the unlock button down** — it should not be easily accessible; this is intentional friction

**2.3 — Add session completion celebration**

When a session ends:
- Show a full-screen celebration overlay with confetti animation (use Lottie)
- Display session stats: "You stayed focused for 2 hours!"
- "Well done" message with a friendly illustration
- "Start Another" button
- This is the single highest-impact UX improvement for user retention

**Files to modify:** `HomeScreen.kt`, `HomeViewModel.kt`, new `ui/components/SessionCompleteOverlay.kt`

---

## 3. NAVIGATION & TRANSITIONS (High Priority - UI)

### Current State
Navigation (`MainActivity.kt`, lines 57-92) uses basic `NavHost` with `composable()` calls and no transition animations. Navigation between screens is instant — no enter/exit transitions, no shared element animations, no predictive back support.

### Problems
- Transitions feel abrupt and cheap — no sense of spatial relationship between screens
- No predictive back gesture support (mandatory since Android 15)
- No shared element transitions (stable since Compose 1.10, December 2025)
- The app feels static rather than fluid

### Recommendations

**3.1 — Enable predictive back navigation**

Add to `AndroidManifest.xml`:
```xml
<application android:enableOnBackInvokedCallback="true" ...>
```

Update navigation to use Navigation Compose 2.8.0+ built-in predictive back support. This gives free cross-fade animation on back gestures.

**3.2 — Add screen transition animations**

Define enter/exit transitions for each route:

| Transition | Animation |
|---|---|
| Home → AppPicker | Slide in from right + fade |
| Home → UrlManager | Slide in from right + fade |
| Home → Settings | Slide in from right + fade |
| Back navigation | Slide out to right + fade (via `popExitTransition`) |

Use `AnimatedNavHost` or configure `composable()` with `enterTransition`, `exitTransition`, `popEnterTransition`, `popExitTransition` parameters.

**3.3 — Add shared element transitions for blocked item chips**

When a user taps "Add Apps", the Apps card on the HomeScreen should visually morph into the AppPickerScreen. Use `Modifier.sharedElement()` (stable in Compose 1.10+) to connect the "Blocked Apps" header text across screens.

**Files to modify:** `MainActivity.kt`, `AndroidManifest.xml`

---

## 4. MICRO-INTERACTIONS & MOTION (Medium Priority - UI)

### Current State
The only animation in the entire app is `animateFloatAsState` on the circular progress indicator in `HomeScreen.kt` (line 156). Everything else is static.

### Problems
- The app feels lifeless and mechanical
- No feedback on user actions (tapping, toggling, adding/removing items)
- Material 3 Expressive (2025) emphasizes spring-based motion as a core design principle — this app has almost none
- Competing apps like Forest and Session use animation as a key differentiator

### Recommendations

**4.1 — Animate list item changes**

In `AppPickerScreen.kt`, add `animateItem()` modifier to LazyColumn items for smooth add/remove animations. Same for the FlowRow chips on all screens.

**4.2 — Add toggle animation in AppPicker**

When toggling an app's selection state, animate the icon transition from `RadioButtonUnchecked` → `CheckCircle` using `Crossfade` or `AnimatedContent`. Add a subtle scale bounce.

**4.3 — Animate the Start Focus button**

- Idle/enabled state: Subtle pulse animation (scale 1.0 → 1.02 → 1.0, 2s loop) to draw attention
- Press: Spring-based scale down + ripple
- After press: Morph into the timer card (shared element transition)

**4.4 — Chip add/remove animations**

When adding or removing a blocked item chip:
- Add: Chip scales from 0 → 1 with a spring overshoot
- Remove: Chip shrinks to 0 with fade-out
- Currently chips just appear/disappear instantly

**4.5 — Timer tick animation**

In active session, add a subtle bounce or pulse on each second tick of the countdown timer to create a "heartbeat" effect.

**4.6 — Loading states**

Replace the plain `CircularProgressIndicator` in `AppPickerScreen.kt` (line 100) with a shimmer/skeleton loading pattern — show placeholder app list items that shimmer while loading. This is now industry standard.

**Files to modify:** `AppPickerScreen.kt`, `HomeScreen.kt`, `BlockedItemChip.kt`, `DurationPicker.kt`, new `ui/components/ShimmerEffect.kt`

---

## 5. BLOCKED OVERLAY REDESIGN (Medium Priority - UI + UX)

### Current State
`BlockedOverlayActivity.kt` shows a simple centered column with a block icon, "App Blocked" text, countdown timer, "Go Back" button, and optional password unlock.

### Problems
- The overlay looks like an error screen rather than supportive encouragement
- No visual connection to the app the user was trying to open
- The block icon (red error icon) triggers a negative emotional response
- No motivational content to reinforce the user's focus goal
- The "Go Back" button label is vague — go back where?
- Password unlock flow is hidden behind an extra tap, but the whole unlock section sits right there

### Recommendations

**5.1 — Redesign with encouraging, branded tone**

Replace the error-style layout with a calming, branded design:

```
┌──────────────────────────────────────────┐
│                                           │
│        (Soft shield icon in primary)      │
│                                           │
│       "You're in Focus Mode"              │
│                                           │
│    "Instagram is blocked for another"     │
│           1:24:36                         │
│    ━━━━━━━━━━━━━░░░░░░░  progress bar    │
│                                           │
│    💡 "The secret of getting ahead is     │
│       getting started." — Mark Twain      │
│                                           │
│       [ Return to Home Screen ]           │
│                                           │
│       Unlock with password ▸              │
│                                           │
└──────────────────────────────────────────┘
```

Key changes:
- **Replace the error icon** with a calming shield or focus icon in the primary color
- **Name the blocked app**: "Instagram is blocked" instead of generic "App Blocked" — use the `EXTRA_PACKAGE_NAME` that's already passed but unused
- **Add motivational quotes** that rotate
- **Rename "Go Back" to "Return to Home Screen"** for clarity
- **Collapse the password section** into a subtle text link at the bottom, not a full button

**5.2 — Add blur/frosted glass background**

Apply a `RenderEffect.createBlurEffect()` or dim overlay to hint at the blocked app underneath. This creates a modern glassmorphism effect and reinforces "you can see it but can't use it."

**Files to modify:** `BlockedOverlayActivity.kt`, `strings.xml`

---

## 6. TYPOGRAPHY & VISUAL POLISH (Medium Priority - UI)

### Current State
The theme (`Theme.kt`) defines color schemes but uses **no custom typography** — it falls back to default Material 3 typography. The app doesn't define a `Typography` object at all.

### Problems
- Default Roboto everywhere makes the app feel generic
- No typographic hierarchy beyond what Material provides out of the box
- Timer display (`displayMedium`) could be more impactful with a custom font
- The app has no visual personality or brand identity beyond the green color

### Recommendations

**6.1 — Add custom typography**

Define a `Typography` object in `Theme.kt` using Google Fonts:

- **Display/Headline:** Use a rounded geometric sans-serif like **Nunito**, **Plus Jakarta Sans**, or **DM Sans** — these convey friendliness and calm
- **Body/Label:** Keep Roboto or use the same font at regular weight for readability
- **Timer:** Use a monospaced or tabular-figure variant so digits don't jump as they change

Pass the custom typography to `MaterialTheme(typography = ...)`.

**6.2 — Improve color token usage**

Current cards all use default surface color. Differentiate them:
- Apps section card: Use `surfaceContainerLow`
- URLs section card: Use `surfaceContainerLow`
- Duration picker card: Use `surfaceContainer`
- Active session timer card: Keep `primaryContainer`
- Info/hint cards: Keep `secondaryContainer`

This creates subtle visual hierarchy through color layering — a key Material 3 principle.

**6.3 — Add elevation/shadow depth**

- Active timer card: `tonalElevation = 4.dp` for prominence
- Regular cards: `tonalElevation = 1.dp`
- Start button: Use `shadowElevation` for floating effect

**Files to modify:** `Theme.kt`, `HomeScreen.kt`, `UrlManagerScreen.kt`

---

## 7. SETTINGS & QUALITY-OF-LIFE (Lower Priority - UX)

### Current State
Settings (`SettingsScreen.kt`) contains only master password management. The screen is sparse and has some usability issues.

### Problems
- Settings has only one feature — feels incomplete
- Password forms don't show/hide password toggle (all fields use `PasswordVisualTransformation` with no eye icon)
- No haptic feedback on any interaction
- No app version info, about section, or help
- The "Remove Password" flow is confusing — two-step confirmation with unclear instructions

### Recommendations

**7.1 — Add password visibility toggle**

Add a trailing icon to all password `OutlinedTextField` fields that toggles between `PasswordVisualTransformation()` and `VisualTransformation.None`. Use `Icons.Default.Visibility` / `Icons.Default.VisibilityOff`.

**7.2 — Expand Settings with additional sections**

```
Settings Screen Layout:
├── Master Password (existing, improved)
├── Permissions Status (new - see 1.3)
├── Appearance
│   ├── Theme (System / Light / Dark)
│   └── Dynamic Colors toggle
├── About
│   ├── Version info
│   ├── Privacy Policy link
│   ├── Open Source licenses
│   └── Made by T7Lab
└── Session History (future consideration)
```

**7.3 — Add haptic feedback**

Add light haptic feedback (`HapticFeedbackType.LightTap`) on:
- App toggle in AppPicker
- Chip removal
- Duration chip selection
- Session start
- Session end/unlock

Use `LocalHapticFeedback.current.performHapticFeedback()`.

**7.4 — Improve Remove Password UX**

Replace the two-step inline flow with a confirmation dialog:
1. User taps "Remove Password"
2. Dialog appears: "Enter current password to remove"
3. Password field + Confirm/Cancel buttons

This is clearer and more standard than the current inline approach.

**Files to modify:** `SettingsScreen.kt`, `SettingsViewModel.kt`, `AppPickerScreen.kt`, `HomeScreen.kt`

---

## Implementation Priority Matrix

| # | Change | Impact | Effort | Priority |
|---|--------|--------|--------|----------|
| 2.3 | Session completion celebration | Very High | Medium | **P0** |
| 1.1-1.2 | Onboarding + contextual permissions | Very High | High | **P0** |
| 2.1 | Home screen visual hierarchy redesign | High | Medium | **P1** |
| 5.1 | Blocked overlay redesign (encouraging tone) | High | Low | **P1** |
| 3.1 | Predictive back navigation | High | Low | **P1** |
| 3.2 | Screen transition animations | Medium | Low | **P1** |
| 7.1 | Password visibility toggle | Medium | Low | **P1** |
| 6.1 | Custom typography | Medium | Low | **P2** |
| 4.6 | Shimmer loading in AppPicker | Medium | Low | **P2** |
| 4.1-4.2 | List/toggle animations | Medium | Medium | **P2** |
| 2.2 | Active session immersive timer | Medium | Medium | **P2** |
| 4.3 | Start button pulse animation | Low-Med | Low | **P2** |
| 4.4 | Chip add/remove animations | Low-Med | Low | **P2** |
| 6.2-6.3 | Color token + elevation polish | Low-Med | Low | **P2** |
| 7.2 | Expanded settings screen | Low | Medium | **P3** |
| 7.3 | Haptic feedback | Low | Low | **P3** |
| 5.2 | Glassmorphism blur on overlay | Low | Medium | **P3** |
| 3.3 | Shared element transitions | Low | High | **P3** |
| 4.5 | Timer tick animation | Low | Low | **P3** |
| 7.4 | Remove password UX improvement | Low | Low | **P3** |

---

## New Dependencies Required

```toml
# libs.versions.toml additions
lottieCompose = "6.6.2"

# [libraries]
lottie-compose = { group = "com.airbnb.android", name = "lottie-compose", version.ref = "lottieCompose" }
```

Lottie is needed for:
- Onboarding illustrations
- Session completion confetti/celebration
- Empty state animations

---

## New Files to Create

```
ui/onboarding/
  ├── OnboardingScreen.kt        # 3-screen onboarding flow
  └── OnboardingViewModel.kt     # Tracks first-run state

ui/components/
  ├── SessionCompleteOverlay.kt  # Celebration on session end
  ├── ShimmerEffect.kt           # Shimmer loading placeholder
  ├── MotivationalQuotes.kt      # Rotating quotes data + composable
  └── PermissionCard.kt          # Permission status + request card

ui/theme/
  └── Type.kt                    # Custom Typography definition
```

---

## Files to Modify

| File | Changes |
|---|---|
| `MainActivity.kt` | Add onboarding gate, transition animations, predictive back, remove upfront permission requests |
| `AndroidManifest.xml` | Add `enableOnBackInvokedCallback="true"` |
| `HomeScreen.kt` | Redesigned layout, animations, session completion |
| `HomeViewModel.kt` | Session completion state, motivational quotes |
| `AppPickerScreen.kt` | Shimmer loading, item animations, toggle animation |
| `BlockedOverlayActivity.kt` | Encouraging redesign, named blocked app, quotes |
| `SettingsScreen.kt` | Password toggle, expanded sections, permission status |
| `DurationPicker.kt` | Selection animation |
| `BlockedItemChip.kt` | Add/remove animations |
| `Theme.kt` | Custom typography, verify color token usage |
| `strings.xml` | New copy for onboarding, celebrations, permissions, quotes |
| `libs.versions.toml` | Add Lottie dependency |
| `build.gradle.kts` | Add Lottie dependency |

---

## Summary

The current FocusTime app is **functionally complete but emotionally flat**. The three highest-ROI improvements are:

1. **Session completion celebration** — This is the "moment of delight" that turns a utility into an experience. Forest's tree, Focus Keeper's stats — every successful focus app has this.

2. **Onboarding + contextual permissions** — The current permission dump on first launch will cause >50% of users to abandon the app before ever using it. Fix this first.

3. **Home screen hierarchy + Start button prominence** — The primary action (starting a focus session) should be the most visually prominent element, not buried below three cards.

These three changes alone will transform FocusTime from "works correctly" to "feels great to use."
