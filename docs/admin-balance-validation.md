# Administrator balance validation

Run `./gradlew build` (Windows: `gradlew.bat build`) for compilation and automated
decimal/arithmetic tests. Live server checks below require a disposable development
server, two accounts, and a client without MMO Econ. Never copy production balances
into the test server. Use its own working/config directory.

## Commands and permission checks

1. Join with both accounts to create stored balances. Record their initial values.
2. As a non-OP, verify `/bal` works but `get`, `set`, `add`, `subtract`, and `admin`
   are inaccessible, including attempts to type the commands directly.
3. As an OP level 2 player, set the other account to `12.34`, add `0.01`, and subtract
   `0.35`. `/bal get <target>` must show `12.00`; target `/bal` must show `$12`.
4. From console, repeat get/set/add/subtract; `/bal admin` must require a player.
5. Log the target out. Repeat using its cached username. Rejoin and verify
   its balance was retained, rather than reset to the starting amount.
6. Reject unknown names and UUID input, including an existing account's UUID, and
   confirm no account was created. Tab completion must suggest usernames only.
7. Reject `-1`, `0.001`, `1.000`, `1e2`, `NaN`, `1,000`, and `92233720368547758.08`.
   Reject add/subtract `0`; allow set `0`. Verify failed attempts leave the account unchanged.
8. Set to `92233720368547758.07`; reject adding `0.01`. Restore a modest balance
   before testing other economy features (their existing arithmetic is unchanged).
9. Reject subtracting more than the current balance; allow subtracting exactly it.
10. With a NeoForge permission handler installed, check explicit admin grants to a
    non-OP and explicit denials to an OP. Also deny `mmoecon.command.bal` and verify
    the `/bal` tree remains inaccessible despite an admin-node grant.

## Vanilla chest GUI

1. Open `/bal admin` without MMO Econ on the client. Check names, UUIDs, exact
   balances, refresh, empty state, and paging (if more than 45 accounts exist).
2. Select an account and verify all amount buttons, reset, and operation switching.
3. Apply set/add/subtract changes; cancel a change and verify no money moved.
4. Trigger insufficient funds and overflow; verify no money moved.
5. While the confirmation screen is open, change the target balance from console.
   Apply must refuse with a request to review and confirm again.
6. Revoke the administrator's permission while the menu is open. The menu must
   close/refuse actions and must not change balances.
7. Try right clicks, shift clicks, number-key swaps, drag, dropping, and double clicks.
   Display items must never be obtainable and unsupported click types must not
   trigger actions. Each completed confirmation applies once.

## Persistence and regression checks

1. Check `config/mmoecon/transactions.log` for actor identity, target UUID, operation,
   and exact amount/before/after values for successful mutations.
2. Wait at least 1,200 ticks after a change, restart, and verify the balance.
3. Make a new change and stop normally before the periodic flush; restart and verify.
4. In a disposable copy only, simulate a save failure. Confirm it is logged and a
   later scheduled save retries after restoring write access. Command success
   describes the live balance, not completed persistence.
5. Exercise `/bal`, `/money`, `/bal top`, `/pay`, `/shop`, `/sell hand`, `/sell inv`,
   receipt refunds, playtime rewards, and `/ah` buy/list/delist, including an offline
   seller. Confirm behavior and server-side GUI rendering remain unchanged.

## Runtime investigation

For a reproduced runtime failure, inspect the command/menu entry point,
`BalanceAdministration.execute`, the matching `PlayerBalanceManager` mutation,
and the relevant load/save event. Record the target UUID, amount in cents,
permission result, before/after balance, server thread, and call order. Use
breakpoints or logpoints in IntelliJ before changing the implementation.
