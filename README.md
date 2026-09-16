# The Jargons - Jarzy

Jarzy is an Android budgeting app built for parents and their kids to use together. A parent creates an account, registers their child, and sets up a monthly allowance along with minimum and maximum monthly spending limits. The child then gets their own login, where they can organise their money into savings categories, log their own expenses (with an optional receipt photo), and track their spending over time - all while the parent can check in on their child's account, top up their balance, and adjust their settings from their own side of the app.

## Featured Screens

- **Login** - a single screen where either a parent or a child can log in, with a toggle to choose which account type is signing in.
- **Register (Parent / Child)** - separate registration screens for creating a new parent account or registering a child under an existing parent, including the child's starting savings category, monthly allowance, and minimum/maximum monthly spend.
- **Parent Home** - lists the parent's registered children and lets the parent register a new child or open a selected child's detail screen.
- **Child Detail (Parent view)** - the parent's view of one specific child, split into three tabs: Stats (balance and budget summary, with account editing), Categories (the child's savings categories as swipeable cards), and History (a per-category spending summary with a link to the full expense history).
- **Child Home** - the child's own landing screen after logging in, organised the same way as the parent's Child Detail screen (Stats / Categories / History tabs), so a child manages their own account with the same layout the parent uses to view it.
- **Move Money Between Categories** - lets money already saved in one category be shifted into another, without ever creating money that wasn't there to begin with.
- **Add Expense** - logs a new expense against a chosen savings category, with an optional photo of the receipt.
- **Expense History** - a filterable log of a child's expenses, with a date range and a toggle between viewing every entry or just each category's total.

## Features/Functionality

- Separate parent and child account types, each with their own login and registration flow
- Input validation on login and registration, including minimum username length, minimum password length, and a required special character in passwords
- Savings categories that a child can add, rename, and delete, with money automatically redirected to a protected "General" category if one is deleted
- A parent-set monthly allowance, plus minimum and maximum monthly spending limits, editable at any time from the child's detail screen
- A child's balance is always calculated as the sum of their own categories, rather than stored separately, so it can never drift out of sync
- Money can be moved between a child's own categories, or added to their balance by the parent, but is never created out of nowhere
- Expense logging with an optional receipt photo attached to each entry
- Expense history with date-range filtering and a toggle between an entry-by-entry view and a per-category totals view
- Data is stored locally on the device using Room, so accounts and balances persist between sessions

## How to Use

1. On the login screen, choose **Register as Parent** to create a parent account, or log in if you already have one.
2. Once logged in as a parent, use **Register a Child Account** to create a child account - this is also where the child's starting savings category, monthly allowance, and minimum/maximum monthly spend are set.
3. From the parent's home screen, select a child from the list to open their detail screen, where their balance, categories, and account settings can all be viewed and edited.
4. Log out and log back in using the child's username and password to see the app from the child's side.
5. From the child's home screen, use the **Categories** tab to add, rename, or delete savings categories, or to move money between them.
6. Use **Log an Expense** to record spending against a category, with an optional receipt photo.
7. Use **View Expense History** to look back over past spending, filter by date range, and switch between the full entry list and category totals.

## Developers

- *Danielle Poulton ST10440070 - Lead Programmer*
- *Giselle Khan ST10447061 - Secondary Programmer*
- *Ivan Willaims ST10439493 - UI Designer*

