# INITIATIVE

## Overview

We are implementing an update for an existing **banking application**.

> **Current state:** Each user in the app has a single account with a single balance.

---

## New Feature: Shared Virtual Card

A new feature allowing users to create a virtual card whose charges are **split across multiple accounts** belonging to different users.

---

## Card Creation

1. The user selects a list of accounts to link:
    - Their own account
    - Other users' accounts, looked up by **phone number**

2. The user defines a **percentage-based split** — how charges will be distributed across the selected accounts.

3. A **confirmation request notification** is sent to the apps of all other linked account holders.

4. The card is created only **after all linked accounts have confirmed**.

5. Once created:
    - The card is **added to all linked accounts**
    - All linked account holders receive a **creation notification**

---

## Updating Card Parameters

- The **charge split ratios** can be modified after the card is created.
- Any change requires **confirmation from every linked account**, same as during creation.

---

## Card Removal

- Any linked account holder can **independently detach themselves** from the card.
- Upon detachment:
    - All remaining linked accounts receive a **notification**
    - The card is **deleted for all remaining linked accounts** as well
