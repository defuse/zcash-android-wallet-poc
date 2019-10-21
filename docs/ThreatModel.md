Reference Wallet Threat Model
==============================

This threat model is intended for users of the reference wallet. See the
[Invariant-Centric Threat Modeling](https://github.com/defuse/ictm) for
a complete explanation of the threat modeling methodology we're using, but the
short version of the story is:

- We believe the reference wallet satisfies the security invariants listed here.
- Users *shouldn't rely on any security or privacy properties that are not
  listed here*. If you would like to be able to rely on a property you don't see
  here, please raise an issue.

If you're a security auditor, please try to break one of the security
invariants! Please also think about important security invariants that might be
missing from this list!

While we pride ourselves on building high-quality software, all software
contains bugs, and so this threat model should not be construed as a *guarantee*
that our software satisfies these security properties.

## Global Assumptions

- The reference wallet app is installed on an Android device with all security
  patches applied.

## Usage Scenarios

- HONEST: There is a trust relationship between the user and the `lightwalletd`
  service they connect to. The `lightwalletd` service only ever provides valid
  information coming from a consistent Zcash blockchain state. The information
  is not guaranteed to be recent, and part of it may change (e.g. after
  a reorg). The connection to `lightwalletd` is protected by TLS.
- UNTRUSTED: The `lightwalletd` service the user connects to could be malicious.

## Adversaries

- APP is in control of another app on the end-user's device, and we assume
  APP knows one of the user's z-addresses.
- MITM can intercept and modify all traffic between the end-user's device
  and the Internet, and we assume MITM knows one of the user's z-addresses.
- LWDRO has read-only access to the `lightwalletd` server's memory and storage.
- In the UNTRUSTED scenario, LWD is in control of the `lightwalletd` server the
  user connects to.

### Adversary Classes

- TYPICAL: {APP, MITM, LWDRO, APP+LWDRO+MITM}

Here, the `+` sum of two adversaries is another adversary with the combination
of their capabilities.

## Security Invariants

In the HONEST scenario, TYPICAL adversaries cannot:

- Steal the user's funds.
- Burn or destroy the user's funds.
- Cause the user to lose their spending keys.
- Execute arbitrary code on the user's device.
- Make the user think they don't have funds when they actually do.
- Stop the wallet from functioning (denial of service) in a way that's permanent
  or difficult to recover from.
- Cause the user to send the wrong amount of ZEC to someone.
- Cause the user to send ZEC to anyone when they haven't intended to.
- Make the user think they've been robbed when they haven't.
- Send the user an official-looking message that isn't official (phishing).

In the UNTRUSTED scenario, LWD cannot:

- Execute arbitrary code on the user's device.
- Steal the user's funds.
- Burn or destroy the user's funds.
- Cause the user to lose their spending keys.

## Known Weaknesses

Some of the TYPICAL adversaries, and possibly others, can:

- Find out where the user is physically located (IP address, GPS).
- Find out who the user is (IP address).
- Temporarily stop the wallet from functioning by blocking the connection
  between the app and `lightwalletd`.
- Tell that the user is using a cryptocurrency wallet.
- Tell that the user is using *this* particular cryptocurrency wallet.
- Tell when the user is sending or receiving a transaction.

Using the reference wallet in the UNTRUSTED scenario is currently dangerous.
It's probably possible for LWD to permanently DoS the wallet and confuse the
user in various ways that may result in loss of funds.

This threat model is missing important details, e.g. about adversaries that have
physical access to the user's device. There might also be important distinctions
between z-addrs and t-addrs that it does not capture.

## Future Desired Invariants

*This is not part of the threat model, we'd track each of these invariants we
like a feature-to-be-implemented and add them to the threat model once we become
confident.*

We hope that one day, in the HONEST scenario, TYPICAL adversaries cannot:

- Trick the user into thinking some of their spent funds are unspent.
- Make the user think they received a transaction when they haven't.
- Make the user think their balance is greater than it really is.
- Find out how much ZEC the user has.
- Tell whether it's the same connecting to the lightwalletd server for the
  second time or someone else.
- Find out who the user is sending money to.
- Find out who is sending the user money.
- Make the user think outdated balance/transaction info is up-to-date.
- Make it look (to anyone else) like the user is spending money to someone they are not.
