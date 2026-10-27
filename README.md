# The Jargons - Jarzy

Jarzy is an Android budgeting app built for parents and their kids to use together. A parent creates an account, registers their child, and sets up a monthly allowance along with minimum and maximum monthly spending limits. The child then gets their own login, where they can organise their money into savings categories, complete quests to earn extra money, log their own expenses (with an optional receipt photo), and track their spending over time - all while the parent can check in on their child's account, assign and confirm quests, top up their balance, and adjust their settings from their own side of the app.

As of Part 3, the app's data (parent accounts, child accounts, savings categories, expenses and quests) is stored in and synced through Firebase Firestore, instead of the local Room database Part 2 used - so a child's balance and history now live in the cloud rather than being tied to one device.

## Featured Screens

- **Login** - a single screen where either a parent or a child can log in, with a toggle to choose which account type is signing in.
- **Register (Parent / Child)** - separate registration screens for creating a new parent account or registering a child under an existing parent, including the child's starting savings category, monthly allowance, and minimum/maximum monthly spend.
- **Parent Home** - lists the parent's registered children and lets the parent register a new child or open a selected child's detail screen.
- **Child Detail (Parent view)** - the parent's view of one specific child, now split into four tabs: Stats (balance and budget summary, with account editing and the min/max monthly spend gauge), Categories (the child's savings categories as swipeable jar cards), Quests (the child's chore list, with a progress tracker and a batch "Confirm" action that pays out every completed quest at once), and History (a per-category spending summary with a link to the full expense history).
- **Child Home** - the child's own landing screen after logging in, organised the same way as the parent's Child Detail screen (Stats / Categories / Quests / History tabs), so a child manages their own account with the same layout the parent uses to view it.
- **Move Money Between Categories** - lets money already saved in one category be shifted into another, without ever creating money that wasn't there to begin with.
- **Add Expense** - logs a new expense against a chosen savings category, with an optional photo of the receipt.
- **Expense History** - a filterable log of a child's expenses, with a date range, a toggle between viewing every entry or just each category's total, and a bar graph of spending per category with the child's monthly min/max spend goals drawn across it as reference lines.

## Features/Functionality

- Separate parent and child account types, each with their own login and registration flow
- Input validation on login and registration, including minimum username length, minimum password length, and a required special character in passwords
- Savings categories that a child can add, rename, and delete, with money automatically redirected to a protected "General" category if one is deleted
- A parent-set monthly allowance, plus minimum and maximum monthly spending limits, editable at any time from the child's detail screen, with a half-circle gauge showing how this month's spending compares against those limits at a glance
- A child's balance is always calculated as the sum of their own categories, rather than stored separately, so it can never drift out of sync
- Money can be moved between a child's own categories, or added to their balance by the parent, but is never created out of nowhere
- A quest/chore system - the parent assigns quests (once-off, daily or weekly) with a cash reward, the child checks them off, and the parent confirms and pays out everything completed in one batch
- Expense logging with an optional receipt photo attached to each entry
- Expense history with date-range filtering, a toggle between an entry-by-entry view and a per-category totals view, and a spend-per-category graph with the monthly min/max goals overlaid as reference lines
- A per-category overspending warning, shown the moment an expense pushes that category's spending past its own budget for the month
- A jar/wallet visual theme throughout - each savings category is drawn as its own coloured jar graphic rather than a plain shape, so categories are easy to tell apart at a glance
- Data is stored in Firebase Firestore, so a child's accounts, categories and history are available independent of any one device, and update as soon as they're saved

## Own Features (Part 3)

Beyond what the Part 3 brief asked for, we built three extra features:

1. **Quest / chore system** - parents can create quests for a child (a name, a cash reward, and a schedule: once-off, daily, or weekly), which the child then checks off as they're completed. Checking a quest off doesn't pay out straight away - the parent's view shows a running progress tracker (e.g. "3 of 5 done today"), and once they're happy, a single "Confirm" action pays every completed quest's reward into the child's balance at once. Daily and weekly quests reset automatically on their own schedule rather than needing to be recreated each time.
2. **General Savings jar polish** - every child automatically has a protected "General" category that can't be deleted. Any money that isn't routed to a specific savings category (allowance, quest payouts, etc.) lands here by default, so nothing is ever left with nowhere to go.
3. **Overspending warnings** - each savings category can have its own monthly budget. If logging an expense pushes that category's spending for the month past its budget, the app shows a clear warning at the moment the expense is saved, so the parent notices without the app blocking the expense outright (the money might genuinely still be there - it's a nudge, not a hard stop).

## How to Use

1. On the login screen, choose **Register as Parent** to create a parent account, or log in if you already have one.
2. Once logged in as a parent, use **Register a Child Account** to create a child account - this is also where the child's starting savings category, monthly allowance, and minimum/maximum monthly spend are set.
3. From the parent's home screen, select a child from the list to open their detail screen, where their balance, categories, quests, and account settings can all be viewed and edited.
4. From the **Quests** tab, assign a chore with a reward and a schedule, and confirm payouts once the child has checked them off.
5. Log out and log back in using the child's username and password to see the app from the child's side.
6. From the child's home screen, use the **Categories** tab to add, rename, or delete savings categories, or to move money between them.
7. Use **Log an Expense** to record spending against a category, with an optional receipt photo - watch for the overspend warning if a category goes over its monthly budget.
8. Use **View Expense History** to look back over past spending, filter by date range, switch between the full entry list and category totals, and see the spend-per-category graph against the monthly min/max goals.

## Developers

- *Danielle Poulton ST10440070 - Lead Programmer*
- *Giselle Khan ST10447061 - Secondary Programmer*
- *Ivan Willaims ST10439493 - UI Designer*

## Youtube Link
https://youtu.be/tedGs6W2YKQ*
